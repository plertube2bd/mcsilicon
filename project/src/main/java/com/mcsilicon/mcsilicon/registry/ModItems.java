package com.mcsilicon.mcsilicon.registry;

import com.mcsilicon.mcsilicon.MCSilicon;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, MCSilicon.MOD_ID);

    public static final RegistryObject<Item> WIRE_ITEM = ITEMS.register("wire",
        () -> new BlockItem(ModBlocks.WIRE.get(), new Item.Properties()));

    public static final RegistryObject<Item> CHIP_ITEM = ITEMS.register("chip",
        () -> new BlockItem(ModBlocks.CHIP.get(), new Item.Properties()));

    private ModItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
