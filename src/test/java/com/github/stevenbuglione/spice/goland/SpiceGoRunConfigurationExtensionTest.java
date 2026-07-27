package com.github.stevenbuglione.spice.goland;

import com.goide.execution.GoBuildingRunConfiguration;
import com.goide.execution.application.GoApplicationConfiguration;
import com.intellij.execution.ExecutionException;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public final class SpiceGoRunConfigurationExtensionTest
        extends BasePlatformTestCase {
    public void testRejectsRawAnnotationInTemporaryFileRun() throws Exception {
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        @Application
                        func main() {}
                        """
        );
        ExecutionException failure = org.junit.Assert.assertThrows(
                ExecutionException.class,
                () -> SpiceGoRunConfigurationExtension
                        .validateSource(
                                myFixture.getEditor().getDocument().getText()
                        )
        );

        assertTrue(failure.getMessage().contains("raw @ annotation"));
    }

    public void testRejectsCommentAnnotationInTemporaryFileRun()
            throws Exception {
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        // @Application
                        func main() {}
                        """
        );
        ExecutionException failure = org.junit.Assert.assertThrows(
                ExecutionException.class,
                () -> SpiceGoRunConfigurationExtension
                        .validateSource(
                                myFixture.getEditor().getDocument().getText()
                        )
        );

        assertTrue(failure.getMessage().contains("complete package"));
    }

    public void testRejectsAliasedApplicationImportInTemporaryFileRun()
            throws Exception {
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        // @import { Application as App } from "example.com/sdk/core"
                        // @App
                        func main() {}
                        """
        );
        org.junit.Assert.assertThrows(
                ExecutionException.class,
                () -> SpiceGoRunConfigurationExtension
                        .validateSource(
                                myFixture.getEditor().getDocument().getText()
                        )
        );
    }

    public void testAllowsOrdinaryGoFileAndCompletePackage() throws Exception {
        myFixture.configureByText(
                "main.go",
                "package main\n\nfunc main() {}\n"
        );
        SpiceGoRunConfigurationExtension.validateSource(
                myFixture.getEditor().getDocument().getText()
        );

        myFixture.configureByText(
                "main.go",
                "package main\n\n// @Application\nfunc main() {}\n"
        );
        GoApplicationConfiguration complete =
                new GoApplicationConfiguration(
                        getProject(),
                        "Go package",
                        com.goide.execution.application
                                .GoApplicationRunConfigurationType
                                .getInstance()
                );
        complete.setKind(GoBuildingRunConfiguration.Kind.DIRECTORY);
        SpiceGoRunConfigurationExtension.validateSingleFileConfiguration(
                complete
        );
    }
}
