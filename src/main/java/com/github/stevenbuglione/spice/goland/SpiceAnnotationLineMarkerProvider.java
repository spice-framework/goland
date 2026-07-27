package com.github.stevenbuglione.spice.goland;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

public final class SpiceAnnotationLineMarkerProvider implements LineMarkerProvider {
    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(PsiElement element) {
        if (!(element instanceof PsiComment comment)) {
            return null;
        }
        Optional<SpiceAnnotationSyntax.Match> parsed =
                SpiceAnnotationSyntax.parse(comment.getText());
        if (parsed.isEmpty()) {
            return null;
        }
        SpiceAnnotationSyntax.Match match = parsed.orElseThrow();
        if ("import".equals(match.name())) {
            return null;
        }
        PsiFile file = comment.getContainingFile();
        if (file == null) {
            return null;
        }
        SpiceAnnotationIndex.DescriptorSymbol descriptor =
                SpiceAnnotationIndex.resolveImport(file.getText(), match.name());
        if (descriptor == null) {
            return null;
        }
        TextRange referenceRange = match.referenceRange();
        PsiElement target = new SpiceAnnotationReference(
                comment,
                referenceRange,
                match.name()
        ).resolve();
        if (target == null) {
            return null;
        }
        String provenance = descriptor.packagePath() + "." + descriptor.symbol();
        SpiceDescriptorMetadata metadata =
                SpiceAnnotationIndex.getInstance(comment.getProject())
                        .metadata(comment, match.name(), descriptor);
        String tooltip = "Spice annotation from " + provenance;
        if (metadata != null) {
            tooltip += provenanceDetails(metadata);
        }
        return NavigationGutterIconBuilder.create(AllIcons.Nodes.Annotationtype)
                .setTarget(target)
                .setTooltipText(tooltip)
                .createLineMarkerInfo(comment);
    }

    private static String provenanceDetails(SpiceDescriptorMetadata metadata) {
        StringBuilder result = new StringBuilder();
        if (!metadata.provenance().version().isBlank()) {
            result.append(" | version ")
                    .append(metadata.provenance().version());
        }
        if (!metadata.provenance().replacement().isBlank()) {
            result.append(" | replace ")
                    .append(metadata.provenance().replacement());
        }
        if (!metadata.tool().isBlank()) {
            result.append(" | tool ").append(metadata.tool());
        }
        if (!metadata.handler().isBlank()) {
            result.append(" | handler ").append(metadata.handler());
        }
        if (!metadata.protocol().isBlank()) {
            result.append(" | protocol ").append(metadata.protocol());
        }
        if (metadata.provenance().authorizationKnown()) {
            result.append(
                    metadata.provenance().authorized()
                            ? " | tool authorized"
                            : " | tool not authorized"
            );
        }
        return result.toString();
    }
}
