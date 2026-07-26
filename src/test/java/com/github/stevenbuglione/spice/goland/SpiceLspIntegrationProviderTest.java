package com.github.stevenbuglione.spice.goland;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public final class SpiceLspIntegrationProviderTest extends BasePlatformTestCase {
    public void testLaunchesSharedServerOnlyForGoFiles() {
        VirtualFile goFile = new LightVirtualFile("main.go", "package main");
        VirtualFile textFile = new LightVirtualFile("notes.txt", "text");
        assertTrue(
                SpiceLspIntegrationProvider.SpiceLspClientDescriptor.supports(goFile)
        );
        assertFalse(
                SpiceLspIntegrationProvider.SpiceLspClientDescriptor.supports(textFile)
        );

        String property = "spice.executable";
        String previous = System.getProperty(property);
        try {
            System.setProperty(property, "C:/tools/spice.exe");
            var descriptor =
                    new SpiceLspIntegrationProvider.SpiceLspClientDescriptor(getProject());
            GeneralCommandLine commandLine = descriptor.createCommandLine();
            assertEquals("C:/tools/spice.exe", commandLine.getExePath());
            assertEquals(
                    "lsp",
                    commandLine.getParametersList().getParameters().getFirst()
            );
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }
}
