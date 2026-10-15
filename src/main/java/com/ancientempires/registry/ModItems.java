package com.ancientempires.registry;

import com.ancientempires.AncientEmpires;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems
{
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AncientEmpires.MOD_ID);

    public static final RegistryObject<Item> NPC_SPAWN_EGG = ITEMS.register("npc_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.NPC, 0x3E4A3A, 0xC4B48A, new Item.Properties()));

    private ModItems()
    {
    }
}
