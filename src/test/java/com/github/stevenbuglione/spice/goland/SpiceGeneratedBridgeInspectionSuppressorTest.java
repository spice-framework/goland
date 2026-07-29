package com.github.stevenbuglione.spice.goland;

import com.goide.psi.GoReferenceExpression;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public final class SpiceGeneratedBridgeInspectionSuppressorTest
        extends BasePlatformTestCase {
    public void testSuppressesOnlyImportedApplicationMainBridge() {
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        import "os"

                        // @import { Application as App } from "github.com/StevenBuglione/spice/annotation/core"

                        // @App
                        func main() {
                            os.Exit(spiceMain(os.Args[1:]))
                        }
                        """
        );
        GoReferenceExpression reference = PsiTreeUtil.findChildrenOfType(
                myFixture.getFile(),
                GoReferenceExpression.class
        ).stream().filter(
                candidate -> "spiceMain".equals(candidate.getText())
        ).findFirst().orElseThrow();
        SpiceGeneratedBridgeInspectionSuppressor suppressor =
                new SpiceGeneratedBridgeInspectionSuppressor();

        assertTrue(suppressor.isSuppressedFor(
                reference,
                "GoUnresolvedReference"
        ));
        assertTrue(suppressor.isSuppressedFor(
                reference,
                "GoUnresolvedReferenceInspection"
        ));
        assertFalse(suppressor.isSuppressedFor(reference, "GoUnusedImport"));
        assertEmpty(suppressor.getSuppressActions(
                reference,
                "GoUnresolvedReference"
        ));
        HighlightInfo falsePositive = HighlightInfo.newHighlightInfo(
                HighlightInfoType.ERROR
        ).range(reference).description("undefined: spiceMain")
                .createUnconditionally();
        assertFalse(new SpiceGeneratedBridgeHighlightFilter().accept(
                falsePositive,
                myFixture.getFile()
        ));
        HighlightInfo unrelated = HighlightInfo.newHighlightInfo(
                HighlightInfoType.ERROR
        ).range(reference).description("undefined: anotherSymbol")
                .createUnconditionally();
        assertTrue(new SpiceGeneratedBridgeHighlightFilter().accept(
                unrelated,
                myFixture.getFile()
        ));
        assertTrue(
                HighlightInfoFilter.EXTENSION_POINT_NAME
                        .getExtensionList().stream().anyMatch(
                                SpiceGeneratedBridgeHighlightFilter.class
                                        ::isInstance
                        )
        );
        assertFalse(myFixture.doHighlighting().stream().anyMatch(
                info -> "undefined: spiceMain".equals(info.getDescription())
        ));
    }

    public void testRejectsUnannotatedAndUnrelatedReferences() {
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        func main() {
                            _ = spiceMain(nil)
                        }
                        """
        );
        GoReferenceExpression reference = PsiTreeUtil.findChildrenOfType(
                myFixture.getFile(),
                GoReferenceExpression.class
        ).stream().filter(
                candidate -> "spiceMain".equals(candidate.getText())
        ).findFirst().orElseThrow();

        assertFalse(new SpiceGeneratedBridgeInspectionSuppressor()
                .isSuppressedFor(reference, "GoUnresolvedReference"));
    }
}
