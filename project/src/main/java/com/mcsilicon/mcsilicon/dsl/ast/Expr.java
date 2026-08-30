package com.mcsilicon.mcsilicon.dsl.ast;

import java.util.List;

public abstract class Expr {

    public static final class NumberLiteral extends Expr {
        public final long value;
        public NumberLiteral(long value) { this.value = value; }
    }

    /** 단순 변수 참조: 입력/출력 파라미터 또는 LOCAL 변수. */
    public static final class Ident extends Expr {
        public final String name;
        public Ident(String name) { this.name = name; }
    }

    /** IDENT(arg, arg, ...) - 다른 칩을 호출(인스턴스화)한다. */
    public static final class ChipCall extends Expr {
        public final String chipName;
        public final List<Expr> args;
        public ChipCall(String chipName, List<Expr> args) { this.chipName = chipName; this.args = args; }
    }

    /** base.field - ChipCall 결과에서 출력 필드 하나를 읽는다. */
    public static final class FieldAccess extends Expr {
        public final Expr base;
        public final String field;
        public FieldAccess(Expr base, String field) { this.base = base; this.field = field; }
    }

    /** (OP expr expr ...) - 접두 표기 연산. ADD/SUB/MUL/DIV(산술) 또는 AND/OR/XOR/NAND/NOR/XNOR/NOT(논리). */
    public static final class PrefixOp extends Expr {
        public final String op;
        public final List<Expr> args;
        public PrefixOp(String op, List<Expr> args) { this.op = op; this.args = args; }
    }
}
