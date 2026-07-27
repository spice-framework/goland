package com.github.stevenbuglione.spice.goland;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

final class SpiceHighlighting {
    static final TextAttributesKey PREFIX = key(
            "SPICE_COMMENT_PREFIX",
            DefaultLanguageHighlighterColors.LINE_COMMENT
    );
    static final TextAttributesKey SIGIL = key(
            "SPICE_ANNOTATION_SIGIL",
            DefaultLanguageHighlighterColors.OPERATION_SIGN
    );
    static final TextAttributesKey NAMESPACE = key(
            "SPICE_ANNOTATION_NAMESPACE",
            DefaultLanguageHighlighterColors.CLASS_REFERENCE
    );
    static final TextAttributesKey ANNOTATION = key(
            "SPICE_ANNOTATION",
            DefaultLanguageHighlighterColors.METADATA
    );
    static final TextAttributesKey PARAMETER = key(
            "SPICE_ANNOTATION_PARAMETER",
            DefaultLanguageHighlighterColors.PARAMETER
    );
    static final TextAttributesKey IMPORT_SYMBOL = key(
            "SPICE_IMPORT_SYMBOL",
            DefaultLanguageHighlighterColors.METADATA
    );
    static final TextAttributesKey IMPORT_ALIAS = key(
            "SPICE_IMPORT_ALIAS",
            DefaultLanguageHighlighterColors.CLASS_REFERENCE
    );
    static final TextAttributesKey TYPE_REFERENCE = key(
            "SPICE_TYPE_REFERENCE",
            DefaultLanguageHighlighterColors.CLASS_REFERENCE
    );
    static final TextAttributesKey STRING = key(
            "SPICE_ANNOTATION_STRING",
            DefaultLanguageHighlighterColors.STRING
    );
    static final TextAttributesKey NUMBER = key(
            "SPICE_ANNOTATION_NUMBER",
            DefaultLanguageHighlighterColors.NUMBER
    );
    static final TextAttributesKey BOOLEAN = key(
            "SPICE_ANNOTATION_BOOLEAN",
            DefaultLanguageHighlighterColors.KEYWORD
    );
    static final TextAttributesKey IDENTIFIER = key(
            "SPICE_ANNOTATION_IDENTIFIER",
            DefaultLanguageHighlighterColors.IDENTIFIER
    );
    static final TextAttributesKey KEYWORD = key(
            "SPICE_ANNOTATION_KEYWORD",
            DefaultLanguageHighlighterColors.KEYWORD
    );
    static final TextAttributesKey OPERATOR = key(
            "SPICE_ANNOTATION_OPERATOR",
            DefaultLanguageHighlighterColors.OPERATION_SIGN
    );
    static final TextAttributesKey UNRESOLVED = key(
            "SPICE_UNRESOLVED_SYMBOL",
            CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES
    );
    static final TextAttributesKey DEPRECATED = key(
            "SPICE_DEPRECATED_SYMBOL",
            CodeInsightColors.DEPRECATED_ATTRIBUTES
    );

    private SpiceHighlighting() {}

    static TextAttributesKey forKind(SpiceAnnotationSyntax.TokenKind kind) {
        return switch (kind) {
            case PREFIX -> PREFIX;
            case SIGIL -> SIGIL;
            case NAMESPACE -> NAMESPACE;
            case ANNOTATION -> ANNOTATION;
            case PARAMETER -> PARAMETER;
            case IMPORT_SYMBOL -> IMPORT_SYMBOL;
            case IMPORT_ALIAS -> IMPORT_ALIAS;
            case TYPE_REFERENCE -> TYPE_REFERENCE;
            case STRING -> STRING;
            case NUMBER -> NUMBER;
            case BOOLEAN -> BOOLEAN;
            case IDENTIFIER -> IDENTIFIER;
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
