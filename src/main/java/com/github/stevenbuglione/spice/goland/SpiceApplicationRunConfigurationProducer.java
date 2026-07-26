package com.github.stevenbuglione.spice.goland;

import com.goide.execution.GoBuildingRunConfiguration;
import com.goide.execution.GoConfigurationFactoryBase;
import com.goide.execution.GoRunConfigurationProducerBase;
import com.goide.execution.application.GoApplicationConfiguration;
import com.goide.execution.application.GoApplicationRunConfigurationType;
import com.goide.psi.GoFile;
import com.goide.psi.GoFunctionDeclaration;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Prevents GoLand from running a Spice application as a temporary single-file
 * fragment. Package or directory execution preserves annotation comments and
 * includes the committed generated bootstrap.
 */
public final class SpiceApplicationRunConfigurationProducer
        extends GoRunConfigurationProducerBase<GoApplicationConfiguration>
        implements DumbAware {
    @Override
    protected boolean setupConfigurationFromContext(
            @NotNull GoApplicationConfiguration configuration,
            @NotNull ConfigurationContext context,
            @NotNull Ref<PsiElement> sourceElement
    ) {
        PsiElement element = getContextElement(context);
        ApplicationContext application = applicationContext(element);
        if (application == null) {
            return false;
        }

        Module module = findModule(application.file(), context);
        if (module != null) {
            prepareConfigurationFromContext(configuration, module);
        }
        if (!configurePackage(configuration, application.file())) {
            return false;
        }
        configuration.setFilePaths(java.util.List.of());
        configuration.setName(suggestedName(application.file()));
        sourceElement.set(application.marker());
        return true;
    }

    @Override
    public boolean isConfigurationFromContext(
            @NotNull GoApplicationConfiguration configuration,
            @NotNull ConfigurationContext context
    ) {
        ApplicationContext application = applicationContext(getContextElement(context));
        if (application == null) {
            return false;
        }
        GoFile file = application.file();
        String importPath = file.getImportPath(false);
        if (configuration.getKind() == GoBuildingRunConfiguration.Kind.PACKAGE) {
            return importPath != null && importPath.equals(configuration.getPackage());
        }
        VirtualFile virtualFile = file.getVirtualFile();
        return configuration.getKind() == GoBuildingRunConfiguration.Kind.DIRECTORY
                && virtualFile != null
                && virtualFile.getParent() != null
                && FileUtil.pathsEqual(
                        virtualFile.getParent().getPath(),
                        configuration.getDirectoryPath()
                );
    }

    @Override
    public @NotNull GoConfigurationFactoryBase getConfigurationFactory() {
        return GoApplicationRunConfigurationType.getInstance().getFactory();
    }

    @Override
    public boolean isPreferredConfiguration(
            @NotNull ConfigurationFromContext self,
            @NotNull ConfigurationFromContext other
    ) {
        if (other.getConfiguration() instanceof GoApplicationConfiguration otherGo) {
            return otherGo.getKind() == GoBuildingRunConfiguration.Kind.FILE;
        }
        return super.isPreferredConfiguration(self, other);
    }

    @Override
    public boolean shouldReplace(
            @NotNull ConfigurationFromContext self,
            @NotNull ConfigurationFromContext other
    ) {
        return other.getConfiguration() instanceof GoApplicationConfiguration otherGo
                && otherGo.getKind() == GoBuildingRunConfiguration.Kind.FILE;
    }

    private static boolean configurePackage(
            GoApplicationConfiguration configuration,
            GoFile file
    ) {
        String importPath = file.getImportPath(false);
        if (importPath != null && !importPath.isBlank()) {
            configuration.setKind(GoBuildingRunConfiguration.Kind.PACKAGE);
            configuration.setPackage(importPath);
            return true;
        }
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null || virtualFile.getParent() == null) {
            return false;
        }
        configuration.setKind(GoBuildingRunConfiguration.Kind.DIRECTORY);
        configuration.setDirectoryPath(virtualFile.getParent().getPath());
        return true;
    }

    private static String suggestedName(GoFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null || virtualFile.getParent() == null) {
            return "Spice Application";
        }
        return "Spice " + virtualFile.getParent().getName();
    }

    private static ApplicationContext applicationContext(PsiElement element) {
        if (element == null) {
            return null;
        }
        PsiFile containingFile = element instanceof PsiFile
                ? (PsiFile) element
                : element.getContainingFile();
        if (!(containingFile instanceof GoFile file)
                || !"main".equals(file.getPackageName())) {
            return null;
        }
        for (PsiComment comment : PsiTreeUtil.findChildrenOfType(
                file,
                PsiComment.class
        )) {
            SpiceAnnotationSyntax.Match annotation =
                    SpiceAnnotationSyntax.parse(comment.getText()).orElse(null);
            if (annotation == null || !"Application".equals(annotation.name())) {
                continue;
            }
            PsiElement declaration =
                    PsiTreeUtil.skipWhitespacesAndCommentsForward(comment);
            if (declaration instanceof GoFunctionDeclaration function
                    && "main".equals(function.getName())) {
                return new ApplicationContext(file, comment);
            }
        }
        return null;
    }

    private record ApplicationContext(GoFile file, PsiComment marker) {}
}
