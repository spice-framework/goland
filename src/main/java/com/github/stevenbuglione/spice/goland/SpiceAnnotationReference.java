package com.github.stevenbuglione.spice.goland;

import com.intellij.codeInsight.highlighting.HighlightedReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SpiceAnnotationReference extends PsiReferenceBase<PsiComment>
        implements HighlightedReference {
    private final String name;

    SpiceAnnotationReference(PsiComment element, TextRange range, String name) {
        super(element, range, true);
        this.name = name;
    }

    @Override
    public @Nullable PsiElement resolve() {
        return SpiceAnnotationIndex.getInstance(myElement.getProject()).resolve(name);
    }

    @Override
    public Object @NotNull [] getVariants() {
        return EMPTY_ARRAY;
    }
}
