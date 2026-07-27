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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SpiceDescriptorDocumentationTarget implements DocumentationTarget {
    private final PsiComment origin;
    private final SpiceDescriptorMetadata metadata;

    SpiceDescriptorDocumentationTarget(
            PsiComment origin,
            SpiceDescriptorMetadata metadata
    ) {
        this.origin = origin;
        this.metadata = metadata;
    }

    SpiceDescriptorMetadata metadataForTest() {
        return metadata;
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        String localName = metadata.localName();
        SpiceAnnotationIndex.DescriptorSymbol descriptor =
                metadata.descriptor();
        return Pointer.fileRangePointer(
                origin.getContainingFile(),
                origin.getTextRange(),
                (file, range) -> {
                    PsiComment comment = file.findElementAt(
                            range.getStartOffset()
                    ) instanceof PsiComment value ? value : null;
                    if (comment == null) {
                        return null;
                    }
                    SpiceDescriptorMetadata refreshed =
                            SpiceAnnotationIndex.getInstance(
                                    file.getProject()
                            ).metadata(comment, localName, descriptor);
                    return refreshed == null
                            ? null
                            : new SpiceDescriptorDocumentationTarget(
                                    comment,
                                    refreshed
                            );
                }
        );
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder("@" + metadata.localName())
                .containerText(
                        metadata.descriptor().packagePath()
                                + "."
                                + metadata.descriptor().symbol()
                )
                .icon(AllIcons.Nodes.Annotationtype)
                .presentation();
    }

    @Override
    public @Nullable Navigatable getNavigatable() {
        return metadata.descriptorFunction();
    }

    @Override
    public @Nullable String computeDocumentationHint() {
        String summary = metadata.summary().isBlank()
                ? metadata.documentation()
                : metadata.summary();
        if (summary.isBlank()) {
            return metadata.descriptor().packagePath()
                    + "."
                    + metadata.descriptor().symbol();
        }
        int newline = summary.indexOf('\n');
        return newline < 0 ? summary : summary.substring(0, newline);
    }

    @Override
    public @NotNull DocumentationResult computeDocumentation() {
        String rendered = render();
        return DocumentationResult.documentation(rendered);
    }

    String render() {
        StringBuilder result = new StringBuilder();
        result.append(DocumentationMarkup.DEFINITION_START)
                .append("<b>@")
                .append(escape(metadata.localName()))
                .append("</b><br><code>")
                .append(escape(metadata.signature()))
                .append("</code>")
                .append(DocumentationMarkup.DEFINITION_END);
        if (!metadata.documentation().isBlank()
                || !metadata.summary().isBlank()) {
            result.append(DocumentationMarkup.CONTENT_START);
            if (!metadata.documentation().isBlank()) {
                result.append(paragraphs(metadata.documentation()));
            }
            if (!metadata.summary().isBlank()
                    && !metadata.documentation()
                    .contains(metadata.summary())) {
                result.append("<p>")
                        .append(escape(metadata.summary()))
                        .append("</p>");
            }
            result.append(DocumentationMarkup.CONTENT_END);
        }
        result.append(DocumentationMarkup.SECTIONS_START);
        section(
                result,
                "Descriptor",
                metadata.descriptor().packagePath()
                        + "."
                        + metadata.descriptor().symbol()
        );
        section(result, "Module", metadata.provenance().modulePath());
        section(result, "Version", metadata.provenance().version());
        section(
                result,
                "Replacement",
                metadata.provenance().replacement()
        );
        section(result, "Targets", metadata.targets());
        section(result, "Tool", metadata.tool());
        if (metadata.provenance().authorizationKnown()) {
            section(
                    result,
                    "Tool authorization",
                    metadata.provenance().authorized()
                            ? "declared in application go.mod"
                            : "not declared in application go.mod"
            );
        }
        section(result, "Handler", implementationLabel());
        section(result, "Protocol", metadata.protocol());
        if (metadata.handlerFunction() != null
                && metadata.handlerFunction().getContainingFile() != null
                && metadata.handlerFunction().getContainingFile()
                .getVirtualFile() != null) {
            section(
                    result,
                    "Implementation source",
                    metadata.handlerFunction().getContainingFile()
                            .getVirtualFile()
                            .getPresentableUrl()
            );
        }
        result.append(DocumentationMarkup.SECTIONS_END);
        return result.toString();
    }

    private String implementationLabel() {
        if (metadata.handlerFunction() == null) {
            return metadata.handler();
        }
        String name = metadata.handlerFunction().getName();
        return name == null ? metadata.handler() : name;
    }

    private static void section(
            StringBuilder result,
            String label,
            String value
    ) {
        if (value.isBlank()) {
            return;
        }
        result.append(DocumentationMarkup.SECTION_HEADER_START)
                .append(escape(label))
                .append(DocumentationMarkup.SECTION_SEPARATOR)
                .append("<code>")
                .append(escape(value))
                .append("</code>")
                .append(DocumentationMarkup.SECTION_END);
    }

    private static String paragraphs(String value) {
        return "<p>"
                + escape(value).replace("\n\n", "</p><p>")
                .replace("\n", "<br>")
                + "</p>";
    }

    private static String escape(String value) {
        return StringUtil.escapeXmlEntities(value);
    }
}
