package com.ancientempires.registry;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.SettlersPostBlock;
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

    private ModBlocks()
    {
    }
}
