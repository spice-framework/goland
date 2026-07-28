package com.github.stevenbuglione.spice.goland;

import com.intellij.openapi.util.TextRange;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SpiceAnnotationSyntax {
    private static final String PREFIX = "// @";
    private static final Pattern IMPORT_DIRECTIVE = Pattern.compile(
            "^// @import\\s+(?:\\{[^}]+}\\s+from\\s+\"[^\"]+\""
                    + "|\\*\\s+as\\s+[A-Za-z_][A-Za-z0-9_]*"
                    + "\\s+from\\s+\"[^\"]+\")\\s*$"
    );
    private static final Pattern NAMED_IMPORT_DIRECTIVE = Pattern.compile(
            "^// @import\\s+\\{([^}]+)}\\s+from\\s+\"([^\"]+)\"\\s*$"
    );
    private static final Pattern NAMESPACE_IMPORT_DIRECTIVE = Pattern.compile(
            "^// @import\\s+\\*\\s+as\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s+from\\s+\"([^\"]+)\"\\s*$"
    );
    private static final Pattern IMPORT_BINDING = Pattern.compile(
            "^\\s*([A-Za-z_][A-Za-z0-9_]*)"
                    + "(?:\\s+as\\s+([A-Za-z_][A-Za-z0-9_]*))?\\s*$"
    );

    private SpiceAnnotationSyntax() {}

    static Optional<Match> parse(String comment) {
        if (!comment.startsWith(PREFIX)) {
            return Optional.empty();
        }
        int nameStart = PREFIX.length();
        if (nameStart >= comment.length() || !isIdentifierStart(comment.charAt(nameStart))) {
            return Optional.empty();
        }
        int offset = nameStart;
        boolean segmentStart = true;
        while (offset < comment.length()) {
            char value = comment.charAt(offset);
            if (value == '.') {
                if (segmentStart) {
                    return Optional.empty();
                }
                segmentStart = true;
                offset++;
                continue;
            }
            if (!isIdentifierCharacter(value) || segmentStart && !isIdentifierStart(value)) {
                break;
            }
            segmentStart = false;
            offset++;
        }
        if (segmentStart || !isValidSuffix(comment, offset)) {
            return Optional.empty();
        }
        return Optional.of(new Match(
                comment.substring(nameStart, offset),
                new TextRange(0, PREFIX.length() - 1),
                new TextRange(PREFIX.length() - 1, offset)
        ));
    }

    static List<Token> highlightTokens(String comment) {
        Optional<Match> parsed = parse(comment);
        if (parsed.isEmpty()) {
            return highlightImportDirective(comment);
        }
        Match match = parsed.orElseThrow();
        List<Token> tokens = new ArrayList<>();
        tokens.add(new Token(TokenKind.PREFIX, match.prefixRange()));
        addAnnotationNameTokens(tokens, comment, match.referenceRange());
        int offset = match.referenceRange().getEndOffset();
        while (offset < comment.length()) {
            char value = comment.charAt(offset);
            if (Character.isWhitespace(value)) {
                offset++;
                continue;
            }
            if (value == '"') {
                int end = quotedEnd(comment, offset);
                tokens.add(new Token(TokenKind.STRING, new TextRange(offset, end)));
                offset = end;
                continue;
            }
            if (isNumberStart(comment, offset)) {
                int end = offset + 1;
                while (end < comment.length()
                        && Character.isDigit(comment.charAt(end))) {
                    end++;
                }
                tokens.add(new Token(TokenKind.NUMBER, new TextRange(offset, end)));
                offset = end;
                continue;
            }
            if (isIdentifierStart(value)) {
                int end = offset + 1;
                while (end < comment.length()
                        && isIdentifierCharacter(comment.charAt(end))) {
                    end++;
                }
                int next = end;
                while (next < comment.length()
                        && Character.isWhitespace(comment.charAt(next))) {
                    next++;
                }
                if (next < comment.length() && comment.charAt(next) == '.') {
                    offset = addQualifiedTypeTokens(tokens, comment, offset, end);
                    continue;
                }
                String identifier = comment.substring(offset, end);
                TokenKind kind;
                if (next < comment.length() && comment.charAt(next) == '=') {
                    kind = TokenKind.PARAMETER;
                } else if (identifier.equals("true")
                        || identifier.equals("false")
                        || identifier.equals("nil")) {
                    kind = TokenKind.BOOLEAN;
                } else if (Character.isUpperCase(identifier.charAt(0))) {
                    kind = TokenKind.TYPE_REFERENCE;
                } else {
                    kind = TokenKind.IDENTIFIER;
                }
                tokens.add(new Token(kind, new TextRange(offset, end)));
                offset = end;
                continue;
            }
            if ("()[]=,{}*:".indexOf(value) >= 0) {
                tokens.add(new Token(
                        TokenKind.OPERATOR,
                        new TextRange(offset, offset + 1)
                ));
            }
            offset++;
        }
        return List.copyOf(tokens);
    }

    static Optional<ImportDirective> parseImportDirective(String comment) {
        Matcher named = NAMED_IMPORT_DIRECTIVE.matcher(comment);
        if (named.matches()) {
            List<ImportBinding> bindings = new ArrayList<>();
            String source = named.group(1);
            int sourceStart = named.start(1);
            int bindingStart = 0;
            while (bindingStart <= source.length()) {
                int separator = source.indexOf(',', bindingStart);
                int bindingEnd = separator < 0 ? source.length() : separator;
                Matcher binding = IMPORT_BINDING.matcher(
                        source.substring(bindingStart, bindingEnd)
                );
                if (!binding.matches()) {
                    return Optional.empty();
                }
                int base = sourceStart + bindingStart;
                String imported = binding.group(1);
                String alias = binding.group(2);
                TextRange importedRange = new TextRange(
                        base + binding.start(1),
                        base + binding.end(1)
                );
                TextRange localRange = importedRange;
                String localName = imported;
                if (alias != null) {
                    localName = alias;
                    localRange = new TextRange(
                            base + binding.start(2),
                            base + binding.end(2)
                    );
                }
                bindings.add(new ImportBinding(
                        imported,
                        importedRange,
                        localName,
                        localRange,
                        false
                ));
                if (separator < 0) {
                    break;
                }
                bindingStart = separator + 1;
            }
            return Optional.of(new ImportDirective(
                    named.group(2),
                    new TextRange(named.start(2), named.end(2)),
                    List.copyOf(bindings)
            ));
        }

        Matcher namespace = NAMESPACE_IMPORT_DIRECTIVE.matcher(comment);
        if (!namespace.matches()) {
            return Optional.empty();
        }
        TextRange namespaceRange = new TextRange(
                namespace.start(1),
                namespace.end(1)
        );
        return Optional.of(new ImportDirective(
                namespace.group(2),
                new TextRange(namespace.start(2), namespace.end(2)),
                List.of(new ImportBinding(
                        "",
                        TextRange.EMPTY_RANGE,
                        namespace.group(1),
                        namespaceRange,
                        true
                ))
        ));
    }

    static List<TypeArgument> typeArguments(String comment) {
        Optional<Match> parsed = parse(comment);
        if (parsed.isEmpty()) {
            return List.of();
        }
        int open = skipHorizontalSpace(
                comment,
                parsed.orElseThrow().referenceRange().getEndOffset()
        );
        if (open >= comment.length() || comment.charAt(open) != '(') {
            return List.of();
        }
        int close = matchingParenthesis(comment, open);
        if (close < 0) {
            close = comment.length();
        }
        List<TypeArgument> result = new ArrayList<>();
        int start = open + 1;
        int squareDepth = 0;
        for (int offset = start; offset <= close; offset++) {
            char value = offset == close ? ',' : comment.charAt(offset);
            if (value == '[') {
                squareDepth++;
            } else if (value == ']' && squareDepth > 0) {
                squareDepth--;
            }
            if (value != ',' || squareDepth != 0) {
                continue;
            }
            addTypeArgument(result, comment, start, offset);
            start = offset + 1;
        }
        return List.copyOf(result);
    }

    static Optional<TypeCompletion> typeCompletion(
            String comment,
            int relativeOffset
    ) {
        Optional<Match> parsed = parse(comment);
        if (parsed.isEmpty()
                || relativeOffset < 0
                || relativeOffset > comment.length()) {
            return Optional.empty();
        }
        int open = skipHorizontalSpace(
                comment,
                parsed.orElseThrow().referenceRange().getEndOffset()
        );
        if (open >= relativeOffset
                || open >= comment.length()
                || comment.charAt(open) != '(') {
            return Optional.empty();
        }
        int start = open + 1;
        int squareDepth = 0;
        for (int offset = start; offset < relativeOffset; offset++) {
            char value = comment.charAt(offset);
            if (value == '[') {
                squareDepth++;
            } else if (value == ']' && squareDepth > 0) {
                squareDepth--;
            } else if (value == ')' && squareDepth == 0) {
                return Optional.empty();
            } else if (value == ',' && squareDepth == 0) {
                start = offset + 1;
            }
        }
        while (start < relativeOffset
                && Character.isWhitespace(comment.charAt(start))) {
            start++;
        }
        String prefix = comment.substring(start, relativeOffset);
        if (prefix.indexOf('[') >= 0 || !isQualifiedIdentifierPrefix(prefix)) {
            return Optional.empty();
        }
        return Optional.of(new TypeCompletion(
                prefix,
                new TextRange(start, relativeOffset)
        ));
    }

    static Optional<TextRange> concealmentRange(String comment) {
        if (PREFIX.equals(comment)) {
            return Optional.of(new TextRange(0, PREFIX.length() - 1));
        }
        Optional<Match> annotation = parse(comment);
        if (annotation.isPresent()) {
            return Optional.of(annotation.orElseThrow().prefixRange());
        }
        if (IMPORT_DIRECTIVE.matcher(comment).matches()) {
            return Optional.of(new TextRange(0, PREFIX.length() - 1));
        }
        return Optional.empty();
    }

    private static void addAnnotationNameTokens(
            List<Token> tokens,
            String comment,
            TextRange range
    ) {
        int sigil = range.getStartOffset();
        tokens.add(new Token(TokenKind.SIGIL, new TextRange(sigil, sigil + 1)));
        int nameStart = sigil + 1;
        int separator = comment.lastIndexOf('.', range.getEndOffset() - 1);
        if (separator >= nameStart) {
            tokens.add(new Token(
                    TokenKind.NAMESPACE,
                    new TextRange(nameStart, separator)
            ));
            tokens.add(new Token(
                    TokenKind.OPERATOR,
                    new TextRange(separator, separator + 1)
            ));
            nameStart = separator + 1;
        }
        tokens.add(new Token(
                TokenKind.ANNOTATION,
                new TextRange(nameStart, range.getEndOffset())
        ));
    }

    private static List<Token> highlightImportDirective(String comment) {
        if (!IMPORT_DIRECTIVE.matcher(comment).matches()) {
            return List.of();
        }
        List<Token> tokens = new ArrayList<>();
        tokens.add(new Token(
                TokenKind.PREFIX,
                new TextRange(0, PREFIX.length() - 1)
        ));
        int sigil = PREFIX.length() - 1;
        tokens.add(new Token(TokenKind.SIGIL, new TextRange(sigil, sigil + 1)));
        int importStart = sigil + 1;
        int importEnd = importStart + "import".length();
        tokens.add(new Token(
                TokenKind.KEYWORD,
                new TextRange(importStart, importEnd)
        ));
        boolean namespaceImport = comment.substring(importEnd).stripLeading()
                .startsWith("*");
        boolean aliasFollows = false;
        int offset = importEnd;
        while (offset < comment.length()) {
            char value = comment.charAt(offset);
            if (Character.isWhitespace(value)) {
                offset++;
                continue;
            }
            if (value == '"') {
                int end = quotedEnd(comment, offset);
                tokens.add(new Token(TokenKind.STRING, new TextRange(offset, end)));
                offset = end;
                continue;
            }
            if (isIdentifierStart(value)) {
                int end = offset + 1;
                while (end < comment.length()
                        && isIdentifierCharacter(comment.charAt(end))) {
                    end++;
                }
                String identifier = comment.substring(offset, end);
                TokenKind kind;
                if (identifier.equals("as") || identifier.equals("from")) {
                    kind = TokenKind.KEYWORD;
                    aliasFollows = identifier.equals("as");
                } else if (aliasFollows) {
                    kind = namespaceImport
                            ? TokenKind.NAMESPACE
                            : TokenKind.IMPORT_ALIAS;
                    aliasFollows = false;
                } else {
                    kind = TokenKind.IMPORT_SYMBOL;
                }
                tokens.add(new Token(kind, new TextRange(offset, end)));
                offset = end;
                continue;
            }
            if ("{}*,".indexOf(value) >= 0) {
                tokens.add(new Token(
                        TokenKind.OPERATOR,
                        new TextRange(offset, offset + 1)
                ));
            }
            offset++;
        }
        return List.copyOf(tokens);
    }

    private static int addQualifiedTypeTokens(
            List<Token> tokens,
            String comment,
            int start,
            int firstEnd
    ) {
        int segmentStart = start;
        int segmentEnd = firstEnd;
        while (segmentEnd < comment.length()
                && comment.charAt(segmentEnd) == '.') {
            tokens.add(new Token(
                    TokenKind.NAMESPACE,
                    new TextRange(segmentStart, segmentEnd)
            ));
            tokens.add(new Token(
                    TokenKind.OPERATOR,
                    new TextRange(segmentEnd, segmentEnd + 1)
            ));
            segmentStart = segmentEnd + 1;
            if (segmentStart >= comment.length()
                    || !isIdentifierStart(comment.charAt(segmentStart))) {
                return segmentStart;
            }
            segmentEnd = segmentStart + 1;
            while (segmentEnd < comment.length()
                    && isIdentifierCharacter(comment.charAt(segmentEnd))) {
                segmentEnd++;
            }
        }
        tokens.add(new Token(
                TokenKind.TYPE_REFERENCE,
                new TextRange(segmentStart, segmentEnd)
        ));
        return segmentEnd;
    }

    private static int quotedEnd(String comment, int start) {
        boolean escaped = false;
        for (int offset = start + 1; offset < comment.length(); offset++) {
            char value = comment.charAt(offset);
            if (value == '"' && !escaped) {
                return offset + 1;
            }
            if (value == '\\' && !escaped) {
                escaped = true;
            } else {
                escaped = false;
            }
        }
        return comment.length();
    }

    private static void addTypeArgument(
            List<TypeArgument> result,
            String comment,
            int rawStart,
            int rawEnd
    ) {
        int start = rawStart;
        int end = rawEnd;
        while (start < end && Character.isWhitespace(comment.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(comment.charAt(end - 1))) {
            end--;
        }
        if (start == end) {
            return;
        }
        int referenceEnd = start;
        if (!isIdentifierStart(comment.charAt(referenceEnd))) {
            return;
        }
        referenceEnd++;
        while (referenceEnd < end
                && isIdentifierCharacter(comment.charAt(referenceEnd))) {
            referenceEnd++;
        }
        if (referenceEnd < end && comment.charAt(referenceEnd) == '.') {
            referenceEnd++;
            if (referenceEnd >= end
                    || !isIdentifierStart(comment.charAt(referenceEnd))) {
                return;
            }
            referenceEnd++;
            while (referenceEnd < end
                    && isIdentifierCharacter(comment.charAt(referenceEnd))) {
                referenceEnd++;
            }
        }
        if (referenceEnd < end && comment.charAt(referenceEnd) != '[') {
            return;
        }
        result.add(new TypeArgument(
                comment.substring(start, end),
                new TextRange(start, end),
                new TextRange(start, referenceEnd)
        ));
    }

    private static int matchingParenthesis(String value, int open) {
        int squareDepth = 0;
        for (int offset = open + 1; offset < value.length(); offset++) {
            char current = value.charAt(offset);
            if (current == '[') {
                squareDepth++;
            } else if (current == ']' && squareDepth > 0) {
                squareDepth--;
            } else if (current == ')' && squareDepth == 0) {
                return offset;
            }
        }
        return -1;
    }

    private static int skipHorizontalSpace(String value, int start) {
        int offset = start;
        while (offset < value.length()) {
            char current = value.charAt(offset);
            if (current != ' ' && current != '\t' && current != '\r') {
                break;
            }
            offset++;
        }
        return offset;
    }

    private static boolean isQualifiedIdentifierPrefix(String value) {
        if (value.isEmpty()) {
            return true;
        }
        boolean segmentStart = true;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '.') {
                if (segmentStart) {
                    return false;
                }
                segmentStart = true;
                continue;
            }
            if (segmentStart
                    ? !isIdentifierStart(current)
                    : !isIdentifierCharacter(current)) {
                return false;
            }
            segmentStart = false;
        }
        return true;
    }

    private static boolean isNumberStart(String comment, int offset) {
        char value = comment.charAt(offset);
        return Character.isDigit(value)
                || value == '-'
                && offset + 1 < comment.length()
                && Character.isDigit(comment.charAt(offset + 1));
    }

    private static boolean isValidSuffix(String comment, int offset) {
        if (offset == comment.length() || comment.charAt(offset) == '(') {
            return true;
        }
        for (int index = offset; index < comment.length(); index++) {
            char value = comment.charAt(index);
            if (value != ' ' && value != '\t' && value != '\r') {
                return value == '(';
            }
        }
        return true;
    }

    private static boolean isIdentifierStart(char value) {
        return value == '_' || value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z';
    }

    private static boolean isIdentifierCharacter(char value) {
        return isIdentifierStart(value) || value >= '0' && value <= '9';
    }

    enum TokenKind {
        PREFIX,
        SIGIL,
        NAMESPACE,
        ANNOTATION,
        PARAMETER,
        IMPORT_SYMBOL,
        IMPORT_ALIAS,
        TYPE_REFERENCE,
        STRING,
        NUMBER,
        BOOLEAN,
        IDENTIFIER,
        KEYWORD,
        OPERATOR
    }

    record Token(TokenKind kind, TextRange range) {}

    record Match(String name, TextRange prefixRange, TextRange referenceRange) {}

    record ImportDirective(
            String packagePath,
            TextRange packageRange,
            List<ImportBinding> bindings
    ) {}

    record ImportBinding(
            String importedName,
            TextRange importedRange,
            String localName,
            TextRange localRange,
            boolean namespace
    ) {}

    record TypeArgument(
            String expression,
            TextRange expressionRange,
            TextRange referenceRange
    ) {}

    record TypeCompletion(String prefix, TextRange range) {}
}
