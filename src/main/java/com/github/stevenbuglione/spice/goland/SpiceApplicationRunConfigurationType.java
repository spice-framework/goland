package com.github.stevenbuglione.spice.goland;

import com.goide.execution.GoConfigurationFactoryBase;
import com.goide.execution.application.GoApplicationRunConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class SpiceApplicationRunConfigurationType
        extends ConfigurationTypeBase {
    static final String ID = "SpiceApplication";

    public SpiceApplicationRunConfigurationType() {
        super(
                ID,
                "Spice Application",
                "Run or debug a generated Spice application package",
                GoApplicationRunConfigurationType.getInstance().getIcon()
        );
        addFactory(new GoConfigurationFactoryBase(this) {
            @Override
            public @NotNull RunConfiguration createTemplateConfiguration(
                    @NotNull Project project
            ) {
                return new SpiceApplicationConfiguration(
                        project,
                        "Spice Application",
                        SpiceApplicationRunConfigurationType.this
                );
            }

            @Override
            public @NotNull String getId() {
                return ID;
            }
        });
    }

    static SpiceApplicationRunConfigurationType getInstance() {
        return ConfigurationTypeUtil.findConfigurationType(
                SpiceApplicationRunConfigurationType.class
        );
    }

    GoConfigurationFactoryBase factory() {
        return (GoConfigurationFactoryBase) getConfigurationFactories()[0];
    }
}
