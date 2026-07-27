package com.github.stevenbuglione.spice.goland;

import com.goide.psi.GoFunctionDeclaration;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * Connects a descriptor declaration to its actual SDK handler declaration.
 *
 * <p>Go to Implementation first resolves an annotation PSI reference to its
 * descriptor function. This query then supplies the statically indexed handler
 * without executing descriptor code or depending on a live LSP process.
 */
public final class SpiceImplementationSearch implements QueryExecutor<
        PsiElement,
        DefinitionsScopedSearch.SearchParameters> {
    @Override
    public boolean execute(
            @NotNull DefinitionsScopedSearch.SearchParameters parameters,
            @NotNull Processor<? super PsiElement> consumer
    ) {
        PsiElement element = parameters.getElement();
        GoFunctionDeclaration function =
                element instanceof GoFunctionDeclaration declaration
                        ? declaration
                        : PsiTreeUtil.getParentOfType(
                                element,
                                GoFunctionDeclaration.class,
                                false
                        );
        if (function == null) {
            return true;
        }
        SpiceDescriptorMetadata metadata =
                SpiceAnnotationIndex.getInstance(element.getProject())
                        .metadata(function);
        if (metadata == null || metadata.handlerFunction() == null) {
            return true;
        }
        PsiElement identifier = metadata.handlerFunction().getIdentifier();
        return identifier == null || consumer.process(identifier);
    }
}
