package com.ancientempires.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The animal pen worksite. Worker-path NPCs holding shears ranch it by hand. */
public final class AnimalPenBlock extends WorksiteBlock
{
    public AnimalPenBlock(Properties properties)
    {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new AnimalPenBlockEntity(pos, state);
    }
}
