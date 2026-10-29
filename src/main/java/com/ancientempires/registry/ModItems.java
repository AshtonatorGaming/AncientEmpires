package com.ancientempires.registry;

import com.ancientempires.item.InstrumentItem;
import com.ancientempires.item.TradeOrderItem;
import com.ancientempires.AncientEmpires;
import com.ancientempires.block.WorksiteBlockEntity;
import com.ancientempires.entity.npc.NpcRank;
import com.ancientempires.entity.npc.NpcRoster;
import com.ancientempires.gate.GateSpawnerItem;
import com.ancientempires.gate.GateType;
import com.ancientempires.item.BandageItem;
import com.ancientempires.item.CaptureWandItem;
import com.ancientempires.item.CoinPurseItem;
import com.ancientempires.item.RoutingOrderItem;
import com.ancientempires.item.WorkOrderItem;
import com.ancientempires.item.UpkeepOrderItem;
import com.ancientempires.item.CombatOrderItem;
import com.ancientempires.item.FlagItem;
import com.ancientempires.item.InsigniaItem;
import com.ancientempires.item.MobScrollItem;
import com.ancientempires.item.NpcSpawnEggItem;
import com.ancientempires.item.ScannerItem;
import com.ancientempires.item.SignalFlagItem;
import com.ancientempires.item.WarHornItem;
import com.ancientempires.item.WorksiteUpgradeItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every item. Most are fixed (food bundle, bandages, worksite upgrades, the War
 * Horn, rank insignia, coins, the capture wand, one gate spawner per gate
 * type, block items); the NPC scrolls are not: one {@link NpcSpawnEggItem} is
 * registered per row of {@code roster.json} ({@link NpcRoster}), owned rows
 * first, so the roster decides which scrolls exist. {@link #eggFor} finds a row's scroll again for
 * repacking.
 */
public final class ModItems
{
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AncientEmpires.MOD_ID);

    /** Ancient Warfare's food bundle: settler crafting, and a hearty meal (several NPC rations) a player can eat too. */
    public static final RegistryObject<Item> FOOD_BUNDLE = ITEMS.register("food_bundle", () -> new Item(new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(10).saturationMod(0.8F).build())));
    public static final RegistryObject<Item> BANDAGE = ITEMS.register("bandage",
            () -> new BandageItem(new Item.Properties().stacksTo(16)));
    /** 32 bandages in one item, as durability: worn items never stack, and a stack of loose ones holds 16. */
    public static final RegistryObject<Item> BANDAGE_ROLL = ITEMS.register("bandage_roll",
            () -> new BandageItem(new Item.Properties().durability(32)));
    /** Ancient Warfare's worksite size upgrades: bounds for every worksite, quarry for the quarry. */
    public static final RegistryObject<Item> UPGRADE_SIZE_MEDIUM = ITEMS.register("worksite_upgrade_size_medium",
            () -> new WorksiteUpgradeItem(WorksiteBlockEntity.UpgradeKind.BOUNDS, 1, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> UPGRADE_SIZE_LARGE = ITEMS.register("worksite_upgrade_size_large",
            () -> new WorksiteUpgradeItem(WorksiteBlockEntity.UpgradeKind.BOUNDS, 2, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> UPGRADE_QUARRY_MEDIUM = ITEMS.register("worksite_upgrade_quarry_medium",
            () -> new WorksiteUpgradeItem(WorksiteBlockEntity.UpgradeKind.QUARRY, 1, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> UPGRADE_QUARRY_LARGE = ITEMS.register("worksite_upgrade_quarry_large",
            () -> new WorksiteUpgradeItem(WorksiteBlockEntity.UpgradeKind.QUARRY, 2, new Item.Properties().stacksTo(16)));
    /** Rallies, calls to arms, or stands down the owner's NPCs in range. */
    public static final RegistryObject<Item> WAR_HORN = ITEMS.register("war_horn",
            () -> new WarHornItem(new Item.Properties().stacksTo(1)));
    /** Rank insignia: used on a soldier of the level, promotes it (sergeant, captain, general). */
    public static final RegistryObject<Item> SERGEANT_INSIGNIA = ITEMS.register("sergeant_insignia",
            () -> new InsigniaItem(NpcRank.SERGEANT, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> CAPTAIN_INSIGNIA = ITEMS.register("captain_insignia",
            () -> new InsigniaItem(NpcRank.CAPTAIN, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> GENERAL_INSIGNIA = ITEMS.register("general_insignia",
            () -> new InsigniaItem(NpcRank.GENERAL, new Item.Properties().stacksTo(16)));
    /** Ancient Warfare's coins: loot and faction NPC drops for now; trade and wages come with the economy. */
    public static final RegistryObject<Item> COIN_COPPER = ITEMS.register("coin_copper", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> COIN_SILVER = ITEMS.register("coin_silver", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> COIN_GOLD = ITEMS.register("coin_gold", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> COIN_ANCIENT = ITEMS.register("coin_ancient", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ROUTING_ORDER = ITEMS.register("routing_order", () -> new RoutingOrderItem(new Item.Properties()));
    public static final RegistryObject<Item> WORK_ORDER = ITEMS.register("work_order", () -> new WorkOrderItem(new Item.Properties()));
    public static final RegistryObject<Item> UPKEEP_ORDER = ITEMS.register("upkeep_order", () -> new UpkeepOrderItem(new Item.Properties()));
    public static final RegistryObject<Item> COMBAT_ORDER = ITEMS.register("combat_order", () -> new CombatOrderItem(new Item.Properties()));
    public static final RegistryObject<Item> TRADE_ORDER = ITEMS.register("trade_order", () -> new TradeOrderItem(new Item.Properties()));
    public static final RegistryObject<Item> INSTRUMENT_LUTE = ITEMS.register("instrument_lute", () -> new InstrumentItem(InstrumentItem.Kind.LUTE, new Item.Properties()));
    public static final RegistryObject<Item> INSTRUMENT_FLUTE = ITEMS.register("instrument_flute", () -> new InstrumentItem(InstrumentItem.Kind.FLUTE, new Item.Properties()));
    public static final RegistryObject<Item> INSTRUMENT_HARP = ITEMS.register("instrument_harp", () -> new InstrumentItem(InstrumentItem.Kind.HARP, new Item.Properties()));
    public static final RegistryObject<Item> INSTRUMENT_DRUM = ITEMS.register("instrument_drum", () -> new InstrumentItem(InstrumentItem.Kind.DRUM, new Item.Properties()));
    public static final RegistryObject<Item> COIN_PURSE = ITEMS.register("coin_purse", () -> new CoinPurseItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> COIN_STACK_COPPER = ITEMS.register("coin_stack_copper", () -> new BlockItem(ModBlocks.COIN_STACK_COPPER.get(), new Item.Properties()));
    public static final RegistryObject<Item> COIN_STACK_SILVER = ITEMS.register("coin_stack_silver", () -> new BlockItem(ModBlocks.COIN_STACK_SILVER.get(), new Item.Properties()));
    public static final RegistryObject<Item> COIN_STACK_GOLD = ITEMS.register("coin_stack_gold", () -> new BlockItem(ModBlocks.COIN_STACK_GOLD.get(), new Item.Properties()));
    public static final RegistryObject<Item> COIN_STACK_ANCIENT = ITEMS.register("coin_stack_ancient", () -> new BlockItem(ModBlocks.COIN_STACK_ANCIENT.get(), new Item.Properties()));
    /** A captured mob for a spawner slot (anything but our NPCs, which use their own scrolls). */
    public static final RegistryObject<Item> MOB_SCROLL = ITEMS.register("mob_scroll", () -> new MobScrollItem(new Item.Properties()));
    /** AW's Mob Spawner Placement Tool: captures a mob, places or fills a garrison spawner. Admins only. */
    public static final RegistryObject<Item> STRUCTURE_SCANNER = ITEMS.register("structure_scanner", () -> new ScannerItem(new Item.Properties().stacksTo(1)));
    /** The chain of command's field tool: select a leader, assign soldiers, give the unit its order and formation. */
    public static final RegistryObject<Item> SIGNAL_FLAG = ITEMS.register("signal_flag", () -> new SignalFlagItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CAPTURE_WAND = ITEMS.register("capture_wand", () -> new CaptureWandItem(new Item.Properties().stacksTo(1)));
    /** AW's flags; the faction and kind ride in each stack's BlockEntityTag (one creative entry per faction). */
    public static final RegistryObject<FlagItem> FLAG = ITEMS.register("flag", () -> new FlagItem(ModBlocks.FLAG.get(), new Item.Properties()));
    /** AW's Gate Spawners, one per gate type, in GateType order. */
    public static final List<RegistryObject<Item>> GATE_SPAWNERS = gateSpawners();

    private static List<RegistryObject<Item>> gateSpawners()
    {
        List<RegistryObject<Item>> items = new ArrayList<>();
        for (GateType type : GateType.values())
        {
            items.add(ITEMS.register(type.itemId, () -> new GateSpawnerItem(type, new Item.Properties().stacksTo(16))));
        }
        return List.copyOf(items);
    }

    public static final RegistryObject<Item> NPC_SPAWNER = ITEMS.register("npc_spawner",
            () -> new BlockItem(ModBlocks.NPC_SPAWNER.get(), new Item.Properties()));
    public static final RegistryObject<Item> SETTLERS_POST = ITEMS.register("settlers_post",
            () -> new BlockItem(ModBlocks.SETTLERS_POST.get(), new Item.Properties()));
    public static final RegistryObject<Item> CROP_FARM = ITEMS.register("crop_farm",
            () -> new BlockItem(ModBlocks.CROP_FARM.get(), new Item.Properties()));
    public static final RegistryObject<Item> FRUIT_FARM = ITEMS.register("fruit_farm",
            () -> new BlockItem(ModBlocks.FRUIT_FARM.get(), new Item.Properties()));
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
    public static final RegistryObject<Item> CRAFTING_STATION = ITEMS.register("crafting_station",
            () -> new BlockItem(ModBlocks.CRAFTING_STATION.get(), new Item.Properties()));
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

    public static final RegistryObject<Item> WOODEN_COFFIN = ITEMS.register("wooden_coffin", () -> new BlockItem(ModBlocks.WOODEN_COFFIN.get(), new Item.Properties()));
    public static final RegistryObject<Item> STONE_COFFIN = ITEMS.register("stone_coffin", () -> new BlockItem(ModBlocks.STONE_COFFIN.get(), new Item.Properties()));
    public static final RegistryObject<Item> STATUE = ITEMS.register("statue", () -> new BlockItem(ModBlocks.STATUE.get(), new Item.Properties()));
    public static final RegistryObject<Item> SOUND_BLOCK = ITEMS.register("sound_block", () -> new BlockItem(ModBlocks.SOUND_BLOCK.get(), new Item.Properties()));

    /** The items of AW's decor blocks, by name; a wood or stone variant rides in the stack's BlockStateTag. */
    public static final Map<String, RegistryObject<Item>> DECOR = decor();

    private static Map<String, RegistryObject<Item>> decor()
    {
        Map<String, RegistryObject<Item>> decor = new LinkedHashMap<>();
        ModBlocks.DECOR.forEach((name, block) -> decor.put(name, ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()))));
        return Collections.unmodifiableMap(decor);
    }
}
