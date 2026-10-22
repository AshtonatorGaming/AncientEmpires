package com.ancientempires.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The fishing dock worksite. Worker-path NPCs holding a fishing rod fish its water by hand. */
public final class FishingDockBlock extends WorksiteBlock
{
    public FishingDockBlock(Properties properties)
    {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new FishingDockBlockEntity(pos, state);
    }
}
