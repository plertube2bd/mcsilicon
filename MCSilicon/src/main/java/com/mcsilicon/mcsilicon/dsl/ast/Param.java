package com.mcsilicon.mcsilicon.dsl.ast;

/** 칩의 입력/출력 파라미터. 타입 생략 시 BIT. */
public final class Param {
    public enum Type { BIT, INT, IBUSRULE }

    public final String name;
    public final Type type;
    public final int width; // BIT=1, INT=0(폭 제한 없음), IBUSRULE<n>=n

    public Param(String name, Type type, int width) {
        this.name = name;
        this.type = type;
        this.width = width;
    }
}
