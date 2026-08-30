package com.mcsilicon.mcsilicon.signal;

import net.minecraft.core.BlockPos;

/** 와이어 신호망의 일부가 될 수 있는 블록 엔티티(WireBlockEntity)가 구현한다. */
public interface IWireConnectable {
    BlockPos wirePos();

    /** 이 와이어가 속한 신호망이 새 값을 확정했을 때 호출된다. */
    void onNetworkValueChanged(SignalValue value);

    int preferredWidth();
}
