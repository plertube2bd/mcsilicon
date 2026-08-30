package com.mcsilicon.mcsilicon.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 칩 DSL(v3) 렉서.
 *
 * 예:
 *   NEW HA(A, B) -> (SUM, CARRY) RULE (
 *        SUM
 *     A SELF CARRY
 *        B
 *   ) DO
 *       AND A, B THEN
 *           0 >> SUM;
 *           1 >> CARRY;
 *           RETURN;
 *       END
 *       NOTRETURN THEN RETURN; END
 *   END
 */
public final class Lexer {
    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
        Map.entry("NEW", TokenType.NEW),
        Map.entry("GLOBAL", TokenType.GLOBAL),
        Map.entry("GROUP", TokenType.GROUP),
        Map.entry("RULE", TokenType.RULE),
        Map.entry("DO", TokenType.DO),
        Map.entry("END", TokenType.END),
        Map.entry("SELF", TokenType.SELF),
        Map.entry("THEN", TokenType.THEN),
        Map.entry("RETURN", TokenType.RETURN),
        Map.entry("USE", TokenType.USE),
        Map.entry("CHIP", TokenType.CHIP),
        Map.entry("TABLE", TokenType.TABLE),
        Map.entry("IBUSRULE", TokenType.IBUSRULE),
        Map.entry("LABEL", TokenType.LABEL),
        Map.entry("JMP", TokenType.JMP),
        Map.entry("NOTRETURN", TokenType.NOTRETURN),
        Map.entry("BIT", TokenType.BIT),
        Map.entry("INT", TokenType.INT),
        Map.entry("AND", TokenType.AND),
        Map.entry("OR", TokenType.OR),
        Map.entry("XOR", TokenType.XOR),
        Map.entry("NAND", TokenType.NAND),
        Map.entry("NOR", TokenType.NOR),
        Map.entry("XNOR", TokenType.XNOR),
        Map.entry("NOT", TokenType.NOT),
        Map.entry("ADD", TokenType.ADD),
        Map.entry("SUB", TokenType.SUB),
        Map.entry("MUL", TokenType.MUL),
        Map.entry("DIV", TokenType.DIV)
    );

    private final String src;
    private int pos = 0;
    private int line = 1;

    public Lexer(String src) { this.src = src; }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        Token t;
        do {
            t = next();
            tokens.add(t);
        } while (t.type != TokenType.EOF);
        return tokens;
    }

    private char peek() { return pos < src.length() ? src.charAt(pos) : '\0'; }
    private char peekAt(int off) { int p = pos + off; return p < src.length() ? src.charAt(p) : '\0'; }
    private char advance() { char c = src.charAt(pos++); if (c == '\n') line++; return c; }

    private Token next() {
        skipWhitespaceAndComments();
        if (pos >= src.length()) return new Token(TokenType.EOF, "", 0, line);

        char c = peek();
        int startLine = line;

        if (Character.isLetter(c) || c == '_') return identifierOrKeyword();
        if (Character.isDigit(c)) return number();

        advance();
        switch (c) {
            case '(': return new Token(TokenType.LPAREN, "(", 0, startLine);
            case ')': return new Token(TokenType.RPAREN, ")", 0, startLine);
            case ',': return new Token(TokenType.COMMA, ",", 0, startLine);
            case ';': return new Token(TokenType.SEMI, ";", 0, startLine);
            case '.': return new Token(TokenType.DOT, ".", 0, startLine);
            case ':': return new Token(TokenType.COLON, ":", 0, startLine);
            case '/': return new Token(TokenType.SLASH, "/", 0, startLine);
            case '-':
                if (peek() == '>') { advance(); return new Token(TokenType.ARROW, "->", 0, startLine); }
                throw new DslException("알 수 없는 문자 '-' (라인 " + startLine + ")");
            case '<':
                if (peek() == '<') { advance(); return new Token(TokenType.SHL, "<<", 0, startLine); }
                return new Token(TokenType.LT, "<", 0, startLine);
            case '>':
                if (peek() == '>') { advance(); return new Token(TokenType.SHR, ">>", 0, startLine); }
                return new Token(TokenType.GT, ">", 0, startLine);
            default:
                throw new DslException("알 수 없는 문자 '" + c + "' (라인 " + startLine + ")");
        }
    }

    private void skipWhitespaceAndComments() {
        while (pos < src.length()) {
            char c = peek();
            if (Character.isWhitespace(c)) {
                advance();
            } else if (c == '%') {
                while (pos < src.length() && peek() != '\n') advance();
            } else if (c == '/' && peekAt(1) == '/') {
                while (pos < src.length() && peek() != '\n') advance();
            } else if (c == '/' && peekAt(1) == '*') {
                advance(); advance();
                while (pos < src.length() && !(peek() == '*' && peekAt(1) == '/')) advance();
                if (pos < src.length()) { advance(); advance(); }
            } else break;
        }
    }

    private Token identifierOrKeyword() {
        int start = pos;
        int startLine = line;
        while (pos < src.length() && (Character.isLetterOrDigit(peek()) || peek() == '_')) advance();
        String text = src.substring(start, pos);
        TokenType kw = KEYWORDS.get(text.toUpperCase());
        if (kw != null) return new Token(kw, text, 0, startLine);
        return new Token(TokenType.IDENT, text, 0, startLine);
    }

    private Token number() {
        int start = pos;
        int startLine = line;
        while (pos < src.length() && Character.isDigit(peek())) advance();
        long v = Long.parseLong(src.substring(start, pos));
        return new Token(TokenType.NUMBER, src.substring(start, pos), v, startLine);
    }
}
