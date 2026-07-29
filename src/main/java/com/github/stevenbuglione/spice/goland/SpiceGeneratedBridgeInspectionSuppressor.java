package com.github.stevenbuglione.spice.goland;

import com.goide.psi.GoFunctionDeclaration;
import com.goide.psi.GoReferenceExpression;
import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Suppresses GoLand's file-fragment unresolved-reference result for the one
 * generated command bridge used by an explicitly imported {@code @Application}
 * marker.
 */
public final class SpiceGeneratedBridgeInspectionSuppressor
        implements InspectionSuppressor {
    private static final String SHORT_NAME = "GoUnresolvedReference";
    private static final String CLASS_NAME = "GoUnresolvedReferenceInspection";
    private static final SuppressQuickFix[] NO_ACTIONS = new SuppressQuickFix[0];

    @Override
    public boolean isSuppressedFor(
            @NotNull PsiElement element,
            @NotNull String toolId
    ) {
        if (!SHORT_NAME.equals(toolId) && !CLASS_NAME.equals(toolId)) {
            return false;
        }
        return isGeneratedBridgeReference(element);
    }

    static boolean isGeneratedBridgeReference(PsiElement element) {
        GoReferenceExpression reference = PsiTreeUtil.getParentOfType(
                element,
                GoReferenceExpression.class,
                false
        );
        if (reference == null
                || reference.getQualifier() != null
                || !"spiceMain".equals(reference.getText())) {
            return false;
        }
        GoFunctionDeclaration function = PsiTreeUtil.getParentOfType(
                reference,
                GoFunctionDeclaration.class,
                true
        );
        return function != null
                && "main".equals(function.getName())
                && hasApplicationMarker(function);
    }

    @Override
    public SuppressQuickFix @NotNull [] getSuppressActions(
            @Nullable PsiElement element,
            @NotNull String toolId
    ) {
        return NO_ACTIONS;
    }

    private static boolean hasApplicationMarker(
            GoFunctionDeclaration function
    ) {
        PsiFile file = function.getContainingFile();
        if (file == null) {
            return false;
        }
        String source = file.getText();
        int lineStart = source.lastIndexOf(
                '\n',
                Math.max(0, function.getTextRange().getStartOffset() - 1)
        ) + 1;
        int cursor = lineStart;
        while (cursor > 0) {
            int lineEnd = cursor - 1;
            if (lineEnd > 0 && source.charAt(lineEnd - 1) == '\r') {
                lineEnd--;
            }
            int previousBreak = source.lastIndexOf('\n', lineEnd - 1);
            int previousStart = previousBreak + 1;
            String line = source.substring(previousStart, lineEnd).strip();
            if (!line.startsWith("// @")) {
                break;
            }
            Optional<SpiceAnnotationSyntax.Match> parsed =
                    SpiceAnnotationSyntax.parse(line);
            if (parsed.isPresent()) {
                SpiceAnnotationIndex.DescriptorSymbol descriptor =
                        SpiceAnnotationIndex.resolveImport(
                                source,
                                parsed.orElseThrow().name()
                        );
                if (descriptor != null
                        && descriptor.packagePath().equals(
                        "github.com/StevenBuglione/spice/annotation/core"
                )
                        && descriptor.symbol().equals("Application")) {
                    return true;
                }
            }
            cursor = previousStart;
        }
        return false;
    }
}
