package com.github.stevenbuglione.spice.goland;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.Arrays;
import java.util.List;

public final class SpiceCompletionContributorTest
        extends BasePlatformTestCase {
    public void testCompletesExplicitImportsWithoutTheLsp() {
        configureDescriptorModule();
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        // @import { Application as App } from "example.com/application/annotation/core"
                        // @import * as core from "example.com/application/annotation/core"

                        // @<caret>
                        func main() {}
                        """
        );

        LookupElement[] variants = myFixture.completeBasic();
        assertNotNull(variants);
        List<String> names = Arrays.stream(variants)
                .map(LookupElement::getLookupString)
                .toList();
        assertContainsElements(names, "App", "core.Application");

        LookupElement app = Arrays.stream(variants)
                .filter(value -> value.getLookupString().equals("App"))
                .findFirst()
                .orElseThrow();
        myFixture.getLookup().setCurrentItem(app);
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
        myFixture.checkResult(
                """
                        package main

                        // @import { Application as App } from "example.com/application/annotation/core"
                        // @import * as core from "example.com/application/annotation/core"

                        // @App
                        func main() {}
                        """
        );
    }

    public void testDoesNotCompleteInsideOrdinaryComments() {
        configureDescriptorModule();
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        // @import { Application } from "example.com/application/annotation/core"
                        // ordinary @<caret>
                        func main() {}
                        """
        );
        LookupElement[] variants = myFixture.completeBasic();
        if (variants != null) {
            assertFalse(
                    Arrays.stream(variants)
                            .map(LookupElement::getLookupString)
                            .toList()
                            .contains("Application")
            );
        }
    }

    public void testDoesNotInferInterfaceCandidatesFromTheIdeIndex() {
        configureImplementsModule();
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        import "example.com/application/payments"

                        // @import { Implements } from "example.com/application/annotation/core"

                        // @Implements(payments.Pro<caret>)
                        type Stripe struct{}
                        """
        );

        LookupElement[] variants = myFixture.completeBasic();
        if (variants == null) {
            return;
        }
        List<String> names = Arrays.stream(variants)
                .flatMap(value -> value.getAllLookupStrings().stream())
                .toList();
        assertFalse(
                "Spice interface candidates must come from the shared "
                        + "compiler/LSP, not GoLand's partial index",
                names.contains("payments.Processor")
        );
    }

    private void configureDescriptorModule() {
        myFixture.configureByText(
                "go.mod",
                "module example.com/application\n\ngo 1.26.0\n"
        );
        myFixture.addFileToProject(
                "annotation/core/application.go",
                """
                        package core

                        type Definition struct{}

                        // Application defines an application.
                        func Application() Definition {
                            return Definition{}
                        }
                        """
        );
    }

    private void configureImplementsModule() {
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

                        type Provider interface {
                            Provide() error
                        }

                        type Ordered interface {
                            ~int
                        }

                        type Record struct{}
                        """
        );
    }
}
