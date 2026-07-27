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
                "module example.com/application\n\ngo 1.26.0\n"
        );
        myFixture.addFileToProject(
                "annotation/core/application.go",
                """
                        package core

                        type Definition struct{}

                        // Application is the real descriptor.
                        func Application() Definition {
                            return Definition{}
                        }
                        """
        );
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        // @spice.import { Application as App } from "example.com/application/annotation/core"

                        // @App
                        func main() {}
                        """
        );
        PsiComment invocation = annotationComment("@App");
        assertNotNull(invocation);

        LineMarkerInfo<?> marker = new SpiceAnnotationLineMarkerProvider()
                .getLineMarkerInfo(invocation);
        assertNotNull(marker);
        assertEquals(
                "Spice annotation from "
                        + "example.com/application/annotation/core.Application",
                marker.getLineMarkerTooltip()
        );
        assertNotNull(marker.getNavigationHandler());

        PsiComment directive = annotationComment("@spice.import");
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
