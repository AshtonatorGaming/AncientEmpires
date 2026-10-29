package com.ancientempires.registry;

import com.ancientempires.block.CraftingStationBlock;
import com.ancientempires.block.FruitFarmBlock;
import com.ancientempires.AncientEmpires;
import com.ancientempires.block.AnimalPenBlock;
import com.ancientempires.block.CoinStackBlock;
import com.ancientempires.block.CropFarmBlock;
import com.ancientempires.block.FlagBlock;
import com.ancientempires.block.FlagTopBlock;
import com.ancientempires.block.FishingDockBlock;
import com.ancientempires.block.HuntingLodgeBlock;
import com.ancientempires.block.KitchenBlock;
import com.ancientempires.block.NpcSpawnerBlock;
import com.ancientempires.block.QuarryBlock;
import com.ancientempires.block.SettlersPostBlock;
import com.ancientempires.block.TownHallBlock;
import com.ancientempires.block.TreeFarmBlock;
import com.ancientempires.block.decor.AwDecor;
import com.ancientempires.block.decor.AwDecorBlock;
import com.ancientempires.block.decor.AwSoundBlock;
import com.ancientempires.block.decor.CoffinBlock;
import com.ancientempires.block.decor.StatueBlock;
import com.ancientempires.gate.GateProxyBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every block this mod adds: the worksites (crop farm, tree farm, quarry, animal
 * pen, hunting lodge, fishing dock, kitchen), the Town Hall and Settlers' Post,
 * the NPC spawner, Ancient Warfare's coin stacks and flags, and the invisible
 * gate proxy a closed gate fills its opening with. Their items are in
 * {@link ModItems}, their block entities in {@link ModBlockEntities}.
 */
public final class ModBlocks
{
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AncientEmpires.MOD_ID);

    /** An admin block: spawns NPCs from the scrolls it holds (garrison, repeating, redstone). */
    public static final RegistryObject<Block> NPC_SPAWNER = BLOCKS.register("npc_spawner",
            () -> new NpcSpawnerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(5.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /** Ancient Warfare's coin stacks, by metal: Copper Owls, Silver Stags, Gold Dragons, Ancient Coins. */
    public static final RegistryObject<Block> COIN_STACK_COPPER = BLOCKS.register("coin_stack_copper", () -> coinStack(MapColor.COLOR_ORANGE));
    public static final RegistryObject<Block> COIN_STACK_SILVER = BLOCKS.register("coin_stack_silver", () -> coinStack(MapColor.METAL));
    public static final RegistryObject<Block> COIN_STACK_GOLD = BLOCKS.register("coin_stack_gold", () -> coinStack(MapColor.GOLD));
    public static final RegistryObject<Block> COIN_STACK_ANCIENT = BLOCKS.register("coin_stack_ancient", () -> coinStack(MapColor.TERRACOTTA_BROWN));

    private static Block coinStack(MapColor color)
    {
        return new CoinStackBlock(BlockBehaviour.Properties.of().mapColor(color).strength(0.5F).sound(SoundType.CHAIN).noOcclusion());
    }

    /** AW's faction flags: decorative, or a structure's protection flag. Drawn by FlagRenderer. */
    public static final RegistryObject<Block> FLAG = BLOCKS.register("flag", () -> new FlagBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion()));
    /** A flag's invisible upper part, so the whole banner can be clicked. */
    public static final RegistryObject<Block> FLAG_TOP = BLOCKS.register("flag_top", () -> new FlagTopBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion().noLootTable().pushReaction(PushReaction.DESTROY)));

    /** The invisible solid stand-in a closed gate fills its opening with; cannot be mined. */
    public static final RegistryObject<Block> GATE_PROXY = BLOCKS.register("gate_proxy", () -> new GateProxyBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.NONE).strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().isValidSpawn((state, level, pos, type) -> false)));

    public static final RegistryObject<Block> SETTLERS_POST = BLOCKS.register("settlers_post",
            () -> new SettlersPostBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final RegistryObject<Block> CROP_FARM = BLOCKS.register("crop_farm",
            () -> new CropFarmBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)));

    public static final RegistryObject<Block> FRUIT_FARM = BLOCKS.register("fruit_farm",
            () -> new FruitFarmBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)));

    public static final RegistryObject<Block> TREE_FARM = BLOCKS.register("tree_farm",
            () -> new TreeFarmBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)));

    public static final RegistryObject<Block> QUARRY = BLOCKS.register("quarry",
            () -> new QuarryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F)
                    .sound(SoundType.STONE)));

    public static final RegistryObject<Block> TOWN_HALL = BLOCKS.register("town_hall",
            () -> new TownHallBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(3.0F)
                    .sound(SoundType.WOOD)));

    public static final RegistryObject<Block> ANIMAL_PEN = BLOCKS.register("animal_pen",
            () -> new AnimalPenBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)));

    public static final RegistryObject<Block> HUNTING_LODGE = BLOCKS.register("hunting_lodge",
            () -> new HuntingLodgeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)));

    public static final RegistryObject<Block> FISHING_DOCK = BLOCKS.register("fishing_dock",
            () -> new FishingDockBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)));

    public static final RegistryObject<Block> CRAFTING_STATION = BLOCKS.register("crafting_station",
            () -> new CraftingStationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)));

    public static final RegistryObject<Block> KITCHEN = BLOCKS.register("kitchen",
            () -> new KitchenBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F)
                    .sound(SoundType.STONE)));

    /** AW's coffins: wooden (six woods) and stone sarcophagi (stone, sandstone, prismarine, demonic). */
    public static final RegistryObject<Block> WOODEN_COFFIN = BLOCKS.register("wooden_coffin", () -> new CoffinBlock(false,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Block> STONE_COFFIN = BLOCKS.register("stone_coffin", () -> new CoffinBlock(true,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5F, 6.0F).sound(SoundType.STONE).noOcclusion()));
    /** AW's statue: a mob standing frozen. */
    public static final RegistryObject<Block> STATUE = BLOCKS.register("statue", () -> new StatueBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F, 6.0F).sound(SoundType.STONE).noOcclusion()));
    /** AW's sound block, dressed as another block; admins place and set it. */
    public static final RegistryObject<Block> SOUND_BLOCK = BLOCKS.register("sound_block", () -> new AwSoundBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F, 6.0F).sound(SoundType.STONE)));

    /** AW's decor blocks by name (chair, gravestone, totem_part...), one generic block each ({@link AwDecor}). */
    public static final Map<String, RegistryObject<Block>> DECOR = decor();

    private static Map<String, RegistryObject<Block>> decor()
    {
        Map<String, RegistryObject<Block>> decor = new LinkedHashMap<>();
        for (AwDecor.Spec spec : AwDecor.specs().values())
        {
            decor.put(spec.name(), BLOCKS.register(spec.name(), () -> AwDecorBlock.create(spec, AwDecor.properties(spec.name()))));
        }
        return Collections.unmodifiableMap(decor);
    }

    private ModBlocks()
    {
    }
}
