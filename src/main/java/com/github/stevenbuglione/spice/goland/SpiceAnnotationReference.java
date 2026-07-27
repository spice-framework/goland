package com.github.stevenbuglione.spice.goland;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SpiceAnnotationReference extends PsiReferenceBase<PsiComment> {
    private final String localName;
    private final SpiceAnnotationIndex.DescriptorSymbol descriptor;
    private final String packagePath;

    SpiceAnnotationReference(PsiComment element, TextRange range, String name) {
        super(element, range, true);
        this.localName = name;
        this.descriptor = null;
        this.packagePath = null;
    }

    private SpiceAnnotationReference(
            PsiComment element,
            TextRange range,
            SpiceAnnotationIndex.DescriptorSymbol descriptor,
            String packagePath
    ) {
        super(element, range, true);
        this.localName = null;
        this.descriptor = descriptor;
        this.packagePath = packagePath;
    }

    static SpiceAnnotationReference descriptor(
            PsiComment element,
            TextRange range,
            SpiceAnnotationIndex.DescriptorSymbol descriptor
    ) {
        return new SpiceAnnotationReference(element, range, descriptor, null);
    }

    static SpiceAnnotationReference annotationPackage(
            PsiComment element,
            TextRange range,
            String packagePath
    ) {
        return new SpiceAnnotationReference(element, range, null, packagePath);
    }

    @Override
    public @Nullable PsiElement resolve() {
        SpiceAnnotationIndex index =
                SpiceAnnotationIndex.getInstance(myElement.getProject());
        if (descriptor != null) {
            return index.resolveDescriptor(myElement, descriptor);
        }
        if (packagePath != null) {
            return index.resolvePackage(myElement, packagePath);
        }
        return index.resolve(myElement, localName);
    }

    @Override
    public Object @NotNull [] getVariants() {
        return EMPTY_ARRAY;
    }
}
