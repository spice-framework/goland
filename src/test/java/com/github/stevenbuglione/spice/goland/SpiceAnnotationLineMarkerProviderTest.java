package com.github.stevenbuglione.spice.goland;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.psi.PsiComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public final class SpiceAnnotationLineMarkerProviderTest
        extends BasePlatformTestCase {
    public void testShowsResolvedDescriptorProvenanceInGutter() {
        myFixture.configureByText(
                "go.mod",
                """
                        module example.com/application

                        go 1.26.0

                        tool example.com/application/cmd/annotations
                        """
        );
        myFixture.addFileToProject(
                "annotation/core/application.go",
                """
                        package core

                        type Definition struct{}

                        // Application is the real descriptor.
                        func Application() Definition {
                            return Definition{
                                Implementation: sdk.Implementation{
                                    Tool: "example.com/application/cmd/annotations",
                                    Handler: ApplicationHandler,
                                    Protocol: sdk.ProtocolV1Alpha2,
                                },
                            }
                        }

                        func ApplicationHandler() {}
                        """
        );
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        // @import { Application as App } from "example.com/application/annotation/core"

                        // @App
                        func main() {}
                        """
        );
        // Descriptor resolution uses Go's stub index. Complete the fixture's
        // indexing pass before invoking the line-marker provider directly.
        myFixture.doHighlighting();
        PsiComment invocation = annotationComment("@App");
        assertNotNull(invocation);

        LineMarkerInfo<?> marker = new SpiceAnnotationLineMarkerProvider()
                .getLineMarkerInfo(invocation);
        assertNotNull(marker);
        assertEquals(
                "Spice annotation from "
                        + "example.com/application/annotation/core.Application"
                        + " | version (workspace)"
                        + " | tool example.com/application/cmd/annotations"
                        + " | handler ApplicationHandler"
                        + " | protocol sdk.ProtocolV1Alpha2"
                        + " | tool authorized",
                marker.getLineMarkerTooltip()
        );
        assertNotNull(marker.getNavigationHandler());

        PsiComment directive = annotationComment("@import");
        assertNotNull(directive);
        assertNull(
                new SpiceAnnotationLineMarkerProvider()
                        .getLineMarkerInfo(directive)
        );
    }

    public void testOmitsUnresolvedAnnotationGutter() {
        myFixture.configureByText(
                "main.go",
                "package main\n\n// @Unknown\nfunc main() {}\n"
        );
        PsiComment invocation = annotationComment("@Unknown");
        assertNotNull(invocation);
        assertNull(
                new SpiceAnnotationLineMarkerProvider()
                        .getLineMarkerInfo(invocation)
        );
    }

    private PsiComment annotationComment(String annotation) {
        for (PsiComment comment
                : PsiTreeUtil.findChildrenOfType(
                        myFixture.getFile(),
                        PsiComment.class
                )) {
            if (comment.getText().contains(annotation)) {
                return comment;
            }
        }
        return null;
    }
}
