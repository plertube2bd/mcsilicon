package com.mcsilicon.mcsilicon.block;

import com.mcsilicon.mcsilicon.signal.SignalNetworkManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * 칩 블록. FACING(방향성)을 가지며, RULE( ) 배치도의 front/left/right/back은
 * 이 FACING을 기준으로 실제 월드 방향(front=FACING, back=반대, left/right=좌우)으로 변환된다.
 */
public class ChipBlock extends HorizontalDirectionalBlock implements EntityBlock {

    // 1.20.5+부터 블록마다 codec()을 직접 구현해야 한다(블록 상태 데이터 기반 직렬화용).
    // 이 블록은 속성이 고정이라 Properties 인자는 그냥 무시하고 항상 같은 생성자를 쓴다.
    public static final MapCodec<ChipBlock> CODEC = simpleCodec(p -> new ChipBlock());

    @Override
    protected MapCodec<ChipBlock> codec() {
        return CODEC;
    }

    public ChipBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0f).noOcclusion());
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChipBlockEntity(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ChipBlockEntity chip) {
                chip.unregisterFromChipRegistry();
            }
            SignalNetworkManager.get(level).remove(pos);
            for (Direction dir : Direction.values()) {
                SignalNetworkManager.get(level).markTopologyDirty(pos.relative(dir));
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null; // 실행은 SignalNetworkManager의 settle 루프가 IChipNode#tickExecute를 통해 수행
    }
}
