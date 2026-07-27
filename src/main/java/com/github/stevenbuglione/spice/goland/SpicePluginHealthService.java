package com.github.stevenbuglione.spice.goland;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.platform.lsp.api.LspClient;
import com.intellij.platform.lsp.api.LspClientManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Maintains the bounded, read-only health information shown by the plugin.
 *
 * <p>Refreshes run outside the Swing event thread. Commands inherit no module
 * download permission: this service only asks locally installed executables for
 * their versions and reads the workspace go.mod directly.
 */
@Service(Service.Level.PROJECT)
public final class SpicePluginHealthService implements Disposable {
    private static final int COMMAND_TIMEOUT_MILLIS = 5_000;
    private static final int OUTPUT_LIMIT = 2_000;

    private final Project project;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile Snapshot snapshot;

    public SpicePluginHealthService(Project project) {
        this.project = project;
        this.snapshot = Snapshot.pending(project.getBasePath());
    }

    static SpicePluginHealthService getInstance(Project project) {
        return project.getService(SpicePluginHealthService.class);
    }

    Snapshot snapshot() {
        return snapshot;
    }

    boolean isRefreshing() {
        return refreshing.get();
    }

    void addListener(Runnable listener, Disposable parent) {
        listeners.add(listener);
        Disposer.register(parent, () -> listeners.remove(listener));
    }

    void refresh() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            Snapshot next;
            try {
                next = inspect();
            } catch (RuntimeException exception) {
                next = snapshot.withFailure(
                        bounded(exception.getMessage())
                );
            }
            snapshot = next;
            refreshing.set(false);
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed()) {
                    listeners.forEach(Runnable::run);
                }
            });
        });
    }

    private Snapshot inspect() {
        String basePath = project.getBasePath();
        Path moduleRoot = findModuleRoot(basePath);
        Path goMod = moduleRoot == null ? null : moduleRoot.resolve("go.mod");
        List<String> tools = goMod == null
                ? List.of()
                : parseToolDirectives(read(goMod));
        boolean vendor = moduleRoot != null
                && Files.isDirectory(moduleRoot.resolve("vendor"));
        String spiceExecutable = SpiceExecutable.resolve();
        CommandResult spiceVersion = run(
                moduleRoot,
                spiceExecutable,
                "version"
        );
        CommandResult goVersion = run(moduleRoot, "go", "version");
        String failure = firstFailure(spiceVersion, goVersion);
        return new Snapshot(
                spiceExecutable,
                value(spiceVersion),
                value(goVersion),
                moduleRoot == null ? "" : moduleRoot.toString(),
                lspState(),
                vendor ? "vendor / offline" : "module read-only / offline",
                tools,
                failure
        );
    }

    private String lspState() {
        Collection<LspClient> clients = LspClientManager.getInstance(project)
                .getClients(SpiceLspIntegrationProvider.class);
        if (clients.isEmpty()) {
            return Boolean.getBoolean("spice.lsp.disabled")
                    ? "disabled"
                    : "not started";
        }
        return clients.stream()
                .map(client -> client.getState().name())
                .sorted()
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("not started");
    }

    private static String firstFailure(CommandResult... results) {
        for (CommandResult result : results) {
            if (!result.success()) {
                return result.failure();
            }
        }
        return "";
    }

    private static String value(CommandResult result) {
        return result.success() ? result.output() : "unavailable";
    }

    private static CommandResult run(
            @Nullable Path directory,
            String executable,
            String... arguments
    ) {
        try {
            GeneralCommandLine commandLine = new GeneralCommandLine()
                    .withExePath(executable)
                    .withParameters(arguments)
                    .withCharset(StandardCharsets.UTF_8);
            if (directory != null) {
                commandLine.withWorkDirectory(directory.toFile());
            }
            ProcessOutput output = new CapturingProcessHandler(commandLine)
                    .runProcess(COMMAND_TIMEOUT_MILLIS, true);
            String combined = bounded((
                    output.getStdout() + "\n" + output.getStderr()
            ).strip());
            if (output.isTimeout()) {
                return new CommandResult(
                        false,
                        "",
                        executable + " timed out after five seconds"
                );
            }
            if (output.isCancelled()) {
                return new CommandResult(
                        false,
                        "",
                        executable + " was cancelled"
                );
            }
            if (output.getExitCode() != 0) {
                return new CommandResult(
                        false,
                        "",
                        executable + " exited "
                                + output.getExitCode()
                                + ": "
                                + combined
                );
            }
            return new CommandResult(true, combined, "");
        } catch (ExecutionException exception) {
            return new CommandResult(
                    false,
                    "",
                    executable + " could not start: "
                            + bounded(exception.getMessage())
            );
        }
    }

    static List<String> parseToolDirectives(String goMod) {
        List<String> result = new ArrayList<>();
        boolean toolBlock = false;
        for (String raw : goMod.lines().toList()) {
            String line = stripComment(raw).strip();
            if (line.equals("tool (")) {
                toolBlock = true;
                continue;
            }
            if (toolBlock && line.equals(")")) {
                toolBlock = false;
                continue;
            }
            String tool = "";
            if (toolBlock) {
                tool = firstField(line);
            } else if (line.startsWith("tool ")) {
                tool = firstField(line.substring("tool ".length()));
            }
            if (!tool.isBlank() && !result.contains(tool)) {
                result.add(tool);
            }
        }
        return result.stream().sorted().toList();
    }

    private static String stripComment(String value) {
        int comment = value.indexOf("//");
        return comment < 0 ? value : value.substring(0, comment);
    }

    private static String firstField(String value) {
        String stripped = value.strip();
        int whitespace = 0;
        while (whitespace < stripped.length()
                && !Character.isWhitespace(stripped.charAt(whitespace))) {
            whitespace++;
        }
        return stripped.substring(0, whitespace);
    }

    private static @Nullable Path findModuleRoot(@Nullable String basePath) {
        if (basePath == null || basePath.isBlank()) {
            return null;
        }
        Path candidate = Path.of(basePath).toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("go.mod"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        return null;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            return "";
        }
    }

    private static String bounded(@Nullable String value) {
        if (value == null) {
            return "";
        }
        String stripped = value.strip();
        return stripped.length() <= OUTPUT_LIMIT
                ? stripped
                : stripped.substring(0, OUTPUT_LIMIT) + "…";
    }

    @Override
    public void dispose() {
        listeners.clear();
    }

    record Snapshot(
            String executable,
            String spiceVersion,
            String goVersion,
            String moduleRoot,
            String lspState,
            String dependencyMode,
            List<String> authorizedTools,
            String lastFailure
    ) {
        static Snapshot pending(@Nullable String basePath) {
            return new Snapshot(
                    SpiceExecutable.resolve(),
                    "pending refresh",
                    "pending refresh",
                    basePath == null ? "" : basePath,
                    "pending refresh",
                    "pending refresh",
                    List.of(),
                    ""
            );
        }

        Snapshot withFailure(String failure) {
            return new Snapshot(
                    executable,
                    spiceVersion,
                    goVersion,
                    moduleRoot,
                    lspState,
                    dependencyMode,
                    authorizedTools,
                    failure
            );
        }

        @NotNull String render() {
            StringBuilder result = new StringBuilder()
                    .append("Spice executable: ").append(executable).append('\n')
                    .append("Spice version: ").append(spiceVersion).append('\n')
                    .append("Go version: ").append(goVersion).append('\n')
                    .append("Module root: ").append(moduleRoot).append('\n')
                    .append("LSP state: ").append(lspState).append('\n')
                    .append("Dependency mode: ")
                    .append(dependencyMode)
                    .append('\n')
                    .append("Authorized annotation tools:");
            if (authorizedTools.isEmpty()) {
                result.append(" none");
            } else {
                for (String tool : authorizedTools) {
                    result.append("\n  • ").append(tool);
                }
            }
            if (!lastFailure.isBlank()) {
                result.append("\n\nLast failure:\n").append(lastFailure);
            }
            return result.toString();
        }
    }

    private record CommandResult(
            boolean success,
            String output,
            String failure
    ) {}
}
