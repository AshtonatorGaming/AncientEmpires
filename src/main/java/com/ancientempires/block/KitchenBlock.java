package com.ancientempires.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The kitchen worksite. Worker-path NPCs holding a bowl cook and pack food bundles at it. */
public final class KitchenBlock extends WorksiteBlock
{
    public KitchenBlock(Properties properties)
    {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new KitchenBlockEntity(pos, state);
    }
}
