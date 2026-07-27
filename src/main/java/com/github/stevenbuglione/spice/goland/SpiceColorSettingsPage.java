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
import static java.util.Map.entry;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SpiceColorSettingsPage implements ColorSettingsPage {
    private static final AttributesDescriptor[] DESCRIPTORS = {
            new AttributesDescriptor("Comment prefix", SpiceHighlighting.PREFIX),
            new AttributesDescriptor("Annotation sigil", SpiceHighlighting.SIGIL),
            new AttributesDescriptor("Annotation namespace", SpiceHighlighting.NAMESPACE),
            new AttributesDescriptor("Annotation name", SpiceHighlighting.ANNOTATION),
            new AttributesDescriptor("Argument name", SpiceHighlighting.PARAMETER),
            new AttributesDescriptor("Imported symbol", SpiceHighlighting.IMPORT_SYMBOL),
            new AttributesDescriptor("Import alias", SpiceHighlighting.IMPORT_ALIAS),
            new AttributesDescriptor("Type reference", SpiceHighlighting.TYPE_REFERENCE),
            new AttributesDescriptor("String value", SpiceHighlighting.STRING),
            new AttributesDescriptor("Number value", SpiceHighlighting.NUMBER),
            new AttributesDescriptor("Boolean value", SpiceHighlighting.BOOLEAN),
            new AttributesDescriptor("Identifier value", SpiceHighlighting.IDENTIFIER),
            new AttributesDescriptor("Directive keyword", SpiceHighlighting.KEYWORD),
            new AttributesDescriptor("Punctuation", SpiceHighlighting.OPERATOR),
            new AttributesDescriptor("Unresolved symbol", SpiceHighlighting.UNRESOLVED),
            new AttributesDescriptor("Deprecated symbol", SpiceHighlighting.DEPRECATED)
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
    private static final Map<String, TextAttributesKey> TAGS = Map.ofEntries(
            entry("prefix", SpiceHighlighting.PREFIX),
            entry("sigil", SpiceHighlighting.SIGIL),
            entry("namespace", SpiceHighlighting.NAMESPACE),
            entry("annotation", SpiceHighlighting.ANNOTATION),
            entry("parameter", SpiceHighlighting.PARAMETER),
            entry("importSymbol", SpiceHighlighting.IMPORT_SYMBOL),
            entry("importAlias", SpiceHighlighting.IMPORT_ALIAS),
            entry("type", SpiceHighlighting.TYPE_REFERENCE),
            entry("string", SpiceHighlighting.STRING),
            entry("number", SpiceHighlighting.NUMBER),
            entry("boolean", SpiceHighlighting.BOOLEAN),
            entry("identifier", SpiceHighlighting.IDENTIFIER),
            entry("keyword", SpiceHighlighting.KEYWORD),
            entry("operator", SpiceHighlighting.OPERATOR),
            entry("unresolved", SpiceHighlighting.UNRESOLVED),
            entry("deprecated", SpiceHighlighting.DEPRECATED)
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
                <prefix>// </prefix><sigil>@</sigil><namespace>spice</namespace><operator>.</operator><keyword>import</keyword> <operator>{</operator> <importSymbol>Controller</importSymbol><operator>,</operator> <importSymbol>Get</importSymbol> <keyword>as</keyword> <importAlias>GET</importAlias> <operator>}</operator> <keyword>from</keyword> <string>"example.com/annotation/web"</string>
                <prefix>// </prefix><sigil>@</sigil><annotation>Application</annotation>
                <prefix>// </prefix><sigil>@</sigil><namespace>management</namespace><operator>.</operator><annotation>Enable</annotation><operator>(</operator><parameter>expose</parameter><operator>=</operator><operator>[</operator><string>"health"</string><operator>,</operator> <string>"metrics"</string><operator>]</operator><operator>)</operator>
                <prefix>// </prefix><sigil>@</sigil><annotation>Implements</annotation><operator>(</operator><type>payments.Processor</type><operator>)</operator>
                <prefix>// </prefix><sigil>@</sigil><namespace>data</namespace><operator>.</operator><annotation>Transactional</annotation><operator>(</operator><parameter>readOnly</parameter><operator>=</operator><boolean>true</boolean><operator>,</operator> <parameter>isolation</parameter><operator>=</operator><string>"serializable"</string><operator>)</operator>
                <prefix>// </prefix><sigil>@</sigil><namespace>event</namespace><operator>.</operator><annotation>Listener</annotation><operator>(</operator><parameter>order</parameter><operator>=</operator><number>10</number><operator>)</operator>
                <prefix>// </prefix><sigil>@</sigil><unresolved>Missing</unresolved>
                <prefix>// </prefix><sigil>@</sigil><deprecated>Legacy</deprecated>
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
