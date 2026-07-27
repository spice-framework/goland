package com.github.stevenbuglione.spice.goland;

import com.intellij.openapi.util.TextRange;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

final class SpiceAnnotationSyntax {
    private static final String PREFIX = "// @";
    private static final Pattern IMPORT_DIRECTIVE = Pattern.compile(
            "^// @spice\\.import\\s+(?:\\{[^}]+}\\s+from\\s+\"[^\"]+\""
                    + "|\\*\\s+as\\s+[A-Za-z_][A-Za-z0-9_]*"
                    + "\\s+from\\s+\"[^\"]+\")\\s*$"
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
        int spiceStart = sigil + 1;
        int separator = comment.indexOf('.', spiceStart);
        tokens.add(new Token(
                TokenKind.NAMESPACE,
                new TextRange(spiceStart, separator)
        ));
        tokens.add(new Token(
                TokenKind.OPERATOR,
                new TextRange(separator, separator + 1)
        ));
        int importEnd = separator + ".import".length();
        tokens.add(new Token(
                TokenKind.KEYWORD,
                new TextRange(separator + 1, importEnd)
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
}
