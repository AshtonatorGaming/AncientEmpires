package com.ancientempires.registry;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.AnimalPenBlock;
import com.ancientempires.block.CropFarmBlock;
import com.ancientempires.block.FishingDockBlock;
import com.ancientempires.block.HuntingLodgeBlock;
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

    private ModBlocks()
    {
    }
}
