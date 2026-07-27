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
        if ("spice.import".equals(match.name())) {
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
        return NavigationGutterIconBuilder.create(AllIcons.Nodes.Annotationtype)
                .setTarget(target)
                .setTooltipText("Spice annotation from " + provenance)
                .createLineMarkerInfo(comment);
    }
}
