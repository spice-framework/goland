package com.github.stevenbuglione.spice.goland;

import com.intellij.lang.Language;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.testFramework.LightVirtualFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
public final class SpiceAnnotationIndex {
    private static final int MAX_REFERENCE_BYTES = 2 << 20;
    private static final String RESOURCE = "/spice/annotations.md";

    private final Project project;
    private final String content;
    private final LightVirtualFile virtualFile;
    private final PsiFile psiFile;

    public SpiceAnnotationIndex(Project project) {
        this.project = project;
        content = loadReference();
        virtualFile = new LightVirtualFile(
                "Spice Annotations.md",
                PlainTextFileType.INSTANCE,
                content
        );
        psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                virtualFile.getName(),
                Language.findLanguageByID("TEXT"),
                content
        );
    }

    static SpiceAnnotationIndex getInstance(Project project) {
        return project.getService(SpiceAnnotationIndex.class);
    }

    @Nullable PsiElement resolve(String name) {
        String marker = "| `@" + name + "` |";
        int markerOffset = content.indexOf(marker);
        if (markerOffset < 0) {
            return null;
        }
        int annotationOffset = markerOffset + "| `".length();
        return new SpiceAnnotationTarget(
                project,
                psiFile,
                virtualFile,
                name,
                annotationOffset
        );
    }

    private static String loadReference() {
        try (InputStream input = SpiceAnnotationIndex.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                return "";
            }
            byte[] content = input.readNBytes(MAX_REFERENCE_BYTES + 1);
            if (content.length > MAX_REFERENCE_BYTES) {
                return "";
            }
            return new String(content, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    // Fake PSI navigation targets live only for the IDE session and are never serialized.
    @SuppressWarnings("serial")
    private static final class SpiceAnnotationTarget extends FakePsiElement {
        private final Project project;
        private final PsiFile parent;
        private final VirtualFile virtualFile;
        private final String name;
        private final TextRange range;

        private SpiceAnnotationTarget(
                Project project,
                PsiFile parent,
                VirtualFile virtualFile,
                String name,
                int offset
        ) {
            this.project = project;
            this.parent = parent;
            this.virtualFile = virtualFile;
            this.name = name;
            range = new TextRange(offset, offset + name.length() + 1);
        }

        @Override
        public @NotNull PsiElement getParent() {
            return parent;
        }

        @Override
        public @NotNull String getName() {
            return name;
        }

        @Override
        public @NotNull String getText() {
            return "@" + name;
        }

        @Override
        public @NotNull TextRange getTextRange() {
            return range;
        }

        @Override
        public int getTextOffset() {
            return range.getStartOffset();
        }

        @Override
        public boolean canNavigate() {
            return true;
        }

        @Override
        public boolean canNavigateToSource() {
            return true;
        }

        @Override
        public void navigate(boolean requestFocus) {
            new OpenFileDescriptor(project, virtualFile, range.getStartOffset())
                    .navigate(requestFocus);
        }
    }
}
