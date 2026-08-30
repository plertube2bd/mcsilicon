package com.mcsilicon.mcsilicon.block;

import com.mcsilicon.mcsilicon.registry.ModBlockEntities;
import com.mcsilicon.mcsilicon.signal.IWireConnectable;
import com.mcsilicon.mcsilicon.signal.SignalNetworkManager;
import com.mcsilicon.mcsilicon.signal.SignalValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class WireBlockEntity extends BlockEntity implements IWireConnectable {

    private SignalValue currentValue = SignalValue.LOW;
    private int width = 1;

    public WireBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIRE.get(), pos, state);
    }

    @Override
    public BlockPos wirePos() {
        return getBlockPos();
    }

    @Override
    public void onNetworkValueChanged(SignalValue value) {
        this.currentValue = value;
        // TODO: 클라이언트 렌더링 동기화(발광 텍스처 등)는 이후 구현
        setChanged();
    }

    @Override
    public int preferredWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = Math.max(1, width);
        setChanged();
    }

    public SignalValue currentValue() {
        return currentValue;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            SignalNetworkManager.get(level).markTopologyDirty(getBlockPos());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Width", width);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        width = Math.max(1, tag.getInt("Width"));
    }
}
