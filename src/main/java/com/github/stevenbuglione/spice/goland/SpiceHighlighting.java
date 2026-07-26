package com.github.stevenbuglione.spice.goland;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

final class SpiceHighlighting {
    static final TextAttributesKey ANNOTATION = key(
            "SPICE_ANNOTATION",
            DefaultLanguageHighlighterColors.METADATA
    );
    static final TextAttributesKey PARAMETER = key(
            "SPICE_ANNOTATION_PARAMETER",
            DefaultLanguageHighlighterColors.PARAMETER
    );
    static final TextAttributesKey STRING = key(
            "SPICE_ANNOTATION_STRING",
            DefaultLanguageHighlighterColors.STRING
    );
    static final TextAttributesKey NUMBER = key(
            "SPICE_ANNOTATION_NUMBER",
            DefaultLanguageHighlighterColors.NUMBER
    );
    static final TextAttributesKey KEYWORD = key(
            "SPICE_ANNOTATION_KEYWORD",
            DefaultLanguageHighlighterColors.KEYWORD
    );
    static final TextAttributesKey OPERATOR = key(
            "SPICE_ANNOTATION_OPERATOR",
            DefaultLanguageHighlighterColors.OPERATION_SIGN
    );

    private SpiceHighlighting() {}

    static TextAttributesKey forKind(SpiceAnnotationSyntax.TokenKind kind) {
        return switch (kind) {
            case ANNOTATION -> ANNOTATION;
            case PARAMETER -> PARAMETER;
            case STRING -> STRING;
            case NUMBER -> NUMBER;
            case KEYWORD -> KEYWORD;
            case OPERATOR -> OPERATOR;
        };
    }

    private static TextAttributesKey key(
            String name,
            TextAttributesKey fallback
    ) {
        return TextAttributesKey.createTextAttributesKey(name, fallback);
    }
}
