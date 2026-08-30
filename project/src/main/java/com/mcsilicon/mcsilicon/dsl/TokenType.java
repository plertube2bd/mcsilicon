package com.mcsilicon.mcsilicon.dsl;

public enum TokenType {
    NEW, GLOBAL, GROUP, RULE, DO, END, SELF, THEN, RETURN, USE, CHIP, TABLE,
    LABEL, JMP, NOTRETURN,
    BIT, INT, IBUSRULE,
    AND, OR, XOR, NAND, NOR, XNOR, NOT,
    ADD, SUB, MUL, DIV,

    IDENT, NUMBER,

    LPAREN, RPAREN, COMMA, SEMI, DOT, ARROW, COLON, SLASH,
    SHL,  // <<  칩 호출 / 칩 선언 입력 목록
    SHR,  // >>  대입
    LT,   // <   IBUSRULE<n>의 여는 괄호
    GT,   // >   IBUSRULE<n>의 닫는 괄호

    EOF
}
