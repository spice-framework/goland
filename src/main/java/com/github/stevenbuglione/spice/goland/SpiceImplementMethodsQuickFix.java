package com.github.stevenbuglione.spice.goland;

import com.goide.psi.GoFile;
import com.goide.psi.GoInterfaceType;
import com.goide.psi.GoMethodSpec;
import com.goide.psi.GoSpecType;
import com.goide.psi.GoTopLevelDeclaration;
import com.goide.psi.GoType;
import com.goide.psi.GoTypeParamDefinition;
import com.goide.psi.GoTypeParameterDeclaration;
import com.goide.psi.GoTypeParameters;
import com.goide.psi.GoTypeReferenceExpression;
import com.goide.psi.GoTypeSpec;
import com.goide.psi.impl.GoElementFactory;
import com.goide.quickfix.GoImplementMissingMethodsQuickfix;
import com.goide.refactor.GoImplementMethodsHandler;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingListener;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

final class SpiceImplementMethodsQuickFix
        extends GoImplementMissingMethodsQuickfix {
    private final SmartPsiElementPointer<GoTypeSpec> interfaceType;
    private final SmartPsiElementPointer<GoTypeSpec> targetType;
    private final String interfaceReference;
    private final boolean pointerReceiver;

    SpiceImplementMethodsQuickFix(
            GoTypeSpec interfaceType,
            GoTypeSpec targetType,
            String interfaceReference,
            boolean pointerReceiver
    ) {
        super(interfaceType.getSpecType(), targetType);
        SmartPointerManager pointers = SmartPointerManager.getInstance(
                interfaceType.getProject()
        );
        this.interfaceType = pointers.createSmartPsiElementPointer(
                interfaceType
        );
        this.targetType = pointers.createSmartPsiElementPointer(targetType);
        this.interfaceReference = interfaceReference;
        this.pointerReceiver = pointerReceiver;
    }

    @Override
    public String getText() {
        return "Implement missing methods for " + interfaceReference;
    }

    @Override
    public void invoke(
            Project project,
            PsiFile file,
            Editor editor,
            PsiElement startElement,
            PsiElement endElement
    ) {
        if (!(file instanceof GoFile goFile)
                || editor == null) {
            return;
        }
        GoTypeSpec target = targetType.getElement();
        GoTypeSpec originalContract = interfaceType.getElement();
        GoTypeSpec contract = generatedContract(originalContract);
        contract = contract == null ? originalContract : contract;
        GoTopLevelDeclaration declaration = PsiTreeUtil.getParentOfType(
                target,
                GoTopLevelDeclaration.class,
                false
        );
        if (contract == null || declaration == null) {
            return;
        }
        Document document = editor.getDocument();
        String before = document.getText();
        GoImplementMethodsHandler.generateTemplate(
                goFile,
                editor,
                target,
                contract,
                declaration
        );
        if (pointerReceiver && target.getName() != null) {
            makeGeneratedReceiversPointersWhenReady(
                    project,
                    editor,
                    before,
                    target.getName()
            );
        }
    }

    private static void makeGeneratedReceiversPointersWhenReady(
            Project project,
            Editor editor,
            String before,
            String typeName
    ) {
        TemplateState state = TemplateManagerImpl.getTemplateState(editor);
        if (state == null || state.isFinished()) {
            makeGeneratedReceiversPointers(
                    editor.getDocument(),
                    before,
                    typeName
            );
            return;
        }
        selectPointerReceiver(project, state, editor.getDocument(), typeName);
        state.addTemplateStateListener(new PointerReceiverListener(
                project,
                editor.getDocument(),
                before,
                typeName
        ));
    }

    private static void selectPointerReceiver(
            Project project,
            TemplateState state,
            Document document,
            String typeName
    ) {
        TextRange range = state.getVariableRange("RECEIVER");
        if (range == null
                || range.getEndOffset() > document.getTextLength()) {
            return;
        }
        String current = document.getText(range);
        if (current.equals("*" + typeName)) {
            return;
        }
        if (!current.equals(typeName)) {
            return;
        }
        WriteCommandAction.runWriteCommandAction(
                project,
                () -> document.replaceString(
                        range.getStartOffset(),
                        range.getEndOffset(),
                        "*" + typeName
                )
        );
    }

    private static void makeGeneratedReceiversPointers(
            Document document,
            String before,
            String typeName
    ) {
        String after = document.getText();
        int prefix = 0;
        int shared = Math.min(before.length(), after.length());
        while (prefix < shared
                && before.charAt(prefix) == after.charAt(prefix)) {
            prefix++;
        }
        int beforeEnd = before.length();
        int afterEnd = after.length();
        while (beforeEnd > prefix
                && afterEnd > prefix
                && before.charAt(beforeEnd - 1)
                == after.charAt(afterEnd - 1)) {
            beforeEnd--;
            afterEnd--;
        }
        String changed = after.substring(prefix, afterEnd);
        String pointer = changed.replaceAll(
                "(func\\s+\\([^\\s()]+\\s+)"
                        + java.util.regex.Pattern.quote(typeName)
                        + "(\\))",
                "$1*" + java.util.regex.Matcher.quoteReplacement(typeName)
                        + "$2"
        );
        if (!pointer.equals(changed)) {
            document.replaceString(prefix, afterEnd, pointer);
        }
    }

    private GoTypeSpec generatedContract(GoTypeSpec contract) {
        if (contract == null
                || !(contract.getContainingFile() instanceof GoFile source)) {
            return null;
        }
        List<String> arguments = typeArguments(interfaceReference);
        if (arguments.isEmpty()) {
            return null;
        }
        GoSpecType specType = contract.getSpecType();
        GoType body = specType == null ? null : specType.getType();
        GoTypeParameters parameters = specType == null
                ? null
                : specType.getTypeParameters();
        if (body == null || parameters == null) {
            return null;
        }
        List<GoTypeParamDefinition> definitions = parameters
                .getTypeParameterDeclarationList()
                .stream()
                .map(GoTypeParameterDeclaration::getTypeParamDefinitionList)
                .flatMap(List::stream)
                .toList();
        if (definitions.size() != arguments.size()) {
            return null;
        }
        Map<String, String> substitutions = new LinkedHashMap<>();
        for (int index = 0; index < definitions.size(); index++) {
            substitutions.put(
                    definitions.get(index).getName(),
                    arguments.get(index)
            );
        }
        String substituted = expandedBody(
                contract,
                arguments,
                new LinkedHashMap<>(),
                new HashSet<>()
        );
        if (substituted == null) {
            substituted = substitutedText(body, substitutions);
        }
        String packageName = source.getPackageName();
        if (packageName == null || packageName.isBlank()) {
            return null;
        }
        String imports = source.getImportList() == null
                ? ""
                : source.getImportList().getText() + "\n";
        GoFile generated = GoElementFactory.createFileFromText(
                source.getProject(),
                "spice_generated_contract.go",
                "package " + packageName + "\n\n"
                        + imports
                + "type spiceGeneratedContract " + substituted + "\n",
                false,
                source,
                null
        );
        return generated.getTypes("spiceGeneratedContract").stream()
                .findFirst()
                .orElse(null);
    }

    private static String expandedBody(
            GoTypeSpec contract,
            List<String> arguments,
            Map<String, String> methods,
            Set<String> visiting
    ) {
        GoSpecType specType = contract.getSpecType();
        GoType body = specType == null ? null : specType.getType();
        if (!(body instanceof GoInterfaceType interfaceType)) {
            return null;
        }
        List<GoTypeParamDefinition> definitions = typeParameters(specType);
        if (definitions.size() != arguments.size()) {
            return null;
        }
        Map<String, String> substitutions = new LinkedHashMap<>();
        for (int index = 0; index < definitions.size(); index++) {
            substitutions.put(
                    definitions.get(index).getName(),
                    arguments.get(index)
            );
        }
        String identity = contract.getContainingFile()
                .getVirtualFile()
                .getPath()
                + ":"
                + contract.getName()
                + arguments;
        if (!visiting.add(identity)) {
            return null;
        }
        try {
            for (GoMethodSpec method : interfaceType.getMethods()) {
                String name = method.getName();
                if (name != null) {
                    methods.putIfAbsent(
                            name,
                            substitutedText(method, substitutions)
                    );
                }
            }
            for (GoTypeReferenceExpression reference
                    : interfaceType.getBaseTypesReferences()) {
                PsiElement resolved = reference.getReference().resolve();
                if (!(resolved instanceof GoTypeSpec base)) {
                    continue;
                }
                PsiElement expression = enclosingType(
                        reference,
                        interfaceType
                );
                List<String> baseArguments = typeArguments(
                        substitutedTypeExpression(
                                expression,
                                substitutions
                        )
                );
                if (expandedBody(
                        base,
                        baseArguments,
                        methods,
                        visiting
                ) == null) {
                    return null;
                }
            }
        } finally {
            visiting.remove(identity);
        }
        StringBuilder result = new StringBuilder("interface {\n");
        for (String method : methods.values()) {
            result.append('\t').append(method).append('\n');
        }
        return result.append('}').toString();
    }

    private static List<GoTypeParamDefinition> typeParameters(
            GoSpecType specType
    ) {
        GoTypeParameters parameters = specType.getTypeParameters();
        if (parameters == null) {
            return List.of();
        }
        return parameters.getTypeParameterDeclarationList()
                .stream()
                .map(GoTypeParameterDeclaration::getTypeParamDefinitionList)
                .flatMap(List::stream)
                .toList();
    }

    private static PsiElement enclosingType(
            GoTypeReferenceExpression reference,
            GoInterfaceType owner
    ) {
        PsiElement result = reference;
        PsiElement current = reference.getParent();
        while (current != null && current != owner) {
            result = current;
            current = current.getParent();
        }
        return result;
    }

    private static String substitutedText(
            PsiElement element,
            Map<String, String> substitutions
    ) {
        int elementStart = element.getTextRange().getStartOffset();
        List<Replacement> replacements = new ArrayList<>();
        for (GoTypeReferenceExpression reference
                : PsiTreeUtil.findChildrenOfType(
                        element,
                        GoTypeReferenceExpression.class
                )) {
            PsiElement resolved = reference.getReference().resolve();
            if (!(resolved instanceof GoTypeParamDefinition parameter)) {
                continue;
            }
            String replacement = substitutions.get(parameter.getName());
            if (replacement != null) {
                replacements.add(new Replacement(
                        reference.getTextRange().getStartOffset() - elementStart,
                        reference.getTextRange().getEndOffset() - elementStart,
                        replacement
                ));
            }
        }
        replacements.sort(Comparator.comparingInt(Replacement::start).reversed());
        StringBuilder result = new StringBuilder(element.getText());
        for (Replacement replacement : replacements) {
            result.replace(
                    replacement.start(),
                    replacement.end(),
                    replacement.text()
            );
        }
        return result.toString();
    }

    private static String substitutedTypeExpression(
            PsiElement element,
            Map<String, String> substitutions
    ) {
        String value = substitutedText(element, substitutions);
        StringBuilder result = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length();) {
            char current = value.charAt(offset);
            if (!Character.isJavaIdentifierStart(current)) {
                result.append(current);
                offset++;
                continue;
            }
            int end = offset + 1;
            while (end < value.length()
                    && Character.isJavaIdentifierPart(value.charAt(end))) {
                end++;
            }
            String identifier = value.substring(offset, end);
            String replacement = offset > 0 && value.charAt(offset - 1) == '.'
                    ? null
                    : substitutions.get(identifier);
            result.append(replacement == null ? identifier : replacement);
            offset = end;
        }
        return result.toString();
    }

    private static List<String> typeArguments(String reference) {
        int open = reference.indexOf('[');
        if (open < 0 || !reference.endsWith("]")) {
            return List.of();
        }
        String body = reference.substring(open + 1, reference.length() - 1);
        List<String> result = new ArrayList<>();
        int start = 0;
        int depth = 0;
        for (int offset = 0; offset < body.length(); offset++) {
            char value = body.charAt(offset);
            if (value == '[' || value == '(' || value == '{') {
                depth++;
            } else if (value == ']' || value == ')' || value == '}') {
                depth--;
            } else if (value == ',' && depth == 0) {
                result.add(body.substring(start, offset).strip());
                start = offset + 1;
            }
        }
        result.add(body.substring(start).strip());
        if (result.stream().anyMatch(String::isBlank)) {
            return List.of();
        }
        return List.copyOf(result);
    }

    private static final class PointerReceiverListener
            implements TemplateEditingListener {
        private final Project project;
        private final Document document;
        private final String before;
        private final String typeName;
        private final AtomicBoolean applied = new AtomicBoolean();

        private PointerReceiverListener(
                Project project,
                Document document,
                String before,
                String typeName
        ) {
            this.project = project;
            this.document = document;
            this.before = before;
            this.typeName = typeName;
        }

        @Override
        public void beforeTemplateFinished(
                TemplateState state,
                Template template
        ) {}

        @Override
        public void templateFinished(Template template, boolean brokenOff) {
            apply();
        }

        @Override
        public void templateCancelled(Template template) {
            apply();
        }

        @Override
        public void currentVariableChanged(
                TemplateState state,
                Template template,
                int oldIndex,
                int newIndex
        ) {}

        @Override
        public void waitingForInput(Template template) {}

        private void apply() {
            if (!applied.compareAndSet(false, true)) {
                return;
            }
            Runnable operation = () -> makeGeneratedReceiversPointers(
                    document,
                    before,
                    typeName
            );
            if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
                operation.run();
                return;
            }
            WriteCommandAction.runWriteCommandAction(project, operation);
        }
    }

    private record Replacement(int start, int end, String text) {}
}
