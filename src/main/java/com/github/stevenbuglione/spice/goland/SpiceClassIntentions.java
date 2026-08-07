package com.github.stevenbuglione.spice.goland;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Registered caret-scoped entrypoints for class-oriented source edits. */
public final class SpiceClassIntentions {
    private SpiceClassIntentions() {}

    public static final class GenerateConstructor extends Action {
        public GenerateConstructor() {
            super(
                    SpiceClassAuthoring.ActionKind.GENERATE_CONSTRUCTOR,
                    "Generate constructor"
            );
        }
    }

    public static final class MoveMethod extends Action {
        public MoveMethod() {
            super(
                    SpiceClassAuthoring.ActionKind.MOVE_METHOD,
                    "Move method to owning type file"
            );
        }
    }

    public static final class ConvertToMethod extends Action {
        public ConvertToMethod() {
            super(
                    SpiceClassAuthoring.ActionKind.CONVERT_TO_METHOD,
                    "Convert function to method"
            );
        }
    }

    public static final class ConvertToComponent extends Action {
        public ConvertToComponent() {
            super(
                    SpiceClassAuthoring.ActionKind.CONVERT_TO_COMPONENT,
                    "Convert function to @Component"
            );
        }
    }

    public static final class AddImplements extends Action {
        public AddImplements() {
            super(
                    SpiceClassAuthoring.ActionKind.ADD_IMPLEMENTS,
                    "Add @Implements"
            );
        }
    }

    public static final class CreateImplementation extends Action {
        public CreateImplementation() {
            super(
                    SpiceClassAuthoring.ActionKind.CREATE_IMPLEMENTATION,
                    "Create implementation"
            );
        }
    }

    public static final class CreateInterface extends Action {
        public CreateInterface() {
            super(
                    SpiceClassAuthoring.ActionKind.CREATE_INTERFACE,
                    "Create interface"
            );
        }
    }

    public static final class MoveBean extends Action {
        public MoveBean() {
            super(
                    SpiceClassAuthoring.ActionKind.MOVE_BEAN,
                    "Move @Bean to @Configuration"
            );
        }
    }

    public abstract static class Action implements IntentionAction {
        private final SpiceClassAuthoring.ActionKind kind;
        private final String text;

        private Action(
                SpiceClassAuthoring.ActionKind kind,
                String text
        ) {
            this.kind = kind;
            this.text = "Spice: " + text;
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
            IntentionAction delegate = SpiceClassAuthoring.actionAt(
                    kind,
                    editor,
                    file
            );
            return delegate != null
                    && delegate.isAvailable(project, editor, file);
        }

        @Override
        public void invoke(
                @NotNull Project project,
                @Nullable Editor editor,
                @Nullable PsiFile file
        ) {
            IntentionAction delegate = SpiceClassAuthoring.actionAt(
                    kind,
                    editor,
                    file
            );
            if (delegate != null) {
                delegate.invoke(project, editor, file);
            }
        }

        @Override
        public boolean startInWriteAction() {
            return false;
        }
    }
}
