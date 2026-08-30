package com.mcsilicon.mcsilicon.dsl.ast;

/**
 * '>>' 대입의 대상 하나. 세 가지 형태가 있다:
 *  - 이미 선언된 이름:            base="X", field=null, declareType=null
 *  - 번들 변수의 필드:            base="K", field="A",  declareType=null
 *  - 이 자리에서 곧바로 새로 선언: base="X", field=null, declareType=<타입> (NEW <타입> X 를 대입 대상 자리에 인라인으로 쓴 것)
 */
public final class Target {
    public final String base;
    public final String field;         // null이면 필드 없는 단순 대상
    public final TypeRef declareType;  // null이 아니면 이 대입이 base를 새로 선언한다("NEW"가 대상 자리에 인라인으로 옴)

    public Target(String base, String field, TypeRef declareType) {
        this.base = base;
        this.field = field;
        this.declareType = declareType;
    }

    public boolean isField() { return field != null; }
    public boolean isDeclaration() { return declareType != null; }

    @Override
    public String toString() {
        if (isDeclaration()) return "NEW " + declareType + " " + base;
        return field == null ? base : base + "." + field;
    }
}
