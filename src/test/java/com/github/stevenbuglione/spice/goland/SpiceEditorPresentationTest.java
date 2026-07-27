package com.github.stevenbuglione.spice.goland;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.JComponent;

public final class SpiceEditorPresentationTest extends BasePlatformTestCase {
    private static final int COMPARISON_BLOCK = 8;
    private static final double MAXIMUM_MEAN_BLOCK_DELTA = 10.0;
    private static final double MAXIMUM_CHANGED_BLOCK_FRACTION = 0.20;
    private static final int CHANGED_BLOCK_DELTA = 24;

    public void testRendersConcealedStructuredAnnotations() throws IOException {
        String source = """
                package main

                import "os"

                // @spice.import { Application } from "github.com/StevenBuglione/spice/annotation/core"
                // @spice.import * as management from "github.com/StevenBuglione/spice/annotation/management"
                // @spice.import * as data from "github.com/StevenBuglione/spice/annotation/data"
                // @spice.import * as event from "github.com/StevenBuglione/spice/annotation/event"

                // @Application
                // @management.Enable(expose=["health", "metrics"])
                // @data.Transactional(readOnly=true, isolation="serializable")
                // @event.Listener(order=10)
                func main() {
                    os.Exit(spiceMain(os.Args[1:]))
                }
                """;
        myFixture.configureByText("main.go", source);
        List<HighlightInfo> highlights = myFixture.doHighlighting();
        CodeFoldingManager.getInstance(getProject())
                .updateFoldRegions(myFixture.getEditor());

        int management = source.indexOf("// @management.Enable");
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
                SpiceHighlighting.KEYWORD
        );
        assertHighlight(
                highlights,
                source,
                "10",
                SpiceHighlighting.NUMBER
        );
        assertEquals(
                8,
                List.of(myFixture.getEditor().getFoldingModel().getAllFoldRegions())
                        .stream()
                        .filter(region -> region.getPlaceholderText().isEmpty())
                        .filter(region -> !region.isExpanded())
                        .count()
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
