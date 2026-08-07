package com.github.stevenbuglione.spice.goland;

import com.goide.psi.GoConstDefinition;
import com.goide.psi.GoFile;
import com.goide.psi.GoFunctionDeclaration;
import com.goide.psi.GoInterfaceType;
import com.goide.psi.GoMethodDeclaration;
import com.goide.psi.GoNamedElement;
import com.goide.psi.GoStructType;
import com.goide.psi.GoType;
import com.goide.psi.GoTypeSpec;
import com.goide.psi.GoVarDefinition;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adds a class-oriented projection over Go PSI. The projection never changes
 * source: it only groups constructors, static factories, and receiver methods
 * beneath their named type for presentation and navigation.
 */
public final class SpiceStructureViewFactory
        implements PsiStructureViewFactory {
    @Override
    public @Nullable StructureViewBuilder getStructureViewBuilder(
            @NotNull PsiFile psiFile
    ) {
        if (!(psiFile instanceof GoFile goFile)) {
            return null;
        }
        return new TreeBasedStructureViewBuilder() {
            @Override
            public @NotNull StructureViewModel createStructureViewModel(
                    @Nullable Editor editor
            ) {
                return new StructureViewModelBase(
                        goFile,
                        editor,
                        new RootElement(goFile)
                ).withSuitableClasses(
                        GoTypeSpec.class,
                        GoFunctionDeclaration.class,
                        GoMethodDeclaration.class
                );
            }
        };
    }

    private static final class RootElement extends Element {
        private final GoFile file;

        private RootElement(GoFile file) {
            super(file, "");
            this.file = file;
        }

        @Override
        protected List<StructureViewTreeElement> children() {
            Map<String, GoTypeSpec> associatedTypes = associatedTypes(file);
            List<StructureViewTreeElement> result = new ArrayList<>();
            for (GoTypeSpec type : file.getTypes()) {
                result.add(new TypeElement(type, file));
            }
            for (GoFunctionDeclaration function : file.getFunctions()) {
                if (!associatedTypes.containsKey(function.getName())) {
                    result.add(new Element(function, ""));
                }
            }
            for (GoMethodDeclaration method : file.getMethods()) {
                if (!sameFile(method.resolveTypeSpec(), file)) {
                    result.add(new Element(method, ""));
                }
            }
            for (GoConstDefinition constant : file.getConstants()) {
                result.add(new Element(constant, ""));
            }
            for (GoVarDefinition variable : file.getVars()) {
                result.add(new Element(variable, ""));
            }
            result.sort(Comparator.comparingInt(value ->
                    ((PsiElement) value.getValue())
                            .getTextRange()
                            .getStartOffset()));
            return List.copyOf(result);
        }
    }

    static final class TypeElement extends Element {
        private final GoTypeSpec type;
        private final GoFile contextFile;

        private TypeElement(GoTypeSpec type, GoFile contextFile) {
            super(type, "");
            this.type = type;
            this.contextFile = contextFile;
        }

        @Override
        protected List<StructureViewTreeElement> children() {
            List<StructureViewTreeElement> result = new ArrayList<>();
            for (GoFunctionDeclaration function
                    : associatedFunctions(type, contextFile)) {
                String prefix = isConstructor(type, function)
                        ? "constructor "
                        : "static ";
                result.add(new Element(function, prefix));
            }
            GoType declared = type.getSpecType().getType();
            if (declared instanceof GoStructType structType) {
                for (GoNamedElement field
                        : structType.getFieldDefinitions()) {
                    result.add(new Element(field, ""));
                }
            } else if (declared instanceof GoInterfaceType interfaceType) {
                interfaceType.getAllMethods(type).stream()
                        .map(value -> new Element(value, ""))
                        .forEach(result::add);
            }
            type.getMethods().stream()
                    .sorted(Comparator
                            .comparing((GoMethodDeclaration value) ->
                                    value.getContainingFile() != contextFile)
                            .thenComparingInt(value -> value
                                    .getTextRange()
                                    .getStartOffset()))
                    .map(value -> new Element(value, ""))
                    .forEach(result::add);
            return List.copyOf(result);
        }
    }

    private static class Element implements StructureViewTreeElement {
        private final PsiElement element;
        private final String prefix;

        private Element(PsiElement element, String prefix) {
            this.element = element;
            this.prefix = prefix;
        }

        @Override
        public Object getValue() {
            return element;
        }

        @Override
        public @NotNull ItemPresentation getPresentation() {
            ItemPresentation nativePresentation =
                    element instanceof NavigationItem item
                            ? item.getPresentation()
                            : null;
            return new ItemPresentation() {
                @Override
                public @Nullable String getPresentableText() {
                    String text;
                    if (nativePresentation != null) {
                        text = nativePresentation.getPresentableText();
                    } else if (element instanceof PsiNamedElement named) {
                        text = named.getName();
                    } else if (element instanceof PsiFile file) {
                        text = file.getName();
                    } else {
                        text = element.getText();
                    }
                    return prefix + (text == null ? "" : text);
                }

                @Override
                public @Nullable String getLocationString() {
                    return nativePresentation == null
                            ? null
                            : nativePresentation.getLocationString();
                }

                @Override
                public @Nullable Icon getIcon(boolean unused) {
                    return nativePresentation == null
                            ? element.getIcon(0)
                            : nativePresentation.getIcon(unused);
                }
            };
        }

        @Override
        public StructureViewTreeElement @NotNull [] getChildren() {
            return children().toArray(StructureViewTreeElement[]::new);
        }

        protected List<StructureViewTreeElement> children() {
            return List.of();
        }

        @Override
        public void navigate(boolean requestFocus) {
            if (element instanceof Navigatable navigatable) {
                navigatable.navigate(requestFocus);
            }
        }

        @Override
        public boolean canNavigate() {
            return element instanceof Navigatable navigatable
                    && navigatable.canNavigate();
        }

        @Override
        public boolean canNavigateToSource() {
            return element instanceof Navigatable navigatable
                    && navigatable.canNavigateToSource();
        }
    }

    private static Map<String, GoTypeSpec> associatedTypes(GoFile file) {
        Map<String, GoTypeSpec> result = new LinkedHashMap<>();
        for (GoTypeSpec type : file.getTypes()) {
            String name = type.getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            result.put("New" + name, type);
            result.put("Parse" + name, type);
            result.put("Must" + name, type);
            for (GoFunctionDeclaration function : file.getFunctions()) {
                String functionName = function.getName();
                if (functionName != null
                        && functionName.startsWith(name + "From")) {
                    result.put(functionName, type);
                }
            }
        }
        return result;
    }

    private static List<GoFunctionDeclaration> associatedFunctions(
            GoTypeSpec type,
            GoFile file
    ) {
        String typeName = type.getName();
        if (typeName == null) {
            return List.of();
        }
        List<GoFunctionDeclaration> result = new ArrayList<>();
        for (GoFunctionDeclaration function : file.getFunctions()) {
            String functionName = function.getName();
            if (functionName != null
                    && (functionName.equals("New" + typeName)
                    || functionName.equals("Parse" + typeName)
                    || functionName.equals("Must" + typeName)
                    || functionName.startsWith(typeName + "From"))) {
                result.add(function);
            }
        }
        result.sort(Comparator
                .comparing((GoFunctionDeclaration value) ->
                        !isConstructor(type, value))
                .thenComparingInt(value -> value
                        .getTextRange()
                        .getStartOffset()));
        return result;
    }

    private static boolean isConstructor(
            GoTypeSpec type,
            GoFunctionDeclaration function
    ) {
        return ("New" + type.getName()).equals(function.getName());
    }

    private static boolean sameFile(
            @Nullable GoTypeSpec type,
            GoFile file
    ) {
        return type != null && type.getContainingFile() == file;
    }
}
