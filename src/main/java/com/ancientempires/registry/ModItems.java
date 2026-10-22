package com.ancientempires.registry;

import com.ancientempires.AncientEmpires;
import com.ancientempires.entity.npc.NpcRoster;
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

    /** Ancient Warfare's food bundle: a crafting component for new settlers, later NPC upkeep. */
    public static final RegistryObject<Item> FOOD_BUNDLE = ITEMS.register("food_bundle", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SETTLERS_POST = ITEMS.register("settlers_post",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.SETTLERS_POST.get(), new Item.Properties()));
    public static final RegistryObject<Item> CROP_FARM = ITEMS.register("crop_farm",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.CROP_FARM.get(), new Item.Properties()));

    public static final List<RegistryObject<Item>> OWNED_EGGS;
    public static final List<RegistryObject<Item>> ROSTER_EGGS;
    public static final List<RegistryObject<Item>> SPAWN_EGGS;

    static
    {
        List<RegistryObject<Item>> owned = new ArrayList<>();
        List<RegistryObject<Item>> roster = new ArrayList<>();
        for (NpcRoster.Entry entry : NpcRoster.entries())
        {
            String rosterId = entry.id();
            RegistryObject<Item> egg = ITEMS.register(entry.itemId(), () -> new NpcSpawnEggItem(ModEntities.NPC, 0xFFFFFF, 0xFFFFFF, new Item.Properties(), rosterId));
            if (entry.owned())
            {
                owned.add(egg);
            }
            else
            {
                roster.add(egg);
            }
        }
        OWNED_EGGS = List.copyOf(owned);
        ROSTER_EGGS = List.copyOf(roster);
        List<RegistryObject<Item>> all = new ArrayList<>();
        all.addAll(OWNED_EGGS);
        all.addAll(ROSTER_EGGS);
        SPAWN_EGGS = List.copyOf(all);
    }

    private ModItems()
    {
    }

    /**
     * The spawn egg for a roster row, or null. Repack uses this so an NPC goes
     * back into the scroll it came from. A short walk; repack is a rare click.
     */
    @Nullable
    public static Item eggFor(String rosterId)
    {
        if (rosterId == null || rosterId.isEmpty())
        {
            return null;
        }
        for (RegistryObject<Item> egg : SPAWN_EGGS)
        {
            if (egg.get() instanceof NpcSpawnEggItem item && rosterId.equals(item.rosterId()))
            {
                return item;
            }
        }
        return null;
    }
}
