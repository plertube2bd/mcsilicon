package com.mcsilicon.mcsilicon.dsl;

import java.util.HashMap;
import java.util.Map;

/**
 * 인터프리터가 실행 중 다루는 값 하나. 둘 중 하나다:
 *  - 스칼라: long 하나 (BIT/INT/IBUSRULE&lt;n&gt; 공통 표현). scalarWidth가 폭을 나타낸다
 *    (BIT=1, IBUSRULE&lt;n&gt;=n, INT=0="폭 제한 없음"). 폭이 있는 값은 그 폭으로
 *    마스킹된 상태로 저장된다(선언된 이름에 쓰일 때마다 그 이름의 폭으로 다시 마스킹됨).
 *  - 번들: 이름 -> 스칼라 값들의 묶음. 세 종류가 있다(BundleKind):
 *      CHIP_INPUT  : 어떤 칩의 입력 묶음(&lt;칩&gt;._IRULE)
 *      CHIP_OUTPUT : 어떤 칩의 출력 묶음(&lt;칩&gt;._ORULE), 칩 호출( &lt;&lt; ) 결과도 이 종류
 *      GROUP       : GROUP 문으로 그 자리에서 필드를 직접 선언한 묶음 - 필드 이름/타입이
 *                    전부 그 문장 안에서 확정되므로, 필드마다 폭(width)을 기억해뒀다가
 *                    대입될 때마다 마스킹한다(칩 번들은 실제 칩 정의를 항상 알 수는
 *                    없어서 필드 폭을 마스킹하지 않는 것과 대비된다).
 */
public final class Value {
    public enum BundleKind { CHIP_INPUT, CHIP_OUTPUT, GROUP }

    private final boolean bundle;
    private final long scalar;
    private final int scalarWidth;      // 스칼라일 때만 의미 있음: 0=폭 제한 없음(INT), n=n비트
    private final Map<String, Long> fields;
    private final Map<String, Integer> fieldWidths; // GROUP일 때만 사용(필드별 폭), 그 외엔 null
    private final String tag;           // bundle일 때만 의미 있음: 칩 이름 또는 GROUP 이름
    private final BundleKind bundleKind;

    private Value(boolean bundle, long scalar, int scalarWidth,
                  Map<String, Long> fields, Map<String, Integer> fieldWidths,
                  String tag, BundleKind bundleKind) {
        this.bundle = bundle;
        this.scalar = scalar;
        this.scalarWidth = scalarWidth;
        this.fields = fields;
        this.fieldWidths = fieldWidths;
        this.tag = tag;
        this.bundleKind = bundleKind;
    }

    /** 폭 정보 없는 임시 계산값(리터럴, 연산 결과 등)을 만들 때 쓴다. */
    public static Value scalar(long v) {
        return new Value(false, v, 0, null, null, null, null);
    }

    /** 선언된 이름에 저장할 때 쓴다 - width에 맞춰 값을 마스킹한다. */
    public static Value scalarTyped(long v, int width) {
        return new Value(false, mask(v, width), width, null, null, null, null);
    }

    /** 칩의 _IRULE/_ORULE 번들(또는 칩 호출 결과). 필드 폭은 추적하지 않는다(마스킹 없음). */
    public static Value chipBundle(String chipName, boolean output, Map<String, Long> fields) {
        return new Value(true, 0L, 0, new HashMap<>(fields), null,
            chipName, output ? BundleKind.CHIP_OUTPUT : BundleKind.CHIP_INPUT);
    }

    /** GROUP 번들. 필드마다 폭을 기억해서 이후 대입에서도 마스킹한다. */
    public static Value group(String groupName, Map<String, Long> fields, Map<String, Integer> widths) {
        Map<String, Long> maskedFields = new HashMap<>();
        for (Map.Entry<String, Long> e : fields.entrySet()) {
            maskedFields.put(e.getKey(), mask(e.getValue(), widths.getOrDefault(e.getKey(), 0)));
        }
        return new Value(true, 0L, 0, maskedFields, new HashMap<>(widths), groupName, BundleKind.GROUP);
    }

    private static long mask(long v, int width) {
        if (width <= 0 || width >= 64) return v; // 0 = 폭 제한 없음(INT)
        long m = (1L << width) - 1;
        return v & m;
    }

    public boolean isBundle() { return bundle; }
    public String tag() { return tag; }
    public BundleKind bundleKind() { return bundleKind; }
    public int scalarWidth() { return scalarWidth; }

    public long asScalar() {
        if (bundle) {
            throw new DslException("스칼라 값이 필요한 자리에 번들(" + bundleDescription() + ") 값이 쓰였습니다");
        }
        return scalar;
    }

    public Map<String, Long> asFields() {
        if (!bundle) {
            throw new DslException("번들 값이 필요한 자리에 스칼라 값이 쓰였습니다");
        }
        return fields;
    }

    /** 필드 하나를 스칼라로 읽는다. 없는 필드면 0. */
    public long field(String name) {
        return asFields().getOrDefault(name, 0L);
    }

    /** 이 번들의 필드 하나를 바꾼 새 Value를 돌려준다(불변 값이라 복사해서 만든다).
     *  GROUP이면 그 필드의 선언된 폭으로 마스킹하고, 칩 번들이면 마스킹하지 않는다. */
    public Value withField(String name, long value) {
        Map<String, Long> copy = new HashMap<>(asFields());
        long v = value;
        if (fieldWidths != null && fieldWidths.containsKey(name)) {
            v = mask(value, fieldWidths.get(name));
        }
        copy.put(name, v);
        return new Value(true, 0L, 0, copy, fieldWidths, tag, bundleKind);
    }

    private String bundleDescription() {
        if (bundleKind == BundleKind.GROUP) return "GROUP " + tag;
        return tag + (bundleKind == BundleKind.CHIP_OUTPUT ? "._ORULE" : "._IRULE");
    }
}
