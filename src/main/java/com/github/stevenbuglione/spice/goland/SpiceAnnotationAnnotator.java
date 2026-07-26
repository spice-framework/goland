package com.github.stevenbuglione.spice.goland;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public final class SpiceAnnotationAnnotator implements Annotator {
    @Override
    public void annotate(
            @NotNull PsiElement element,
            @NotNull AnnotationHolder holder
    ) {
        if (!(element instanceof PsiComment comment)) {
            return;
        }
        int commentStart = comment.getTextRange().getStartOffset();
        for (SpiceAnnotationSyntax.Token token
                : SpiceAnnotationSyntax.highlightTokens(comment.getText())) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(token.range().shiftRight(commentStart))
                    .textAttributes(SpiceHighlighting.forKind(token.kind()))
                    .create();
        }
    }
}
