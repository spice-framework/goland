package com.github.stevenbuglione.spice.goland;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.ProgramParametersConfigurator;
import com.intellij.util.execution.ParametersListUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

final class SpiceApplicationRunningState extends CommandLineState {
    private final SpiceApplicationConfiguration configuration;

    SpiceApplicationRunningState(
            ExecutionEnvironment environment,
            SpiceApplicationConfiguration configuration
    ) {
        super(environment);
        this.configuration = configuration;
    }

    @Override
    protected @NotNull ProcessHandler startProcess()
            throws ExecutionException {
        return new KillableColoredProcessHandler(commandLine(configuration));
    }

    static GeneralCommandLine commandLine(
            SpiceApplicationConfiguration configuration
    ) throws ExecutionException {
        List<String> arguments = new ArrayList<>();
        arguments.add(SpiceExecutable.resolve());
        arguments.add("run");
        if (!configuration.getSpiceTarget().isBlank()) {
            arguments.add("--target");
            arguments.add(configuration.getSpiceTarget());
        }
        arguments.add(pattern(configuration));
        if (!configuration.getParams().isBlank()) {
            arguments.add("--");
            arguments.addAll(ParametersListUtil.parse(configuration.getParams()));
        }
        GeneralCommandLine commandLine = new GeneralCommandLine(arguments)
                .withCharset(StandardCharsets.UTF_8)
                .withEnvironment(configuration.getCustomEnvironment())
                .withParentEnvironmentType(
                        configuration.isPassParentEnvironment()
                                ? GeneralCommandLine.ParentEnvironmentType.SYSTEM
                                : GeneralCommandLine.ParentEnvironmentType.NONE
                );
        String workingDirectory = workingDirectory(configuration);
        if (!workingDirectory.isBlank()) {
            commandLine.withWorkDirectory(workingDirectory);
        }
        return commandLine;
    }

    static String pattern(SpiceApplicationConfiguration configuration)
            throws ExecutionException {
        if (!configuration.getSpicePattern().isBlank()) {
            return configuration.getSpicePattern();
        }
        if (configuration.getKind()
                == com.goide.execution.GoBuildingRunConfiguration.Kind.PACKAGE
                && !configuration.getPackage().isBlank()) {
            return configuration.getPackage();
        }
        if (configuration.getKind()
                == com.goide.execution.GoBuildingRunConfiguration.Kind.DIRECTORY) {
            return ".";
        }
        throw new ExecutionException(
                "Spice application has no complete Go package to run"
        );
    }

    static String workingDirectory(
            SpiceApplicationConfiguration configuration
    ) {
        String configured = configuration.getWorkingDirectory();
        if (configured != null && !configured.isBlank()) {
            return new ProgramParametersConfigurator().expandPathAndMacros(
                    configured,
                    configuration.getConfigurationModule().getModule(),
                    configuration.getProject()
            );
        }
        String base = configuration.getProject().getBasePath();
        return base == null ? "" : base;
    }
}
