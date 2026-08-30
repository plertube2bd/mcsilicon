package com.mcsilicon.mcsilicon.block;

import com.mcsilicon.mcsilicon.dsl.ChipProgram;
import com.mcsilicon.mcsilicon.dsl.ChipRegistry;
import com.mcsilicon.mcsilicon.dsl.DslException;
import com.mcsilicon.mcsilicon.dsl.ExecutionContext;
import com.mcsilicon.mcsilicon.dsl.ast.Param;
import com.mcsilicon.mcsilicon.registry.ModBlockEntities;
import com.mcsilicon.mcsilicon.signal.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class ChipBlockEntity extends BlockEntity implements IChipNode {

    private String source = defaultSource();
    private ChipProgram program;
    private ExecutionContext ctx;
    private String lastError = null;
    private String publishedName = null; // ChipRegistry에 현재 등록된 이름(있다면)
    private com.mcsilicon.mcsilicon.dsl.ast.ChipDef publishedDef = null; // 등록에 쓰인 정확한 인스턴스(GLOBAL 해제용)

    public ChipBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHIP.get(), pos, state);
        recompile();
    }

    private static String defaultSource() {
        return """
            // 기본 예시: 반가산기(Half Adder)
            NEW HA << A, B -> SUM, CARRY RULE (
                 SUM
              A SELF CARRY
                 B
            ) DO
                AND A, B THEN
                    0 >> SUM;
                    1 >> CARRY;
                    RETURN;
                END
                XOR A, B THEN
                    1 >> SUM;
                    0 >> CARRY;
                    RETURN;
                END
                NAND A, B THEN
                    0 >> SUM;
                    0 >> CARRY;
                    RETURN;
                END
                NOTRETURN THEN RETURN; END
            END
            """;
    }

    public String getSource() { return source; }
    public String getLastError() { return lastError; }

    /** 플레이어가 GUI 등을 통해 DSL 소스를 수정했을 때 호출. */
    public void setSource(String newSource) {
        unregisterFromChipRegistry();
        this.source = newSource;
        recompile();
        registerToChipRegistry();
        if (level != null && !level.isClientSide) {
            for (Direction dir : Direction.values()) {
                SignalNetworkManager.get(level).markTopologyDirty(getBlockPos().relative(dir));
            }
            SignalNetworkManager.get(level).markChipDirty(getBlockPos());
        }
        setChanged();
    }

    private void recompile() {
        try {
            program = ChipProgram.compile(source);
            ctx = program.newContext();
            lastError = null;
        } catch (DslException ex) {
            program = null;
            ctx = new ExecutionContext();
            lastError = ex.getMessage();
        }
    }

    private void registerToChipRegistry() {
        if (level == null || level.isClientSide || program == null) return;
        String name = program.def().name;
        ChipRegistry.get(level).publish(name, program.def());
        publishedName = name;
        publishedDef = program.def();
    }

    public void unregisterFromChipRegistry() {
        if (level == null || level.isClientSide || publishedName == null) return;
        ChipRegistry.get(level).unpublish(publishedName, publishedDef);
        publishedName = null;
        publishedDef = null;
    }

    // ---------------- IChipNode ----------------
    // RULE( ) 배치도의 front/left/right/back은 이 칩의 FACING을 기준으로
    // 실제 월드 방향(front=FACING, back=반대편, left/right=좌우)으로 변환된다.

    private Direction faceFor(String paramName) {
        if (program == null) return null;
        var layout = program.def().layout;
        Direction facing = getBlockState().getValue(ChipBlock.FACING);
        if (paramName.equals(layout.front)) return facing;
        if (paramName.equals(layout.back)) return facing.getOpposite();
        if (paramName.equals(layout.left)) return facing.getCounterClockWise();
        if (paramName.equals(layout.right)) return facing.getClockWise();
        return null;
    }

    @Override
    public List<SignalPort> ports() {
        List<SignalPort> result = new ArrayList<>();
        if (program == null) return result;
        for (Param p : program.def().inputs) {
            Direction face = faceFor(p.name);
            if (face != null) result.add(new SignalPort(getBlockPos(), face, p.name, 1, PortDirection.INPUT));
        }
        for (Param p : program.def().outputs) {
            Direction face = faceFor(p.name);
            if (face != null) result.add(new SignalPort(getBlockPos(), face, p.name, 1, PortDirection.OUTPUT));
        }
        return result;
    }

    @Override
    public void setInputValue(SignalPort port, SignalValue value) {
        if (ctx == null) return;
        ctx.set(port.name(), value.asBoolean() ? 1L : 0L);
    }

    @Override
    public SignalValue getOutputValue(SignalPort port) {
        if (ctx == null) return SignalValue.LOW;
        return SignalValue.of(ctx.get(port.name()) != 0);
    }

    @Override
    public boolean tickExecute() {
        if (program == null || level == null) return false;
        return program.execute(ctx, ChipRegistry.get(level));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            registerToChipRegistry();
            for (Direction dir : Direction.values()) {
                SignalNetworkManager.get(level).markTopologyDirty(getBlockPos().relative(dir));
            }
            SignalNetworkManager.get(level).markChipDirty(getBlockPos());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Source", source);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Source")) {
            source = tag.getString("Source");
        }
        recompile();
    }
}
