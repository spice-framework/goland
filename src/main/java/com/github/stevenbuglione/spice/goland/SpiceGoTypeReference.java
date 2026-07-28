package com.github.stevenbuglione.spice.goland;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SpiceGoTypeReference extends PsiReferenceBase<PsiComment> {
    SpiceGoTypeReference(PsiComment element, TextRange range) {
        super(element, range, true);
    }

    @Override
    public @Nullable PsiElement resolve() {
        var type = SpiceGoTypes.resolveTypeSpec(myElement, getRangeInElement());
        return type == null ? null : type.getIdentifier();
    }

    @Override
    public Object @NotNull [] getVariants() {
        return EMPTY_ARRAY;
    }
}
