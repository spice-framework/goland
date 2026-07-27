package com.github.stevenbuglione.spice.goland;

import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.Collection;
import java.util.List;

public final class SpiceDocumentationTargetProviderTest
        extends BasePlatformTestCase {
    public void testDocumentsAnnotationFromIndexedDescriptorAndHandler() {
        configureDescriptorModule();
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        // @import { Application as App } from "example.com/application/annotation/core"

                        // @Ap<caret>p
                        func main() {}
                        """
        );

        DocumentationTarget target = targetAtCaret();
        assertInstanceOf(target, SpiceDescriptorDocumentationTarget.class);
        PsiComment comment = PsiTreeUtil.getParentOfType(
                myFixture.getFile().findElementAt(
                        myFixture.getCaretOffset()
                ),
                PsiComment.class,
                false
        );
        assertNotNull(comment);
        SpiceDescriptorMetadata metadata =
                SpiceAnnotationIndex.getInstance(getProject())
                        .metadata(comment, "App");
        assertNotNull(metadata);
        SpiceDescriptorDocumentationTarget descriptor =
                new SpiceDescriptorDocumentationTarget(comment, metadata);
        assertEquals("@App", descriptor.computePresentation()
                .getPresentableText());
        assertEquals(
                "example.com/application/annotation/core.Application",
                descriptor.computePresentation().getContainerText()
        );
        assertEquals(
                "Declares the application root.",
                descriptor.computeDocumentationHint()
        );

        String documentation = descriptor.render();
        for (String expected : List.of(
                "Application defines",
                "Declares the application root.",
                "sdk.TargetFunction",
                "coretool.Path",
                "ApplicationHandler",
                "sdk.ProtocolV1Alpha1",
                "handler.go",
                "example.com/application",
                "(workspace)",
                "not declared in application go.mod"
        )) {
            assertTrue(
                    "missing documentation section: " + expected,
                    documentation.contains(expected)
            );
        }
        assertNotNull(descriptor.computeDocumentation());
        assertNotNull(descriptor.createPointer().dereference());

        Collection<PsiElement> implementations =
                DefinitionsScopedSearch.search(
                        descriptor.metadataForTest().descriptorFunction()
                ).findAll();
        assertEquals(1, implementations.size());
        assertEquals(
                "ApplicationHandler",
                implementations.iterator().next().getText()
        );
    }

    public void testDocumentsImportedSymbolAliasAndPackagePath() {
        configureDescriptorModule();
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        // @import { Appli<caret>cation as App } from "example.com/application/annotation/core"
                        func main() {}
                        """
        );
        assertInstanceOf(
                targetAtCaret(),
                SpiceDescriptorDocumentationTarget.class
        );

        int alias = myFixture.getFile().getText().indexOf("App }");
        List<? extends DocumentationTarget> aliasTargets =
                new SpiceDocumentationTargetProvider().documentationTargets(
                        myFixture.getFile(),
                        alias + 1
                );
        assertSize(1, aliasTargets);
        assertInstanceOf(
                aliasTargets.getFirst(),
                SpiceDescriptorDocumentationTarget.class
        );

        int path = myFixture.getFile().getText()
                .indexOf("example.com/application");
        List<? extends DocumentationTarget> packageTargets =
                new SpiceDocumentationTargetProvider().documentationTargets(
                        myFixture.getFile(),
                        path + 4
                );
        assertSize(1, packageTargets);
        assertInstanceOf(
                packageTargets.getFirst(),
                SpicePackageDocumentationTarget.class
        );
        assertNotNull(packageTargets.getFirst().getNavigatable());
    }

    public void testIgnoresOrdinaryComments() {
        configureDescriptorModule();
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        // ordinary Appli<caret>cation prose
                        func main() {}
                        """
        );
        assertEmpty(
                new SpiceDocumentationTargetProvider().documentationTargets(
                        myFixture.getFile(),
                        myFixture.getCaretOffset()
                )
        );
    }

    public void testPublicPsiProviderAcceptsCommentTargetWithoutSource() {
        configureDescriptorModule();
        myFixture.configureByText(
                "main.go",
                """
                        package main

                        // @import { Application } from "example.com/application/annotation/core"

                        // @Application
                        func main() {}
                        """
        );
        PsiComment comment = PsiTreeUtil.findChildrenOfType(
                myFixture.getFile(),
                PsiComment.class
        ).stream()
                .filter(value -> value.getText().contains("@Application"))
                .findFirst()
                .orElseThrow();
        DocumentationTarget target =
                new SpicePsiDocumentationTargetProvider()
                        .documentationTarget(comment, null);
        assertInstanceOf(target, SpiceDescriptorDocumentationTarget.class);
    }

    private DocumentationTarget targetAtCaret() {
        List<? extends DocumentationTarget> targets =
                new SpiceDocumentationTargetProvider().documentationTargets(
                        myFixture.getFile(),
                        myFixture.getCaretOffset()
                );
        assertSize(1, targets);
        return targets.getFirst();
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

                        // Application defines an application root and generated lifecycle.
                        //
                        // It is valid on an argument-free function.
                        func Application() Definition {
                            return Definition{
                                Summary: "Declares the application root.",
                                Targets: []sdk.Target{sdk.TargetFunction},
                                Implementation: sdk.Implementation{
                                    Tool: coretool.Path,
                                    Handler: "core/application",
                                    Protocol: sdk.ProtocolV1Alpha1,
                                    Source: sdk.Symbol{
                                        Package: "example.com/application/internal/annotationcore",
                                        Name: "ApplicationHandler",
                                    },
                                },
                            }
                        }
                        """
        );
        myFixture.addFileToProject(
                "internal/annotationcore/handler.go",
                """
                        package annotationcore

                        func ApplicationHandler() {}
                        """
        );
    }
}
