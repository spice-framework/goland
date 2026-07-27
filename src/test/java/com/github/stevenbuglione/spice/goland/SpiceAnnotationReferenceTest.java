package com.github.stevenbuglione.spice.goland;

import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public final class SpiceAnnotationReferenceTest extends BasePlatformTestCase {
    public void testCreatesHighlightedReferenceToRealDescriptorFunction() {
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
        PsiComment comment = annotationComment("@App");
        assertNotNull(comment);

        PsiReference[] references = SpiceAnnotationReferenceContributor.referencesFor(comment);
        assertEquals(1, references.length);
        assertEquals("@App", references[0].getCanonicalText());

        PsiElement target = references[0].resolve();
        assertNotNull(target);
        assertEquals("Application", target.getText());
        assertTrue(
                target.getContainingFile().getVirtualFile().getPath()
                        .endsWith("annotation/core/application.go")
        );
        assertTrue(target instanceof Navigatable);
        assertTrue(((Navigatable) target).canNavigate());
    }

    public void testLeavesUnknownAnnotationsSoftAndUnresolved() {
        myFixture.configureByText(
                "main.go",
                "package main\n\n// @fixture.Unknown\nfunc main() {}\n"
        );
        PsiComment comment = PsiTreeUtil.findChildOfType(
                myFixture.getFile(),
                PsiComment.class
        );
        assertNotNull(comment);

        PsiReference[] references = SpiceAnnotationReferenceContributor.referencesFor(comment);
        assertEquals(1, references.length);
        assertTrue(references[0].isSoft());
        assertNull(references[0].resolve());
    }

    public void testResolvesNamedAndNamespaceImportBindings() {
        String source = """
                // @spice.import { Controller, Get as GET } from "example.com/sdk/web"
                // @spice.import * as web from "example.com/sdk/web"
                """;
        assertEquals(
                new SpiceAnnotationIndex.DescriptorSymbol(
                        "example.com/sdk/web",
                        "Controller"
                ),
                SpiceAnnotationIndex.resolveImport(source, "Controller")
        );
        assertEquals(
                new SpiceAnnotationIndex.DescriptorSymbol(
                        "example.com/sdk/web",
                        "Get"
                ),
                SpiceAnnotationIndex.resolveImport(source, "GET")
        );
        assertEquals(
                new SpiceAnnotationIndex.DescriptorSymbol(
                        "example.com/sdk/web",
                        "Post"
                ),
                SpiceAnnotationIndex.resolveImport(source, "web.Post")
        );
        assertNull(SpiceAnnotationIndex.resolveImport(source, "Post"));
        assertNull(SpiceAnnotationIndex.resolveImport(source, "web.bad.Name"));
    }

    private PsiComment annotationComment(String annotation) {
        for (PsiComment comment
                : PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiComment.class)) {
            if (comment.getText().contains(annotation)) {
                return comment;
            }
        }
        return null;
    }
}
