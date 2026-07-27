package com.github.stevenbuglione.spice.goland;

import com.goide.psi.GoBlock;
import com.goide.psi.GoFile;
import com.goide.psi.GoParameters;
import com.goide.psi.GoStringLiteral;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Keeps a freshly typed Spice annotation valid Go from its first character.
 *
 * <p>The editor displays the folded line as {@code @...}, but the document
 * receives the canonical {@code // @} source in the same typing command.
 */
public final class SpiceTypedHandler extends TypedHandlerDelegate {
    private static final String ANNOTATION_START = "// @";

    @Override
    public @NotNull Result beforeCharTyped(
            char typed,
            @NotNull Project project,
            @NotNull Editor editor,
            @NotNull PsiFile file,
            @NotNull FileType fileType
    ) {
        if (typed != '@'
                || !(file instanceof GoFile)
                || !currentCaretIsAnnotationPosition(editor, file)) {
            return Result.CONTINUE;
        }

        Caret caret = editor.getCaretModel().getCurrentCaret();
        int offset = caret.getOffset();
        Document document = editor.getDocument();
        String insertion = annotationInsertion(document, offset);
        document.insertString(offset, insertion);
        caret.moveToOffset(offset + ANNOTATION_START.length());
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
        refreshFolding(project, editor);
        return Result.STOP;
    }

    private static String annotationInsertion(Document document, int offset) {
        int line = document.getLineNumber(offset);
        int lineStart = document.getLineStartOffset(line);
        int lineEnd = document.getLineEndOffset(line);
        CharSequence content = document.getImmutableCharSequence();
        if (isHorizontalWhitespace(content, offset, lineEnd)) {
            return ANNOTATION_START;
        }
        return ANNOTATION_START
                + "\n"
                + content.subSequence(lineStart, offset);
    }

    private static boolean currentCaretIsAnnotationPosition(
            Editor editor,
            PsiFile file
    ) {
        Caret caret = editor.getCaretModel().getCurrentCaret();
        return !caret.hasSelection()
                && isAnnotationPosition(
                editor.getDocument(),
                file,
                caret.getOffset()
        );
    }

    private static boolean isAnnotationPosition(
            Document document,
            PsiFile file,
            int offset
    ) {
        int line = document.getLineNumber(offset);
        int lineStart = document.getLineStartOffset(line);
        if (!isHorizontalWhitespace(
                document.getImmutableCharSequence(),
                lineStart,
                offset
        )) {
            return false;
        }

        PsiElement element = elementAt(file, offset);
        if (element == null
                || inside(element, PsiComment.class)
                || inside(element, GoStringLiteral.class)) {
            return false;
        }
        if (inside(element, GoParameters.class)) {
            return true;
        }
        return !inside(element, GoBlock.class);
    }

    private static PsiElement elementAt(PsiFile file, int offset) {
        if (file.getTextLength() == 0) {
            return file;
        }
        int bounded = Math.min(offset, file.getTextLength() - 1);
        PsiElement element = file.findElementAt(bounded);
        if (element == null && bounded > 0) {
            element = file.findElementAt(bounded - 1);
        }
        return element;
    }

    private static <T extends PsiElement> boolean inside(
            PsiElement element,
            Class<T> type
    ) {
        return type.isInstance(element)
                || PsiTreeUtil.getParentOfType(element, type, false) != null;
    }

    private static boolean isHorizontalWhitespace(
            CharSequence content,
            int start,
            int end
    ) {
        for (int index = start; index < end; index++) {
            char character = content.charAt(index);
            if (character != ' ' && character != '\t') {
                return false;
            }
        }
        return true;
    }

    private static void refreshFolding(Project project, Editor editor) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed() || editor.isDisposed()) {
                return;
            }
            PsiDocumentManager.getInstance(project).commitDocument(
                    editor.getDocument()
            );
            CodeFoldingManager.getInstance(project).updateFoldRegions(editor);
        });
    }
}
