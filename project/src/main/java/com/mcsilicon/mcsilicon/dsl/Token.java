package com.mcsilicon.mcsilicon.dsl;

public final class Token {
    public final TokenType type;
    public final String text;
    public final long number;
    public final int line;

    public Token(TokenType type, String text, long number, int line) {
        this.type = type;
        this.text = text;
        this.number = number;
        this.line = line;
    }

    @Override
    public String toString() {
        return type + "(" + text + ")@L" + line;
    }
}
