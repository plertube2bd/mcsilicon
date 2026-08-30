package com.mcsilicon.mcsilicon.block;

import com.mcsilicon.mcsilicon.registry.ModBlockEntities;
import com.mcsilicon.mcsilicon.signal.SignalNetworkManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 독자 디지털 신호선. 레드스톤 더스트와 달리 감쇠하지 않고,
 * 서로 인접한 와이어들이 하나의 WireNetwork를 이루어 동일한 값을 공유한다.
 */
public class WireBlock extends Block implements EntityBlock {

    public static final MapCodec<WireBlock> CODEC = simpleCodec(p -> new WireBlock());

    @Override
    protected MapCodec<WireBlock> codec() {
        return CODEC;
    }

    public WireBlock() {
        super(Properties.of().mapColor(MapColor.METAL).strength(0.3f).noOcclusion());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WireBlockEntity(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            SignalNetworkManager.get(level).markTopologyDirty(pos);
            SignalNetworkManager.get(level).remove(pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null; // 와이어는 자체 틱이 필요 없음 - SignalNetworkManager가 값을 밀어 넣는다
    }
}
