package com.github.stevenbuglione.spice.goland;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SpiceGenerateBeforeRunTaskProvider
        extends BeforeRunTaskProvider<SpiceGenerateBeforeRunTask>
        implements DumbAware {
    static final Key<SpiceGenerateBeforeRunTask> ID =
            Key.create("Spice.GenerateBeforeRun");
    private static final int GENERATION_TIMEOUT_MILLIS = 120_000;
    private static final int MAXIMUM_ERROR_CHARACTERS = 8_192;

    @Override
    public @NotNull Key<SpiceGenerateBeforeRunTask> getId() {
        return ID;
    }

    @Override
    public @NotNull String getName() {
        return "Generate Spice application";
    }

    @Override
    public @Nullable SpiceGenerateBeforeRunTask createTask(
            @NotNull RunConfiguration configuration
    ) {
        if (!(configuration instanceof SpiceApplicationConfiguration)) {
            return null;
        }
        return new SpiceGenerateBeforeRunTask();
    }

    @Override
    public boolean canExecuteTask(
            @NotNull RunConfiguration configuration,
            @NotNull SpiceGenerateBeforeRunTask task
    ) {
        return configuration instanceof SpiceApplicationConfiguration
                && task.isEnabled();
    }

    @Override
    public boolean executeTask(
            @NotNull DataContext context,
            @NotNull RunConfiguration runConfiguration,
            @NotNull ExecutionEnvironment environment,
            @NotNull SpiceGenerateBeforeRunTask task
    ) {
        if (DefaultRunExecutor.EXECUTOR_ID.equals(
                environment.getExecutor().getId()
        )) {
            return true;
        }
        if (!(runConfiguration
                instanceof SpiceApplicationConfiguration configuration)) {
            return false;
        }
        try {
            CapturingProcessHandler handler = new CapturingProcessHandler(
                    generationCommandLine(configuration)
            );
            ProgressIndicator indicator =
                    ProgressManager.getInstance().getProgressIndicator();
            ProcessOutput output = indicator == null
                    ? handler.runProcess(GENERATION_TIMEOUT_MILLIS, true)
                    : handler.runProcessWithProgressIndicator(
                            indicator,
                            GENERATION_TIMEOUT_MILLIS,
                            true
                    );
            if (!output.isTimeout()
                    && !output.isCancelled()
                    && output.getExitCode() == 0) {
                return true;
            }
            notifyFailure(
                    configuration,
                    output.isTimeout()
                            ? "Spice generation timed out."
                            : combinedOutput(output)
            );
        } catch (ExecutionException exception) {
            notifyFailure(configuration, exception.getMessage());
        }
        return false;
    }

    @SuppressWarnings("rawtypes")
    static void attach(SpiceApplicationConfiguration configuration) {
        RunManagerEx manager = RunManagerEx.getInstanceEx(
                configuration.getProject()
        );
        List<BeforeRunTask> tasks = new ArrayList<>(
                manager.getBeforeRunTasks(configuration)
        );
        for (BeforeRunTask task : tasks) {
            if (ID.equals(task.getProviderId())) {
                task.setEnabled(true);
                manager.setBeforeRunTasks(configuration, tasks);
                return;
            }
        }
        SpiceGenerateBeforeRunTask task = new SpiceGenerateBeforeRunTask();
        task.setEnabled(true);
        tasks.add(task);
        manager.setBeforeRunTasks(configuration, tasks);
    }

    static GeneralCommandLine generationCommandLine(
            SpiceApplicationConfiguration configuration
    ) throws ExecutionException {
        List<String> arguments = new ArrayList<>();
        arguments.add(SpiceExecutable.resolve());
        arguments.add("generate");
        if (!configuration.getSpiceTarget().isBlank()) {
            arguments.add("--target");
            arguments.add(configuration.getSpiceTarget());
        }
        arguments.add(SpiceApplicationRunningState.pattern(configuration));
        GeneralCommandLine commandLine = new GeneralCommandLine(arguments)
                .withCharset(StandardCharsets.UTF_8)
                .withEnvironment(configuration.getCustomEnvironment())
                .withParentEnvironmentType(
                        configuration.isPassParentEnvironment()
                                ? GeneralCommandLine.ParentEnvironmentType.SYSTEM
                                : GeneralCommandLine.ParentEnvironmentType.NONE
                );
        String workingDirectory =
                SpiceApplicationRunningState.workingDirectory(configuration);
        if (!workingDirectory.isBlank()) {
            commandLine.withWorkDirectory(workingDirectory);
        }
        return commandLine;
    }

    private static void notifyFailure(
            SpiceApplicationConfiguration configuration,
            String detail
    ) {
        String content = detail == null || detail.isBlank()
                ? "Spice generation failed without diagnostic output."
                : detail.strip();
        content = StringUtil.shortenTextWithEllipsis(
                content,
                MAXIMUM_ERROR_CHARACTERS,
                0
        );
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Spice")
                .createNotification(
                        "Spice generation failed",
                        "<pre>" + StringUtil.escapeXmlEntities(content) + "</pre>",
                        NotificationType.ERROR
                )
                .notify(configuration.getProject());
    }

    private static String combinedOutput(ProcessOutput output) {
        String stdout = output.getStdout().strip();
        String stderr = output.getStderr().strip();
        if (stdout.isBlank()) {
            return stderr;
        }
        if (stderr.isBlank()) {
            return stdout;
        }
        return stdout + "\n" + stderr;
    }
}
