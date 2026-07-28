package com.github.stevenbuglione.spice.goland;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.psi.PsiComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SpiceImplementsAuthoringTest
        extends BasePlatformTestCase {
    public void testOffersGoLandsNativeMissingMethodFix() {
        configureModule();
        myFixture.configureByText(
                "main.go",
                        """
                        package main

                        // @import { Implements } from "example.com/application/annotation/core"
                        // @import * as payments from "example.com/application/payments"

                        // @Implements(payments.Processor)
                        type Stripe struct{}
                        """
        );

        PsiComment annotation = PsiTreeUtil.findChildrenOfType(
                myFixture.getFile(),
                PsiComment.class
        ).stream()
                .filter(value -> value.getText().contains("@Implements"))
                .findFirst()
                .orElseThrow();
        List<SpiceGoTypes.ResolvedInterface> contracts =
                SpiceGoTypes.resolveInterfaces(annotation);
        assertEquals(1, contracts.size());
        assertEquals("Processor", contracts.getFirst().typeSpec().getName());

        myFixture.doHighlighting();
        List<IntentionAction> fixes = myFixture.getAllQuickFixes();
        IntentionAction implement = fixes.stream()
                .filter(action -> action.getText()
                        .toLowerCase()
                        .contains("implement missing methods"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "native Go missing-method fix is absent: "
                                + fixes.stream()
                                .map(IntentionAction::getText)
                                .toList()
                ));
        assertEquals(
                "com.github.stevenbuglione.spice.goland."
                        + "SpiceImplementMethodsQuickFix",
                implement.getClass().getName()
        );
        myFixture.launchAction(implement);
        TemplateManager.getInstance(getProject()).finishTemplate(
                myFixture.getEditor()
        );

        String result = myFixture.getFile().getText();
        assertTrue(result, result.contains("func ("));
        assertTrue(result, Pattern.compile(
                "func \\([^\\n]+ \\*Stripe\\) Process\\(\\) error"
        ).matcher(result).find());
        assertTrue(result, result.contains("Process() error"));
        assertTrue(result, result.contains("panic(\"implement me\")"));
        assertTrue(result, result.contains("// @Implements(payments.Processor)"));
    }

    public void testResolvesConcreteFactoryOutputForAuthoringFixes() {
        configureModule();
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        import "example.com/application/payments"

                        type Stripe struct{}

                        // @import { Implements } from "example.com/application/annotation/core"

                        // @Implements(payments.Processor)
                        func NewStripe() *Stripe { return &Stripe{} }
                        """
        );
        PsiComment comment = PsiTreeUtil.findChildrenOfType(
                myFixture.getFile(),
                PsiComment.class
        ).stream()
                .filter(value -> value.getText().contains("@Implements"))
                .findFirst()
                .orElseThrow();
        assertNotNull(SpiceGoTypes.targetType(comment));
        assertEquals("Stripe", SpiceGoTypes.targetType(comment).getName());
        assertTrue(SpiceGoTypes.targetUsesPointer(comment));

        myFixture.doHighlighting();
        IntentionAction implement = myFixture.getAllQuickFixes().stream()
                .filter(action -> action.getClass().getName().endsWith(
                        "SpiceImplementMethodsQuickFix"
                ))
                .findFirst()
                .orElseThrow();
        myFixture.launchAction(implement);
        TemplateManager.getInstance(getProject()).finishTemplate(
                myFixture.getEditor()
        );
        assertTrue(
                myFixture.getFile().getText(),
                Pattern.compile(
                        "func \\([^\\n]+ \\*Stripe\\) Process\\(\\) error"
                ).matcher(myFixture.getFile().getText()).find()
        );
    }

    public void testLeavesInterfaceVerificationToGeneratedSource() {
        configureModule();
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        import "example.com/application/payments"

                        // @import { Implements, Service } from "example.com/application/annotation/core"

                        // @Service
                        // @Implements(payments.Processor)
                        type Stripe struct{}

                        func (*Stripe) Process() error { return nil }
                        """
        );

        myFixture.doHighlighting();
        String result = myFixture.getFile().getText();
        assertFalse(
                result,
                result.contains("var _ payments.Processor")
        );
        assertFalse(
                result,
                myFixture.getAllQuickFixes().stream().anyMatch(
                        action -> action.getText().equals(
                                "Add compile-time assertion for "
                                        + "payments.Processor"
                        )
                )
        );
    }

    public void testNativeFixSubstitutesGenericInterfaceArguments() {
        configureModule();
        myFixture.addFileToProject(
                "generic/generic.go",
                """
                        package generic

                        type Base[T any] interface {
                            BaseMethod(T) error
                        }

                        type Processor[T any] interface {
                            Base[T]
                            Process(T) (T, error)
                        }
                        """
        );
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        import "example.com/application/generic"

                        // @import { Implements } from "example.com/application/annotation/core"

                        // @Implements(generic.Processor[string])
                        type Stripe struct{}
                        """
        );

        myFixture.doHighlighting();
        IntentionAction implement = myFixture.getAllQuickFixes().stream()
                .filter(action -> action.getClass().getName().equals(
                        "com.github.stevenbuglione.spice.goland."
                                + "SpiceImplementMethodsQuickFix"
                ))
                .findFirst()
                .orElseThrow();
        myFixture.launchAction(implement);
        TemplateManager.getInstance(getProject()).finishTemplate(
                myFixture.getEditor()
        );

        String result = myFixture.getFile().getText();
        Matcher signature = Pattern.compile(
                "(?m)^func \\([^\\n]+\\) Process\\(([^)]*)\\)"
                        + " \\(([^)]*)\\) \\{"
        ).matcher(result);
        assertTrue(result, signature.find());
        assertTrue(result, signature.group(1).contains("string"));
        assertTrue(result, signature.group(2).contains("string"));
        assertFalse(result, signature.group().contains(" T"));
        assertTrue(
                result,
                Pattern.compile(
                        "(?m)^func \\([^\\n]+\\) BaseMethod\\([^)]*string\\)"
                                + " error \\{"
                ).matcher(result).find()
        );
    }

    public void testMultipleInterfacesOfferDistinctNativeActionsAndKeepMethods() {
        configureModule();
        myFixture.addFileToProject(
                "health/health.go",
                """
                        package health

                        type Checker interface {
                            Check() error
                        }
                        """
        );
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        import (
                            "example.com/application/health"
                            "example.com/application/payments"
                        )

                        // @import { Implements } from "example.com/application/annotation/core"

                        // @Implements(payments.Processor, health.Checker)
                        type Stripe struct{}

                        func (s *Stripe) Process() error { return nil }
                        """
        );

        myFixture.doHighlighting();
        List<IntentionAction> actions = myFixture.getAllQuickFixes().stream()
                .filter(action -> action.getClass().getName().endsWith(
                        "SpiceImplementMethodsQuickFix"
                ))
                .toList();
        assertEquals(
                List.of(
                        "Implement missing methods for payments.Processor",
                        "Implement missing methods for health.Checker"
                ),
                actions.stream().map(IntentionAction::getText).toList()
        );
        IntentionAction checker = actions.stream()
                .filter(action -> action.getText().contains("health.Checker"))
                .findFirst()
                .orElseThrow();
        myFixture.launchAction(checker);
        TemplateManager.getInstance(getProject()).finishTemplate(
                myFixture.getEditor()
        );

        String result = myFixture.getFile().getText();
        assertEquals(1, occurrences(result, "Process() error"));
        assertTrue(result, Pattern.compile(
                "func \\([^\\n]+ \\*Stripe\\) Check\\(\\) error"
        ).matcher(result).find());
    }

    private void configureModule() {
        myFixture.configureByText(
                "go.mod",
                "module example.com/application\n\ngo 1.26.0\n"
        );
        myFixture.addFileToProject(
                "payments/payments.go",
                """
                        package payments

                        type Processor interface {
                            Process() error
                        }
                        """
        );
    }

    private static int occurrences(String value, String needle) {
        int result = 0;
        for (int offset = value.indexOf(needle);
                offset >= 0;
                offset = value.indexOf(needle, offset + needle.length())) {
            result++;
        }
        return result;
    }
}
