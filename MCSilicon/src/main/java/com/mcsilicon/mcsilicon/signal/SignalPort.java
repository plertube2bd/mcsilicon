package com.mcsilicon.mcsilicon.signal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 칩 블록의 한 면(face)에 정의된 신호 접점.
 * DSL의 INPUT/OUTPUT 선언 하나가 SignalPort 하나에 대응한다.
 */
public final class SignalPort {
    private final BlockPos chipPos;
    private final Direction face;
    private final String name;
    private final int width;
    private final PortDirection direction;

    public SignalPort(BlockPos chipPos, Direction face, String name, int width, PortDirection direction) {
        this.chipPos = chipPos;
        this.face = face;
        this.name = name;
        this.width = width;
        this.direction = direction;
    }

    public BlockPos chipPos() { return chipPos; }
    public Direction face() { return face; }
    public String name() { return name; }
    public int width() { return width; }
    public PortDirection direction() { return direction; }

    /** 이 포트가 신호망과 연결되는 이웃 블록 좌표. */
    public BlockPos neighborPos() {
        return chipPos.relative(face);
    }

    @Override
    public String toString() {
        return "Port[" + name + "@" + chipPos + face + " w=" + width + " " + direction + "]";
    }
}
