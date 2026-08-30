package com.mcsilicon.mcsilicon.registry;

import com.mcsilicon.mcsilicon.MCSilicon;
import com.mcsilicon.mcsilicon.block.ChipBlock;
import com.mcsilicon.mcsilicon.block.WireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, MCSilicon.MOD_ID);

    public static final RegistryObject<Block> WIRE = BLOCKS.register("wire", WireBlock::new);
    public static final RegistryObject<Block> CHIP = BLOCKS.register("chip", ChipBlock::new);

    private ModBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
