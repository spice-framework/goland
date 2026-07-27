package com.github.stevenbuglione.spice.goland;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public final class SpiceTypedHandlerTest extends BasePlatformTestCase {
    public void testTypingAnnotationCreatesCanonicalValidGoSource() {
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        <caret>
                        func main() {}
                        """
        );

        myFixture.type('@');
        ApplicationManager.getApplication().invokeAndWait(() -> {});
        CodeFoldingManager.getInstance(getProject())
                .updateFoldRegions(myFixture.getEditor());

        assertEquals(
                """
                        package main

                        // @
                        func main() {}
                        """,
                myFixture.getEditor().getDocument().getText()
        );
        assertEquals(
                1,
                myFixture.getEditor().getFoldingModel().getAllFoldRegions().length
        );
        assertEquals(
                "",
                myFixture.getEditor().getFoldingModel()
                        .getAllFoldRegions()[0]
                        .getPlaceholderText()
        );
    }

    public void testTypingAnnotationInParameterListCreatesComment() {
        myFixture.configureByText(
                "provider.go",
                """
                        package example

                        func NewService(
                            <caret>
                            dependency Dependency,
                        ) *Service {
                            return nil
                        }
                        """
        );

        myFixture.type('@');

        assertTrue(
                myFixture.getEditor().getDocument().getText()
                        .contains("    // @\n    dependency Dependency")
        );
    }

    public void testTypingAtInsideFunctionRemainsOrdinaryGoTyping() {
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        func main() {
                            <caret>
                        }
                        """
        );

        myFixture.type('@');

        assertTrue(
                myFixture.getEditor().getDocument().getText()
                        .contains("    @\n")
        );
        assertFalse(
                myFixture.getEditor().getDocument().getText()
                        .contains("    // @\n")
        );
    }

    public void testTypingInsideRawStringDoesNotCreateComment() {
        myFixture.configureByText(
                "value.go",
                """
                        package example

                        var value = `
                        <caret>
                        `
                        """
        );

        myFixture.type('@');

        assertFalse(
                myFixture.getEditor().getDocument().getText()
                        .contains("// @")
        );
    }

    public void testTypingAfterExistingTextDoesNotCreateComment() {
        myFixture.configureByText(
                "value.go",
                """
                        package example

                        var value = <caret>
                        """
        );

        myFixture.type('@');

        assertTrue(
                myFixture.getEditor().getDocument().getText()
                        .contains("var value = @")
        );
        assertFalse(
                myFixture.getEditor().getDocument().getText()
                        .contains("// @")
        );
    }
}
