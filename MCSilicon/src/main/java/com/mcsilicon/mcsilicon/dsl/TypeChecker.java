package com.mcsilicon.mcsilicon.dsl;

import com.mcsilicon.mcsilicon.dsl.ast.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 칩 DSL의 컴파일 타임 타입 검사기. DO 바디를 위에서 아래로(JMP로 실제
 * 실행 순서가 바뀌는 것은 고려하지 않고, 글로 적힌 순서 그대로) 훑으면서
 * 다음을 검사한다:
 *
 *  - '>>' 의 대상이 선언된 이름인지(입력 파라미터/출력 파라미터/그 시점까지 나온
 *    NEW 선언/GROUP 선언)와, 입력 파라미터에 대입하려 하지 않는지.
 *  - NEW/GROUP으로 선언하는 이름이 이미 같은 칩 안에서 쓰인 적 없는지(대입 대상
 *    자리에 인라인으로 온 NEW도 마찬가지).
 *  - '>>' 좌우의 "종류"(스칼라 vs 어떤 칩의 _IRULE/_ORULE 번들 vs GROUP)가 서로 맞는지.
 *  - 칩 호출( << )과 NEW의 번들 타입이 USE CHIP으로 선언된 이름만 쓰는지.
 *  - GROUP 필드 접근(NAME.FIELD)이 실제로 그 GROUP에 선언된 필드인지 - GROUP은
 *    필드가 전부 같은 문장 안에서 확정되므로 이건 컴파일 타임에 완전히 검증된다
 *    (반대로 칩의 _IRULE/_ORULE 필드는 그 칩이 아직 컴파일 안 됐을 수 있어서
 *    컴파일 타임에 완전히 검증하지 못하고 실행 시점에 관대하게 처리된다).
 *  - JMP가 가리키는 라벨이 이 칩 안에 실제로 존재하는지.
 */
public final class TypeChecker {
    private TypeChecker() {}

    private enum Kind { SCALAR, BUNDLE, GROUP }

    private record SymType(Kind kind, String tag, boolean output, Set<String> groupFields) {
        static final SymType SCALAR = new SymType(Kind.SCALAR, null, false, null);

        static SymType bundle(String chip, boolean output) {
            return new SymType(Kind.BUNDLE, chip, output, null);
        }

        static SymType group(String name, Set<String> fields) {
            return new SymType(Kind.GROUP, name, false, fields);
        }

        boolean matches(SymType other) {
            if (kind != other.kind) return false;
            if (kind == Kind.SCALAR) return true;
            if (kind == Kind.BUNDLE) return tag.equals(other.tag) && output == other.output;
            return tag.equals(other.tag); // GROUP: 같은 이름의 GROUP 선언끼리만
        }

        @Override
        public String toString() {
            return switch (kind) {
                case SCALAR -> "스칼라(BIT/INT/IBUSRULE<n>)";
                case BUNDLE -> tag + (output ? "._ORULE" : "._IRULE");
                case GROUP -> "GROUP " + tag;
            };
        }
    }

    private record SymEntry(SymType type, boolean readOnly) {}

    public static void check(ChipDef def) {
        if (def.isTable() || def.isDeclarationOnly()) return; // 리터럴 표만 있거나 본문이 아예 없으면 검사할 게 없다

        Map<String, SymEntry> symbols = new HashMap<>();
        for (Param p : def.inputs) symbols.put(p.name, new SymEntry(SymType.SCALAR, true));
        for (Param p : def.outputs) symbols.put(p.name, new SymEntry(SymType.SCALAR, false));

        Set<String> labels = new HashSet<>();
        for (Clause c : def.clauses) {
            if (c instanceof Clause.Label l) labels.add(l.name);
        }

        for (Clause c : def.clauses) {
            List<Stmt> body = switch (c) {
                case Clause.Gate g -> g.body;
                case Clause.NotReturn nr -> nr.body;
                case Clause.Label l -> l.body;
                default -> List.of();
            };
            checkBody(body, symbols, labels, def);
        }
    }

    private static void checkBody(List<Stmt> body, Map<String, SymEntry> symbols, Set<String> labels, ChipDef def) {
        for (Stmt s : body) {
            if (s instanceof Stmt.NewLocalDecl decl) {
                if (symbols.containsKey(decl.name)) {
                    throw new DslException("이미 선언된 이름입니다: " + decl.name);
                }
                if (decl.type.kind == TypeRef.Kind.BUNDLE) {
                    requireUsable(decl.type.bundleChip, def);
                }
                SymType t = (decl.type.kind == TypeRef.Kind.SCALAR)
                    ? SymType.SCALAR
                    : SymType.bundle(decl.type.bundleChip, decl.type.bundleIsOutput);
                symbols.put(decl.name, new SymEntry(t, false));

            } else if (s instanceof Stmt.GroupDecl gd) {
                if (symbols.containsKey(gd.name)) {
                    throw new DslException("이미 선언된 이름입니다: " + gd.name);
                }
                Set<String> fieldNames = new HashSet<>();
                for (GroupFieldDecl f : gd.fields) {
                    if (!fieldNames.add(f.param.name)) {
                        throw new DslException("GROUP '" + gd.name + "' 안에 필드 '" + f.param.name + "'가 중복 선언되었습니다");
                    }
                    if (f.init != null) {
                        SymType initType = inferExpr(f.init, symbols, def);
                        if (initType.kind != Kind.SCALAR) {
                            throw new DslException("GROUP 필드 '" + f.param.name + "'의 초기값은 스칼라여야 합니다");
                        }
                    }
                }
                symbols.put(gd.name, new SymEntry(SymType.group(gd.name, fieldNames), false));

            } else if (s instanceof Stmt.Assign a) {
                for (int i = 0; i < a.values.size(); i++) {
                    SymType exprType = inferExpr(a.values.get(i), symbols, def);
                    SymType targetType = resolveTarget(a.targets.get(i), symbols, def);
                    if (!exprType.matches(targetType)) {
                        throw new DslException("타입이 일치하지 않습니다: " + describeExpr(a.values.get(i))
                            + "(" + exprType + ") -> " + a.targets.get(i) + "(" + targetType + ")");
                    }
                }

            } else if (s instanceof Stmt.ExprStmt es) {
                inferExpr(es.expr, symbols, def);

            } else if (s instanceof Stmt.Jmp j) {
                if (!labels.contains(j.label)) {
                    throw new DslException("존재하지 않는 라벨: " + j.label);
                }
            }
            // Stmt.Return: 검사할 것 없음
        }
    }

    private static void requireUsable(String chipName, ChipDef def) {
        if (!def.isUsable(chipName)) {
            throw new DslException("칩 '" + chipName + "'을(를) 쓰려면 먼저 USE CHIP " + chipName + "; 를 선언하세요");
        }
    }

    private static SymType resolveTarget(Target t, Map<String, SymEntry> symbols, ChipDef def) {
        if (t.isDeclaration()) {
            if (symbols.containsKey(t.base)) {
                throw new DslException("이미 선언된 이름입니다: " + t.base);
            }
            if (t.declareType.kind == TypeRef.Kind.BUNDLE) {
                requireUsable(t.declareType.bundleChip, def);
            }
            SymType type = (t.declareType.kind == TypeRef.Kind.SCALAR)
                ? SymType.SCALAR
                : SymType.bundle(t.declareType.bundleChip, t.declareType.bundleIsOutput);
            symbols.put(t.base, new SymEntry(type, false));
            return type;
        }
        SymEntry base = symbols.get(t.base);
        if (base == null) {
            throw new DslException("선언되지 않은 이름에 대입할 수 없습니다: " + t.base);
        }
        if (!t.isField()) {
            if (base.readOnly()) {
                throw new DslException("입력 파라미터에는 값을 대입할 수 없습니다: " + t.base);
            }
            return base.type();
        }
        if (base.type().kind == Kind.GROUP) {
            if (!base.type().groupFields.contains(t.field)) {
                throw new DslException("GROUP '" + t.base + "'에 필드 '" + t.field + "'가 없습니다");
            }
            return SymType.SCALAR;
        }
        if (base.type().kind != Kind.BUNDLE) {
            throw new DslException("필드 대입('" + t + "')은 _IRULE/_ORULE 또는 GROUP 타입 변수에만 할 수 있습니다: " + t.base);
        }
        return SymType.SCALAR;
    }

    private static SymType inferExpr(Expr e, Map<String, SymEntry> symbols, ChipDef def) {
        if (e instanceof Expr.NumberLiteral) return SymType.SCALAR;

        if (e instanceof Expr.Ident id) {
            SymEntry entry = symbols.get(id.name);
            if (entry == null) {
                throw new DslException("선언되지 않은 이름입니다: " + id.name);
            }
            return entry.type();
        }

        if (e instanceof Expr.PrefixOp op) {
            for (Expr arg : op.args) {
                SymType t = inferExpr(arg, symbols, def);
                if (t.kind != Kind.SCALAR) {
                    throw new DslException("연산자 '" + op.op + "'에는 스칼라 값만 올 수 있습니다");
                }
            }
            return SymType.SCALAR;
        }

        if (e instanceof Expr.ChipCall call) {
            requireUsable(call.chipName, def);
            if (call.args.size() == 1) {
                SymType t = inferExpr(call.args.get(0), symbols, def);
                boolean okScalar = t.kind == Kind.SCALAR;
                boolean okBundle = t.kind == Kind.BUNDLE && t.tag.equals(call.chipName) && !t.output;
                if (!okScalar && !okBundle) {
                    throw new DslException("칩 '" + call.chipName + "' 호출 인자가 올바르지 않습니다 "
                        + "(스칼라 값이거나 " + call.chipName + "._IRULE 타입이어야 합니다)");
                }
            } else {
                for (Expr arg : call.args) {
                    SymType t = inferExpr(arg, symbols, def);
                    if (t.kind != Kind.SCALAR) {
                        throw new DslException("칩 호출 인자는 스칼라 값이어야 합니다 "
                            + "(인자가 1개일 때만 " + call.chipName + "._IRULE 번들을 통째로 넘길 수 있습니다)");
                    }
                }
            }
            return SymType.bundle(call.chipName, true);
        }

        if (e instanceof Expr.FieldAccess fa) {
            SymType baseType = inferExpr(fa.base, symbols, def);
            if (baseType.kind == Kind.GROUP) {
                if (!baseType.groupFields.contains(fa.field)) {
                    String groupName = (fa.base instanceof Expr.Ident id) ? id.name : baseType.tag;
                    throw new DslException("GROUP '" + groupName + "'에 필드 '" + fa.field + "'가 없습니다");
                }
                return SymType.SCALAR;
            }
            if (baseType.kind != Kind.BUNDLE) {
                throw new DslException("필드 접근('.')은 칩 호출 결과, _IRULE/_ORULE 변수, 또는 GROUP 변수에만 사용할 수 있습니다");
            }
            return SymType.SCALAR;
        }

        throw new DslException("알 수 없는 표현식");
    }

    private static String describeExpr(Expr e) {
        if (e instanceof Expr.Ident id) return id.name;
        if (e instanceof Expr.NumberLiteral n) return String.valueOf(n.value);
        if (e instanceof Expr.ChipCall c) return c.chipName + " 호출";
        if (e instanceof Expr.FieldAccess f) return "필드 접근";
        if (e instanceof Expr.PrefixOp p) return p.op + " 연산";
        return "식";
    }
}
