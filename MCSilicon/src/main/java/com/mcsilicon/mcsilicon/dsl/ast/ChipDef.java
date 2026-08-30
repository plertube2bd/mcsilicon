package com.mcsilicon.mcsilicon.dsl.ast;

import java.util.List;
import java.util.Map;

/**
 * NEW ... 전체를 컴파일한 칩 정의 하나.
 *
 * isGlobal이 true면(NEW GLOBAL ...) 이 칩은 "전역 공유" 칩이다: 같은 이름으로
 * 월드 여러 블록에 컴파일된 GLOBAL 칩이 여러 개 있어도, USE CHIP으로 호출할 때마다
 * 그중 하나를 무작위로 골라 그걸 호출한다(ChipRegistry 참고) - 여러 인스턴스 중
 * 어느 것이 "진짜"인지 따로 관리하지 않는다.
 *
 * 몸통(body)은 셋 중 하나다:
 *  - DO { 절(Clause)... } END          -> clauses에 절 목록이 들어있다.
 *  - TABLE ( 행... )                    -> tableRows/tableLookup에 진리표가 들어있다
 *                                          ("빠른 칩" - 절 해석 없이 O(1) 조회).
 *  - (본문 없음) RULE(...) END;          -> "선언만" 하는 칩. 실행하면 모든 출력이
 *                                          항상 0인 빈 구현으로 동작한다.
 */
public final class ChipDef {
    public enum BodyKind { CLAUSES, TABLE, NONE }

    public final String name;
    public final boolean isGlobal;
    public final List<Param> inputs;
    public final List<Param> outputs;
    public final List<UseRef> uses;      // USE CHIP <경로>;
    public final RuleLayout layout;

    public final BodyKind bodyKind;
    public final List<Clause> clauses;               // bodyKind == CLAUSES일 때만 사용
    public final List<TableRow> tableRows;            // bodyKind == TABLE일 때만 사용(원본 행, 오류 메시지용)
    public final Map<List<Long>, List<Long>> tableLookup; // bodyKind == TABLE일 때만 사용(실제 조회 테이블)

    public ChipDef(String name, boolean isGlobal, List<Param> inputs, List<Param> outputs,
                    List<UseRef> uses, RuleLayout layout,
                    List<Clause> clauses) {
        this(name, isGlobal, inputs, outputs, uses, layout, BodyKind.CLAUSES, clauses, List.of(), Map.of());
    }

    public ChipDef(String name, boolean isGlobal, List<Param> inputs, List<Param> outputs,
                    List<UseRef> uses, RuleLayout layout,
                    BodyKind bodyKind, List<Clause> clauses,
                    List<TableRow> tableRows, Map<List<Long>, List<Long>> tableLookup) {
        this.name = name;
        this.isGlobal = isGlobal;
        this.inputs = inputs;
        this.outputs = outputs;
        this.uses = uses;
        this.layout = layout;
        this.bodyKind = bodyKind;
        this.clauses = clauses;
        this.tableRows = tableRows;
        this.tableLookup = tableLookup;
    }

    public boolean isTable() { return bodyKind == BodyKind.TABLE; }
    public boolean isDeclarationOnly() { return bodyKind == BodyKind.NONE; }

    public boolean isInput(String name) {
        return inputs.stream().anyMatch(p -> p.name.equals(name));
    }

    public boolean isOutput(String name) {
        return outputs.stream().anyMatch(p -> p.name.equals(name));
    }

    /** alias(칩 안에서 << 로 쓰는 짧은 이름)로 USE 선언을 찾는다. 자기 자신도 항상 usable하다. */
    public UseRef findUse(String alias) {
        for (UseRef u : uses) if (u.alias.equals(alias)) return u;
        return null;
    }

    public boolean isUsable(String alias) {
        return alias.equals(name) || findUse(alias) != null;
    }

    /** alias를 ChipRegistry 조회용 실제 경로로 바꾼다(USE 선언 없이 자기 자신을 가리키면 그대로 name). */
    public String resolvePath(String alias) {
        UseRef u = findUse(alias);
        return (u != null) ? u.path : alias;
    }
}
