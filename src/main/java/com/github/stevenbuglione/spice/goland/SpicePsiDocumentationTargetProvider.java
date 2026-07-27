package com.github.stevenbuglione.spice.goland;

import com.goide.psi.GoFunctionDeclaration;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Replaces Go's generic documentation target for a resolved Spice reference.
 *
 * <p>This public extension point receives both the descriptor declaration and
 * the source comment. Keeping the source element is essential: one descriptor
 * may be imported under several file-scoped aliases.
 */
public final class SpicePsiDocumentationTargetProvider
        implements PsiDocumentationTargetProvider {
    @Override
    public @Nullable DocumentationTarget documentationTarget(
            @NotNull PsiElement targetElement,
            @Nullable PsiElement sourceElement
    ) {
        PsiComment comment = sourceElement instanceof PsiComment value
                ? value
                : targetElement instanceof PsiComment value
                ? value
                : sourceElement == null
                ? null
                : PsiTreeUtil.getParentOfType(
                        sourceElement,
                        PsiComment.class,
                        false
                );
        if (comment == null) {
            return null;
        }
        var annotation = SpiceAnnotationSyntax.parse(comment.getText());
        if (annotation.isPresent()) {
            SpiceDescriptorMetadata metadata =
                    SpiceAnnotationIndex.getInstance(comment.getProject())
                            .metadata(
                                    comment,
                                    annotation.orElseThrow().name()
                            );
            return metadata == null
                    ? null
                    : new SpiceDescriptorDocumentationTarget(
                            comment,
                            metadata
                    );
        }

        GoFunctionDeclaration descriptor = PsiTreeUtil.getParentOfType(
                targetElement,
                GoFunctionDeclaration.class,
                false
        );
        SpiceDescriptorMetadata metadata = descriptor == null
                ? null
                : SpiceAnnotationIndex.getInstance(comment.getProject())
                .metadata(descriptor);
        return metadata == null
                ? null
                : new SpiceDescriptorDocumentationTarget(comment, metadata);
    }
}
