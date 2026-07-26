package com.github.stevenbuglione.spice.goland;

import com.intellij.lexer.EmptyLexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.psi.tree.IElementType;
import java.util.Map;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SpiceColorSettingsPage implements ColorSettingsPage {
    private static final AttributesDescriptor[] DESCRIPTORS = {
            new AttributesDescriptor("Annotation name", SpiceHighlighting.ANNOTATION),
            new AttributesDescriptor("Argument name", SpiceHighlighting.PARAMETER),
            new AttributesDescriptor("String value", SpiceHighlighting.STRING),
            new AttributesDescriptor("Number value", SpiceHighlighting.NUMBER),
            new AttributesDescriptor("Keyword value", SpiceHighlighting.KEYWORD),
            new AttributesDescriptor("Punctuation", SpiceHighlighting.OPERATOR)
    };
    private static final SyntaxHighlighter HIGHLIGHTER = new SyntaxHighlighterBase() {
        @Override
        public @NotNull EmptyLexer getHighlightingLexer() {
            return new EmptyLexer();
        }

        @Override
        public TextAttributesKey @NotNull [] getTokenHighlights(
                IElementType tokenType
        ) {
            return TextAttributesKey.EMPTY_ARRAY;
        }
    };
    private static final Map<String, TextAttributesKey> TAGS = Map.of(
            "annotation", SpiceHighlighting.ANNOTATION,
            "parameter", SpiceHighlighting.PARAMETER,
            "string", SpiceHighlighting.STRING,
            "number", SpiceHighlighting.NUMBER,
            "keyword", SpiceHighlighting.KEYWORD,
            "operator", SpiceHighlighting.OPERATOR
    );

    @Override
    public @Nullable Icon getIcon() {
        return null;
    }

    @Override
    public @NotNull SyntaxHighlighter getHighlighter() {
        return HIGHLIGHTER;
    }

    @Override
    public @NotNull String getDemoText() {
        return """
                // <annotation>@Application</annotation>
                // <annotation>@management.Enable</annotation><operator>(</operator><parameter>expose</parameter><operator>=</operator><operator>[</operator><string>"health"</string><operator>,</operator> <string>"metrics"</string><operator>]</operator><operator>)</operator>
                // <annotation>@data.Transactional</annotation><operator>(</operator><parameter>readOnly</parameter><operator>=</operator><keyword>true</keyword><operator>,</operator> <parameter>isolation</parameter><operator>=</operator><string>"serializable"</string><operator>)</operator>
                // <annotation>@event.Listener</annotation><operator>(</operator><parameter>order</parameter><operator>=</operator><number>10</number><operator>)</operator>
                """;
    }

    @Override
    public @NotNull Map<String, TextAttributesKey>
            getAdditionalHighlightingTagToDescriptorMap() {
        return TAGS;
    }

    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return DESCRIPTORS.clone();
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Spice";
    }
}
