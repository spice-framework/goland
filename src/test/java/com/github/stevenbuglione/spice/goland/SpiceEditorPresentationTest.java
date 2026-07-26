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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.JComponent;

public final class SpiceEditorPresentationTest extends BasePlatformTestCase {
    public void testRendersConcealedStructuredAnnotations() throws IOException {
        String source = """
                package main

                import "os"

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

        assertHighlight(
                highlights,
                source,
                "@management.Enable",
                SpiceHighlighting.ANNOTATION
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
                4,
                List.of(myFixture.getEditor().getFoldingModel().getAllFoldRegions())
                        .stream()
                        .filter(region -> region.getPlaceholderText().isEmpty())
                        .filter(region -> !region.isExpanded())
                        .count()
        );

        String output = System.getProperty("spice.visual.output");
        if (output != null && !output.isBlank()) {
            Path light = Path.of(output);
            renderEditor(light);
            EditorColorsScheme dark = EditorColorsManager.getInstance()
                    .getScheme("Darcula");
            assertNotNull("Darcula color scheme", dark);
            EditorEx editor = (EditorEx) myFixture.getEditor();
            EditorColorsScheme original = editor.getColorsScheme();
            try {
                editor.setColorsScheme(dark);
                editor.reinitSettings();
                renderEditor(light.resolveSibling("spice-annotations-dark.png"));
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

    private void renderEditor(Path output) throws IOException {
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
}
