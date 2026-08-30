package com.mcsilicon.mcsilicon.registry;

import com.mcsilicon.mcsilicon.MCSilicon;
import com.mcsilicon.mcsilicon.block.ChipBlockEntity;
import com.mcsilicon.mcsilicon.block.WireBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MCSilicon.MOD_ID);

    public static final RegistryObject<BlockEntityType<WireBlockEntity>> WIRE =
        BLOCK_ENTITIES.register("wire", () -> BlockEntityType.Builder.of(
            WireBlockEntity::new, ModBlocks.WIRE.get()).build(null));

    public static final RegistryObject<BlockEntityType<ChipBlockEntity>> CHIP =
        BLOCK_ENTITIES.register("chip", () -> BlockEntityType.Builder.of(
            ChipBlockEntity::new, ModBlocks.CHIP.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
