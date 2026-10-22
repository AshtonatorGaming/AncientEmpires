package com.ancientempires.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The tree farm worksite. Worker-path NPCs holding an axe fell, gather, and replant it by hand. */
public final class TreeFarmBlock extends WorksiteBlock
{
    public TreeFarmBlock(Properties properties)
    {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new TreeFarmBlockEntity(pos, state);
    }
}
