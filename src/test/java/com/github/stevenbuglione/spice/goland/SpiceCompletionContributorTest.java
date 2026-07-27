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

                        // @spice.import { Application as App } from "example.com/application/annotation/core"
                        // @spice.import * as core from "example.com/application/annotation/core"

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

                        // @spice.import { Application as App } from "example.com/application/annotation/core"
                        // @spice.import * as core from "example.com/application/annotation/core"

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

                        // @spice.import { Application } from "example.com/application/annotation/core"
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
}
