package com.github.stevenbuglione.spice.goland;

import com.intellij.icons.AllIcons;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.model.Pointer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SpicePackageDocumentationTarget implements DocumentationTarget {
    private final PsiComment origin;
    private final String packagePath;
    private final @Nullable PsiElement packageElement;
    private final SpiceAnnotationIndex.ModuleProvenance provenance;

    SpicePackageDocumentationTarget(PsiComment origin, String packagePath) {
        this.origin = origin;
        this.packagePath = packagePath;
        this.packageElement = SpiceAnnotationIndex.getInstance(
                origin.getProject()
        ).resolvePackage(origin, packagePath);
        this.provenance = SpiceAnnotationIndex.getInstance(
                origin.getProject()
        ).provenance(packagePath, "");
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        SmartPsiElementPointer<PsiComment> pointer =
                SmartPointerManager.createPointer(origin);
        return Pointer.delegatingPointer(
                pointer,
                comment -> new SpicePackageDocumentationTarget(
                        comment,
                        packagePath
                )
        );
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder(packagePath)
                .containerText("Spice annotation package")
                .icon(AllIcons.Nodes.Package)
                .presentation();
    }

    @Override
    public @Nullable Navigatable getNavigatable() {
        if (packageElement instanceof Navigatable navigatable) {
            return navigatable;
        }
        return null;
    }

    @Override
    public @NotNull String computeDocumentationHint() {
        return packagePath;
    }

    @Override
    public @NotNull DocumentationResult computeDocumentation() {
        return DocumentationResult.documentation(render());
    }

    String render() {
        String escaped = StringUtil.escapeXmlEntities(packagePath);
        String source = "";
        if (packageElement != null
                && packageElement.getContainingFile() != null
                && packageElement.getContainingFile().getVirtualFile() != null) {
            source = packageElement.getContainingFile()
                    .getVirtualFile()
                    .getPresentableUrl();
        }
        StringBuilder result = new StringBuilder()
                .append(DocumentationMarkup.DEFINITION_START)
                .append("<b>")
                .append(escaped)
                .append("</b>")
                .append(DocumentationMarkup.DEFINITION_END)
                .append(DocumentationMarkup.CONTENT_START)
                .append("Explicit Spice annotation descriptor package.")
                .append(DocumentationMarkup.CONTENT_END);
        if (!source.isBlank()) {
            result.append(DocumentationMarkup.SECTIONS_START)
                    .append(DocumentationMarkup.SECTION_HEADER_START)
                    .append("Indexed source")
                    .append(DocumentationMarkup.SECTION_SEPARATOR)
                    .append("<code>")
                    .append(StringUtil.escapeXmlEntities(source))
                    .append("</code>")
                    .append(DocumentationMarkup.SECTION_END);
            packageSection(result, "Module", provenance.modulePath());
            packageSection(result, "Version", provenance.version());
            packageSection(result, "Replacement", provenance.replacement());
            result.append(DocumentationMarkup.SECTIONS_END);
        } else if (!provenance.modulePath().isBlank()) {
            result.append(DocumentationMarkup.SECTIONS_START);
            packageSection(result, "Module", provenance.modulePath());
            packageSection(result, "Version", provenance.version());
            packageSection(result, "Replacement", provenance.replacement());
            result.append(DocumentationMarkup.SECTIONS_END);
        }
        return result.toString();
    }

    private static void packageSection(
            StringBuilder result,
            String name,
            String value
    ) {
        if (value.isBlank()) {
            return;
        }
        result.append(DocumentationMarkup.SECTION_HEADER_START)
                .append(StringUtil.escapeXmlEntities(name))
                .append(DocumentationMarkup.SECTION_SEPARATOR)
                .append("<code>")
                .append(StringUtil.escapeXmlEntities(value))
                .append("</code>")
                .append(DocumentationMarkup.SECTION_END);
    }
}
