package com.ancientempires.registry;

import com.ancientempires.AncientEmpires;
import com.ancientempires.entity.npc.NpcRoster;
import com.ancientempires.entity.npc.NpcVariant;
import com.ancientempires.item.NpcSpawnEggItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ModItems
{
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AncientEmpires.MOD_ID);

    public static final RegistryObject<Item> NPC_SPAWN_EGG = egg("npc_spawn_egg", 0x3E4A3A, 0xC4B48A, null);
    public static final RegistryObject<Item> VIKING_SOLDIER_SPAWN_EGG = egg("viking_soldier_spawn_egg", 0x3A4A62, 0xC4B48A, NpcVariant.VIKING_SOLDIER);
    public static final RegistryObject<Item> EMPIRE_SOLDIER_SPAWN_EGG = egg("empire_soldier_spawn_egg", 0x7A1E1E, 0xD4C07A, NpcVariant.EMPIRE_SOLDIER);
    public static final RegistryObject<Item> BRIGAND_SPAWN_EGG = egg("brigand_spawn_egg", 0x3A2A22, 0x6A4A32, NpcVariant.BRIGAND);
    public static final RegistryObject<Item> ARCHER_SPAWN_EGG = egg("archer_spawn_egg", 0x2E4A2A, 0xC4A060, NpcVariant.ARCHER);
    public static final RegistryObject<Item> DWARF_SOLDIER_SPAWN_EGG = egg("dwarf_soldier_spawn_egg", 0x5A4030, 0xC0A060, NpcVariant.DWARF_SOLDIER);
    public static final RegistryObject<Item> GIANT_SPAWN_EGG = egg("giant_spawn_egg", 0x4A5A3A, 0x8A9A6A, NpcVariant.GIANT);
    public static final RegistryObject<Item> CIVILIAN_SPAWN_EGG = egg("civilian_spawn_egg", 0x6A5A48, 0xD8C8A8, NpcVariant.CIVILIAN);
    public static final RegistryObject<Item> LEADER_SPAWN_EGG = egg("leader_spawn_egg", 0x1E2A4A, 0xE0C060, NpcVariant.LEADER);

    public static final List<RegistryObject<Item>> ROSTER_EGGS;
    public static final List<RegistryObject<Item>> SPAWN_EGGS;

    static
    {
        List<RegistryObject<Item>> roster = new ArrayList<>();
        for (NpcRoster.Entry entry : NpcRoster.entries())
        {
            String rosterId = entry.id();
            roster.add(ITEMS.register(entry.itemId(), () -> new NpcSpawnEggItem(ModEntities.NPC, 0xFFFFFF, 0xFFFFFF, new Item.Properties(), null, rosterId)));
        }
        ROSTER_EGGS = List.copyOf(roster);
        List<RegistryObject<Item>> all = new ArrayList<>();
        all.add(NPC_SPAWN_EGG);
        all.add(VIKING_SOLDIER_SPAWN_EGG);
        all.add(EMPIRE_SOLDIER_SPAWN_EGG);
        all.add(BRIGAND_SPAWN_EGG);
        all.add(ARCHER_SPAWN_EGG);
        all.add(DWARF_SOLDIER_SPAWN_EGG);
        all.add(GIANT_SPAWN_EGG);
        all.add(CIVILIAN_SPAWN_EGG);
        all.add(LEADER_SPAWN_EGG);
        all.addAll(ROSTER_EGGS);
        SPAWN_EGGS = List.copyOf(all);
    }

    private ModItems()
    {
    }

    private static RegistryObject<Item> egg(String name, int background, int highlight, @Nullable NpcVariant variant)
    {
        return ITEMS.register(name, () -> new NpcSpawnEggItem(ModEntities.NPC, background, highlight, new Item.Properties(), variant, null));
    }
}
