package com.github.stevenbuglione.spice.goland;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SpiceFoldingBuilder extends FoldingBuilderEx implements DumbAware {
    private static final String PLACEHOLDER = "";

    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(
            @NotNull PsiElement root,
            @NotNull Document document,
            boolean quick
    ) {
        List<FoldingDescriptor> descriptors = new ArrayList<>();
        root.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof PsiComment comment) {
                    SpiceAnnotationSyntax.parse(comment.getText()).ifPresent(match -> {
                        TextRange range = match.prefixRange().shiftRight(
                                comment.getTextRange().getStartOffset()
                        );
                        descriptors.add(new FoldingDescriptor(
                                comment.getNode(),
                                range,
                                null,
                                Collections.emptySet(),
                                true,
                                PLACEHOLDER,
                                Boolean.TRUE
                        ));
                    });
                }
                super.visitElement(element);
            }
        });
        return descriptors.toArray(FoldingDescriptor.EMPTY_ARRAY);
    }

    @Override
    public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
        return PLACEHOLDER;
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return true;
    }
}
