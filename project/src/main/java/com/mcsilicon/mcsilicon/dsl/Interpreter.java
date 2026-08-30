package com.mcsilicon.mcsilicon.dsl;

import com.mcsilicon.mcsilicon.dsl.ast.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 칩 DSL(v3) 인터프리터. ChipDef 하나를 입력값과 함께 "한 번" 실행해서 출력값을 낸다.
 * USE CHIP으로 다른 칩을 호출하면(<<) 그 칩의 정의를 ChipRegistry에서 찾아
 * 새 변수 공간(COPY 의미)으로 재귀 실행한다.
 *
 * 칩의 입력/출력 파라미터(포트)는 항상 스칼라(Long)이므로, 외부에 보이는 계약
 * (run()의 입력/출력 타입)은 여전히 Map<String, Long>이다. 실행 도중 다루는
 * 지역 변수(vars)만 스칼라/번들을 모두 담을 수 있는 Value로 되어 있다.
 */
public final class Interpreter {
    private static final int MAX_DEPTH = 32;

    private Interpreter() {}

    public static Map<String, Long> run(ChipDef def, Map<String, Long> inputValues, ChipRegistry registry) {
        return run(def, inputValues, registry, 0);
    }

    private static Map<String, Long> run(ChipDef def, Map<String, Long> inputValues, ChipRegistry registry, int depth) {
        if (depth > MAX_DEPTH) {
            throw new DslException("칩 호출 재귀 깊이 초과(" + MAX_DEPTH + "): '" + def.name + "' - 순환 참조를 확인하세요");
        }

        if (def.isTable()) {
            return runTable(def, inputValues);
        }
        if (def.isDeclarationOnly()) {
            Map<String, Long> outputs = new HashMap<>();
            for (Param p : def.outputs) outputs.put(p.name, 0L);
            return outputs;
        }

        Map<String, Value> vars = new HashMap<>();
        for (Param p : def.inputs) vars.put(p.name, Value.scalarTyped(inputValues.getOrDefault(p.name, 0L), p.width));
        for (Param p : def.outputs) vars.put(p.name, Value.scalarTyped(0L, p.width));

        Map<String, Integer> labelIndex = new HashMap<>();
        for (int i = 0; i < def.clauses.size(); i++) {
            if (def.clauses.get(i) instanceof Clause.Label l) labelIndex.put(l.name, i);
        }

        int ip = 0;
        int guard = 0;
        while (ip < def.clauses.size()) {
            if (++guard > 10_000) {
                throw new DslException("칩 '" + def.name + "' 실행이 너무 오래 걸립니다 (JMP 무한루프 의심)");
            }
            Clause c = def.clauses.get(ip);
            List<Stmt> body;
            boolean shouldRun;

            if (c instanceof Clause.Gate g) {
                long[] argVals = new long[g.args.size()];
                for (int i = 0; i < g.args.size(); i++) {
                    Value v = vars.get(g.args.get(i));
                    argVals[i] = (v != null) ? v.asScalar() : 0L;
                }
                shouldRun = g.op.apply(argVals);
                body = g.body;
            } else if (c instanceof Clause.NotReturn nr) {
                shouldRun = true;
                body = nr.body;
            } else if (c instanceof Clause.Label l) {
                shouldRun = true;
                body = l.body;
            } else {
                shouldRun = false;
                body = List.of();
            }

            if (shouldRun) {
                ExecResult res = execBody(body, vars, def, registry, depth);
                if (res.returned) break;
                if (res.jumpTarget != null) {
                    Integer target = labelIndex.get(res.jumpTarget);
                    if (target == null) throw new DslException("존재하지 않는 라벨: " + res.jumpTarget);
                    ip = target;
                    continue;
                }
            }
            ip++;
        }

        Map<String, Long> outputs = new HashMap<>();
        for (Param p : def.outputs) {
            Value v = vars.get(p.name);
            outputs.put(p.name, (v != null) ? v.asScalar() : 0L);
        }
        return outputs;
    }

    /**
     * TABLE 기반 칩의 "빠른" 실행 경로. 절을 하나씩 해석하는 대신, 컴파일 시
     * 미리 만들어둔 조회 맵(ChipDef#tableLookup)에서 입력 조합을 O(1)로 찾아
     * 바로 출력을 돌려준다. 목록에 없는 입력 조합은 모든 출력을 0으로 취급한다.
     */
    private static Map<String, Long> runTable(ChipDef def, Map<String, Long> inputValues) {
        List<Long> key = new java.util.ArrayList<>(def.inputs.size());
        for (Param p : def.inputs) key.add(inputValues.getOrDefault(p.name, 0L));

        List<Long> row = def.tableLookup.get(key);

        Map<String, Long> outputs = new HashMap<>();
        for (int i = 0; i < def.outputs.size(); i++) {
            long v = (row != null) ? row.get(i) : 0L;
            outputs.put(def.outputs.get(i).name, v);
        }
        return outputs;
    }

    private static final class ExecResult {
        boolean returned;
        String jumpTarget;
    }

    private static ExecResult execBody(List<Stmt> body, Map<String, Value> vars, ChipDef def, ChipRegistry registry, int depth) {
        ExecResult r = new ExecResult();
        for (Stmt s : body) {
            if (s instanceof Stmt.Return) { r.returned = true; return r; }
            if (s instanceof Stmt.Jmp j) { r.jumpTarget = j.label; return r; }

            if (s instanceof Stmt.NewLocalDecl decl) {
                vars.put(decl.name, defaultValue(decl.type, registry));
            } else if (s instanceof Stmt.GroupDecl gd) {
                vars.put(gd.name, buildGroup(gd, vars, def, registry, depth));
            } else if (s instanceof Stmt.Assign a) {
                for (int i = 0; i < a.values.size(); i++) {
                    Value v = eval(a.values.get(i), vars, def, registry, depth);
                    applyTarget(a.targets.get(i), v, vars);
                }
            } else if (s instanceof Stmt.ExprStmt es) {
                eval(es.expr, vars, def, registry, depth); // 결과는 버림 - 부수효과(오류 검증 등) 확인용
            }
        }
        return r;
    }

    /** GROUP 문을 실행해서 필드가 채워진 Value.group을 만든다(초기값 있는 필드는 평가해서 넣는다). */
    private static Value buildGroup(Stmt.GroupDecl gd, Map<String, Value> vars, ChipDef def, ChipRegistry registry, int depth) {
        Map<String, Long> fields = new HashMap<>();
        Map<String, Integer> widths = new HashMap<>();
        for (GroupFieldDecl f : gd.fields) {
            widths.put(f.param.name, f.param.width);
            long v = 0L;
            if (f.init != null) {
                v = eval(f.init, vars, def, registry, depth).asScalar();
            }
            fields.put(f.param.name, v);
        }
        return Value.group(gd.name, fields, widths);
    }

    private static void applyTarget(Target t, Value v, Map<String, Value> vars) {
        if (t.isDeclaration()) {
            vars.put(t.base, coerceToDeclaredType(v, t.declareType));
            return;
        }
        if (!t.isField()) {
            Value existing = vars.get(t.base);
            if (existing != null && !existing.isBundle() && !v.isBundle()) {
                // 이미 선언된 스칼라 이름 - 그 이름의 폭을 유지하며 다시 마스킹한다.
                vars.put(t.base, Value.scalarTyped(v.asScalar(), existing.scalarWidth()));
            } else {
                vars.put(t.base, v);
            }
            return;
        }
        Value base = vars.get(t.base);
        if (base == null || !base.isBundle()) {
            throw new DslException("필드 대입 대상 '" + t.base + "'은(는) 번들(._IRULE/._ORULE/GROUP) 타입이 아닙니다");
        }
        long scalar = v.isBundle() ? 0L : v.asScalar();
        vars.put(t.base, base.withField(t.field, scalar));
    }

    /** 값 v를 선언 타입 type에 맞춰 넣을 Value로 바꾼다(스칼라면 그 폭으로 마스킹). */
    private static Value coerceToDeclaredType(Value v, TypeRef type) {
        if (type.kind == TypeRef.Kind.SCALAR) {
            return Value.scalarTyped(v.isBundle() ? 0L : v.asScalar(), type.scalarWidth);
        }
        return v; // 번들 - 그대로(타입 일치는 TypeChecker가 이미 컴파일 타임에 보장)
    }

    private static Value defaultValue(TypeRef type, ChipRegistry registry) {
        if (type.kind == TypeRef.Kind.SCALAR) {
            return Value.scalarTyped(0L, type.scalarWidth);
        }
        // 번들 기본값: 실제 칩 정의를 찾을 수 있으면 그 필드 이름들을 0으로 미리 채워둔다
        // (없어도 동작에는 지장 없음 - field()는 없는 키를 0으로 처리한다).
        Map<String, Long> fields = new HashMap<>();
        ChipDef target = registry.get(type.bundleChip);
        if (target != null) {
            List<Param> params = type.bundleIsOutput ? target.outputs : target.inputs;
            for (Param p : params) fields.put(p.name, 0L);
        }
        return Value.chipBundle(type.bundleChip, type.bundleIsOutput, fields);
    }

    private static Value eval(Expr e, Map<String, Value> vars, ChipDef def, ChipRegistry registry, int depth) {
        if (e instanceof Expr.NumberLiteral n) return Value.scalar(n.value);

        if (e instanceof Expr.Ident id) {
            Value v = vars.get(id.name);
            return (v != null) ? v : Value.scalar(0L);
        }

        if (e instanceof Expr.PrefixOp op) {
            long[] a = new long[op.args.size()];
            for (int i = 0; i < a.length; i++) a[i] = eval(op.args.get(i), vars, def, registry, depth).asScalar();
            return Value.scalar(applyOp(op.op, a));
        }

        if (e instanceof Expr.ChipCall call) {
            Map<String, Long> outs = evalChipCall(call, vars, def, registry, depth);
            return Value.chipBundle(call.chipName, true, outs);
        }

        if (e instanceof Expr.FieldAccess fa) {
            Value base = eval(fa.base, vars, def, registry, depth);
            if (!base.isBundle()) {
                throw new DslException("필드 접근('.')은 칩 호출 결과, _IRULE/_ORULE 변수, 또는 GROUP 변수에만 사용할 수 있습니다");
            }
            if (!base.asFields().containsKey(fa.field)) {
                throw new DslException("칩 '" + base.tag() + "'에 필드 '" + fa.field + "'가 없습니다");
            }
            return Value.scalar(base.field(fa.field));
        }

        throw new DslException("알 수 없는 표현식");
    }

    private static Map<String, Long> evalChipCall(Expr.ChipCall call, Map<String, Value> vars, ChipDef def, ChipRegistry registry, int depth) {
        if (!def.isUsable(call.chipName)) {
            throw new DslException("칩 '" + call.chipName + "'을(를) 쓰려면 먼저 USE CHIP " + call.chipName + "; 를 선언하세요");
        }
        String path = def.resolvePath(call.chipName);
        ChipDef target = registry.get(path);
        if (target == null) {
            throw new DslException("칩 '" + path + "'이(가) 존재하지 않습니다");
        }

        Map<String, Long> inputArgs = new HashMap<>();
        if (call.args.size() == 1) {
            Value v = eval(call.args.get(0), vars, def, registry, depth);
            if (v.isBundle()) {
                inputArgs.putAll(v.asFields()); // <칩>._IRULE 번들을 통째로 넘김("스프레드")
            } else if (!target.inputs.isEmpty()) {
                inputArgs.put(target.inputs.get(0).name, v.asScalar());
            }
        } else {
            for (int i = 0; i < target.inputs.size() && i < call.args.size(); i++) {
                Value v = eval(call.args.get(i), vars, def, registry, depth);
                inputArgs.put(target.inputs.get(i).name, v.isBundle() ? 0L : v.asScalar());
            }
        }
        return run(target, inputArgs, registry, depth + 1);
    }

    private static long applyOp(String op, long[] a) {
        return switch (op) {
            case "ADD" -> reduce(a, (x, y) -> x + y);
            case "SUB" -> reduce(a, (x, y) -> x - y);
            case "MUL" -> reduce(a, (x, y) -> x * y);
            case "DIV" -> reduce(a, (x, y) -> y == 0 ? 0 : x / y);
            case "AND" -> bool(GateOp.AND.apply(a));
            case "OR" -> bool(GateOp.OR.apply(a));
            case "XOR" -> bool(GateOp.XOR.apply(a));
            case "NAND" -> bool(GateOp.NAND.apply(a));
            case "NOR" -> bool(GateOp.NOR.apply(a));
            case "XNOR" -> bool(GateOp.XNOR.apply(a));
            case "NOT" -> bool(GateOp.NOT.apply(a));
            default -> throw new DslException("알 수 없는 연산자 " + op);
        };
    }

    private interface LongOp { long apply(long a, long b); }

    private static long reduce(long[] a, LongOp op) {
        if (a.length == 0) return 0;
        long acc = a[0];
        for (int i = 1; i < a.length; i++) acc = op.apply(acc, a[i]);
        return acc;
    }

    private static long bool(boolean b) { return b ? 1 : 0; }
}
