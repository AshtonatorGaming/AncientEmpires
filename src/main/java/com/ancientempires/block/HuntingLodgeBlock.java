package com.ancientempires.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The hunting lodge worksite. Worker-path NPCs holding a bow hunt its ground. */
public final class HuntingLodgeBlock extends WorksiteBlock
{
    public HuntingLodgeBlock(Properties properties)
    {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new HuntingLodgeBlockEntity(pos, state);
    }
}
