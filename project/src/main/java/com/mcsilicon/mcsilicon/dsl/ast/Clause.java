package com.mcsilicon.mcsilicon.dsl.ast;

import java.util.List;

/** DO 블록을 이루는 최상위 절(clause) 하나. */
public abstract class Clause {

    /** <GATE> arg, arg, ... THEN ... END - 게이트 조건이 참이면 body를 실행. */
    public static final class Gate extends Clause {
        public final GateOp op;
        public final List<String> args;
        public final List<Stmt> body;
        public Gate(GateOp op, List<String> args, List<Stmt> body) { this.op = op; this.args = args; this.body = body; }
    }

    /** NOTRETURN THEN ... END - 여기까지 RETURN 없이 도달하면 항상 실행. */
    public static final class NotReturn extends Clause {
        public final List<Stmt> body;
        public NotReturn(List<Stmt> body) { this.body = body; }
    }

    /** LABEL <name> ... END - 이름 붙은 구간. 순서대로 도달해도 실행되고, JMP로도 진입 가능. */
    public static final class Label extends Clause {
        public final String name;
        public final List<Stmt> body;
        public Label(String name, List<Stmt> body) { this.name = name; this.body = body; }
    }
}
