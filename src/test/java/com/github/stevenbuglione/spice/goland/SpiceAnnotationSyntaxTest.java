package com.github.stevenbuglione.spice.goland;

import com.intellij.openapi.util.TextRange;
import java.util.List;
import junit.framework.TestCase;

public final class SpiceAnnotationSyntaxTest extends TestCase {
    public void testRecognizesCanonicalAnnotationComments() {
        SpiceAnnotationSyntax.Match match = SpiceAnnotationSyntax
                .parse("// @management.Enable(expose=[\"health\"])")
                .orElseThrow();

        assertEquals("management.Enable", match.name());
        assertEquals(new TextRange(0, 3), match.prefixRange());
        assertEquals(new TextRange(3, 21), match.referenceRange());
    }

    public void testRejectsOrdinaryAndMalformedComments() {
        String[] comments = {
                "// ordinary @Application text",
                "/* @Application */",
                "//@Application",
                "// @",
                "// @management.",
                "// @management..Enable",
                "// @invalid-name"
        };
        for (String comment : comments) {
            assertTrue(comment, SpiceAnnotationSyntax.parse(comment).isEmpty());
        }
    }

    public void testSegmentsAnnotationPresentationTokens() {
        String comment =
                "// @fixture.Sample(name=\"value\", count=-12, enabled=true)";
        List<SpiceAnnotationSyntax.Token> tokens =
                SpiceAnnotationSyntax.highlightTokens(comment);

        assertEquals(
                List.of(
                        "SIGIL:@",
                        "NAMESPACE:fixture",
                        "OPERATOR:.",
                        "ANNOTATION:Sample",
                        "OPERATOR:(",
                        "PARAMETER:name",
                        "OPERATOR:=",
                        "STRING:\"value\"",
                        "OPERATOR:,",
                        "PARAMETER:count",
                        "OPERATOR:=",
                        "NUMBER:-12",
                        "OPERATOR:,",
                        "PARAMETER:enabled",
                        "OPERATOR:=",
                        "KEYWORD:true",
                        "OPERATOR:)"
                ),
                tokens.stream()
                        .map(token -> token.kind()
                                + ":"
                                + comment.substring(
                                        token.range().getStartOffset(),
                                        token.range().getEndOffset()
                                ))
                        .toList()
        );
    }

    public void testSegmentsAndConcealsAnnotationImports() {
        String comment =
                "// @spice.import { Controller, Get as GET } from \"example.com/sdk/web\"";
        assertEquals(
                new TextRange(0, 3),
                SpiceAnnotationSyntax.concealmentRange(comment).orElseThrow()
        );
        List<String> tokens = SpiceAnnotationSyntax.highlightTokens(comment)
                .stream()
                .map(token -> token.kind()
                        + ":"
                        + comment.substring(
                                token.range().getStartOffset(),
                                token.range().getEndOffset()
                        ))
                .toList();
        for (String expected : List.of(
                "SIGIL:@",
                "NAMESPACE:spice",
                "KEYWORD:import",
                "ANNOTATION:Controller",
                "ANNOTATION:Get",
                "KEYWORD:as",
                "ANNOTATION:GET",
                "KEYWORD:from",
                "STRING:\"example.com/sdk/web\""
        )) {
            assertTrue(expected, tokens.contains(expected));
        }
    }
}
