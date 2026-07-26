package com.github.stevenbuglione.spice.goland;

import com.goide.execution.GoBuildingRunConfiguration;
import com.goide.execution.application.GoApplicationConfiguration;
import com.goide.execution.application.GoApplicationRunConfigurationType;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public final class SpiceApplicationRunConfigurationProducerTest
        extends BasePlatformTestCase {
    public void testApplicationUsesWholeDirectoryInsteadOfTemporaryFile() {
        String source = """
                package main

                import "os"

                // @Application
                // @management.Enable(expose=["health"])
                func main() {
                    os.Exit(spiceMain(os.Args[1:]))
                }
                """;
        myFixture.configureByText("main.go", source);
        PsiComment marker = applicationMarker();

        GoApplicationConfiguration configuration = new GoApplicationConfiguration(
                getProject(),
                "Spice Application",
                GoApplicationRunConfigurationType.getInstance()
        );
        Ref<PsiElement> sourceElement = new Ref<>();
        boolean configured = new SpiceApplicationRunConfigurationProducer()
                .setupConfigurationFromContext(
                        configuration,
                        new ConfigurationContext(marker),
                        sourceElement
                );

        assertTrue(configured);
        assertEquals(GoBuildingRunConfiguration.Kind.DIRECTORY, configuration.getKind());
        assertTrue(configuration.getFilePaths().isEmpty());
        assertEquals(marker, sourceElement.get());
        assertEquals(source, myFixture.getEditor().getDocument().getText());
        assertTrue(myFixture.getEditor().getDocument().getText().contains("// @Application"));
        assertFalse(myFixture.getEditor().getDocument().getText().contains("\n@Application"));
    }

    public void testRejectsApplicationMarkerOnNonMainFunction() {
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        // @Application
                        func helper() {}

                        func main() {}
                        """
        );
        PsiComment marker = applicationMarker();
        GoApplicationConfiguration configuration = new GoApplicationConfiguration(
                getProject(),
                "Spice Application",
                GoApplicationRunConfigurationType.getInstance()
        );

        assertFalse(new SpiceApplicationRunConfigurationProducer()
                .setupConfigurationFromContext(
                        configuration,
                        new ConfigurationContext(marker),
                        new Ref<>()
                ));
    }

    public void testRejectsOrdinaryMainWithoutApplicationMarker() {
        myFixture.configureByText("main.go", "package main\n\nfunc main() {}\n");
        GoApplicationConfiguration configuration = new GoApplicationConfiguration(
                getProject(),
                "Spice Application",
                GoApplicationRunConfigurationType.getInstance()
        );

        assertFalse(new SpiceApplicationRunConfigurationProducer()
                .setupConfigurationFromContext(
                        configuration,
                        new ConfigurationContext(myFixture.getFile()),
                        new Ref<>()
                ));
    }

    private PsiComment applicationMarker() {
        PsiComment marker = PsiTreeUtil.findChildOfType(
                myFixture.getFile(),
                PsiComment.class
        );
        assertNotNull(marker);
        return marker;
    }
}
