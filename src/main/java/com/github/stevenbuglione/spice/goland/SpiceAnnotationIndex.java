package com.github.stevenbuglione.spice.goland;

import com.goide.psi.GoFile;
import com.goide.psi.GoFunctionDeclaration;
import com.goide.stubs.index.GoFunctionIndex;
import com.goide.stubs.index.GoPackagesIndex;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
public final class SpiceAnnotationIndex {
    private static final String IDENTIFIER = "[A-Za-z_][A-Za-z0-9_]*";
    private static final Pattern NAMED_IMPORT = Pattern.compile(
            "^\\s*//\\s*@import\\s*\\{([^}]*)}\\s*"
                    + "from\\s*\"([^\"]+)\"\\s*$"
    );
    private static final Pattern NAMESPACE_IMPORT = Pattern.compile(
            "^\\s*//\\s*@import\\s*\\*\\s+as\\s+("
                    + IDENTIFIER
                    + ")\\s+from\\s*\"([^\"]+)\"\\s*$"
    );
    private static final Pattern NAMED_BINDING = Pattern.compile(
            "^\\s*(" + IDENTIFIER + ")(?:\\s+as\\s+(" + IDENTIFIER + "))?\\s*$"
    );
    private static final Pattern MODULE_DIRECTIVE = Pattern.compile(
            "(?m)^\\s*module\\s+(\\S+)\\s*$"
    );

    private final Project project;

    public SpiceAnnotationIndex(Project project) {
        this.project = project;
    }

    static SpiceAnnotationIndex getInstance(Project project) {
        return project.getService(SpiceAnnotationIndex.class);
    }

    @Nullable PsiElement resolve(PsiComment comment, String localName) {
        PsiFile source = comment.getContainingFile();
        if (source == null || DumbService.isDumb(project)) {
            return null;
        }
        DescriptorSymbol descriptor = resolveImport(source.getText(), localName);
        if (descriptor == null) {
            return null;
        }
        return resolveDescriptor(comment, descriptor);
    }

    @Nullable PsiElement resolveDescriptor(
            PsiComment comment,
            DescriptorSymbol descriptor
    ) {
        GoFunctionDeclaration function = resolveDescriptorFunction(
                comment,
                descriptor
        );
        return function == null ? null : function.getIdentifier();
    }

    @Nullable GoFunctionDeclaration resolveDescriptorFunction(
            PsiElement context,
            DescriptorSymbol descriptor
    ) {
        if (context.getContainingFile() == null || DumbService.isDumb(project)) {
            return null;
        }
        for (GoFile file : packageFiles(descriptor.packagePath())) {
            for (GoFunctionDeclaration function : file.getFunctions()) {
                if (descriptor.symbol().equals(function.getName())) {
                    return function;
                }
            }
        }
        Collection<GoFunctionDeclaration> candidates = GoFunctionIndex.find(
                descriptor.symbol(),
                project,
                GlobalSearchScope.allScope(project),
                null
        );
        for (GoFunctionDeclaration candidate : candidates) {
            PsiFile candidateFile = candidate.getContainingFile();
            if (!(candidateFile instanceof GoFile goFile)
                    || !matchesPackage(goFile, descriptor.packagePath())) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    @Nullable SpiceDescriptorMetadata metadata(
            PsiComment comment,
            String localName
    ) {
        PsiFile source = comment.getContainingFile();
        if (source == null) {
            return null;
        }
        DescriptorSymbol descriptor = resolveImport(source.getText(), localName);
        return descriptor == null
                ? null
                : metadata(comment, localName, descriptor);
    }

    @Nullable SpiceDescriptorMetadata metadata(
            PsiComment comment,
            String localName,
            DescriptorSymbol descriptor
    ) {
        GoFunctionDeclaration function = resolveDescriptorFunction(
                comment,
                descriptor
        );
        return function == null
                ? null
                : SpiceDescriptorMetadata.create(
                        localName,
                        descriptor,
                        function,
                        this,
                        comment
                );
    }

    @Nullable SpiceDescriptorMetadata metadata(
            GoFunctionDeclaration descriptorFunction
    ) {
        if (!(descriptorFunction.getContainingFile() instanceof GoFile file)) {
            return null;
        }
        String packagePath = importPath(file);
        String name = descriptorFunction.getName();
        if (packagePath == null || name == null) {
            return null;
        }
        return SpiceDescriptorMetadata.create(
                name,
                new DescriptorSymbol(packagePath, name),
                descriptorFunction,
                this,
                descriptorFunction
        );
    }

    @Nullable PsiElement resolvePackage(PsiComment comment, String packagePath) {
        if (comment.getContainingFile() == null
                || DumbService.isDumb(project)
                || packagePath.isBlank()) {
            return null;
        }
        for (GoFile candidate : packageFiles(packagePath)) {
            if (candidate.getPackage() != null
                    && candidate.getPackage().getIdentifier() != null) {
                return candidate.getPackage().getIdentifier();
            }
        }
        return null;
    }

    List<CompletionSymbol> explicitCompletionSymbols(PsiFile source) {
        if (DumbService.isDumb(project)) {
            return List.of();
        }
        Map<String, CompletionSymbol> symbols = new LinkedHashMap<>();
        for (PsiComment comment
                : PsiTreeUtil.findChildrenOfType(source, PsiComment.class)) {
            var parsed = SpiceAnnotationSyntax.parseImportDirective(
                    comment.getText()
            );
            if (parsed.isEmpty()) {
                continue;
            }
            SpiceAnnotationSyntax.ImportDirective directive =
                    parsed.orElseThrow();
            for (SpiceAnnotationSyntax.ImportBinding binding
                    : directive.bindings()) {
                if (!binding.namespace()) {
                    symbols.putIfAbsent(
                            binding.localName(),
                            new CompletionSymbol(
                                    binding.localName(),
                                    directive.packagePath(),
                                    binding.importedName()
                            )
                    );
                    continue;
                }
                for (String descriptor
                        : descriptorNames(directive.packagePath())) {
                    String localName = binding.localName() + "." + descriptor;
                    symbols.putIfAbsent(
                            localName,
                            new CompletionSymbol(
                                    localName,
                                    directive.packagePath(),
                                    descriptor
                            )
                    );
                }
            }
        }
        return symbols.values().stream()
                .sorted(Comparator.comparing(CompletionSymbol::localName))
                .toList();
    }

    private List<String> descriptorNames(String packagePath) {
        List<String> result = new ArrayList<>();
        for (GoFile file : packageFiles(packagePath)) {
            for (GoFunctionDeclaration function : file.getFunctions()) {
                String name = function.getName();
                if (name != null
                        && function.isPublic()
                        && function.getSignature().getText()
                        .contains("Definition")) {
                    result.add(name);
                }
            }
        }
        return result.stream().distinct().sorted().toList();
    }

    private List<GoFile> packageFiles(String packagePath) {
        int separator = packagePath.lastIndexOf('/');
        String packageName = packagePath.substring(separator + 1);
        Collection<GoFile> indexed = StubIndex.getElements(
                GoPackagesIndex.KEY,
                packageName,
                project,
                GlobalSearchScope.allScope(project),
                GoFile.class
        );
        Map<String, GoFile> result = new LinkedHashMap<>();
        indexed.stream()
                .filter(file -> matchesPackage(file, packagePath))
                .forEach(file -> result.put(filePath(file), file));
        for (VirtualFile directory : explicitSourceDirectories(packagePath)) {
            for (VirtualFile child : directory.getChildren()) {
                if (child.isDirectory()
                        || !"go".equalsIgnoreCase(child.getExtension())) {
                    continue;
                }
                PsiFile psi = com.intellij.psi.PsiManager.getInstance(project)
                        .findFile(child);
                if (psi instanceof GoFile file) {
                    result.putIfAbsent(filePath(file), file);
                }
            }
        }
        return result.values().stream()
                .sorted(Comparator.comparing(SpiceAnnotationIndex::filePath))
                .toList();
    }

    private List<VirtualFile> explicitSourceDirectories(String packagePath) {
        VirtualFile modFile = moduleFile();
        if (modFile == null || modFile.getParent() == null) {
            return List.of();
        }
        VirtualFile moduleRoot = modFile.getParent();
        ModuleLayout layout = moduleLayout(readText(modFile));
        Path moduleRootPath;
        try {
            moduleRootPath = moduleRoot.toNioPath();
        } catch (UnsupportedOperationException ignored) {
            return List.of();
        }
        LinkedHashSet<Path> paths = new LinkedHashSet<>();
        addModulePackagePath(
                paths,
                moduleRootPath,
                layout.modulePath(),
                packagePath
        );
        paths.add(
                moduleRootPath
                        .resolve("vendor")
                        .resolve(packagePath.replace('/', java.io.File.separatorChar))
                        .normalize()
        );
        for (LocalReplacement replacement : layout.replacements()) {
            Path replacementRoot = localReplacementRoot(
                    moduleRootPath,
                    replacement.replacement()
            );
            if (replacementRoot != null) {
                addModulePackagePath(
                        paths,
                        replacementRoot,
                        replacement.modulePath(),
                        packagePath
                );
            }
        }
        List<VirtualFile> result = new ArrayList<>();
        LocalFileSystem local = LocalFileSystem.getInstance();
        for (Path path : paths) {
            VirtualFile directory = local.findFileByNioFile(path);
            if (directory == null && Files.isDirectory(path)) {
                directory = local.refreshAndFindFileByNioFile(path);
            }
            if (directory != null && directory.isDirectory()) {
                result.add(directory);
            }
        }
        return result;
    }

    private static void addModulePackagePath(
            Collection<Path> paths,
            Path root,
            String modulePath,
            String packagePath
    ) {
        if (modulePath.isBlank()
                || !(packagePath.equals(modulePath)
                || packagePath.startsWith(modulePath + "/"))) {
            return;
        }
        String suffix = packagePath.substring(modulePath.length());
        if (suffix.startsWith("/")) {
            suffix = suffix.substring(1);
        }
        Path path = root;
        if (!suffix.isBlank()) {
            path = path.resolve(
                    suffix.replace('/', java.io.File.separatorChar)
            );
        }
        paths.add(path.normalize());
    }

    private static @Nullable Path localReplacementRoot(
            Path moduleRoot,
            String replacement
    ) {
        if (replacement.isBlank()
                || replacement.contains("@")
                || replacement.matches("[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/.*")) {
            return null;
        }
        try {
            Path path = Path.of(replacement);
            return (path.isAbsolute() ? path : moduleRoot.resolve(path))
                    .normalize();
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    static ModuleLayout moduleLayout(String goMod) {
        String modulePath = "";
        List<LocalReplacement> replacements = new ArrayList<>();
        boolean replaceBlock = false;
        for (String raw : goMod.lines().toList()) {
            String line = stripGoModComment(raw).strip();
            if (line.startsWith("module ")) {
                modulePath = firstField(line.substring("module ".length()));
                continue;
            }
            if (line.equals("replace (")) {
                replaceBlock = true;
                continue;
            }
            if (replaceBlock && line.equals(")")) {
                replaceBlock = false;
                continue;
            }
            String value = replaceBlock
                    ? line
                    : line.startsWith("replace ")
                    ? line.substring("replace ".length()).strip()
                    : "";
            int arrow = value.indexOf("=>");
            if (arrow < 0) {
                continue;
            }
            String original = firstField(value.substring(0, arrow));
            String replacement = firstField(value.substring(arrow + 2));
            if (!original.isBlank() && !replacement.isBlank()) {
                replacements.add(new LocalReplacement(
                        original,
                        replacement
                ));
            }
        }
        return new ModuleLayout(modulePath, List.copyOf(replacements));
    }

    ModuleProvenance provenance(String packagePath, String toolPath) {
        VirtualFile modFile = moduleFile();
        if (modFile == null) {
            return ModuleProvenance.unknown();
        }
        String goMod = readText(modFile);
        ModuleLayout layout = moduleLayout(goMod);
        Map<String, String> requirements = requireDirectives(goMod);
        String owner = "";
        String version = "";
        if (ownsPackage(layout.modulePath(), packagePath)) {
            owner = layout.modulePath();
            version = "(workspace)";
        }
        for (Map.Entry<String, String> requirement
                : requirements.entrySet()) {
            if (requirement.getKey().length() > owner.length()
                    && ownsPackage(requirement.getKey(), packagePath)) {
                owner = requirement.getKey();
                version = requirement.getValue();
            }
        }
        for (LocalReplacement replacement : layout.replacements()) {
            if (replacement.modulePath().length() > owner.length()
                    && ownsPackage(replacement.modulePath(), packagePath)) {
                owner = replacement.modulePath();
                version = requirements.getOrDefault(
                        owner,
                        "(local replacement)"
                );
            }
        }
        String replacementPath = "";
        for (LocalReplacement replacement : layout.replacements()) {
            if (replacement.modulePath().equals(owner)) {
                replacementPath = replacement.replacement();
                break;
            }
        }
        boolean authorizationKnown = !toolPath.isBlank();
        boolean authorized = authorizationKnown
                && SpicePluginHealthService.parseToolDirectives(goMod)
                .contains(toolPath);
        return new ModuleProvenance(
                owner,
                version,
                replacementPath,
                authorizationKnown,
                authorized
        );
    }

    private static Map<String, String> requireDirectives(String goMod) {
        Map<String, String> result = new LinkedHashMap<>();
        boolean block = false;
        for (String raw : goMod.lines().toList()) {
            String line = stripGoModComment(raw).strip();
            if (line.equals("require (")) {
                block = true;
                continue;
            }
            if (block && line.equals(")")) {
                block = false;
                continue;
            }
            String value = block
                    ? line
                    : line.startsWith("require ")
                    ? line.substring("require ".length()).strip()
                    : "";
            List<String> fields = List.of(value.split("\\s+"));
            if (fields.size() >= 2 && !fields.getFirst().isBlank()) {
                result.put(fields.getFirst(), fields.get(1));
            }
        }
        return result;
    }

    private static boolean ownsPackage(
            String modulePath,
            String packagePath
    ) {
        return !modulePath.isBlank()
                && (packagePath.equals(modulePath)
                || packagePath.startsWith(modulePath + "/"));
    }

    private static String stripGoModComment(String value) {
        int comment = value.indexOf("//");
        return comment < 0 ? value : value.substring(0, comment);
    }

    private static String firstField(String value) {
        String stripped = value.strip();
        int offset = 0;
        while (offset < stripped.length()
                && !Character.isWhitespace(stripped.charAt(offset))) {
            offset++;
        }
        return stripped.substring(0, offset);
    }

    private static String readText(VirtualFile file) {
        try {
            return VfsUtilCore.loadText(file);
        } catch (java.io.IOException ignored) {
            return "";
        }
    }

    private static String filePath(GoFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        return virtualFile == null ? "" : virtualFile.getPath();
    }

    private boolean matchesPackage(GoFile file, String packagePath) {
        if (packagePath.equals(importPath(file))) {
            return true;
        }
        return packagePath.equals(inferredProjectImportPath(file));
    }

    private @Nullable String importPath(GoFile file) {
        String path = file.getImportPath(false);
        if (path == null || path.isBlank()) {
            path = file.getImportPath(true);
        }
        if (path == null || path.isBlank()) {
            path = inferredProjectImportPath(file);
        }
        return path == null || path.isBlank() ? null : path;
    }

    private @Nullable String inferredProjectImportPath(GoFile file) {
        VirtualFile projectRoot = ProjectUtil.guessProjectDir(project);
        VirtualFile candidate = file.getVirtualFile();
        if (projectRoot == null || candidate == null || candidate.getParent() == null) {
            return null;
        }
        VirtualFile modFile = moduleFile();
        if (modFile == null) {
            return null;
        }
        PsiFile modPsi = com.intellij.psi.PsiManager.getInstance(project)
                .findFile(modFile);
        if (modPsi == null) {
            return null;
        }
        Matcher module = MODULE_DIRECTIVE.matcher(modPsi.getText());
        if (!module.find()) {
            return null;
        }
        String relative = VfsUtilCore.getRelativePath(
                candidate.getParent(),
                projectRoot,
                '/'
        );
        String inferred = module.group(1);
        if (relative != null && !relative.isEmpty()) {
            inferred += "/" + relative;
        }
        return inferred;
    }

    private @Nullable VirtualFile moduleFile() {
        VirtualFile candidate = ProjectUtil.guessProjectDir(project);
        while (candidate != null) {
            VirtualFile modFile = candidate.findChild("go.mod");
            if (modFile != null && !modFile.isDirectory()) {
                return modFile;
            }
            candidate = candidate.getParent();
        }
        return null;
    }

    @Nullable static DescriptorSymbol resolveImport(String source, String localName) {
        int separator = localName.indexOf('.');
        String qualifier = separator < 0 ? "" : localName.substring(0, separator);
        String qualifiedSymbol = separator < 0 ? "" : localName.substring(separator + 1);
        if (separator >= 0
                && (qualifier.isEmpty()
                || qualifiedSymbol.isEmpty()
                || qualifiedSymbol.indexOf('.') >= 0)) {
            return null;
        }
        for (String line : source.lines().toList()) {
            if (separator >= 0) {
                Matcher namespace = NAMESPACE_IMPORT.matcher(line);
                if (namespace.matches() && qualifier.equals(namespace.group(1))) {
                    return new DescriptorSymbol(namespace.group(2), qualifiedSymbol);
                }
                continue;
            }
            Matcher named = NAMED_IMPORT.matcher(line);
            if (!named.matches()) {
                continue;
            }
            for (String value : named.group(1).split(",")) {
                Matcher binding = NAMED_BINDING.matcher(value);
                if (!binding.matches()) {
                    continue;
                }
                String imported = binding.group(1);
                String local = binding.group(2) == null ? imported : binding.group(2);
                if (localName.equals(local)) {
                    return new DescriptorSymbol(named.group(2), imported);
                }
            }
        }
        return null;
    }

    record DescriptorSymbol(String packagePath, String symbol) {}

    record CompletionSymbol(
            String localName,
            String packagePath,
            String descriptorName
    ) {}

    record ModuleLayout(
            String modulePath,
            List<LocalReplacement> replacements
    ) {}

    record LocalReplacement(String modulePath, String replacement) {}

    record ModuleProvenance(
            String modulePath,
            String version,
            String replacement,
            boolean authorizationKnown,
            boolean authorized
    ) {
        static ModuleProvenance unknown() {
            return new ModuleProvenance("", "", "", false, false);
        }
    }
}
