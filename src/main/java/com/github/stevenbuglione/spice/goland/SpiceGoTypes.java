package com.github.stevenbuglione.spice.goland;

import com.goide.psi.GoFile;
import com.goide.psi.GoFunctionDeclaration;
import com.goide.psi.GoImportSpec;
import com.goide.psi.GoInterfaceType;
import com.goide.psi.GoParameterDeclaration;
import com.goide.psi.GoParameters;
import com.goide.psi.GoResult;
import com.goide.psi.GoSpecType;
import com.goide.psi.GoType;
import com.goide.psi.GoTypeSpec;
import com.goide.psi.impl.GoTypeUtil;
import com.goide.stubs.index.GoTypesIndex;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveState;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

final class SpiceGoTypes {
    private SpiceGoTypes() {}

    static boolean isImplements(PsiComment comment) {
        PsiFile file = comment.getContainingFile();
        if (!(file instanceof GoFile)) {
            return false;
        }
        var parsed = SpiceAnnotationSyntax.parse(comment.getText());
        if (parsed.isEmpty()) {
            return false;
        }
        SpiceAnnotationIndex.DescriptorSymbol descriptor =
                SpiceAnnotationIndex.resolveImport(
                        file.getText(),
                        parsed.orElseThrow().name()
                );
        return descriptor != null
                && descriptor.symbol().equals("Implements");
    }

    static List<ResolvedInterface> resolveInterfaces(PsiComment comment) {
        if (!isImplements(comment)
                || DumbService.isDumb(comment.getProject())) {
            return List.of();
        }
        List<ResolvedInterface> result = new ArrayList<>();
        for (SpiceAnnotationSyntax.TypeArgument argument
                : SpiceAnnotationSyntax.typeArguments(comment.getText())) {
            GoTypeSpec typeSpec = resolveTypeSpec(
                    comment,
                    argument.referenceRange()
            );
            if (typeSpec == null || !isRuntimeInterface(typeSpec, comment)) {
                continue;
            }
            result.add(new ResolvedInterface(argument, typeSpec));
        }
        return List.copyOf(result);
    }

    static @Nullable GoTypeSpec resolveTypeSpec(
            PsiComment comment,
            TextRange referenceRange
    ) {
        String reference = referenceRange.substring(comment.getText());
        if (!(comment.getContainingFile() instanceof GoFile source)) {
            return null;
        }
        return resolveReference(source, reference);
    }

    private static @Nullable GoTypeSpec resolveReference(
            GoFile source,
            String reference
    ) {
        int separator = reference.indexOf('.');
        String qualifier = separator < 0
                ? ""
                : reference.substring(0, separator);
        String name = separator < 0
                ? reference
                : reference.substring(separator + 1);
        if (name.isBlank()) {
            return null;
        }
        String packagePath = separator < 0
                ? importPath(source)
                : importedPath(source, qualifier);
        if (packagePath == null) {
            return null;
        }
        Collection<GoTypeSpec> matches = GoTypesIndex.find(
                name,
                source.getProject(),
                GlobalSearchScope.allScope(source.getProject()),
                null
        );
        for (GoTypeSpec candidate : matches) {
            if (candidate.getContainingFile() instanceof GoFile candidateFile
                    && packagePath.equals(importPath(candidateFile))) {
                return candidate;
            }
        }
        for (GoTypeSpec candidate : source.getTypes(name)) {
            if (separator < 0) {
                return candidate;
            }
        }
        return null;
    }

    static @Nullable GoTypeSpec targetType(PsiComment comment) {
        if (!(comment.getContainingFile() instanceof GoFile source)) {
            return null;
        }
        PsiElement nearest = targetDeclaration(source, comment);
        if (nearest instanceof GoTypeSpec type) {
            return type;
        }
        return nearest instanceof GoFunctionDeclaration function
                ? resultTypeSpec(function, comment)
                : null;
    }

    static boolean targetUsesPointer(PsiComment comment) {
        if (!(comment.getContainingFile() instanceof GoFile source)) {
            return false;
        }
        PsiElement declaration = targetDeclaration(source, comment);
        if (declaration instanceof GoFunctionDeclaration function) {
            GoType result = resultType(function);
            return result != null && result.getText().strip().startsWith("*");
        }
        if (!(declaration instanceof GoTypeSpec type)) {
            return false;
        }
        String constructorName = "New" + type.getName();
        for (GoFunctionDeclaration function : source.getFunctions()) {
            if (!constructorName.equals(function.getName())) {
                continue;
            }
            GoType result = resultType(function);
            if (result != null
                    && type.equals(result.resolve(comment))) {
                return result.getText().strip().startsWith("*");
            }
        }
        return true;
    }

    private static @Nullable PsiElement targetDeclaration(
            GoFile source,
            PsiComment comment
    ) {
        int afterComment = comment.getTextRange().getEndOffset();
        PsiElement nearest = null;
        for (GoTypeSpec type : source.getTypes()) {
            if (type.getTextRange().getStartOffset() >= afterComment
                    && (nearest == null
                    || type.getTextRange().getStartOffset()
                    < nearest.getTextRange().getStartOffset())) {
                nearest = type;
            }
        }
        for (GoFunctionDeclaration function : source.getFunctions()) {
            if (function.getTextRange().getStartOffset() >= afterComment
                    && (nearest == null
                    || function.getTextRange().getStartOffset()
                    < nearest.getTextRange().getStartOffset())) {
                nearest = function;
            }
        }
        return nearest;
    }

    private static @Nullable GoTypeSpec resultTypeSpec(
            GoFunctionDeclaration function,
            PsiElement context
    ) {
        GoType output = resultType(function);
        if (output == null) {
            return null;
        }
        PsiElement resolved = output.resolve(context);
        if (resolved instanceof GoTypeSpec typeSpec) {
            return typeSpec;
        }
        String expression = output.getText().strip();
        while (expression.startsWith("*")) {
            expression = expression.substring(1).stripLeading();
        }
        int arguments = expression.indexOf('[');
        if (arguments >= 0) {
            expression = expression.substring(0, arguments);
        }
        return function.getContainingFile() instanceof GoFile source
                ? resolveReference(source, expression)
                : null;
    }

    private static @Nullable GoType resultType(
            GoFunctionDeclaration function
    ) {
        GoResult result = function.getSignature().getResult();
        if (result == null || result.isVoid()) {
            return null;
        }
        GoType output = result.getType();
        if (output != null) {
            return output;
        }
        GoParameters parameters = result.getParameters();
        if (parameters == null || parameters.getParameterCount() == 0) {
            return null;
        }
        GoParameterDeclaration declaration =
                parameters.getDeclarationByIndex(0);
        return declaration == null ? null : declaration.getType();
    }

    private static boolean isRuntimeInterface(
            GoTypeSpec type,
            PsiElement context
    ) {
        if (!GoTypeUtil.isInterface(type)) {
            return false;
        }
        GoType inner = type.getGoTypeInner(ResolveState.initial());
        GoSpecType specType = type.getSpecType();
        GoType declared = specType == null ? null : specType.getType();
        GoType underlying = declared instanceof GoInterfaceType
                ? declared
                : inner == null
                ? null
                : inner.getUnderlyingType(context);
        return underlying instanceof GoInterfaceType interfaceType
                && interfaceType.getConstraintElemList().isEmpty();
    }

    private static Map<String, String> importedQualifiers(GoFile file) {
        Map<String, String> result = new LinkedHashMap<>();
        for (GoImportSpec imported : file.getImports()) {
            String path = imported.getPath();
            if (path == null || path.isBlank()
                    || imported.isDot()
                    || imported.isForSideEffects()) {
                continue;
            }
            String alias = imported.getAlias();
            if (alias == null || alias.isBlank()) {
                alias = imported.getName();
            }
            if ((alias == null || alias.isBlank()) && path != null) {
                int separator = path.lastIndexOf('/');
                alias = path.substring(separator + 1);
            }
            if (alias != null && !alias.isBlank()) {
                result.put(alias, path);
            }
        }
        for (PsiComment comment
                : PsiTreeUtil.findChildrenOfType(file, PsiComment.class)) {
            SpiceAnnotationSyntax.parseImportDirective(comment.getText())
                    .ifPresent(directive -> {
                        for (SpiceAnnotationSyntax.ImportBinding binding
                                : directive.bindings()) {
                            if (binding.namespace()) {
                                result.put(
                                        binding.localName(),
                                        directive.packagePath()
                                );
                            }
                        }
                    });
        }
        return result;
    }

    private static @Nullable String importedPath(
            GoFile source,
            String qualifier
    ) {
        return importedQualifiers(source).get(qualifier);
    }

    private static @Nullable String importPath(GoFile file) {
        return SpiceAnnotationIndex.getInstance(file.getProject())
                .importPath(file);
    }

    record ResolvedInterface(
            SpiceAnnotationSyntax.TypeArgument argument,
            GoTypeSpec typeSpec
    ) {}

}
