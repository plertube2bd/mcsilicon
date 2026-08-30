package com.mcsilicon.mcsilicon.signal;

import java.util.Arrays;

/**
 * 독자 디지털 신호 값.
 *
 * width == 1 이면 단선(WIRE), width > 1 이면 IBUSRULE&lt;width&gt; 버스에 대응한다.
 * 값은 항상 확정된 0/1 비트 배열이며, 레드스톤의 0~15 세기 개념과는 무관하다.
 * 인덱스 0이 LSB(최하위 비트)이다.
 */
public final class SignalValue {

    public static final SignalValue LOW = new SignalValue(new boolean[]{false});
    public static final SignalValue HIGH = new SignalValue(new boolean[]{true});

    private final boolean[] bits;

    private SignalValue(boolean[] bits) {
        this.bits = bits;
    }

    public static SignalValue of(boolean bit) {
        return bit ? HIGH : LOW;
    }

    public static SignalValue zero(int width) {
        return new SignalValue(new boolean[Math.max(1, width)]);
    }

    public static SignalValue fromLong(long value, int width) {
        boolean[] b = new boolean[Math.max(1, width)];
        for (int i = 0; i < b.length; i++) {
            b[i] = ((value >>> i) & 1L) != 0;
        }
        return new SignalValue(b);
    }

    public static SignalValue fromBits(boolean[] bits) {
        return new SignalValue(Arrays.copyOf(bits, bits.length));
    }

    public int width() {
        return bits.length;
    }

    public boolean bit(int index) {
        if (index < 0 || index >= bits.length) return false;
        return bits[index];
    }

    /** 단일 비트(width==1) 신호를 boolean으로 취급할 때 사용. */
    public boolean asBoolean() {
        return bits[0];
    }

    public long asLong() {
        long v = 0;
        for (int i = 0; i < bits.length && i < 64; i++) {
            if (bits[i]) v |= (1L << i);
        }
        return v;
    }

    public SignalValue resized(int newWidth) {
        boolean[] nb = new boolean[Math.max(1, newWidth)];
        for (int i = 0; i < nb.length && i < bits.length; i++) nb[i] = bits[i];
        return new SignalValue(nb);
    }

    public SignalValue not() {
        boolean[] r = new boolean[bits.length];
        for (int i = 0; i < bits.length; i++) r[i] = !bits[i];
        return new SignalValue(r);
    }

    public SignalValue and(SignalValue other) {
        return combine(other, (a, b) -> a && b);
    }

    public SignalValue or(SignalValue other) {
        return combine(other, (a, b) -> a || b);
    }

    public SignalValue xor(SignalValue other) {
        return combine(other, (a, b) -> a ^ b);
    }

    private interface BoolOp { boolean apply(boolean a, boolean b); }

    private SignalValue combine(SignalValue other, BoolOp op) {
        int w = Math.max(this.bits.length, other.bits.length);
        boolean[] r = new boolean[w];
        for (int i = 0; i < w; i++) {
            r[i] = op.apply(i < bits.length && bits[i], i < other.bits.length && other.bits[i]);
        }
        return new SignalValue(r);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SignalValue other)) return false;
        return Arrays.equals(bits, other.bits);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bits);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = bits.length - 1; i >= 0; i--) sb.append(bits[i] ? '1' : '0');
        return sb.toString();
    }
}
