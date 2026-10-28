package com.ancientempires.registry;

import com.ancientempires.AncientEmpires;
import com.ancientempires.structure.AeLootItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Loot table parts: {@code ancientempires:item}, an entry for items that may be missing (AW's converted tables). */
public final class ModLoot
{
    public static final DeferredRegister<LootPoolEntryType> ENTRY_TYPES = DeferredRegister.create(Registries.LOOT_POOL_ENTRY_TYPE, AncientEmpires.MOD_ID);

    public static final RegistryObject<LootPoolEntryType> ITEM = ENTRY_TYPES.register("item", () -> new LootPoolEntryType(new AeLootItem.Serializer()));

    private ModLoot()
    {
    }
}
