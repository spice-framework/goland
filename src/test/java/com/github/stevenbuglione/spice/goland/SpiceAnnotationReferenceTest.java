package com.github.stevenbuglione.spice.goland;

import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.List;

public final class SpiceAnnotationReferenceTest extends BasePlatformTestCase {
    public void testCreatesNavigableReferenceToRealDescriptorFunction() {
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

                        // @import { Application as App } from "example.com/application/annotation/core"

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

    public void testCreatesReferencesForEveryImportSymbolAndPackageRange() {
        myFixture.configureByText(
                "go.mod",
                "module example.com/application\n\ngo 1.26.0\n"
        );
        myFixture.addFileToProject(
                "annotation/web/web.go",
                """
                        package web

                        type Definition struct{}

                        func Controller() Definition { return Definition{} }
                        func Get() Definition { return Definition{} }
                        """
        );
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        // @import { Controller, Get as GET } from "example.com/application/annotation/web"
                        // @import * as web from "example.com/application/annotation/web"
                        """
        );
        List<PsiComment> comments = PsiTreeUtil.findChildrenOfType(
                myFixture.getFile(),
                PsiComment.class
        ).stream().toList();
        assertEquals(2, comments.size());

        PsiReference[] named =
                SpiceAnnotationReferenceContributor.referencesFor(
                        comments.getFirst()
                );
        assertEquals(4, named.length);
        assertEquals(
                List.of(
                        "example.com/application/annotation/web",
                        "Controller",
                        "Get",
                        "GET"
                ),
                List.of(named).stream()
                        .map(PsiReference::getCanonicalText)
                        .toList()
        );
        assertEquals("web", named[0].resolve().getText());
        assertEquals("Controller", named[1].resolve().getText());
        assertEquals("Get", named[2].resolve().getText());
        assertEquals("Get", named[3].resolve().getText());

        PsiReference[] namespace =
                SpiceAnnotationReferenceContributor.referencesFor(
                        comments.get(1)
                );
        assertEquals(2, namespace.length);
        assertEquals(
                List.of(
                        "example.com/application/annotation/web",
                        "web"
                ),
                List.of(namespace).stream()
                        .map(PsiReference::getCanonicalText)
                        .toList()
        );
        assertEquals("web", namespace[0].resolve().getText());
        assertEquals("web", namespace[1].resolve().getText());
    }

    public void testNavigatesImplementsArgumentsAsNativeGoTypes() {
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
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        import "example.com/application/payments"

                        // @import { Implements } from "example.com/application/annotation/core"

                        // @Implements(payments.Processor)
                        type Stripe struct{}
                        """
        );
        PsiComment comment = annotationComment("@Implements");
        assertNotNull(comment);

        PsiReference[] references =
                SpiceAnnotationReferenceContributor.referencesFor(comment);
        assertEquals(2, references.length);
        assertEquals("@Implements", references[0].getCanonicalText());
        assertEquals(
                "payments.Processor",
                references[1].getCanonicalText()
        );
        PsiElement target = references[1].resolve();
        assertNotNull(target);
        assertEquals("Processor", target.getText());
        assertTrue(
                target.getContainingFile().getVirtualFile().getPath()
                        .endsWith("payments/payments.go")
        );
    }

    public void testResolvesNamedAndNamespaceImportBindings() {
        String source = """
                // @import { Controller, Get as GET } from "example.com/sdk/web"
                // @import * as web from "example.com/sdk/web"
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

    public void testParsesModuleAndLocalReplacementProvenance() {
        SpiceAnnotationIndex.ModuleLayout layout =
                SpiceAnnotationIndex.moduleLayout(
                        """
                                module example.com/application

                                replace (
                                    github.com/StevenBuglione/spice v0.1.0 => ../spice
                                    example.com/acme/plugin => D:/work/plugin
                                )
                                """
                );
        assertEquals("example.com/application", layout.modulePath());
        assertEquals(
                List.of(
                        new SpiceAnnotationIndex.LocalReplacement(
                                "github.com/StevenBuglione/spice",
                                "../spice"
                        ),
                        new SpiceAnnotationIndex.LocalReplacement(
                                "example.com/acme/plugin",
                                "D:/work/plugin"
                        )
                ),
                layout.replacements()
        );
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
