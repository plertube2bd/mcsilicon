package com.mcsilicon.mcsilicon.dsl.ast;

/**
 * NEW 선언에 쓰이는 타입 표기.
 *  - 스칼라: BIT(1비트) / INT(폭 제한 없음) / IBUSRULE&lt;n&gt;(n비트 버스).
 *    셋 다 실행 시엔 long 하나로 표현되지만, BIT와 IBUSRULE&lt;n&gt;은 대입될 때마다
 *    선언된 폭(BIT는 1비트)으로 값이 잘린다(마스킹). INT는 폭이 강제되지 않는다
 *    ("0으로 초기화할 의무가 없다" = 값이 잘리지 않고, 초기값을 안 줘도 되고,
 *    다른 스칼라 타입처럼 폭을 보장하지 않는다는 뜻으로 정했다 - 실제로는 안 쓰인
 *    INT도 0으로 시작하지만, 이건 사양이 보장하는 값이 아니라 구현상의 편의다).
 *  - 번들: &lt;칩이름&gt;._IRULE(그 칩의 입력 묶음) 또는 &lt;칩이름&gt;._ORULE(그 칩의 출력 묶음).
 */
public final class TypeRef {
    public enum Kind { SCALAR, BUNDLE }

    public final Kind kind;
    public final Param.Type scalarType;   // kind == SCALAR일 때만 유효
    public final int scalarWidth;         // kind == SCALAR일 때만 유효 (BIT=1, INT=0, IBUSRULE<n>=n)
    public final String bundleChip;       // kind == BUNDLE일 때만 유효
    public final boolean bundleIsOutput;  // true = _ORULE, false = _IRULE (kind == BUNDLE일 때만 유효)

    private TypeRef(Kind kind, Param.Type scalarType, int scalarWidth, String bundleChip, boolean bundleIsOutput) {
        this.kind = kind;
        this.scalarType = scalarType;
        this.scalarWidth = scalarWidth;
        this.bundleChip = bundleChip;
        this.bundleIsOutput = bundleIsOutput;
    }

    public static TypeRef scalar(Param.Type t, int width) {
        return new TypeRef(Kind.SCALAR, t, width, null, false);
    }

    public static TypeRef bundle(String chipName, boolean isOutput) {
        return new TypeRef(Kind.BUNDLE, null, 0, chipName, isOutput);
    }

    @Override
    public String toString() {
        if (kind == Kind.BUNDLE) return bundleChip + (bundleIsOutput ? "._ORULE" : "._IRULE");
        return switch (scalarType) {
            case BIT -> "BIT";
            case INT -> "INT";
            case IBUSRULE -> "IBUSRULE<" + scalarWidth + ">";
        };
    }
}
