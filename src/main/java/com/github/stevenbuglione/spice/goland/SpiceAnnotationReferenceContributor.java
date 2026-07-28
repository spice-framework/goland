package com.github.stevenbuglione.spice.goland;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import java.util.ArrayList;
import java.util.List;
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
        var directive = SpiceAnnotationSyntax.parseImportDirective(
                comment.getText()
        );
        if (directive.isPresent()) {
            return importReferences(comment, directive.orElseThrow());
        }
        return SpiceAnnotationSyntax.parse(comment.getText())
                .<PsiReference[]>map(match -> annotationReferences(
                        comment,
                        match
                ))
                .orElse(PsiReference.EMPTY_ARRAY);
    }

    private static PsiReference[] annotationReferences(
            PsiComment comment,
            SpiceAnnotationSyntax.Match match
    ) {
        List<PsiReference> references = new ArrayList<>();
        references.add(new SpiceAnnotationReference(
                comment,
                match.referenceRange(),
                match.name()
        ));
        if (SpiceGoTypes.isImplements(comment)) {
            for (SpiceAnnotationSyntax.TypeArgument argument
                    : SpiceAnnotationSyntax.typeArguments(
                            comment.getText()
                    )) {
                references.add(new SpiceGoTypeReference(
                        comment,
                        argument.referenceRange()
                ));
            }
        }
        return references.toArray(PsiReference[]::new);
    }

    private static PsiReference[] importReferences(
            PsiComment comment,
            SpiceAnnotationSyntax.ImportDirective directive
    ) {
        List<PsiReference> references = new ArrayList<>();
        references.add(SpiceAnnotationReference.annotationPackage(
                comment,
                directive.packageRange(),
                directive.packagePath()
        ));
        for (SpiceAnnotationSyntax.ImportBinding binding
                : directive.bindings()) {
            if (binding.namespace()) {
                references.add(SpiceAnnotationReference.annotationPackage(
                        comment,
                        binding.localRange(),
                        directive.packagePath()
                ));
                continue;
            }
            SpiceAnnotationIndex.DescriptorSymbol descriptor =
                    new SpiceAnnotationIndex.DescriptorSymbol(
                            directive.packagePath(),
                            binding.importedName()
                    );
            references.add(SpiceAnnotationReference.descriptor(
                    comment,
                    binding.importedRange(),
                    descriptor
            ));
            if (!binding.localRange().equals(binding.importedRange())) {
                references.add(SpiceAnnotationReference.descriptor(
                        comment,
                        binding.localRange(),
                        descriptor
                ));
            }
        }
        return references.toArray(PsiReference[]::new);
    }
}
