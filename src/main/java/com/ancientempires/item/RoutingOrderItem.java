package com.ancientempires.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.Locale;

/**
 * Ancient Warfare's Routing Order: a courier's round of containers. A
 * Worker-path NPC holding it is a courier (NpcCourierGoal): it walks the stops
 * in order, again and again, moving items between its backpack and each
 * container as the stop says. AW's main modes; its ratio, exact, and minimum
 * modes may follow.
 */
public class RoutingOrderItem extends OrderItem
{
    /** What a courier does at a stop. */
    public enum Mode
    {
        DEPOSIT_ALL, WITHDRAW_ALL, DEPOSIT_ONLY, WITHDRAW_ONLY, DEPOSIT_EXCEPT, WITHDRAW_EXCEPT;

        public boolean deposits()
        {
            return this == DEPOSIT_ALL || this == DEPOSIT_ONLY || this == DEPOSIT_EXCEPT;
        }

        /** Whether this stop moves {@code stack}, given its filter. */
        public boolean moves(ItemStack stack, ItemStack filter)
        {
            return switch (this)
            {
                case DEPOSIT_ALL, WITHDRAW_ALL -> true;
                case DEPOSIT_ONLY, WITHDRAW_ONLY -> !filter.isEmpty() && ItemStack.isSameItem(stack, filter);
                case DEPOSIT_EXCEPT, WITHDRAW_EXCEPT -> filter.isEmpty() || !ItemStack.isSameItem(stack, filter);
            };
        }

        public static Mode of(int index)
        {
            Mode[] all = values();
            return all[Math.floorMod(index, all.length)];
        }
    }

    public RoutingOrderItem(Properties properties)
    {
        super(properties);
    }

    @Override
    protected int modes()
    {
        return Mode.values().length;
    }

    @Override
    protected Component modeName(int mode)
    {
        return Component.translatable("item.ancientempires.routing_order.mode." + Mode.of(mode).name().toLowerCase(Locale.ROOT));
    }

    @Override
    protected boolean accepts(Level level, BlockPos pos)
    {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity != null && entity.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
    }
}
