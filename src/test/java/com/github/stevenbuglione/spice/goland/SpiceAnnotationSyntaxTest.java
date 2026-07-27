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
                        "PREFIX:// ",
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
                        "BOOLEAN:true",
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
                "PREFIX:// ",
                "SIGIL:@",
                "NAMESPACE:spice",
                "KEYWORD:import",
                "IMPORT_SYMBOL:Controller",
                "IMPORT_SYMBOL:Get",
                "KEYWORD:as",
                "IMPORT_ALIAS:GET",
                "KEYWORD:from",
                "STRING:\"example.com/sdk/web\""
        )) {
            assertTrue(expected, tokens.contains(expected));
        }
    }

    public void testParsesExactImportReferenceRanges() {
        String named =
                "// @spice.import { Controller, Get as GET } from \"example.com/sdk/web\"";
        SpiceAnnotationSyntax.ImportDirective directive =
                SpiceAnnotationSyntax.parseImportDirective(named).orElseThrow();
        assertEquals("example.com/sdk/web", directive.packagePath());
        assertEquals(
                "example.com/sdk/web",
                text(named, directive.packageRange())
        );
        assertEquals(2, directive.bindings().size());
        SpiceAnnotationSyntax.ImportBinding controller =
                directive.bindings().getFirst();
        assertEquals("Controller", controller.importedName());
        assertEquals("Controller", controller.localName());
        assertEquals("Controller", text(named, controller.importedRange()));
        assertEquals("Controller", text(named, controller.localRange()));
        assertFalse(controller.namespace());

        SpiceAnnotationSyntax.ImportBinding get =
                directive.bindings().get(1);
        assertEquals("Get", text(named, get.importedRange()));
        assertEquals("GET", text(named, get.localRange()));
        assertEquals("GET", get.localName());

        String namespace =
                "// @spice.import * as web from \"example.com/sdk/web\"";
        SpiceAnnotationSyntax.ImportDirective namespaceDirective =
                SpiceAnnotationSyntax.parseImportDirective(namespace)
                        .orElseThrow();
        SpiceAnnotationSyntax.ImportBinding web =
                namespaceDirective.bindings().getFirst();
        assertTrue(web.namespace());
        assertEquals("web", text(namespace, web.localRange()));
        assertEquals(
                "example.com/sdk/web",
                text(namespace, namespaceDirective.packageRange())
        );
    }

    public void testRejectsMalformedImportReferenceRanges() {
        for (String source : List.of(
                "// @spice.import {} from \"example.com/sdk/web\"",
                "// @spice.import { Controller as } from \"example.com/sdk/web\"",
                "// @spice.import * as 9web from \"example.com/sdk/web\""
        )) {
            assertTrue(
                    source,
                    SpiceAnnotationSyntax.parseImportDirective(source).isEmpty()
            );
        }
    }

    public void testSegmentsTypedInterfaceReferences() {
        String comment =
                "// @Implements(payments.Processor, health.Checker)";

        assertEquals(
                List.of(
                        "PREFIX:// ",
                        "SIGIL:@",
                        "ANNOTATION:Implements",
                        "OPERATOR:(",
                        "NAMESPACE:payments",
                        "OPERATOR:.",
                        "TYPE_REFERENCE:Processor",
                        "OPERATOR:,",
                        "NAMESPACE:health",
                        "OPERATOR:.",
                        "TYPE_REFERENCE:Checker",
                        "OPERATOR:)"
                ),
                SpiceAnnotationSyntax.highlightTokens(comment).stream()
                        .map(token -> token.kind()
                                + ":"
                                + comment.substring(
                                        token.range().getStartOffset(),
                                        token.range().getEndOffset()
                                ))
                        .toList()
        );
    }

    private static String text(String source, TextRange range) {
        return source.substring(range.getStartOffset(), range.getEndOffset());
    }
}
