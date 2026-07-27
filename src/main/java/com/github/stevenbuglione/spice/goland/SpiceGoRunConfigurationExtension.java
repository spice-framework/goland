package com.github.stevenbuglione.spice.goland;

import com.goide.execution.GoBuildingRunConfiguration;
import com.goide.execution.GoRunConfigurationBase;
import com.goide.execution.GoRunningState;
import com.goide.execution.application.GoApplicationConfiguration;
import com.goide.execution.extension.GoRunConfigurationExtension;
import com.goide.util.GoExecutor;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.target.TargetedCommandLineBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fails closed when an old or explicitly selected Go single-file
 * configuration attempts to execute a Spice application.
 */
public final class SpiceGoRunConfigurationExtension
        extends GoRunConfigurationExtension {
    private static final Pattern MAIN_FUNCTION = Pattern.compile(
            "(?m)^\\h*func\\h+main\\h*\\("
    );
    private static final Pattern RAW_ANNOTATION = Pattern.compile(
            "(?m)^\\h*@[A-Za-z_][A-Za-z0-9_.]*"
    );
    private static final Pattern APPLICATION_ANNOTATION = Pattern.compile(
            "(?m)^\\h*//\\h*@Application(?:\\h|\\(|$)"
    );
    private static final Pattern APPLICATION_IMPORT = Pattern.compile(
            "(?m)^\\h*//\\h*@(?:spice\\.)?import\\h+"
                    + "(?:\\{[^}\\r\\n]*\\bApplication\\b[^}\\r\\n]*}"
                    + "|\\*\\h+as\\h+[A-Za-z_][A-Za-z0-9_]*)"
    );

    @Override
    public boolean isApplicableFor(
            @NotNull GoRunConfigurationBase<?> configuration
    ) {
        return configuration instanceof GoApplicationConfiguration
                && !(configuration instanceof SpiceApplicationConfiguration);
    }

    @Override
    public boolean isEnabledFor(
            @NotNull GoRunConfigurationBase<?> configuration,
            @Nullable RunnerSettings runnerSettings
    ) {
        return isApplicableFor(configuration);
    }

    @Override
    protected void validateConfiguration(
            @NotNull GoRunConfigurationBase<?> configuration,
            boolean execution
    ) throws ExecutionException {
        validateSingleFileConfiguration(configuration);
    }

    @Override
    protected void patchExecutor(
            @NotNull GoRunConfigurationBase<?> configuration,
            @Nullable RunnerSettings runnerSettings,
            @NotNull GoExecutor executor,
            @NotNull String runnerId,
            @NotNull GoRunningState<? extends GoRunConfigurationBase<?>> state,
            @NotNull GoRunningState.CommandLineType commandLineType
    ) throws ExecutionException {
        validateSingleFileConfiguration(configuration);
        super.patchExecutor(
                configuration,
                runnerSettings,
                executor,
                runnerId,
                state,
                commandLineType
        );
    }

    @Override
    protected void patchCommandLine(
            @NotNull GoRunConfigurationBase<?> configuration,
            @Nullable RunnerSettings runnerSettings,
            @NotNull TargetedCommandLineBuilder commandLine,
            @NotNull String runnerId,
            @NotNull GoRunningState<? extends GoRunConfigurationBase<?>> state,
            @NotNull GoRunningState.CommandLineType commandLineType
    ) throws ExecutionException {
        validateSingleFileConfiguration(configuration);
        super.patchCommandLine(
                configuration,
                runnerSettings,
                commandLine,
                runnerId,
                state,
                commandLineType
        );
    }

    static void validateSingleFileConfiguration(
            GoRunConfigurationBase<?> configuration
    ) throws ExecutionException {
        if (!(configuration instanceof GoApplicationConfiguration application)
                || application instanceof SpiceApplicationConfiguration
                || application.getKind()
                != GoBuildingRunConfiguration.Kind.FILE) {
            return;
        }
        for (String filePath : application.getFilePaths()) {
            validateSource(sourceText(filePath));
        }
    }

    static void validateSource(String source) throws ExecutionException {
        if (!MAIN_FUNCTION.matcher(source).find()) {
            return;
        }
        if (RAW_ANNOTATION.matcher(source).find()) {
            throw new ExecutionException(
                    "This Spice application contains a raw @ annotation. "
                            + "Convert it to a valid // @ annotation before "
                            + "running the complete package."
            );
        }
        if (APPLICATION_ANNOTATION.matcher(source).find()
                || APPLICATION_IMPORT.matcher(source).find()) {
            throw new ExecutionException(
                    "A Spice application cannot run as a temporary Go "
                            + "single file. Select the Spice Application "
                            + "configuration so generated files and valid "
                            + "annotation comments remain in the complete "
                            + "package."
            );
        }
    }

    private static String sourceText(String filePath)
            throws ExecutionException {
        String normalized = FileUtil.toSystemIndependentName(filePath);
        VirtualFile file = LocalFileSystem.getInstance()
                .findFileByPath(normalized);
        if (file != null) {
            Document document = FileDocumentManager.getInstance()
                    .getDocument(file);
            if (document != null) {
                return document.getImmutableCharSequence().toString();
            }
        }
        try {
            return Files.readString(Path.of(filePath));
        } catch (IOException exception) {
            throw new ExecutionException(
                    "read Go single-file run source " + filePath,
                    exception
            );
        }
    }
}
