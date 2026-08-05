package com.github.stevenbuglione.spice.goland;

import com.goide.psi.GoFunctionDeclaration;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only metadata recovered from an indexed SDK descriptor function.
 *
 * <p>This parser is presentation-only. It never executes descriptor code and
 * does not contribute compiler semantics. The shared compiler/LSP remains the
 * authority; this bounded local view keeps navigation and useful documentation
 * available while that process is unavailable.
 */
record SpiceDescriptorMetadata(
        String localName,
        SpiceAnnotationIndex.DescriptorSymbol descriptor,
        GoFunctionDeclaration descriptorFunction,
        @Nullable GoFunctionDeclaration handlerFunction,
        String documentation,
        String signature,
        String summary,
        String targets,
        String tool,
        String handler,
        String protocol,
        SpiceAnnotationIndex.ModuleProvenance provenance
) {
    private static final Pattern STRING_FIELD = Pattern.compile(
            "(?m)^\\s*%s\\s*:\\s*\"([^\"]*)\"\\s*,?\\s*$"
    );
    private static final Pattern EXPRESSION_FIELD = Pattern.compile(
            "(?m)^\\s*%s\\s*:\\s*([^,\\r\\n]+)\\s*,?\\s*$"
    );
    private static final Pattern TARGETS = Pattern.compile(
            "(?s)Targets\\s*:\\s*\\[]sdk\\.Target\\s*\\{([^}]*)}"
    );
    private static final Pattern IDENTIFIER = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*"
    );

    static SpiceDescriptorMetadata create(
            String localName,
            SpiceAnnotationIndex.DescriptorSymbol descriptor,
            GoFunctionDeclaration function,
            SpiceAnnotationIndex index,
            PsiElement origin
    ) {
        String source = function.getText();
        String handlerExpression = literalOrExpression(source, "Handler");
        String tool = toolValue(literalOrExpression(source, "Tool"));
        SpiceAnnotationIndex.DescriptorSymbol handlerSymbol =
                handlerSymbol(descriptor.packagePath(), handlerExpression);
        GoFunctionDeclaration handlerFunction = handlerSymbol == null
                ? null
                : index.resolveDescriptorFunction(origin, handlerSymbol);
        return new SpiceDescriptorMetadata(
                localName,
                descriptor,
                function,
                handlerFunction,
                documentation(function),
                "func " + function.getName() + function.getSignature().getText(),
                stringField(source, "Summary"),
                targetText(source),
                tool,
                handlerExpression,
                expressionField(source, "Protocol"),
                index.provenance(descriptor.packagePath(), tool)
        );
    }

    private static @Nullable SpiceAnnotationIndex.DescriptorSymbol handlerSymbol(
            String descriptorPackage,
            String handlerExpression
    ) {
        if (IDENTIFIER.matcher(handlerExpression).matches()) {
            return new SpiceAnnotationIndex.DescriptorSymbol(
                    descriptorPackage,
                    handlerExpression
            );
        }
        return null;
    }

    private static String toolValue(String expression) {
        if ("coretool.Path".equals(expression)) {
            return "github.com/spice-framework/spice/cmd/spice-annotation-core";
        }
        return expression;
    }

    private static String targetText(String source) {
        Matcher matcher = TARGETS.matcher(source);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1)
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static String stringField(String source, String field) {
        Matcher matcher = Pattern.compile(
                STRING_FIELD.pattern().formatted(Pattern.quote(field))
        ).matcher(source);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String expressionField(String source, String field) {
        Matcher matcher = Pattern.compile(
                EXPRESSION_FIELD.pattern().formatted(Pattern.quote(field))
        ).matcher(source);
        return matcher.find() ? matcher.group(1).strip() : "";
    }

    private static String literalOrExpression(String source, String field) {
        String literal = stringField(source, field);
        return literal.isBlank() ? expressionField(source, field) : literal;
    }

    private static String documentation(GoFunctionDeclaration function) {
        List<String> comments = new ArrayList<>();
        for (PsiElement sibling = function.getPrevSibling();
                sibling != null;
                sibling = sibling.getPrevSibling()) {
            if (sibling instanceof PsiWhiteSpace) {
                continue;
            }
            if (!(sibling instanceof PsiComment comment)) {
                break;
            }
            comments.add(commentText(comment.getText()));
        }
        Collections.reverse(comments);
        return String.join("\n", comments).strip();
    }

    private static String commentText(String comment) {
        if (comment.startsWith("//")) {
            return comment.substring(2).stripLeading();
        }
        if (comment.startsWith("/*") && comment.endsWith("*/")) {
            return comment.substring(2, comment.length() - 2).strip();
        }
        return comment;
    }
}
