package com.ancientempires.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import com.ancientempires.block.WorksiteBlockEntity;

/**
 * Ancient Warfare's Work Order: the worksites a worker works. Put in a
 * worker's orders slot, it works the first listed site of its trade (staying at
 * the one it was last at while that is listed) instead of its owner's nearest;
 * sites of other trades on the list are for other workers given a copy.
 */
public class WorkOrderItem extends OrderItem
{
    public WorkOrderItem(Properties properties)
    {
        super(properties);
    }

    @Override
    protected int modes()
    {
        return 1;
    }

    @Override
    protected Component modeName(int mode)
    {
        return Component.translatable("item.ancientempires.work_order.mode");
    }

    @Override
    protected boolean accepts(Level level, BlockPos pos)
    {
        return level.getBlockEntity(pos) instanceof WorksiteBlockEntity;
    }
}
