package com.mcsilicon.mcsilicon.signal;

import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * 서로 인접하여 하나로 이어진 와이어 블록들의 집합.
 * 레드스톤 더스트처럼 세기가 감쇠하지 않으며, 망 전체가 항상 동일한 SignalValue를 공유한다.
 */
public final class WireNetwork {
    private final Set<BlockPos> wirePositions = new HashSet<>();
    private final Set<SignalPort> drivers = new HashSet<>();   // OUTPUT 포트들
    private final Set<SignalPort> consumers = new HashSet<>(); // INPUT 포트들

    private SignalValue value = SignalValue.LOW;
    private int width = 1;

    public Set<BlockPos> wirePositions() { return wirePositions; }
    public Set<SignalPort> drivers() { return drivers; }
    public Set<SignalPort> consumers() { return consumers; }

    public SignalValue value() { return value; }

    public int width() { return width; }

    public void setWidth(int width) {
        this.width = Math.max(this.width, width);
        if (value.width() < this.width) value = value.resized(this.width);
    }

    public void setValue(SignalValue newValue) {
        this.value = newValue;
    }
}
