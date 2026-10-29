package com.ancientempires.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import com.ancientempires.block.TownHallBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/**
 * Ancient Warfare's Upkeep Order: where an NPC fetches its food. Put in its
 * upkeep slot, the NPC takes its food from that place instead of the nearest
 * Town Hall: a Town Hall (its owner's), or any container, a chest most often.
 */
public class UpkeepOrderItem extends OrderItem
{
    public UpkeepOrderItem(Properties properties)
    {
        super(properties);
    }

    @Override
    protected int modes()
    {
        return 1;
    }

    @Override
    protected int maxStops()
    {
        return 1;
    }

    @Override
    protected Component modeName(int mode)
    {
        return Component.translatable("item.ancientempires.upkeep_order.mode");
    }

    @Override
    protected boolean accepts(Level level, BlockPos pos)
    {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof TownHallBlockEntity || entity != null && entity.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
    }
}
