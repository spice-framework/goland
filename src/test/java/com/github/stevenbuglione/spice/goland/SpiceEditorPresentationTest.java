package com.github.stevenbuglione.spice.goland;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.JComponent;

public final class SpiceEditorPresentationTest extends BasePlatformTestCase {
    private static final int COMPARISON_BLOCK = 8;
    private static final double MAXIMUM_MEAN_BLOCK_DELTA = 5.0;
    private static final double MAXIMUM_CHANGED_BLOCK_FRACTION = 0.08;
    private static final int CHANGED_BLOCK_DELTA = 24;

    public void testColorSettingsExposeEverySpiceTokenClass() {
        SpiceColorSettingsPage page = new SpiceColorSettingsPage();
        Set<String> descriptors = new HashSet<>();
        Arrays.stream(page.getAttributeDescriptors())
                .forEach(descriptor ->
                        descriptors.add(descriptor.getDisplayName()));
        assertEquals(
                Set.of(
                        "Comment prefix",
                        "Annotation sigil",
                        "Annotation namespace",
                        "Annotation name",
                        "Argument name",
                        "Imported symbol",
                        "Import alias",
                        "Type reference",
                        "String value",
                        "Number value",
                        "Boolean value",
                        "Identifier value",
                        "Directive keyword",
                        "Punctuation",
                        "Unresolved symbol",
                        "Deprecated symbol"
                ),
                descriptors
        );

        Map<String, TextAttributesKey> tags =
                page.getAdditionalHighlightingTagToDescriptorMap();
        assertEquals(SpiceHighlighting.IMPORT_SYMBOL, tags.get("importSymbol"));
        assertEquals(SpiceHighlighting.IMPORT_ALIAS, tags.get("importAlias"));
        assertEquals(SpiceHighlighting.TYPE_REFERENCE, tags.get("type"));
        assertEquals(SpiceHighlighting.UNRESOLVED, tags.get("unresolved"));
        assertEquals(SpiceHighlighting.DEPRECATED, tags.get("deprecated"));
        assertTrue(page.getDemoText().contains("<importAlias>GET</importAlias>"));
        assertTrue(page.getDemoText().contains("<type>payments.Processor</type>"));
    }

    public void testRendersConcealedStructuredAnnotations() throws IOException {
        String source = """
                package main

                import (
                    "os"

                    spiceapp "example.com/app/internal/spicegen/app"
                )

                // @import { Application } from "github.com/spice-framework/spice/annotation/core"
                // @import * as management from "github.com/spice-framework/spice/annotation/management"
                // @import * as data from "github.com/spice-framework/spice/annotation/data"
                // @import * as event from "github.com/spice-framework/spice/annotation/event"

                // @Application
                // @management.Enable(expose=["health", "metrics"])
                // @data.Transactional(readOnly=true, isolation="serializable")
                // @event.Listener(order=10)
                // @Implements(payments.Processor, health.Checker)
                func main() {
                    os.Exit(spiceapp.Main(os.Args[1:]))
                }
                """;
        myFixture.configureByText("main.go", source);
        List<HighlightInfo> highlights = myFixture.doHighlighting();
        CodeFoldingManager.getInstance(getProject())
                .updateFoldRegions(myFixture.getEditor());

        int management = source.indexOf("// @management.Enable");
        assertHighlightAt(
                highlights,
                management,
                "// ".length(),
                SpiceHighlighting.PREFIX
        );
        assertHighlightAt(
                highlights,
                management + "// @".length(),
                "management".length(),
                SpiceHighlighting.NAMESPACE
        );
        assertHighlightAt(
                highlights,
                management + "// @management.".length(),
                "Enable".length(),
                SpiceHighlighting.ANNOTATION
        );
        assertHighlightAt(
                highlights,
                management + "// ".length(),
                1,
                SpiceHighlighting.SIGIL
        );
        assertHighlight(
                highlights,
                source,
                "expose",
                SpiceHighlighting.PARAMETER
        );
        assertHighlight(
                highlights,
                source,
                "\"health\"",
                SpiceHighlighting.STRING
        );
        assertHighlight(
                highlights,
                source,
                "true",
                SpiceHighlighting.BOOLEAN
        );
        assertHighlight(
                highlights,
                source,
                "10",
                SpiceHighlighting.NUMBER
        );
        assertHighlight(
                highlights,
                source,
                "Processor",
                SpiceHighlighting.TYPE_REFERENCE
        );
        assertHighlight(
                highlights,
                source,
                "Application",
                SpiceHighlighting.IMPORT_SYMBOL
        );
        assertEquals(
                9,
                List.of(myFixture.getEditor().getFoldingModel().getAllFoldRegions())
                        .stream()
                        .filter(region -> region.getPlaceholderText().isEmpty())
                        .filter(region -> !region.isExpanded())
                        .count()
        );
        assertConcealmentReclaimsWidth(source, "@Application", "func main");
        assertEquals(
                "folding changed physical source",
                source,
                myFixture.getEditor().getDocument().getText()
        );

        String output = System.getProperty("spice.visual.output");
        if (output != null && !output.isBlank()) {
            Path light = Path.of(output);
            EditorColorsScheme lightScheme = EditorColorsManager.getInstance()
                    .getScheme("Default");
            EditorColorsScheme dark = EditorColorsManager.getInstance()
                    .getScheme("Darcula");
            assertNotNull("Default color scheme", lightScheme);
            assertNotNull("Darcula color scheme", dark);
            assertSchemePalette(lightScheme, false);
            assertSchemePalette(dark, true);
            EditorEx editor = (EditorEx) myFixture.getEditor();
            EditorColorsScheme original = editor.getColorsScheme();
            try {
                editor.setColorsScheme(lightScheme);
                editor.reinitSettings();
                renderEditor(
                        light,
                        "/goldens/spice-annotations-light.png"
                );
                editor.setColorsScheme(dark);
                editor.reinitSettings();
                assertOpenEditorTracksSchemeChanges(editor);
                renderEditor(
                        light.resolveSibling("spice-annotations-dark.png"),
                        "/goldens/spice-annotations-dark.png"
                );
            } finally {
                editor.setColorsScheme(original);
                editor.reinitSettings();
            }
        }
    }

    private void assertConcealmentReclaimsWidth(
            String source,
            String concealedToken,
            String ordinaryToken
    ) {
        EditorEx editor = (EditorEx) myFixture.getEditor();
        int concealedOffset = source.indexOf(concealedToken);
        int ordinaryOffset = source.indexOf(ordinaryToken);
        Point concealed = editor.visualPositionToXY(
                editor.offsetToVisualPosition(concealedOffset)
        );
        Point ordinary = editor.visualPositionToXY(
                editor.offsetToVisualPosition(ordinaryOffset)
        );
        assertEquals(
                "concealed // prefix still consumes horizontal editor width",
                ordinary.x,
                concealed.x
        );
    }

    private static void assertSchemePalette(
            EditorColorsScheme scheme,
            boolean dark
    ) {
        assertForeground(
                scheme,
                SpiceHighlighting.SIGIL,
                dark ? 0xffc66d : 0xc55a11
        );
        assertForeground(
                scheme,
                SpiceHighlighting.NAMESPACE,
                dark ? 0x56a8f5 : 0x00627a
        );
        assertForeground(
                scheme,
                SpiceHighlighting.ANNOTATION,
                dark ? 0xd5b778 : 0x9c3d10
        );
        assertForeground(
                scheme,
                SpiceHighlighting.PARAMETER,
                dark ? 0xc77dbb : 0x871094
        );
        assertForeground(
                scheme,
                SpiceHighlighting.IMPORT_SYMBOL,
                dark ? 0xdcbdfb : 0x7a3e9d
        );
        assertForeground(
                scheme,
                SpiceHighlighting.TYPE_REFERENCE,
                dark ? 0x56a8f5 : 0x00627a
        );
        assertForeground(
                scheme,
                SpiceHighlighting.STRING,
                dark ? 0x6aab73 : 0x067d17
        );
        assertForeground(
                scheme,
                SpiceHighlighting.BOOLEAN,
                dark ? 0xcf8e6d : 0x0033b3
        );
    }

    private static void assertForeground(
            EditorColorsScheme scheme,
            TextAttributesKey key,
            int rgb
    ) {
        TextAttributes attributes = scheme.getAttributes(key);
        assertNotNull(key.getExternalName() + " attributes", attributes);
        assertEquals(
                key.getExternalName() + " foreground",
                new Color(rgb),
                attributes.getForegroundColor()
        );
    }

    private static void assertOpenEditorTracksSchemeChanges(EditorEx editor) {
        EditorColorsScheme scheme = editor.getColorsScheme();
        TextAttributes original = scheme.getAttributes(SpiceHighlighting.SIGIL);
        assertNotNull("sigil attributes", original);
        TextAttributes changed = original.clone();
        changed.setForegroundColor(new Color(0xff00ff));
        try {
            scheme.setAttributes(SpiceHighlighting.SIGIL, changed);
            editor.reinitSettings();
            assertEquals(
                    "open editor retained a stale Spice color",
                    new Color(0xff00ff),
                    editor.getColorsScheme()
                            .getAttributes(SpiceHighlighting.SIGIL)
                            .getForegroundColor()
            );
        } finally {
            scheme.setAttributes(SpiceHighlighting.SIGIL, original);
            editor.reinitSettings();
        }
    }

    private static void assertHighlight(
            List<HighlightInfo> highlights,
            String source,
            String token,
            TextAttributesKey key
    ) {
        int start = source.indexOf(token);
        assertTrue(
                "missing " + key.getExternalName() + " for " + token,
                highlights.stream().anyMatch(info ->
                        info.startOffset == start
                                && info.endOffset == start + token.length()
                                && key.equals(info.forcedTextAttributesKey))
        );
    }

    private static void assertHighlightAt(
            List<HighlightInfo> highlights,
            int start,
            int length,
            TextAttributesKey key
    ) {
        assertTrue(
                "missing " + key.getExternalName() + " at " + start,
                highlights.stream().anyMatch(info ->
                        info.startOffset == start
                                && info.endOffset == start + length
                                && key.equals(info.forcedTextAttributesKey))
        );
    }

    private void renderEditor(
            Path output,
            String goldenResource
    ) throws IOException {
        JComponent component = myFixture.getEditor().getComponent();
        boolean createdPeer = !component.isDisplayable();
        if (createdPeer) {
            component.addNotify();
        }
        try {
            component.setSize(960, 420);
            component.doLayout();
            component.validate();
            BufferedImage image = new BufferedImage(
                    component.getWidth(),
                    component.getHeight(),
                    BufferedImage.TYPE_INT_ARGB
            );
            Graphics2D graphics = image.createGraphics();
            try {
                component.printAll(graphics);
            } finally {
                graphics.dispose();
            }
            assertRenderedContent(image);
            assertMatchesGolden(image, goldenResource);
            Files.createDirectories(output.getParent());
            assertTrue(
                    "write visual report",
                    ImageIO.write(image, "png", output.toFile())
            );
            assertTrue("visual report is empty", Files.size(output) > 1_000);
        } finally {
            if (createdPeer) {
                component.removeNotify();
            }
        }
    }

    private static void assertRenderedContent(BufferedImage image) {
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                colors.add(image.getRGB(x, y));
                if (colors.size() >= 12) {
                    return;
                }
            }
        }
        fail("visual report contains only " + colors.size() + " colors");
    }

    private void assertMatchesGolden(
            BufferedImage image,
            String goldenResource
    ) throws IOException {
        BufferedImage golden;
        try (InputStream stream = getClass().getResourceAsStream(goldenResource)) {
            assertNotNull("missing visual golden " + goldenResource, stream);
            golden = ImageIO.read(stream);
        }
        assertNotNull("decode visual golden " + goldenResource, golden);
        assertEquals("visual golden width", golden.getWidth(), image.getWidth());
        assertEquals("visual golden height", golden.getHeight(), image.getHeight());

        long totalDelta = 0;
        int changedBlocks = 0;
        int blocks = 0;
        for (int y = 0; y < image.getHeight(); y += COMPARISON_BLOCK) {
            for (int x = 0; x < image.getWidth(); x += COMPARISON_BLOCK) {
                int delta = blockDelta(image, golden, x, y);
                totalDelta += delta;
                if (delta > CHANGED_BLOCK_DELTA) {
                    changedBlocks++;
                }
                blocks++;
            }
        }
        double meanDelta = (double) totalDelta / blocks;
        double changedFraction = (double) changedBlocks / blocks;
        assertTrue(
                "visual golden " + goldenResource
                        + " mean block delta " + meanDelta
                        + " exceeds " + MAXIMUM_MEAN_BLOCK_DELTA,
                meanDelta <= MAXIMUM_MEAN_BLOCK_DELTA
        );
        assertTrue(
                "visual golden " + goldenResource
                        + " changed block fraction " + changedFraction
                        + " exceeds " + MAXIMUM_CHANGED_BLOCK_FRACTION,
                changedFraction <= MAXIMUM_CHANGED_BLOCK_FRACTION
        );
    }

    private static int blockDelta(
            BufferedImage actual,
            BufferedImage golden,
            int startX,
            int startY
    ) {
        long[] actualChannels = blockChannels(actual, startX, startY);
        long[] goldenChannels = blockChannels(golden, startX, startY);
        long delta = 0;
        for (int channel = 0; channel < actualChannels.length; channel++) {
            delta += Math.abs(
                    actualChannels[channel] - goldenChannels[channel]
            );
        }
        return Math.toIntExact(delta / actualChannels.length);
    }

    private static long[] blockChannels(
            BufferedImage image,
            int startX,
            int startY
    ) {
        long[] channels = new long[3];
        int pixels = 0;
        int endY = Math.min(image.getHeight(), startY + COMPARISON_BLOCK);
        int endX = Math.min(image.getWidth(), startX + COMPARISON_BLOCK);
        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                int color = image.getRGB(x, y);
                channels[0] += color >> 16 & 0xff;
                channels[1] += color >> 8 & 0xff;
                channels[2] += color & 0xff;
                pixels++;
            }
        }
        for (int channel = 0; channel < channels.length; channel++) {
            channels[channel] /= pixels;
        }
        return channels;
    }
}
