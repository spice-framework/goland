package com.github.stevenbuglione.spice.goland;

import com.goide.GoFileType;
import com.goide.codeInsight.imports.GoImportOptimizer;
import com.goide.psi.GoFieldDeclaration;
import com.goide.psi.GoFile;
import com.goide.psi.GoFunctionDeclaration;
import com.goide.psi.GoFunctionOrMethodDeclaration;
import com.goide.psi.GoImportSpec;
import com.goide.psi.GoInterfaceType;
import com.goide.psi.GoMethodDeclaration;
import com.goide.psi.GoNamedSignatureOwner;
import com.goide.psi.GoStructType;
import com.goide.psi.GoTopLevelDeclaration;
import com.goide.psi.GoType;
import com.goide.psi.GoTypeSpec;
import com.goide.psi.impl.GoPackage;
import com.goide.psi.impl.GoTypeUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Source-preserving class-oriented Go authoring actions for Spice types. */
final class SpiceClassAuthoring {
    private static final String CORE_ANNOTATIONS =
            "github.com/spice-framework/spice/annotation/core";
    private static final Set<String> MANAGED = Set.of(
            "Service",
            "Repository",
            "Controller",
            "Component",
            "Configuration"
    );

    private SpiceClassAuthoring() {}

    static @Nullable IntentionAction actionAt(
            ActionKind kind,
            @Nullable Editor editor,
            @Nullable PsiFile file
    ) {
        if (editor == null || file == null) {
            return null;
        }
        int offset = Math.min(
                editor.getCaretModel().getOffset(),
                Math.max(0, file.getTextLength() - 1)
        );
        PsiElement element = file.findElementAt(offset);
        if (element == null) {
            return null;
        }
        List<SourceAction> actions = new ArrayList<>();
        addFixes(element, (target, action) -> {
            if (action.kind == kind) {
                actions.add(action);
            }
        });
        return actions.isEmpty() ? null : actions.getFirst();
    }

    private static void addFixes(PsiElement element, FixRegistrar registrar) {
        element = actionableElement(element);
        if (element == null) {
            return;
        }
        if (!(element.getContainingFile() instanceof GoFile file)) {
            return;
        }
        if (element instanceof GoTypeSpec type) {
            addTypeFixes(type, file, registrar);
        } else if (element instanceof GoMethodDeclaration method) {
            GoTypeSpec owner = method.resolveTypeSpec();
            if (owner != null
                    && owner.getContainingFile() != method.getContainingFile()) {
                addFix(
                        method.getIdentifier(),
                        registrar,
                        new SourceAction(
                                ActionKind.MOVE_METHOD,
                                method,
                                "",
                                "Move method to owning type file"
                        )
                );
            }
        } else if (element instanceof GoFunctionDeclaration function) {
            addFunctionFixes(function, file, registrar);
        }
    }

    private static @Nullable PsiElement actionableElement(
            PsiElement element
    ) {
        if (element instanceof GoTypeSpec
                || element instanceof GoFunctionDeclaration
                || element instanceof GoMethodDeclaration) {
            return element;
        }
        GoTypeSpec type = PsiTreeUtil.getParentOfType(
                element,
                GoTypeSpec.class,
                false
        );
        if (type != null && contains(type.getIdentifier(), element)) {
            return type;
        }
        GoFunctionOrMethodDeclaration function = PsiTreeUtil.getParentOfType(
                element,
                GoFunctionOrMethodDeclaration.class,
                false
        );
        if (function != null
                && contains(function.getIdentifier(), element)) {
            return function;
        }
        return null;
    }

    private static boolean contains(
            @Nullable PsiElement parent,
            PsiElement child
    ) {
        return parent != null
                && (parent == child
                || PsiTreeUtil.isAncestor(parent, child, false));
    }

    private static void addTypeFixes(
            GoTypeSpec type,
            GoFile file,
            FixRegistrar registrar
    ) {
        boolean managed = hasAnyAnnotation(type, MANAGED);
        boolean contract = GoTypeUtil.isInterface(type);
        if (managed && !contract && !hasConstructor(type, file)) {
            addFix(
                    type.getIdentifier(),
                    registrar,
                    new SourceAction(
                            ActionKind.GENERATE_CONSTRUCTOR,
                            type,
                            "",
                            "Generate constructor"
                    )
            );
        }
        if (contract) {
            String implementation = "Default" + type.getName();
            addFix(
                    type.getIdentifier(),
                    registrar,
                    new SourceAction(
                            ActionKind.CREATE_IMPLEMENTATION,
                            type,
                            implementation,
                            "Create implementation " + implementation
                    )
            );
            return;
        }
        if (!managed || hasAnnotation(type, "Implements")) {
            return;
        }
        for (GoTypeSpec candidate : implementedInterfaces(type, file)) {
            addFix(
                    type.getIdentifier(),
                    registrar,
                    new SourceAction(
                            ActionKind.ADD_IMPLEMENTS,
                            type,
                            candidate.getName(),
                            "Add @Implements(" + candidate.getName() + ")"
                    )
            );
        }
        String contractName = contractName(type.getName());
        addFix(
                type.getIdentifier(),
                registrar,
                new SourceAction(
                        ActionKind.CREATE_INTERFACE,
                        type,
                        contractName,
                        "Create interface " + contractName
                )
        );
    }

    private static void addFunctionFixes(
            GoFunctionDeclaration function,
            GoFile file,
            FixRegistrar registrar
    ) {
        if (hasAnnotation(function, "Bean")) {
            addFix(
                    function.getIdentifier(),
                    registrar,
                    new SourceAction(
                            ActionKind.MOVE_BEAN,
                            function,
                            "",
                            "Move @Bean to @Configuration"
                    )
            );
            return;
        }
        String name = function.getName();
        if (name == null || name.equals("main") || name.equals("init")
                || associatedType(name, file) != null) {
            return;
        }
        List<GoTypeSpec> types = List.copyOf(file.getTypes());
        if (types.size() == 1) {
            addFix(
                    function.getIdentifier(),
                    registrar,
                    new SourceAction(
                            ActionKind.CONVERT_TO_METHOD,
                            function,
                            types.getFirst().getName(),
                            "Convert function to method"
                    )
            );
        }
        addFix(
                function.getIdentifier(),
                registrar,
                new SourceAction(
                        ActionKind.CONVERT_TO_COMPONENT,
                        function,
                        name + "Component",
                        "Convert function to @Component"
                )
        );
    }

    private static void addFix(
            @Nullable PsiElement target,
            FixRegistrar registrar,
            SourceAction action
    ) {
        if (target == null) {
            return;
        }
        registrar.register(target, action);
    }

    enum ActionKind {
        GENERATE_CONSTRUCTOR,
        MOVE_METHOD,
        CONVERT_TO_METHOD,
        CONVERT_TO_COMPONENT,
        ADD_IMPLEMENTS,
        CREATE_IMPLEMENTATION,
        CREATE_INTERFACE,
        MOVE_BEAN
    }

    private static final class SourceAction implements IntentionAction {
        private final ActionKind kind;
        private final SmartPsiElementPointer<PsiElement> target;
        private final String argument;
        private final String text;

        private SourceAction(
                ActionKind kind,
                PsiElement target,
                String argument,
                String text
        ) {
            this.kind = kind;
            this.target = SmartPointerManager.getInstance(target.getProject())
                    .createSmartPsiElementPointer(target);
            this.argument = argument;
            this.text = text;
        }

        @Override
        public @NotNull String getText() {
            return text;
        }

        @Override
        public @NotNull String getFamilyName() {
            return "Spice class-oriented authoring";
        }

        @Override
        public boolean isAvailable(
                @NotNull Project project,
                @Nullable Editor editor,
                @Nullable PsiFile file
        ) {
            PsiElement value = target.getElement();
            return value != null && value.isValid();
        }

        @Override
        public void invoke(
                @NotNull Project project,
                @Nullable Editor editor,
                @Nullable PsiFile file
        ) {
            perform(project);
        }

        private void perform(Project project) {
            PsiElement value = target.getElement();
            if (value != null && value.isValid()
                    && FileModificationService.getInstance()
                    .preparePsiElementForWrite(value)) {
                WriteCommandAction.runWriteCommandAction(project,
                        () -> invokeWrite(project, value));
            }
        }

        private void invokeWrite(Project project, PsiElement value) {
            switch (kind) {
                case GENERATE_CONSTRUCTOR -> generateConstructor(
                        project,
                        (GoTypeSpec) value
                );
                case MOVE_METHOD -> moveMethod(
                        project,
                        (GoMethodDeclaration) value
                );
                case CONVERT_TO_METHOD -> convertToMethod(
                        project,
                        (GoFunctionDeclaration) value,
                        argument
                );
                case CONVERT_TO_COMPONENT -> convertToComponent(
                        project,
                        (GoFunctionDeclaration) value,
                        argument
                );
                case ADD_IMPLEMENTS -> addImplements(
                        project,
                        (GoTypeSpec) value,
                        argument
                );
                case CREATE_IMPLEMENTATION -> createImplementation(
                        project,
                        (GoTypeSpec) value,
                        argument
                );
                case CREATE_INTERFACE -> createInterface(
                        project,
                        (GoTypeSpec) value,
                        argument
                );
                case MOVE_BEAN -> moveBean(
                        project,
                        (GoFunctionDeclaration) value
                );
            }
        }

        @Override
        public boolean startInWriteAction() {
            return false;
        }
    }

    private static void generateConstructor(
            Project project,
            GoTypeSpec type
    ) {
        if (!(type.getContainingFile() instanceof GoFile file)) {
            return;
        }
        String name = type.getName();
        GoType declared = type.getSpecType().getType();
        if (name == null || !(declared instanceof GoStructType structType)) {
            return;
        }
        List<Field> fields = new ArrayList<>();
        for (GoFieldDeclaration declaration
                : structType.getFieldDeclarationList()) {
            GoType fieldType = declaration.getType();
            if (fieldType == null) {
                continue;
            }
            declaration.getFieldDefinitionList().stream()
                    .filter(value -> value.getName() != null
                            && !value.getName().equals("_"))
                    .map(value -> new Field(
                            value.getName(),
                            fieldType.getText()
                    ))
                    .forEach(fields::add);
        }
        StringBuilder constructor = new StringBuilder("\n\nfunc New")
                .append(name);
        if (fields.isEmpty()) {
            constructor.append("() *").append(name).append(" {\n")
                    .append("\treturn &").append(name).append("{}\n")
                    .append("}");
        } else {
            constructor.append("(\n");
            for (Field field : fields) {
                constructor.append("\t").append(field.name())
                        .append(" ").append(field.type()).append(",\n");
            }
            constructor.append(") *").append(name).append(" {\n")
                    .append("\treturn &").append(name).append("{\n");
            for (Field field : fields) {
                constructor.append("\t\t").append(field.name())
                        .append(": ").append(field.name()).append(",\n");
            }
            constructor.append("\t}\n}");
        }
        GoTopLevelDeclaration declaration = topLevel(type);
        if (declaration != null) {
            applyEdits(project, file, List.of(new Edit(
                    declaration.getTextRange().getEndOffset(),
                    declaration.getTextRange().getEndOffset(),
                    constructor.toString()
            )));
        }
    }

    private static void moveMethod(
            Project project,
            GoMethodDeclaration method
    ) {
        GoTypeSpec owner = method.resolveTypeSpec();
        if (!(method.getContainingFile() instanceof GoFile source)
                || owner == null
                || !(owner.getContainingFile() instanceof GoFile destination)
                || source == destination) {
            return;
        }
        if (!FileModificationService.getInstance()
                .preparePsiElementForWrite(destination)) {
            return;
        }
        int start = declarationStart(method);
        String moved = source.getText().substring(
                start,
                method.getTextRange().getEndOffset()
        );
        copyReferencedImports(source, destination, moved);
        Document destinationDocument = document(project, destination);
        Document sourceDocument = document(project, source);
        if (destinationDocument == null || sourceDocument == null) {
            return;
        }
        destinationDocument.insertString(
                destinationDocument.getTextLength(),
                "\n\n" + moved.strip() + "\n"
        );
        sourceDocument.deleteString(
                start,
                method.getTextRange().getEndOffset()
        );
        commit(project, destinationDocument);
        commit(project, sourceDocument);
        optimizeImports(source, destination);
    }

    private static void convertToMethod(
            Project project,
            GoFunctionDeclaration function,
            String typeName
    ) {
        if (!(function.getContainingFile() instanceof GoFile file)) {
            return;
        }
        int afterFunc = function.getFunc().getTextRange().getEndOffset();
        String receiver = receiverName(typeName);
        applyEdits(project, file, List.of(new Edit(
                afterFunc,
                afterFunc,
                " (" + receiver + " *" + typeName + ")"
        )));
    }

    private static void convertToComponent(
            Project project,
            GoFunctionDeclaration function,
            String componentName
    ) {
        if (!(function.getContainingFile() instanceof GoFile file)) {
            return;
        }
        int start = declarationStart(function);
        int afterFunc = function.getFunc().getTextRange().getEndOffset();
        List<Edit> edits = new ArrayList<>();
        edits.add(new Edit(
                start,
                start,
                "// @Component\ntype " + componentName + " struct{}\n\n"
                        + "func New" + componentName + "() *"
                        + componentName + " {\n"
                        + "\treturn &" + componentName + "{}\n"
                        + "}\n\n"
        ));
        edits.add(new Edit(
                afterFunc,
                afterFunc,
                " (*" + componentName + ")"
        ));
        addAnnotationImport(file, Set.of("Component"), edits);
        applyEdits(project, file, edits);
    }

    private static void addImplements(
            Project project,
            GoTypeSpec type,
            String contract
    ) {
        if (!(type.getContainingFile() instanceof GoFile file)) {
            return;
        }
        GoTopLevelDeclaration declaration = topLevel(type);
        if (declaration == null) {
            return;
        }
        List<Edit> edits = new ArrayList<>();
        edits.add(new Edit(
                declaration.getTextRange().getStartOffset(),
                declaration.getTextRange().getStartOffset(),
                "// @Implements(" + contract + ")\n"
        ));
        addAnnotationImport(file, Set.of("Implements"), edits);
        applyEdits(project, file, edits);
    }

    private static void createImplementation(
            Project project,
            GoTypeSpec contract,
            String implementationName
    ) {
        if (!(contract.getContainingFile() instanceof GoFile source)) {
            return;
        }
        List<GoNamedSignatureOwner> methods = interfaceMethods(contract);
        String role = managedRole(contract.getName());
        String signatures = signatures(methods);
        StringBuilder content = new StringBuilder()
                .append("package ").append(source.getPackageName())
                .append("\n");
        appendReferencedImports(content, source, signatures);
        content.append("\n// @import { ").append(role)
                .append(", Implements } from \"")
                .append(CORE_ANNOTATIONS).append("\"\n\n")
                .append("// @").append(role).append("\n")
                .append("// @Implements(").append(contract.getName())
                .append(")\n")
                .append("type ").append(implementationName)
                .append(" struct{}\n\n")
                .append("func New").append(implementationName)
                .append("() *").append(implementationName)
                .append(" {\n\treturn &").append(implementationName)
                .append("{}\n}\n");
        for (GoNamedSignatureOwner method : methods) {
            content.append("\nfunc (*").append(implementationName)
                    .append(") ").append(method.getName())
                    .append(method.getSignature().getText())
                    .append(" {\n\tpanic(\"implement me\")\n}\n");
        }
        createFile(project, source, snakeCase(implementationName) + ".go",
                content.toString());
    }

    private static void createInterface(
            Project project,
            GoTypeSpec type,
            String contractName
    ) {
        if (!(type.getContainingFile() instanceof GoFile source)) {
            return;
        }
        List<GoNamedSignatureOwner> methods = type.getMethods().stream()
                .filter(GoNamedSignatureOwner::isPublic)
                .map(value -> (GoNamedSignatureOwner) value)
                .toList();
        String signatures = signatures(methods);
        StringBuilder content = new StringBuilder()
                .append("package ").append(source.getPackageName())
                .append("\n");
        appendReferencedImports(content, source, signatures);
        content.append("\ntype ").append(contractName)
                .append(" interface {\n");
        for (GoNamedSignatureOwner method : methods) {
            content.append("\t").append(method.getName())
                    .append(method.getSignature().getText()).append("\n");
        }
        content.append("}\n");
        createFile(project, source, snakeCase(contractName) + ".go",
                content.toString());
        addImplements(project, type, contractName);
    }

    private static void moveBean(
            Project project,
            GoFunctionDeclaration function
    ) {
        if (!(function.getContainingFile() instanceof GoFile file)) {
            return;
        }
        String functionName = function.getName();
        if (functionName == null) {
            return;
        }
        String product = functionName.startsWith("New")
                && functionName.length() > 3
                ? functionName.substring(3)
                : functionName;
        if (product.endsWith("Configuration")
                && product.length() > "Configuration".length()) {
            product = product.substring(
                    0,
                    product.length() - "Configuration".length()
            );
        }
        String configuration = product + "Configuration";
        int start = declarationStart(function);
        List<Edit> edits = new ArrayList<>();
        edits.add(new Edit(
                start,
                start,
                "// @Configuration\ntype " + configuration
                        + " struct{}\n\n"
                        + "func New" + configuration + "() *"
                        + configuration + " {\n"
                        + "\treturn &" + configuration + "{}\n"
                        + "}\n\n"
        ));
        int afterFunc = function.getFunc().getTextRange().getEndOffset();
        edits.add(new Edit(
                afterFunc,
                afterFunc,
                " (*" + configuration + ")"
        ));
        if (!functionName.equals(product)) {
            TextRange identifier = function.getIdentifier().getTextRange();
            edits.add(new Edit(
                    identifier.getStartOffset(),
                    identifier.getEndOffset(),
                    product
            ));
        }
        addAnnotationImport(file, Set.of("Configuration"), edits);
        applyEdits(project, file, edits);
    }

    private static void appendReferencedImports(
            StringBuilder content,
            GoFile source,
            String signatures
    ) {
        List<String> imports = referencedImports(source, signatures);
        if (imports.isEmpty()) {
            return;
        }
        content.append("\nimport (\n");
        for (String imported : imports) {
            content.append("\t").append(imported).append("\n");
        }
        content.append(")\n");
    }

    private static List<String> referencedImports(
            GoFile source,
            String value
    ) {
        List<String> result = new ArrayList<>();
        for (GoImportSpec imported : source.getImports()) {
            String qualifier = importQualifier(imported);
            if (qualifier != null && value.contains(qualifier + ".")) {
                result.add(imported.getText().strip());
            }
        }
        return result;
    }

    private static void copyReferencedImports(
            GoFile source,
            GoFile destination,
            String value
    ) {
        for (GoImportSpec imported : source.getImports()) {
            String qualifier = importQualifier(imported);
            if (qualifier != null && value.contains(qualifier + ".")
                    && imported.getPath() != null) {
                destination.addImport(imported.getPath(), imported.getAlias());
            }
        }
    }

    private static @Nullable String importQualifier(GoImportSpec imported) {
        String alias = imported.getAlias();
        if (alias != null && !alias.isBlank()
                && !alias.equals(".") && !alias.equals("_")) {
            return alias;
        }
        String name = imported.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        String path = imported.getPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        return lastSegment(path);
    }

    private static void createFile(
            Project project,
            GoFile source,
            String name,
            String content
    ) {
        PsiDirectory directory = source.getContainingDirectory();
        if (directory == null || directory.findFile(name) != null) {
            return;
        }
        PsiFile created = PsiFileFactory.getInstance(project)
                .createFileFromText(name, GoFileType.INSTANCE, content);
        directory.add(created);
    }

    private static void addAnnotationImport(
            GoFile file,
            Set<String> symbols,
            List<Edit> edits
    ) {
        Set<String> missing = new LinkedHashSet<>();
        for (String symbol : symbols) {
            if (SpiceAnnotationIndex.resolveImport(file.getText(), symbol)
                    == null) {
                missing.add(symbol);
            }
        }
        if (missing.isEmpty() || file.getPackage() == null) {
            return;
        }
        int offset = file.getPackage().getTextRange().getEndOffset();
        edits.add(new Edit(
                offset,
                offset,
                "\n\n// @import { " + String.join(", ", missing)
                        + " } from \"" + CORE_ANNOTATIONS + "\""
        ));
    }

    private static void applyEdits(
            Project project,
            GoFile file,
            Collection<Edit> values
    ) {
        Document document = document(project, file);
        if (document == null) {
            return;
        }
        values.stream()
                .sorted(Comparator
                        .comparingInt(Edit::start)
                        .thenComparingInt(Edit::end)
                        .reversed())
                .forEach(value -> document.replaceString(
                        value.start(),
                        value.end(),
                        value.replacement()
                ));
        commit(project, document);
    }

    private static @Nullable Document document(
            Project project,
            PsiFile file
    ) {
        return PsiDocumentManager.getInstance(project).getDocument(file);
    }

    private static void commit(Project project, Document document) {
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }

    private static void optimizeImports(GoFile... files) {
        GoImportOptimizer optimizer = new GoImportOptimizer();
        for (GoFile file : files) {
            optimizer.processFile(file).run();
        }
    }

    private static boolean hasConstructor(GoTypeSpec type, GoFile file) {
        String name = type.getName();
        if (name == null) {
            return false;
        }
        GoPackage goPackage = GoPackage.of(file);
        if (goPackage == null) {
            return !file.getFunctions("New" + name).isEmpty();
        }
        for (PsiFile packageFile : goPackage.files()) {
            if (packageFile instanceof GoFile goFile
                    && !goFile.getFunctions("New" + name).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable GoTypeSpec associatedType(
            String function,
            GoFile file
    ) {
        for (GoTypeSpec type : file.getTypes()) {
            String name = type.getName();
            if (name != null && (function.equals("New" + name)
                    || function.equals("Parse" + name)
                    || function.equals("Must" + name)
                    || function.startsWith(name + "From"))) {
                return type;
            }
        }
        return null;
    }

    private static List<GoTypeSpec> implementedInterfaces(
            GoTypeSpec type,
            GoFile file
    ) {
        if (DumbService.isDumb(type.getProject())) {
            return List.of();
        }
        Map<String, String> methods = new LinkedHashMap<>();
        for (GoNamedSignatureOwner method : type.getAllMethods(type)) {
            methods.put(
                    method.getName(),
                    normalize(method.getSignature().getText())
            );
        }
        List<GoTypeSpec> result = new ArrayList<>();
        GoPackage goPackage = GoPackage.of(file);
        if (goPackage == null) {
            return List.of();
        }
        for (PsiFile packageFile : goPackage.files()) {
            if (!(packageFile instanceof GoFile candidateFile)) {
                continue;
            }
            for (GoTypeSpec candidate : candidateFile.getTypes()) {
                if (!GoTypeUtil.isInterface(candidate)) {
                    continue;
                }
                boolean matches = true;
                for (GoNamedSignatureOwner method
                        : interfaceMethods(candidate)) {
                    if (!normalize(method.getSignature().getText())
                            .equals(methods.get(method.getName()))) {
                        matches = false;
                        break;
                    }
                }
                if (matches && !interfaceMethods(candidate).isEmpty()) {
                    result.add(candidate);
                }
            }
        }
        result.sort(Comparator.comparing(GoTypeSpec::getName));
        return List.copyOf(result);
    }

    private static List<GoNamedSignatureOwner> interfaceMethods(
            GoTypeSpec type
    ) {
        GoType declared = type.getSpecType().getType();
        if (!(declared instanceof GoInterfaceType interfaceType)) {
            return List.of();
        }
        return interfaceType.getAllMethods(type).stream()
                .map(value -> (GoNamedSignatureOwner) value)
                .toList();
    }

    private static String signatures(
            List<GoNamedSignatureOwner> methods
    ) {
        StringBuilder result = new StringBuilder();
        for (GoNamedSignatureOwner method : methods) {
            result.append(method.getName())
                    .append(method.getSignature().getText()).append('\n');
        }
        return result.toString();
    }

    private static String normalize(String value) {
        return Pattern.compile("\\s+").matcher(value).replaceAll("");
    }

    private static boolean hasAnyAnnotation(
            PsiElement element,
            Set<String> symbols
    ) {
        for (String symbol : symbols) {
            if (hasAnnotation(element, symbol)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnnotation(
            PsiElement element,
            String symbol
    ) {
        for (PsiComment comment : commentsBefore(element)) {
            var parsed = SpiceAnnotationSyntax.parse(comment.getText());
            if (parsed.isEmpty()) {
                continue;
            }
            SpiceAnnotationIndex.DescriptorSymbol descriptor =
                    SpiceAnnotationIndex.resolveImport(
                            element.getContainingFile().getText(),
                            parsed.orElseThrow().name()
                    );
            String resolved = descriptor == null
                    ? lastSegment(parsed.orElseThrow().name())
                    : descriptor.symbol();
            if (resolved.equals(symbol)) {
                return true;
            }
        }
        return false;
    }

    private static List<PsiComment> commentsBefore(PsiElement element) {
        GoTopLevelDeclaration declaration = topLevel(element);
        if (declaration == null) {
            return List.of();
        }
        List<PsiComment> comments = PsiTreeUtil.findChildrenOfType(
                element.getContainingFile(),
                PsiComment.class
        ).stream().filter(value -> value.getTextRange().getEndOffset()
                <= declaration.getTextRange().getStartOffset())
                .sorted(Comparator.comparingInt(value ->
                        -value.getTextRange().getStartOffset()))
                .toList();
        List<PsiComment> result = new ArrayList<>();
        int cursor = declaration.getTextRange().getStartOffset();
        String source = element.getContainingFile().getText();
        for (PsiComment comment : comments) {
            if (!source.substring(
                    comment.getTextRange().getEndOffset(),
                    cursor
            ).isBlank()) {
                break;
            }
            result.add(comment);
            cursor = comment.getTextRange().getStartOffset();
        }
        return List.copyOf(result.reversed());
    }

    private static int declarationStart(PsiElement element) {
        List<PsiComment> comments = commentsBefore(element);
        if (!comments.isEmpty()) {
            return comments.getFirst().getTextRange().getStartOffset();
        }
        GoTopLevelDeclaration declaration = topLevel(element);
        return declaration == null
                ? element.getTextRange().getStartOffset()
                : declaration.getTextRange().getStartOffset();
    }

    private static @Nullable GoTopLevelDeclaration topLevel(
            PsiElement element
    ) {
        if (element instanceof GoTopLevelDeclaration declaration) {
            return declaration;
        }
        return PsiTreeUtil.getParentOfType(
                element,
                GoTopLevelDeclaration.class,
                false
        );
    }

    private static String managedRole(@Nullable String contract) {
        if (contract != null && contract.endsWith("Repository")) {
            return "Repository";
        }
        if (contract != null && contract.endsWith("Service")) {
            return "Service";
        }
        return "Component";
    }

    private static String contractName(@Nullable String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return "ComponentAPI";
        }
        if (typeName.startsWith("Default")
                && typeName.length() > "Default".length()) {
            return typeName.substring("Default".length());
        }
        return typeName + "API";
    }

    private static String receiverName(String typeName) {
        if (typeName.isBlank()) {
            return "value";
        }
        return typeName.substring(0, 1).toLowerCase(Locale.ROOT)
                + typeName.substring(1);
    }

    private static String snakeCase(String value) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isUpperCase(current) && index > 0
                    && (Character.isLowerCase(value.charAt(index - 1))
                    || index + 1 < value.length()
                    && Character.isLowerCase(value.charAt(index + 1)))) {
                result.append('_');
            }
            result.append(Character.toLowerCase(current));
        }
        return result.toString();
    }

    private static String lastSegment(String value) {
        int separator = value.lastIndexOf('.');
        return separator < 0 ? value : value.substring(separator + 1);
    }

    private record Field(String name, String type) {}

    private record Edit(int start, int end, String replacement) {}

    @FunctionalInterface
    private interface FixRegistrar {
        void register(PsiElement target, SourceAction action);
    }
}
