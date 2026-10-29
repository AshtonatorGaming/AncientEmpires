package com.ancientempires.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * Ancient Warfare's Combat Order: a soldier's patrol. Put in a soldier's
 * orders slot (when no unit order holds it), it walks the points in turn, day
 * and night, fighting what it meets and going back to the round after; at a
 * "stand watch" point it waits a while before going on.
 */
public class CombatOrderItem extends OrderItem
{
    public CombatOrderItem(Properties properties)
    {
        super(properties);
    }

    /** What a soldier does at a point. */
    public enum Mode
    {
        PASS, WATCH;

        public static Mode of(int index)
        {
            return values()[Math.floorMod(index, values().length)];
        }
    }

    @Override
    protected int modes()
    {
        return Mode.values().length;
    }

    @Override
    protected Component modeName(int mode)
    {
        return Component.translatable(Mode.of(mode) == Mode.WATCH ? "item.ancientempires.combat_order.watch" : "item.ancientempires.combat_order.pass");
    }

    @Override
    protected boolean accepts(Level level, BlockPos pos)
    {
        return !level.getBlockState(pos).isAir();
    }
}
