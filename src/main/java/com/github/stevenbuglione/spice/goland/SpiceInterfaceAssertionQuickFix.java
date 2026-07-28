package com.github.stevenbuglione.spice.goland;

import com.goide.psi.GoFile;
import com.goide.psi.GoTypeSpec;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import java.util.regex.Pattern;

final class SpiceInterfaceAssertionQuickFix
        extends LocalQuickFixAndIntentionActionOnPsiElement {
    private final SmartPsiElementPointer<PsiComment> annotation;
    private final SmartPsiElementPointer<GoTypeSpec> target;
    private final String interfaceReference;
    private final boolean pointerOutput;

    SpiceInterfaceAssertionQuickFix(
            PsiComment annotation,
            GoTypeSpec target,
            String interfaceReference,
            boolean pointerOutput
    ) {
        super(annotation);
        SmartPointerManager pointers = SmartPointerManager.getInstance(
                annotation.getProject()
        );
        this.annotation = pointers.createSmartPsiElementPointer(annotation);
        this.target = pointers.createSmartPsiElementPointer(target);
        this.interfaceReference = interfaceReference;
        this.pointerOutput = pointerOutput;
    }

    @Override
    public String getText() {
        return "Add compile-time assertion for " + interfaceReference;
    }

    @Override
    public String getFamilyName() {
        return "Add Spice interface assertion";
    }

    @Override
    public boolean isAvailable(
            Project project,
            PsiFile file,
            Editor editor,
            PsiElement startElement,
            PsiElement endElement
    ) {
        return file instanceof GoFile
                && annotation.getElement() != null
                && target.getElement() != null
                && isRequired(file.getText());
    }

    @Override
    public void invoke(
            Project project,
            PsiFile file,
            Editor editor,
            PsiElement startElement,
            PsiElement endElement
    ) {
        PsiComment marker = annotation.getElement();
        GoTypeSpec targetType = target.getElement();
        if (!(file instanceof GoFile)
                || editor == null
                || marker == null
                || targetType == null
                || targetType.getName() == null
                || hasAssertion(editor.getDocument().getText())) {
            return;
        }
        Document document = editor.getDocument();
        int insertion = annotationGroupStart(
                document,
                marker.getTextRange().getStartOffset()
        );
        String assertion = "var _ " + interfaceReference + " = "
                + assertionValue(targetType.getName()) + "\n\n";
        WriteCommandAction.runWriteCommandAction(
                project,
                () -> document.insertString(insertion, assertion)
        );
    }

    boolean isRequired(String source) {
        return !hasAssertion(source);
    }

    private boolean hasAssertion(String source) {
        GoTypeSpec targetType = target.getElement();
        if (targetType == null || targetType.getName() == null) {
            return false;
        }
        String expression = assertionValue(targetType.getName());
        Pattern assertion = Pattern.compile(
                "(?m)^\\s*var\\s+_\\s+"
                        + Pattern.quote(interfaceReference)
                        + "\\s*=\\s*"
                        + Pattern.quote(expression)
                        + "\\s*$"
        );
        return assertion.matcher(source).find();
    }

    private String assertionValue(String typeName) {
        if (pointerOutput) {
            return "(*" + typeName + ")(nil)";
        }
        return typeName + "{}";
    }

    private static int annotationGroupStart(
            Document document,
            int annotationOffset
    ) {
        int line = document.getLineNumber(annotationOffset);
        int result = document.getLineStartOffset(line);
        String source = document.getText();
        for (int previous = line - 1; previous >= 0; previous--) {
            int start = document.getLineStartOffset(previous);
            int end = document.getLineEndOffset(previous);
            String text = source.substring(start, end).stripLeading();
            if (!text.startsWith("// @") && !text.startsWith("//@")) {
                break;
            }
            result = start;
        }
        return result;
    }
}
