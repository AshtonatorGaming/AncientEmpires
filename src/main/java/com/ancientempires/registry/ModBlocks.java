package com.ancientempires.registry;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.AnimalPenBlock;
import com.ancientempires.block.CoinStackBlock;
import com.ancientempires.block.CropFarmBlock;
import com.ancientempires.block.FishingDockBlock;
import com.ancientempires.block.HuntingLodgeBlock;
import com.ancientempires.block.KitchenBlock;
import com.ancientempires.block.NpcSpawnerBlock;
import com.ancientempires.block.QuarryBlock;
import com.ancientempires.block.SettlersPostBlock;
import com.ancientempires.block.TownHallBlock;
import com.ancientempires.block.TreeFarmBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

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

    public static final RegistryObject<Block> KITCHEN = BLOCKS.register("kitchen",
            () -> new KitchenBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F)
                    .sound(SoundType.STONE)));

    private ModBlocks()
    {
    }
}
