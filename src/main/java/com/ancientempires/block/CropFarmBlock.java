package com.ancientempires.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The crop farm worksite. Worker-path NPCs holding a hoe farm it by hand. */
public final class CropFarmBlock extends WorksiteBlock
{
    public CropFarmBlock(Properties properties)
    {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new CropFarmBlockEntity(pos, state);
    }
}
