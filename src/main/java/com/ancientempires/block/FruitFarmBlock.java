package com.ancientempires.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The fruit farm worksite (AW's). Worker-path NPCs holding a hoe pick its fruit by hand. */
public final class FruitFarmBlock extends WorksiteBlock
{
    public FruitFarmBlock(Properties properties)
    {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new FruitFarmBlockEntity(pos, state);
    }
}
