package com.github.stevenbuglione.spice.goland;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.List;

public final class SpiceClassAuthoringTest extends BasePlatformTestCase {
    public void testGeneratesConstructorFromManagedStructFields() {
        configure("""
                package application

                // @Service
                type <caret>OrderService struct {
                    repository string
                    retries int
                }
                """);

        launch("Generate constructor");

        String result = myFixture.getFile().getText();
        assertContains(result, "func NewOrderService(");
        assertContains(result, "repository string,");
        assertContains(result, "retries int,");
        assertContains(result, "repository: repository,");
        assertContains(result, "retries: retries,");
    }

    public void testMovesMethodAndPolicyCommentsToOwningTypeFile() {
        configure("""
                package application

                // @Service
                type OrderService struct{}
                """);
        myFixture.configureByText(
                "order_service_methods.go",
                """
                        package application

                        // @Observed
                        func (*OrderService) <caret>Create() error { return nil }
                        """
        );

        launch("Move method to owning type file");

        String source = myFixture.getFile().getText();
        String destination = fileText("main.go");
        assertFalse(source, source.contains("Create()"));
        assertContains(destination, "// @Observed");
        assertContains(destination,
                "func (*OrderService) Create() error { return nil }");
    }

    public void testConvertsFunctionToMethodOnOnlyType() {
        configure("""
                package application

                type Order struct{}

                func <caret>Calculate() int { return 1 }
                """);

        launch("Convert function to method");

        assertContains(
                myFixture.getFile().getText(),
                "func (order *Order) Calculate() int"
        );
    }

    public void testConvertsFunctionToManagedComponent() {
        configure("""
                package application

                func <caret>Normalize(value string) string { return value }
                """);

        launch("Convert function to @Component");

        String result = myFixture.getFile().getText();
        assertContains(result,
                "// @import { Component } from \"github.com/spice-framework/spice/annotation/core\"");
        assertContains(result, "// @Component\ntype NormalizeComponent struct{}");
        assertContains(result,
                "func NewNormalizeComponent() *NormalizeComponent");
        assertContains(result,
                "func (*NormalizeComponent) Normalize(value string) string");
    }

    public void testAddsExplicitImplementsForSatisfiedApplicationInterface() {
        configure("""
                package application

                type OrderService interface {
                    Create() error
                }

                // @Service
                type <caret>DefaultOrderService struct{}

                func (*DefaultOrderService) Create() error { return nil }
                """);

        launch("Add @Implements");

        String result = myFixture.getFile().getText();
        assertContains(result,
                "// @import { Implements } from \"github.com/spice-framework/spice/annotation/core\"");
        assertContains(result,
                "// @Service\n// @Implements(OrderService)\ntype DefaultOrderService");
    }

    public void testCreatesRoleAwareImplementationFile() {
        configure("""
                package application

                import "context"

                type <caret>OrderService interface {
                    Create(context.Context, string) error
                }
                """);

        launch("Create implementation");

        String result = fileText("default_order_service.go");
        assertContains(result, "import (\n\t\"context\"\n)");
        assertContains(result, "// @Service");
        assertContains(result, "// @Implements(OrderService)");
        assertContains(result, "type DefaultOrderService struct{}");
        assertContains(result,
                "func NewDefaultOrderService() *DefaultOrderService");
        assertContains(result,
                "func (*DefaultOrderService) Create(context.Context, string) error");
        assertContains(result, "panic(\"implement me\")");
    }

    public void testCreatesInterfaceFileAndBindsManagedType() {
        configure("""
                package application

                import "context"

                // @Service
                type <caret>DefaultOrderService struct{}

                func (*DefaultOrderService) Create(context.Context) error {
                    return nil
                }
                """);

        launch("Create interface");

        String contract = fileText("order_service.go");
        assertContains(contract, "import (\n\t\"context\"\n)");
        assertContains(contract, "type OrderService interface {");
        assertContains(contract, "Create(context.Context) error");
        assertContains(
                myFixture.getFile().getText(),
                "// @Implements(OrderService)\ntype DefaultOrderService"
        );
    }

    public void testMovesPackageBeanOntoConfigurationMethod() {
        configure("""
                package application

                // @Bean
                func <caret>NewDatabase(url string) (string, error) {
                    return url, nil
                }
                """);

        launch("Move @Bean to @Configuration");

        String result = myFixture.getFile().getText();
        assertContains(result,
                "// @import { Configuration } from \"github.com/spice-framework/spice/annotation/core\"");
        assertContains(result,
                "// @Configuration\ntype DatabaseConfiguration struct{}");
        assertContains(result,
                "func NewDatabaseConfiguration() *DatabaseConfiguration");
        assertContains(result,
                "// @Bean\nfunc (*DatabaseConfiguration) Database(url string)");
        assertFalse(result, result.contains("func NewDatabase("));
    }

    public void testDoesNotOfferConstructorWhenPackageAlreadyDefinesOne() {
        configure("""
                package application

                // @Service
                type <caret>OrderService struct{}
                """);
        myFixture.addFileToProject(
                "order_service_constructor.go",
                """
                        package application

                        func NewOrderService() *OrderService {
                            return &OrderService{}
                        }
                        """
        );

        assertFalse(new SpiceClassIntentions.GenerateConstructor()
                .isAvailable(getProject(), myFixture.getEditor(),
                        myFixture.getFile()));
    }

    public void testDoesNotOfferClassEditsOnApplicationEntrypoint() {
        configure("""
                package main

                func <caret>main() {}
                """);

        List<IntentionAction> actions = List.of(
                new SpiceClassIntentions.GenerateConstructor(),
                new SpiceClassIntentions.MoveMethod(),
                new SpiceClassIntentions.ConvertToMethod(),
                new SpiceClassIntentions.ConvertToComponent(),
                new SpiceClassIntentions.AddImplements(),
                new SpiceClassIntentions.CreateImplementation(),
                new SpiceClassIntentions.CreateInterface(),
                new SpiceClassIntentions.MoveBean()
        );
        for (IntentionAction action : actions) {
            assertFalse(action.getText(), action.isAvailable(
                    getProject(),
                    myFixture.getEditor(),
                    myFixture.getFile()
            ));
        }
    }

    private void configure(String source) {
        myFixture.configureByText(
                "go.mod",
                "module example.com/application\n\ngo 1.26.0\n"
        );
        myFixture.configureByText("main.go", source);
    }

    private void launch(String text) {
        var highlighting = myFixture.doHighlighting();
        List<IntentionAction> actions = myFixture.getAvailableIntentions();
        String actionText = "Spice: " + text;
        IntentionAction action = actions.stream()
                .filter(value -> value.getText().equals(actionText))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "missing action " + actionText + ": "
                                + actions.stream()
                                .map(IntentionAction::getText)
                                .toList() + "; highlights="
                                + highlighting.stream()
                                .map(value -> value.getDescription())
                                .toList()
                ));
        myFixture.launchAction(action);
    }

    private String fileText(String path) {
        VirtualFile file = myFixture.findFileInTempDir(path);
        assertNotNull("missing " + path, file);
        var psiFile = PsiManager.getInstance(getProject()).findFile(file);
        assertNotNull("missing PSI for " + path, psiFile);
        return psiFile.getText();
    }

    private void assertContains(String value, String expected) {
        assertTrue(value, value.contains(expected));
    }
}
