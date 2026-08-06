package com.github.stevenbuglione.spice.goland;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Creates the installed-IDE fixture module without invalid tracked source. */
final class SpiceFixtureGoMod {
    static final String CORE_MODULE = "github.com/spice-framework/spice";
    static final String TOOLCHAIN_MODULE =
            "github.com/spice-framework/toolchain";

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);

    private SpiceFixtureGoMod() {
    }

    static void materialize(
            Path source,
            Path destination,
            Path core,
            Path toolchain
    ) throws IOException, InterruptedException {
        Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
        run(
                destination.getParent(),
                List.of(
                        "go",
                        "mod",
                        "edit",
                        "-replace=" + CORE_MODULE + "=" + goPath(core),
                        "-replace=" + TOOLCHAIN_MODULE + "="
                                + goPath(toolchain),
                        destination.toString()
                )
        );
    }

    static String inspect(Path goMod) throws IOException, InterruptedException {
        return run(
                goMod.getParent(),
                List.of(
                        "go",
                        "mod",
                        "edit",
                        "-json",
                        goMod.toString()
                )
        );
    }

    static List<Path> trackedGoModules(Path repository)
            throws IOException, InterruptedException {
        String output = run(
                repository,
                List.of(
                        "git",
                        "-C",
                        repository.toString(),
                        "ls-files",
                        "--",
                        "go.mod",
                        ":(glob)**/go.mod"
                )
        );
        List<Path> modules = new ArrayList<>();
        for (String line : output.lines().toList()) {
            if (!line.isBlank()) {
                modules.add(repository.resolve(line).normalize());
            }
        }
        return List.copyOf(modules);
    }

    static String goPath(Path path) {
        return path.toAbsolutePath()
                .normalize()
                .toString()
                .replace('\\', '/');
    }

    private static String run(Path directory, List<String> arguments)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(arguments)
                .directory(directory.toFile())
                .redirectErrorStream(true);
        builder.environment().put("GOWORK", "off");
        builder.environment().put("GOPROXY", "off");
        builder.environment().put("GOTOOLCHAIN", "local");
        Process process = builder.start();
        boolean completed = process.waitFor(
                COMMAND_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
        );
        if (!completed) {
            process.destroyForcibly();
            throw new IOException(
                    String.join(" ", arguments) + " exceeded "
                            + COMMAND_TIMEOUT
            );
        }
        String output = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );
        if (process.exitValue() != 0) {
            throw new IOException(
                    String.join(" ", arguments) + " failed: " + output.strip()
            );
        }
        return output;
    }
}
