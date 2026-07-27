package com.github.stevenbuglione.spice.goland;

import com.intellij.openapi.util.TextRange;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Supplies native Quick Documentation from locally indexed descriptor source.
 *
 * <p>The LSP remains authoritative when available. This provider intentionally
 * reads only explicit imports and indexed Go source so documentation remains
 * useful during an LSP restart and never causes module downloads.
 */
public final class SpiceDocumentationTargetProvider
        implements DocumentationTargetProvider {
    @Override
    public @NotNull List<? extends DocumentationTarget> documentationTargets(
            @NotNull PsiFile file,
            int offset
    ) {
        PsiComment comment = commentAt(file, offset);
        if (comment == null) {
            return List.of();
        }
        int relative = offset - comment.getTextRange().getStartOffset();
        var imported = SpiceAnnotationSyntax.parseImportDirective(
                comment.getText()
        );
        if (imported.isPresent()) {
            SpiceAnnotationSyntax.ImportDirective directive =
                    imported.orElseThrow();
            if (contains(directive.packageRange(), relative)) {
                return List.of(new SpicePackageDocumentationTarget(
                        comment,
                        directive.packagePath()
                ));
            }
            for (SpiceAnnotationSyntax.ImportBinding binding
                    : directive.bindings()) {
                if (contains(binding.importedRange(), relative)) {
                    return descriptorTarget(
                            comment,
                            binding.localName(),
                            binding.importedName(),
                            directive.packagePath()
                    );
                }
                if (contains(binding.localRange(), relative)) {
                    if (binding.namespace()) {
                        return List.of(new SpicePackageDocumentationTarget(
                                comment,
                                directive.packagePath()
                        ));
                    }
                    return descriptorTarget(
                            comment,
                            binding.localName(),
                            binding.importedName(),
                            directive.packagePath()
                    );
                }
            }
            return List.of();
        }

        var annotation = SpiceAnnotationSyntax.parse(comment.getText());
        if (annotation.isEmpty()
                || !contains(
                        annotation.orElseThrow().referenceRange(),
                        relative
                )) {
            return List.of();
        }
        String localName = annotation.orElseThrow().name();
        SpiceDescriptorMetadata metadata =
                SpiceAnnotationIndex.getInstance(file.getProject())
                        .metadata(comment, localName);
        return metadata == null
                ? List.of()
                : List.of(new SpiceDescriptorDocumentationTarget(
                        comment,
                        metadata
                ));
    }

    private static List<? extends DocumentationTarget> descriptorTarget(
            PsiComment comment,
            String localName,
            String importedName,
            String packagePath
    ) {
        SpiceAnnotationIndex index = SpiceAnnotationIndex.getInstance(
                comment.getProject()
        );
        SpiceAnnotationIndex.DescriptorSymbol descriptor =
                new SpiceAnnotationIndex.DescriptorSymbol(
                        packagePath,
                        importedName
                );
        SpiceDescriptorMetadata metadata = index.metadata(
                comment,
                localName,
                descriptor
        );
        return metadata == null
                ? List.of()
                : List.of(new SpiceDescriptorDocumentationTarget(
                        comment,
                        metadata
                ));
    }

    private static boolean contains(TextRange range, int offset) {
        return !range.isEmpty()
                && offset >= range.getStartOffset()
                && offset <= range.getEndOffset();
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
