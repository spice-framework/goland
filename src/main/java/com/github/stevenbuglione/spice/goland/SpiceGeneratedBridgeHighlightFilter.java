package com.github.stevenbuglione.spice.goland;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Removes the Go fragment analyzer's false error for the generated
 * {@code spiceMain} bridge. The shared compiler still reports every invalid
 * marker or unrelated unresolved reference.
 */
public final class SpiceGeneratedBridgeHighlightFilter
        implements HighlightInfoFilter {
    private static final String UNDEFINED_BRIDGE = "undefined: spiceMain";

    @Override
    public boolean accept(
            @NotNull HighlightInfo info,
            @Nullable PsiFile file
    ) {
        if (file == null
                || !HighlightSeverity.ERROR.equals(info.getSeverity())
                || !UNDEFINED_BRIDGE.equals(info.getDescription())) {
            return true;
        }
        String text = file.getText();
        int offset = text.indexOf("spiceMain");
        while (offset >= 0) {
            PsiElement element = file.findElementAt(offset);
            if (element != null
                    && SpiceGeneratedBridgeInspectionSuppressor
                    .isGeneratedBridgeReference(element)) {
                return false;
            }
            offset = text.indexOf("spiceMain", offset + 1);
        }
        return true;
    }
}
