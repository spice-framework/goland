package com.github.stevenbuglione.spice.goland;

import com.intellij.openapi.util.TextRange;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class SpiceAnnotationSyntax {
    private static final String PREFIX = "// @";

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
            return List.of();
        }
        Match match = parsed.orElseThrow();
        List<Token> tokens = new ArrayList<>();
        tokens.add(new Token(TokenKind.ANNOTATION, match.referenceRange()));
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
                TokenKind kind = next < comment.length()
                        && comment.charAt(next) == '='
                        ? TokenKind.PARAMETER
                        : TokenKind.KEYWORD;
                tokens.add(new Token(kind, new TextRange(offset, end)));
                offset = end;
                continue;
            }
            if ("()[]=,".indexOf(value) >= 0) {
                tokens.add(new Token(
                        TokenKind.OPERATOR,
                        new TextRange(offset, offset + 1)
                ));
            }
            offset++;
        }
        return List.copyOf(tokens);
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
        ANNOTATION,
        PARAMETER,
        STRING,
        NUMBER,
        KEYWORD,
        OPERATOR
    }

    record Token(TokenKind kind, TextRange range) {}

    record Match(String name, TextRange prefixRange, TextRange referenceRange) {}
}
