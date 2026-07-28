package com.github.stevenbuglione.spice.goland;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * Supplies an indexed, offline-safe fallback for explicitly imported
 * annotations.
 *
 * <p>The shared LSP remains authoritative for target filtering, arguments,
 * provenance, and import edits. This contributor deliberately offers only
 * symbols already present in valid-Go import comments, so completion remains
 * useful while the language-server process is restarting or unavailable.
 */
public final class SpiceCompletionContributor extends CompletionContributor {
    private static final int ANNOTATION_PREFIX_LENGTH = "// @".length();

    public SpiceCompletionContributor() {
        extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement(),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(
                            @NotNull CompletionParameters parameters,
                            @NotNull ProcessingContext context,
                            @NotNull CompletionResultSet result
                    ) {
                        addExplicitImportCompletions(parameters, result);
                    }
                }
        );
    }

    private static void addExplicitImportCompletions(
            CompletionParameters parameters,
            CompletionResultSet result
    ) {
        PsiFile file = parameters.getOriginalFile();
        int offset = Math.min(parameters.getOffset(), file.getTextLength());
        PsiComment comment = commentAt(file, offset);
        if (comment == null) {
            return;
        }
        String text = comment.getText();
        int relative = offset - comment.getTextRange().getStartOffset();
        if (SpiceGoTypes.isImplements(comment)
                && SpiceAnnotationSyntax.typeCompletion(
                        comment.getText(),
                        relative
                ).isPresent()) {
            // The shared Spice compiler/LSP owns the complete Go interface
            // catalog and import edits. This native contributor deliberately
            // supplies no semantic candidates from GoLand's partial index.
            return;
        }
        if (!text.startsWith("// @")
                || relative < ANNOTATION_PREFIX_LENGTH
                || relative > text.length()) {
            return;
        }
        String prefix = text.substring(ANNOTATION_PREFIX_LENGTH, relative);
        if (!isAnnotationPrefix(prefix)) {
            return;
        }

        CompletionResultSet filtered = result.withPrefixMatcher(prefix);
        for (SpiceAnnotationIndex.CompletionSymbol symbol
                : SpiceAnnotationIndex.getInstance(file.getProject())
                .explicitCompletionSymbols(file)) {
            filtered.addElement(
                    LookupElementBuilder.create(symbol.localName())
                            .withIcon(AllIcons.Nodes.Annotationtype)
                            .withTypeText(symbol.descriptorName(), true)
                            .withTailText(
                                    " from \"" + symbol.packagePath() + "\"",
                                    true
                            )
            );
        }
    }

    private static PsiComment commentAt(PsiFile file, int offset) {
        if (file.getTextLength() == 0) {
            return null;
        }
        int bounded = Math.min(
                Math.max(0, offset - 1),
                file.getTextLength() - 1
        );
        PsiElement element = file.findElementAt(bounded);
        if (element instanceof PsiComment comment) {
            return comment;
        }
        return PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
    }

    private static boolean isAnnotationPrefix(String value) {
        if (value.isEmpty()) {
            return true;
        }
        boolean segmentStart = true;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '.') {
                if (segmentStart) {
                    return false;
                }
                segmentStart = true;
                continue;
            }
            if (segmentStart
                    ? !isIdentifierStart(character)
                    : !isIdentifierCharacter(character)) {
                return false;
            }
            segmentStart = false;
        }
        return true;
    }

    private static boolean isIdentifierStart(char value) {
        return value == '_'
                || value >= 'a' && value <= 'z'
                || value >= 'A' && value <= 'Z';
    }

    private static boolean isIdentifierCharacter(char value) {
        return isIdentifierStart(value) || value >= '0' && value <= '9';
    }
}
