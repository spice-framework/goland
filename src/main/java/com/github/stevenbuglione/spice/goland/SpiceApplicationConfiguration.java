package com.github.stevenbuglione.spice.goland;

import com.goide.execution.application.GoApplicationConfiguration;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import java.io.Serial;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SpiceApplicationConfiguration
        extends GoApplicationConfiguration {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final String TARGET_ATTRIBUTE = "spice-target";
    private static final String PATTERN_ATTRIBUTE = "spice-pattern";

    private String spiceTarget = "";
    private String spicePattern = "";

    SpiceApplicationConfiguration(
            Project project,
            String name,
            ConfigurationType type
    ) {
        super(project, name, type);
    }

    String getSpiceTarget() {
        return spiceTarget;
    }

    void setSpiceTarget(String value) {
        spiceTarget = normalized(value);
    }

    String getSpicePattern() {
        return spicePattern;
    }

    void setSpicePattern(String value) {
        spicePattern = normalized(value);
    }

    @Override
    public @Nullable RunProfileState getState(
            @NotNull Executor executor,
            @NotNull ExecutionEnvironment environment
    ) throws com.intellij.execution.ExecutionException {
        if (DefaultRunExecutor.EXECUTOR_ID.equals(executor.getId())) {
            return new SpiceApplicationRunningState(environment, this);
        }
        return super.getState(executor, environment);
    }

    @Override
    public void writeExternal(@NotNull Element element)
            throws WriteExternalException {
        super.writeExternal(element);
        element.setAttribute(TARGET_ATTRIBUTE, spiceTarget);
        element.setAttribute(PATTERN_ATTRIBUTE, spicePattern);
    }

    @Override
    public void readExternal(@NotNull Element element)
            throws InvalidDataException {
        super.readExternal(element);
        setSpiceTarget(element.getAttributeValue(TARGET_ATTRIBUTE));
        setSpicePattern(element.getAttributeValue(PATTERN_ATTRIBUTE));
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip();
    }
}
