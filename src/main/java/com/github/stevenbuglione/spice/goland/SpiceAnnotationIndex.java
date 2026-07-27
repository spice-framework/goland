package com.github.stevenbuglione.spice.goland;

import com.goide.psi.GoFile;
import com.goide.psi.GoFunctionDeclaration;
import com.goide.stubs.index.GoFunctionIndex;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
public final class SpiceAnnotationIndex {
    private static final String IDENTIFIER = "[A-Za-z_][A-Za-z0-9_]*";
    private static final Pattern NAMED_IMPORT = Pattern.compile(
            "^\\s*//\\s*@spice\\.import\\s*\\{([^}]*)}\\s*"
                    + "from\\s*\"([^\"]+)\"\\s*$"
    );
    private static final Pattern NAMESPACE_IMPORT = Pattern.compile(
            "^\\s*//\\s*@spice\\.import\\s*\\*\\s+as\\s+("
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
            PsiElement identifier = candidate.getIdentifier();
            if (identifier != null) {
                return identifier;
            }
        }
        return null;
    }

    private boolean matchesPackage(GoFile file, String packagePath) {
        if (file.getAllImportPaths(false).contains(packagePath)) {
            return true;
        }
        VirtualFile projectRoot = ProjectUtil.guessProjectDir(project);
        VirtualFile candidate = file.getVirtualFile();
        if (projectRoot == null || candidate == null || candidate.getParent() == null) {
            return false;
        }
        VirtualFile modFile = projectRoot.findChild("go.mod");
        if (modFile == null) {
            return false;
        }
        PsiFile modPsi = com.intellij.psi.PsiManager.getInstance(project)
                .findFile(modFile);
        if (modPsi == null) {
            return false;
        }
        Matcher module = MODULE_DIRECTIVE.matcher(modPsi.getText());
        if (!module.find()) {
            return false;
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
        return packagePath.equals(inferred);
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
}
