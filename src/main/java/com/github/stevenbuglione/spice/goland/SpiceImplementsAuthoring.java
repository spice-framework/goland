package com.github.stevenbuglione.spice.goland;

import com.goide.psi.GoTypeSpec;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiComment;

final class SpiceImplementsAuthoring {
    private SpiceImplementsAuthoring() {}

    static void addNativeMethodFixes(
            PsiComment comment,
            AnnotationHolder holder
    ) {
        GoTypeSpec target = SpiceGoTypes.targetType(comment);
        if (target == null) {
            return;
        }
        boolean pointerReceiver = SpiceGoTypes.targetUsesPointer(comment);
        int commentStart = comment.getTextRange().getStartOffset();
        for (SpiceGoTypes.ResolvedInterface contract
                : SpiceGoTypes.resolveInterfaces(comment)) {
            var range = contract.argument()
                    .referenceRange()
                    .shiftRight(commentStart);
            SpiceImplementMethodsQuickFix fix =
                    new SpiceImplementMethodsQuickFix(
                            contract.typeSpec(),
                            target,
                            contract.argument().expression(),
                            pointerReceiver
                    );
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(range)
                    .withFix(fix)
                    .create();
        }
    }
}
