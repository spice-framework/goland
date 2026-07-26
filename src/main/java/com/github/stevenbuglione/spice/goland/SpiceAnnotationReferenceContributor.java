package com.github.stevenbuglione.spice.goland;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public final class SpiceAnnotationReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(PsiComment.class),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(
                            @NotNull PsiElement element,
                            @NotNull ProcessingContext context
                    ) {
                        return referencesFor((PsiComment) element);
                    }
                }
        );
    }

    static PsiReference[] referencesFor(PsiComment comment) {
        return SpiceAnnotationSyntax.parse(comment.getText())
                .<PsiReference[]>map(match -> new PsiReference[]{
                        new SpiceAnnotationReference(
                                comment,
                                match.referenceRange(),
                                match.name()
                        )
                })
                .orElse(PsiReference.EMPTY_ARRAY);
    }
}
