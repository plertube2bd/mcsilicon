package com.mcsilicon.mcsilicon.dsl.ast;

import java.util.List;

public abstract class Stmt {

    /** NEW <type> <name>; - 지역 변수 선언(초기화 값 없음, 기본값 0 또는 빈 번들). */
    public static final class NewLocalDecl extends Stmt {
        public final TypeRef type;
        public final String name;
        public NewLocalDecl(TypeRef type, String name) { this.type = type; this.name = name; }
    }

    /**
     * <expr>, <expr>, ... >> <target>, <target>, ... ;
     * 값 목록과 대상 목록은 개수가 같아야 하고, 순서대로 하나씩 짝지어 대입한다.
     * 실행 즉시 다음 문장에 반영된다(같은 틱, 지연 없음).
     */
    public static final class Assign extends Stmt {
        public final List<Expr> values;
        public final List<Target> targets;
        public Assign(List<Expr> values, List<Target> targets) { this.values = values; this.targets = targets; }
    }

    /** 대입 없이 표현식 하나만 실행하고 결과를 버리는 문장 (예: 칩 호출만 하고 끝). */
    public static final class ExprStmt extends Stmt {
        public final Expr expr;
        public ExprStmt(Expr expr) { this.expr = expr; }
    }

    /** GROUP <name> { NEW <스칼라타입> <필드이름> [초기값]; }* END - 그 자리에서 필드를 직접 선언하는 번들.
     *  칩의 _IRULE/_ORULE과 달리 필드 이름/타입이 전부 이 문장 안에서 확정되므로,
     *  NAME.FIELD 접근이 컴파일 타임에 완전히 검증되고, 필드 대입도 선언된 폭으로 마스킹된다. */
    public static final class GroupDecl extends Stmt {
        public final String name;
        public final java.util.List<GroupFieldDecl> fields;
        public GroupDecl(String name, java.util.List<GroupFieldDecl> fields) { this.name = name; this.fields = fields; }
    }

    /** RETURN; - 칩 실행을 즉시 종료한다(같은 틱 안에서, 지연 없음). */
    public static final class Return extends Stmt {}

    /** JMP <label>; - 지정한 LABEL 절로 실행 흐름을 옮긴다. */
    public static final class Jmp extends Stmt {
        public final String label;
        public Jmp(String label) { this.label = label; }
    }
}
