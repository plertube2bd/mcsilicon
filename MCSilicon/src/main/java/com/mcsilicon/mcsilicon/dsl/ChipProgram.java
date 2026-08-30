package com.mcsilicon.mcsilicon.dsl;

import com.mcsilicon.mcsilicon.dsl.ast.ChipDef;
import com.mcsilicon.mcsilicon.dsl.ast.Param;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DSL(v3) 소스를 컴파일한 결과. 칩 블록 하나가 이 객체 하나를 들고 있으며,
 * 매 settle 사이클마다 execute()가 호출되어 (지연 명령이 없는 한) 같은 틱 안에서 완결된다.
 */
public final class ChipProgram {
    private final ChipDef def;

    private ChipProgram(ChipDef def) { this.def = def; }

    public static ChipProgram compile(String source) {
        Lexer lexer = new Lexer(source);
        Parser parser = new Parser(lexer.tokenize());
        ChipDef def = parser.parseChipDef();
        validate(def);
        TypeChecker.check(def);
        return new ChipProgram(def);
    }

    /**
     * RULE( ) 크로스 배치도는 4자리(front/left/right/back)이며, 각 자리는 파라미터
     * 이름이거나 빈 자리('.')일 수 있다.
     *
     * - RULE에 등장하는(=.'이 아닌) 모든 이름은 실제로 선언된 입력/출력 파라미터여야 한다.
     * - RULE 안에서 같은 이름을 두 번 쓸 수 없다.
     * - 반대로, 입력/출력 파라미터인데 RULE에는 등장하지 않는 이름이 있어도 오류가 아니다 -
     *   그 파라미터는 그냥 물리적으로 결선되지 않을 뿐이고(포트가 생기지 않음),
     *   USE CHIP으로 다른 칩이 함수처럼 호출할 때만 쓰인다("라이브러리 전용" 칩도 가능).
     * - 결과적으로 물리 포트 개수는 0~4개 사이 아무 값이나 가능하다.
     */
    private static void validate(ChipDef def) {
        Set<String> allParams = new HashSet<>();
        for (Param p : def.inputs) allParams.add(p.name);
        for (Param p : def.outputs) allParams.add(p.name);

        if (allParams.size() != def.inputs.size() + def.outputs.size()) {
            throw new DslException("입력/출력 파라미터 이름이 중복되었습니다");
        }

        List<String> boundList = def.layout.boundNames();
        Set<String> boundSet = new HashSet<>(boundList);
        if (boundSet.size() != boundList.size()) {
            throw new DslException("RULE 배치도 안에서 같은 이름을 두 번 이상 쓸 수 없습니다");
        }
        if (!allParams.containsAll(boundSet)) {
            Set<String> unknown = new HashSet<>(boundSet);
            unknown.removeAll(allParams);
            throw new DslException("RULE 배치도에 선언되지 않은 파라미터 이름이 있습니다: " + unknown);
        }

        if (def.isTable()) {
            for (var row : def.tableRows) {
                if (row.inputs.size() != def.inputs.size() || row.outputs.size() != def.outputs.size()) {
                    // Parser에서 이미 걸러지지만, 방어적으로 한 번 더 확인한다.
                    throw new DslException("TABLE 행의 입출력 개수가 칩 정의와 맞지 않습니다");
                }
            }
        }
    }

    public ChipDef def() { return def; }

    public ExecutionContext newContext() {
        ExecutionContext ctx = new ExecutionContext();
        for (Param p : def.inputs) ctx.set(p.name, 0L);
        for (Param p : def.outputs) ctx.set(p.name, 0L);
        return ctx;
    }

    /** 현재 입력값으로 한 번 실행하고, 출력값 중 하나라도 바뀌었으면 true를 반환한다. */
    public boolean execute(ExecutionContext ctx, ChipRegistry registry) {
        Map<String, Long> inputs = new HashMap<>();
        for (Param p : def.inputs) inputs.put(p.name, ctx.get(p.name));

        Map<String, Long> before = new HashMap<>();
        for (Param p : def.outputs) before.put(p.name, ctx.get(p.name));

        Map<String, Long> outputs = Interpreter.run(def, inputs, registry);

        boolean changed = false;
        for (Param p : def.outputs) {
            long v = outputs.getOrDefault(p.name, 0L);
            if (!before.get(p.name).equals(v)) changed = true;
            ctx.set(p.name, v);
        }
        return changed;
    }
}
