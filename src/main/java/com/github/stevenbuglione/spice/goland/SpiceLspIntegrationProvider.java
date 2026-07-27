package com.github.stevenbuglione.spice.goland;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lsp.api.LspIntegrationProvider;
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

public final class SpiceLspIntegrationProvider implements LspIntegrationProvider {
    private static final String DISABLED_PROPERTY = "spice.lsp.disabled";

    @Override
    public void fileOpened(
            @NotNull Project project,
            @NotNull VirtualFile file,
            @NotNull LspClientStarter clientStarter
    ) {
        if (!Boolean.getBoolean(DISABLED_PROPERTY)
                && SpiceLspClientDescriptor.supports(file)) {
            clientStarter.ensureClientStarted(new SpiceLspClientDescriptor(project));
        }
    }

    static final class SpiceLspClientDescriptor extends ProjectWideLspClientDescriptor {
        SpiceLspClientDescriptor(Project project) {
            super(project, "Spice");
        }

        @Override
        public boolean isSupportedFile(@NotNull VirtualFile file) {
            return supports(file);
        }

        @Override
        public @NotNull GeneralCommandLine createCommandLine() {
            GeneralCommandLine commandLine = new GeneralCommandLine(
                    SpiceExecutable.resolve(),
                    "lsp"
            )
                    .withCharset(StandardCharsets.UTF_8);
            String basePath = getProject().getBasePath();
            if (basePath != null && !basePath.isBlank()) {
                commandLine.withWorkDirectory(basePath);
            }
            return commandLine;
        }

        static boolean supports(VirtualFile file) {
            String extension = file.getExtension();
            return !file.isDirectory()
                    && extension != null
                    && extension.toLowerCase(Locale.ROOT).equals("go");
        }

    }
}
