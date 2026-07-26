package com.github.stevenbuglione.spice.goland;

import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public final class SpiceAnnotationReferenceTest extends BasePlatformTestCase {
    public void testCreatesHighlightedReferenceToBundledDefinition() {
        myFixture.configureByText(
                "main.go",
                "package main\n\n// @Application\nfunc main() {}\n"
        );
        PsiComment comment = PsiTreeUtil.findChildOfType(
                myFixture.getFile(),
                PsiComment.class
        );
        assertNotNull(comment);

        PsiReference[] references = SpiceAnnotationReferenceContributor.referencesFor(comment);
        assertEquals(1, references.length);
        assertEquals("@Application", references[0].getCanonicalText());

        PsiElement target = references[0].resolve();
        assertNotNull(target);
        assertTrue(target instanceof PsiNamedElement);
        assertEquals("Application", ((PsiNamedElement) target).getName());
        assertEquals("@Application", target.getText());
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
}
