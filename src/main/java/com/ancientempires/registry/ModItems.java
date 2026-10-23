package com.ancientempires.registry;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.WorksiteBlockEntity;
import com.ancientempires.entity.npc.NpcRoster;
import com.ancientempires.item.BandageItem;
import com.ancientempires.item.NpcSpawnEggItem;
import com.ancientempires.item.WorksiteUpgradeItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
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

    /** Ancient Warfare's food bundle: settler crafting, and a hearty meal (several NPC rations) a player can eat too. */
    public static final RegistryObject<Item> FOOD_BUNDLE = ITEMS.register("food_bundle", () -> new Item(new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(10).saturationMod(0.8F).build())));
    public static final RegistryObject<Item> BANDAGE = ITEMS.register("bandage",
            () -> new BandageItem(new Item.Properties().stacksTo(16)));
    /** Ancient Warfare's worksite size upgrades: bounds for every worksite, quarry for the quarry. */
    public static final RegistryObject<Item> UPGRADE_SIZE_MEDIUM = ITEMS.register("worksite_upgrade_size_medium",
            () -> new WorksiteUpgradeItem(WorksiteBlockEntity.UpgradeKind.BOUNDS, 1, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> UPGRADE_SIZE_LARGE = ITEMS.register("worksite_upgrade_size_large",
            () -> new WorksiteUpgradeItem(WorksiteBlockEntity.UpgradeKind.BOUNDS, 2, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> UPGRADE_QUARRY_MEDIUM = ITEMS.register("worksite_upgrade_quarry_medium",
            () -> new WorksiteUpgradeItem(WorksiteBlockEntity.UpgradeKind.QUARRY, 1, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> UPGRADE_QUARRY_LARGE = ITEMS.register("worksite_upgrade_quarry_large",
            () -> new WorksiteUpgradeItem(WorksiteBlockEntity.UpgradeKind.QUARRY, 2, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> SETTLERS_POST =ITEMS.register("settlers_post",
            () -> new BlockItem(ModBlocks.SETTLERS_POST.get(), new Item.Properties()));
    public static final RegistryObject<Item> CROP_FARM = ITEMS.register("crop_farm",
            () -> new BlockItem(ModBlocks.CROP_FARM.get(), new Item.Properties()));
    public static final RegistryObject<Item> TREE_FARM = ITEMS.register("tree_farm",
            () -> new BlockItem(ModBlocks.TREE_FARM.get(), new Item.Properties()));
    public static final RegistryObject<Item> QUARRY = ITEMS.register("quarry",
            () -> new BlockItem(ModBlocks.QUARRY.get(), new Item.Properties()));
    public static final RegistryObject<Item> ANIMAL_PEN = ITEMS.register("animal_pen",
            () -> new BlockItem(ModBlocks.ANIMAL_PEN.get(), new Item.Properties()));
    public static final RegistryObject<Item> HUNTING_LODGE = ITEMS.register("hunting_lodge",
            () -> new BlockItem(ModBlocks.HUNTING_LODGE.get(), new Item.Properties()));
    public static final RegistryObject<Item> FISHING_DOCK = ITEMS.register("fishing_dock",
            () -> new BlockItem(ModBlocks.FISHING_DOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> KITCHEN = ITEMS.register("kitchen",
            () -> new BlockItem(ModBlocks.KITCHEN.get(), new Item.Properties()));
    public static final RegistryObject<Item> TOWN_HALL = ITEMS.register("town_hall",
            () -> new BlockItem(ModBlocks.TOWN_HALL.get(), new Item.Properties()));

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
