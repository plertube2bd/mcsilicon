package com.mcsilicon.mcsilicon.dsl;

import com.mcsilicon.mcsilicon.dsl.ast.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 칩 DSL(v3) 재귀 하강 파서. 문법은 Lexer/각 AST 클래스 주석 및 docs/LANGUAGE.md 참고.
 */
public final class Parser {
    private static final Set<TokenType> OP_KEYWORDS = Set.of(
        TokenType.ADD, TokenType.SUB, TokenType.MUL, TokenType.DIV,
        TokenType.AND, TokenType.OR, TokenType.XOR, TokenType.NAND,
        TokenType.NOR, TokenType.XNOR, TokenType.NOT
    );

    private static final Set<TokenType> GATE_TOKENS = Set.of(
        TokenType.AND, TokenType.OR, TokenType.XOR, TokenType.NAND,
        TokenType.NOR, TokenType.XNOR, TokenType.NOT
    );

    private final List<Token> tokens;
    private int pos = 0;

    public Parser(List<Token> tokens) { this.tokens = tokens; }

    public ChipDef parseChipDef() {
        consume(TokenType.NEW, "NEW가 필요합니다");
        boolean isGlobal = match(TokenType.GLOBAL);
        Token name = consume(TokenType.IDENT, "칩 이름이 필요합니다");

        consume(TokenType.SHL, "'<<'가 필요합니다 (입력 목록, 예: NEW HA << A, B -> SUM, CARRY)");
        List<Param> inputs = check(TokenType.ARROW) ? new ArrayList<>() : paramList();
        consume(TokenType.ARROW, "'->'가 필요합니다");
        List<Param> outputs = (check(TokenType.USE) || check(TokenType.RULE)) ? new ArrayList<>() : paramList();

        List<UseRef> uses = new ArrayList<>();
        while (check(TokenType.USE)) {
            advance();
            consume(TokenType.CHIP, "USE 뒤에는 CHIP이 필요합니다");
            uses.add(usePath());
            consume(TokenType.SEMI, "';'가 필요합니다");
        }

        consume(TokenType.RULE, "RULE이 필요합니다");
        consume(TokenType.LPAREN, "'('가 필요합니다");
        String front = layoutSlot("앞쪽(front)");
        String left = layoutSlot("왼쪽(left)");
        consume(TokenType.SELF, "RULE 배치도 가운데는 SELF여야 합니다");
        String right = layoutSlot("오른쪽(right)");
        String back = layoutSlot("뒤쪽(back)");
        consume(TokenType.RPAREN, "')'가 필요합니다");
        RuleLayout layout = new RuleLayout(front, left, right, back);

        if (match(TokenType.TABLE)) {
            List<TableRow> rows = tableBody(inputs.size(), outputs.size());
            Map<List<Long>, List<Long>> lookup = buildLookup(rows);
            return new ChipDef(name.text, isGlobal, inputs, outputs, uses, layout,
                ChipDef.BodyKind.TABLE, new ArrayList<>(), rows, lookup);
        }

        if (match(TokenType.DO)) {
            List<Clause> clauses = new ArrayList<>();
            while (!check(TokenType.END) && !check(TokenType.EOF)) {
                clauses.add(clause());
            }
            consume(TokenType.END, "칩 정의를 닫는 END가 필요합니다");
            return new ChipDef(name.text, isGlobal, inputs, outputs, uses, layout, clauses);
        }

        // 본문이 없다 - "칩 선언만"(구현 없음). 호출되면 항상 모든 출력이 0이다.
        consume(TokenType.END, "칩 선언을 닫는 END가 필요합니다");
        consume(TokenType.SEMI, "본문(DO/TABLE) 없는 칩 선언은 ';'로 끝나야 합니다 (예: RULE(...) END;)");
        return new ChipDef(name.text, isGlobal, inputs, outputs, uses, layout,
            ChipDef.BodyKind.NONE, new ArrayList<>(), new ArrayList<>(), Map.of());
    }

    /**
     * USE CHIP 뒤에 오는 경로: IDENT (( ':' | '/' ) IDENT)* - 예: HA, mcs:alice:HA, path/to/chip.
     * 마지막 조각이 alias(칩 안에서 << 로 쓰는 짧은 이름), 전체가 실제 조회 경로다.
     */
    private UseRef usePath() {
        Token first = consume(TokenType.IDENT, "사용할 칩 이름/경로가 필요합니다");
        StringBuilder path = new StringBuilder(first.text);
        String alias = first.text;
        while (check(TokenType.COLON) || check(TokenType.SLASH)) {
            String sep = advance().text;
            Token next = consume(TokenType.IDENT, "'" + sep + "' 뒤에 경로 조각이 필요합니다");
            path.append(sep).append(next.text);
            alias = next.text;
        }
        return new UseRef(alias, path.toString());
    }

    /** RULE 배치도의 한 자리: 파라미터 이름이거나, 빈 자리를 뜻하는 '.' (DOT 토큰). */
    private String layoutSlot(String label) {
        if (match(TokenType.DOT)) return null;
        Token t = consume(TokenType.IDENT, "RULE 배치도: " + label + " 이름 또는 '.'이 필요합니다");
        return t.text;
    }

    // ---------------- TABLE 본문 ----------------

    private List<TableRow> tableBody(int inputCount, int outputCount) {
        consume(TokenType.LPAREN, "'('가 필요합니다");
        List<TableRow> rows = new ArrayList<>();
        while (!check(TokenType.RPAREN) && !check(TokenType.EOF)) {
            rows.add(tableRow(inputCount, outputCount));
        }
        consume(TokenType.RPAREN, "')'가 필요합니다");
        return rows;
    }

    private TableRow tableRow(int inputCount, int outputCount) {
        List<Long> in = new ArrayList<>();
        in.add(consume(TokenType.NUMBER, "TABLE 행: 입력값이 필요합니다").number);
        while (match(TokenType.COMMA)) {
            in.add(consume(TokenType.NUMBER, "TABLE 행: 입력값이 필요합니다").number);
        }
        consume(TokenType.ARROW, "TABLE 행: '->'가 필요합니다");
        List<Long> out = new ArrayList<>();
        out.add(consume(TokenType.NUMBER, "TABLE 행: 출력값이 필요합니다").number);
        while (match(TokenType.COMMA)) {
            out.add(consume(TokenType.NUMBER, "TABLE 행: 출력값이 필요합니다").number);
        }
        consume(TokenType.SEMI, "';'가 필요합니다");

        if (in.size() != inputCount) {
            throw error("TABLE 행의 입력값 개수(" + in.size() + ")가 칩의 입력 파라미터 개수(" + inputCount + ")와 다릅니다");
        }
        if (out.size() != outputCount) {
            throw error("TABLE 행의 출력값 개수(" + out.size() + ")가 칩의 출력 파라미터 개수(" + outputCount + ")와 다릅니다");
        }
        return new TableRow(in, out);
    }

    private Map<List<Long>, List<Long>> buildLookup(List<TableRow> rows) {
        Map<List<Long>, List<Long>> lookup = new java.util.HashMap<>();
        for (TableRow row : rows) {
            if (lookup.containsKey(row.inputs)) {
                throw error("TABLE에 중복된 입력 조합이 있습니다: " + row.inputs);
            }
            lookup.put(row.inputs, row.outputs);
        }
        return lookup;
    }

    // ---------------- 파라미터 / 스칼라 타입 ----------------

    private List<Param> paramList() {
        List<Param> list = new ArrayList<>();
        list.add(param());
        while (match(TokenType.COMMA)) list.add(param());
        return list;
    }

    private Param param() {
        ScalarType st = tryScalarType();
        if (st == null) st = new ScalarType(Param.Type.BIT, 1); // 타입 생략 시 BIT
        Token name = consume(TokenType.IDENT, "파라미터 이름이 필요합니다");
        return new Param(name.text, st.type, st.width);
    }

    private record ScalarType(Param.Type type, int width) {}

    /** BIT / INT / IBUSRULE<n> 중 하나를 시도해서 파싱한다. 셋 다 아니면 null(호출자가 처리). */
    private ScalarType tryScalarType() {
        if (match(TokenType.BIT)) return new ScalarType(Param.Type.BIT, 1);
        if (match(TokenType.INT)) return new ScalarType(Param.Type.INT, 0);
        if (match(TokenType.IBUSRULE)) {
            consume(TokenType.LT, "'<'가 필요합니다 (예: IBUSRULE<4>)");
            Token n = consume(TokenType.NUMBER, "버스 폭(정수)이 필요합니다");
            consume(TokenType.GT, "'>'가 필요합니다");
            int w = (int) n.number;
            if (w < 1) throw error("버스 폭은 1 이상이어야 합니다");
            return new ScalarType(Param.Type.IBUSRULE, w);
        }
        return null;
    }

    // ---------------- 절(Clause) ----------------

    private Clause clause() {
        if (GATE_TOKENS.contains(peek().type)) return gateClause();
        if (check(TokenType.NOTRETURN)) return notReturnClause();
        if (check(TokenType.LABEL)) return labelClause();
        throw error("게이트 조건절, NOTRETURN, LABEL 중 하나가 필요합니다");
    }

    private Clause gateClause() {
        GateOp op = GateOp.valueOf(advance().type.name());
        List<String> args = new ArrayList<>();
        args.add(consume(TokenType.IDENT, "게이트 인자가 필요합니다").text);
        while (match(TokenType.COMMA)) {
            args.add(consume(TokenType.IDENT, "게이트 인자가 필요합니다").text);
        }
        consume(TokenType.THEN, "THEN이 필요합니다");
        List<Stmt> body = stmtsUntilEnd();
        consume(TokenType.END, "게이트 조건절을 닫는 END가 필요합니다");
        return new Clause.Gate(op, args, body);
    }

    private Clause notReturnClause() {
        consume(TokenType.NOTRETURN, "NOTRETURN 기대");
        consume(TokenType.THEN, "THEN이 필요합니다");
        List<Stmt> body = stmtsUntilEnd();
        consume(TokenType.END, "NOTRETURN 절을 닫는 END가 필요합니다");
        return new Clause.NotReturn(body);
    }

    private Clause labelClause() {
        consume(TokenType.LABEL, "LABEL 기대");
        Token name = consume(TokenType.IDENT, "라벨 이름이 필요합니다");
        List<Stmt> body = stmtsUntilEnd();
        consume(TokenType.END, "LABEL 절을 닫는 END가 필요합니다");
        return new Clause.Label(name.text, body);
    }

    private List<Stmt> stmtsUntilEnd() {
        List<Stmt> body = new ArrayList<>();
        while (!check(TokenType.END) && !check(TokenType.EOF)) {
            body.add(stmt());
        }
        return body;
    }

    // ---------------- 문장 ----------------

    private Stmt stmt() {
        if (match(TokenType.RETURN)) {
            consume(TokenType.SEMI, "';'가 필요합니다");
            return new Stmt.Return();
        }
        if (match(TokenType.JMP)) {
            Token label = consume(TokenType.IDENT, "JMP 대상 라벨이 필요합니다");
            consume(TokenType.SEMI, "';'가 필요합니다");
            return new Stmt.Jmp(label.text);
        }
        if (match(TokenType.GROUP)) {
            return groupDecl();
        }
        if (match(TokenType.NEW)) {
            // NEW <타입> <이름> [값] ;
            //  - 값이 없으면: 선언만(기본값 0 / 빈 번들)
            //  - 값이 있으면: "값 >> NEW <타입> <이름>;" 과 완전히 같은 뜻(선언 + 즉시 대입)
            TypeRef type = typeRef();
            Token name = consume(TokenType.IDENT, "변수 이름이 필요합니다");
            if (match(TokenType.SEMI)) {
                return new Stmt.NewLocalDecl(type, name.text);
            }
            Expr initExpr = expr();
            consume(TokenType.SEMI, "';'가 필요합니다");
            Target t = new Target(name.text, null, type);
            return new Stmt.Assign(List.of(initExpr), List.of(t));
        }

        // 나머지는 전부 "값 목록 [ >> 대상 목록 ] ;" 형태
        List<Expr> values = new ArrayList<>();
        values.add(expr());
        while (match(TokenType.COMMA)) values.add(expr());

        if (match(TokenType.SHR)) {
            List<Target> targets = new ArrayList<>();
            targets.add(target());
            while (match(TokenType.COMMA)) targets.add(target());
            consume(TokenType.SEMI, "';'가 필요합니다");
            if (values.size() != targets.size()) {
                throw error("'>>' 왼쪽 값 개수(" + values.size() + ")와 오른쪽 대상 개수("
                    + targets.size() + ")가 다릅니다");
            }
            return new Stmt.Assign(values, targets);
        }

        consume(TokenType.SEMI, "';'가 필요합니다");
        if (values.size() != 1) {
            throw error("여러 값을 쉼표로 나열했지만 '>>' 대상이 없습니다");
        }
        return new Stmt.ExprStmt(values.get(0));
    }

    /**
     * GROUP <이름> { NEW <스칼라타입> <필드이름> [초기값]; }* END
     * 필드는 항상 스칼라(BIT/INT/IBUSRULE<n>)이고, 이름/타입이 전부 여기서 확정된다.
     */
    private Stmt groupDecl() {
        Token name = consume(TokenType.IDENT, "GROUP 이름이 필요합니다");
        List<GroupFieldDecl> fields = new ArrayList<>();
        while (!check(TokenType.END) && !check(TokenType.EOF)) {
            consume(TokenType.NEW, "GROUP 안의 필드 선언은 NEW로 시작해야 합니다");
            ScalarType st = tryScalarType();
            if (st == null) throw error("GROUP 필드는 BIT, INT, IBUSRULE<n> 중 하나여야 합니다(칩 번들 불가)");
            Token fieldName = consume(TokenType.IDENT, "필드 이름이 필요합니다");
            Expr init = null;
            if (!check(TokenType.SEMI)) init = expr();
            consume(TokenType.SEMI, "';'가 필요합니다");
            fields.add(new GroupFieldDecl(new Param(fieldName.text, st.type(), st.width()), init));
        }
        consume(TokenType.END, "GROUP을 닫는 END가 필요합니다");
        return new Stmt.GroupDecl(name.text, fields);
    }

    /**
     * '>>' 오른쪽의 대상 하나:
     *  - IDENT               : 이미 선언된 이름
     *  - IDENT.IDENT          : 번들 변수의 필드
     *  - NEW <타입> IDENT     : 이 자리에서 곧바로 새로 선언하면서 대입("1, 2 >> NEW BIT X, NEW BIT Y;")
     */
    private Target target() {
        if (match(TokenType.NEW)) {
            TypeRef type = typeRef();
            Token name = consume(TokenType.IDENT, "선언할 변수 이름이 필요합니다");
            return new Target(name.text, null, type);
        }
        Token base = consume(TokenType.IDENT, "대입 대상 이름이 필요합니다");
        String field = null;
        if (match(TokenType.DOT)) {
            field = consume(TokenType.IDENT, "필드 이름이 필요합니다").text;
        }
        return new Target(base.text, field, null);
    }

    /** NEW 뒤에 오는 타입: BIT, INT, IBUSRULE<n>, 또는 <칩이름>._IRULE / <칩이름>._ORULE. */
    private TypeRef typeRef() {
        ScalarType st = tryScalarType();
        if (st != null) return TypeRef.scalar(st.type, st.width);

        Token chip = consume(TokenType.IDENT,
            "타입은 BIT, INT, IBUSRULE<n>, 또는 <칩이름>._IRULE / <칩이름>._ORULE 이어야 합니다");
        consume(TokenType.DOT, "'.'이 필요합니다 (예: " + chip.text + "._ORULE)");
        Token suffix = consume(TokenType.IDENT, "_IRULE 또는 _ORULE 이 필요합니다");

        boolean isOutput;
        if (suffix.text.equalsIgnoreCase("_ORULE")) isOutput = true;
        else if (suffix.text.equalsIgnoreCase("_IRULE")) isOutput = false;
        else throw error("타입 접미사는 _IRULE 또는 _ORULE 이어야 합니다 (실제: " + suffix.text + ")");

        return TypeRef.bundle(chip.text, isOutput);
    }

    // ---------------- 표현식 ----------------

    private Expr expr() {
        if (match(TokenType.LPAREN)) {
            if (OP_KEYWORDS.contains(peek().type)) {
                String op = advance().text.toUpperCase();
                List<Expr> args = new ArrayList<>();
                while (!check(TokenType.RPAREN)) args.add(expr());
                consume(TokenType.RPAREN, "')'가 필요합니다");
                return maybeFieldAccess(new Expr.PrefixOp(op, args));
            }
            Expr inner = expr();
            consume(TokenType.RPAREN, "')'가 필요합니다");
            return maybeFieldAccess(inner);
        }
        if (check(TokenType.NUMBER)) {
            return new Expr.NumberLiteral(advance().number);
        }
        if (check(TokenType.IDENT)) {
            Token id = advance();
            Expr result;
            if (match(TokenType.SHL)) {
                // 칩 호출: IDENT << arg, arg, ...  (쉼표는 이 인자 목록에 계속 붙는다 -
                // 다른 값과 한 목록에 함께 쓰려면 괄호로 묶어서 경계를 지어야 한다)
                List<Expr> args = new ArrayList<>();
                args.add(expr());
                while (match(TokenType.COMMA)) args.add(expr());
                result = new Expr.ChipCall(id.text, args);
            } else {
                result = new Expr.Ident(id.text);
            }
            return maybeFieldAccess(result);
        }
        throw error("표현식이 필요합니다");
    }

    private Expr maybeFieldAccess(Expr base) {
        Expr result = base;
        while (match(TokenType.DOT)) {
            Token field = consume(TokenType.IDENT, "필드 이름이 필요합니다");
            result = new Expr.FieldAccess(result, field.text);
        }
        return result;
    }

    // ---------------- 유틸 ----------------

    private Token peek() { return tokens.get(pos); }
    private boolean check(TokenType t) { return peek().type == t; }
    private Token advance() { return tokens.get(pos++); }
    private boolean match(TokenType t) { if (check(t)) { advance(); return true; } return false; }

    private Token consume(TokenType t, String msg) {
        if (check(t)) return advance();
        throw error(msg);
    }

    private DslException error(String msg) {
        Token cur = peek();
        return new DslException("DSL 파싱 오류(라인 " + cur.line + "): " + msg + " - 실제: " + cur);
    }
}
