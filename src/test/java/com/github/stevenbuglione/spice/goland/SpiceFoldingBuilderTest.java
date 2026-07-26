package com.github.stevenbuglione.spice.goland;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.EditorCopyPasteHelper;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.concurrent.atomic.AtomicReference;

public final class SpiceFoldingBuilderTest extends BasePlatformTestCase {
    public void testConcealsOnlyCommentPrefixWithEmptyPlaceholder() {
        String source = """
                package main

                // @Application
                // ordinary comment
                func main() {}
                """;
        myFixture.configureByText("main.go", source);

        SpiceFoldingBuilder builder = new SpiceFoldingBuilder();
        FoldingDescriptor[] descriptors = builder.buildFoldRegions(
                myFixture.getFile(),
                myFixture.getEditor().getDocument(),
                false
        );

        assertEquals(1, descriptors.length);
        int prefixStart = source.indexOf("// @Application");
        assertEquals(new TextRange(prefixStart, prefixStart + 3), descriptors[0].getRange());
        assertEquals("", descriptors[0].getPlaceholderText());
        assertEquals(Boolean.TRUE, descriptors[0].isCollapsedByDefault());
        assertTrue(descriptors[0].isNonExpandable());

        AtomicReference<FoldRegion> region = new AtomicReference<>();
        myFixture.getEditor().getFoldingModel().runBatchFoldingOperation(() -> {
            FoldRegion created = myFixture.getEditor()
                    .getFoldingModel()
                    .addFoldRegion(prefixStart, prefixStart + 3, "");
            assertNotNull(created);
            created.setExpanded(false);
            region.set(created);
        });
        assertNotNull(region.get());
        assertEquals("", region.get().getPlaceholderText());
        assertFalse(region.get().isExpanded());
    }

    public void testRegisteredBuilderAutomaticallyConcealsCommentPrefix() {
        String source = """
                package main

                // @Application
                func main() {}
                """;
        myFixture.configureByText("main.go", source);

        CodeFoldingManager.getInstance(getProject())
                .updateFoldRegions(myFixture.getEditor());

        int prefixStart = source.indexOf("// @Application");
        FoldRegion region = CodeFoldingManager.getInstance(getProject())
                .findFoldRegion(
                        myFixture.getEditor(),
                        prefixStart,
                        prefixStart + 3
                );
        assertNotNull(region);
        assertEquals("", region.getPlaceholderText());
        assertFalse(region.isExpanded());
        assertEquals(
                "@Application",
                source.substring(region.getEndOffset(), source.indexOf('\n', prefixStart))
        );
    }

    public void testConcealmentPreservesGoSourceForPersistenceAndCopy()
            throws Exception {
        String source = """
                package main

                // @Application
                // @management.Enable(expose=["health"])
                // @observability.Logging
                func main() {}
                """;
        PsiFile file = myFixture.addFileToProject("persist.go", source);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        CodeFoldingManager.getInstance(getProject())
                .updateFoldRegions(myFixture.getEditor());

        assertEquals(source, myFixture.getEditor().getDocument().getText());
        myFixture.getEditor().getSelectionModel().setSelection(0, source.length());
        Transferable selection = EditorCopyPasteHelper.getInstance()
                .getSelectionTransferable(
                        myFixture.getEditor(),
                        EditorCopyPasteHelper.CopyPasteOptions.DEFAULT
                );
        assertNotNull(selection);
        assertEquals(source, selection.getTransferData(DataFlavor.stringFlavor));

        PsiDocumentManager.getInstance(getProject())
                .commitDocument(myFixture.getEditor().getDocument());
        assertEquals(source, myFixture.getFile().getText());
        FileDocumentManager.getInstance()
                .saveDocument(myFixture.getEditor().getDocument());
        assertEquals(source, VfsUtilCore.loadText(file.getVirtualFile()));
    }
}
