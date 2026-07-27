package com.github.stevenbuglione.spice.goland;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adapts Spice comment symbols to GoLand's editor documentation entrypoint.
 *
 * <p>Build 262 still routes Quick Documentation for comment-contained
 * references through this API. The returned content and navigation metadata
 * are produced by the same target objects used by the modern Documentation
 * Target API.
 */
public final class SpiceDocumentationProvider
        extends AbstractDocumentationProvider {
    @Override
    public @Nullable PsiElement getCustomDocumentationElement(
            @NotNull Editor editor,
            @NotNull PsiFile file,
            @Nullable PsiElement contextElement,
            int targetOffset
    ) {
        PsiComment comment = commentAt(file, targetOffset);
        if (comment == null) {
            return null;
        }
        if (SpiceAnnotationSyntax.parse(comment.getText()).isPresent()
                || SpiceAnnotationSyntax.parseImportDirective(
                        comment.getText()
                ).isPresent()) {
            return comment;
        }
        return null;
    }

    @Override
    public @Nullable String generateDoc(
            PsiElement element,
            @Nullable PsiElement originalElement
    ) {
        if (!(element instanceof PsiComment comment)) {
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
                    ).render();
        }
        var imported = SpiceAnnotationSyntax.parseImportDirective(
                comment.getText()
        );
        return imported.isEmpty()
                ? null
                : new SpicePackageDocumentationTarget(
                        comment,
                        imported.orElseThrow().packagePath()
                ).render();
    }

    @Override
    public @Nullable String getQuickNavigateInfo(
            PsiElement element,
            @Nullable PsiElement originalElement
    ) {
        if (!(element instanceof PsiComment comment)) {
            return null;
        }
        var annotation = SpiceAnnotationSyntax.parse(comment.getText());
        if (annotation.isEmpty()) {
            return null;
        }
        SpiceDescriptorMetadata metadata =
                SpiceAnnotationIndex.getInstance(comment.getProject())
                        .metadata(comment, annotation.orElseThrow().name());
        return metadata == null
                ? null
                : new SpiceDescriptorDocumentationTarget(
                        comment,
                        metadata
                ).computeDocumentationHint();
    }

    private static @Nullable PsiComment commentAt(PsiFile file, int offset) {
        if (file.getTextLength() == 0) {
            return null;
        }
        int bounded = Math.max(
                0,
                Math.min(offset, file.getTextLength() - 1)
        );
        PsiElement element = file.findElementAt(bounded);
        if (element instanceof PsiComment comment) {
            return comment;
        }
        return PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
    }
}
