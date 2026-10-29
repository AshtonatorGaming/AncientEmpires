package com.ancientempires.entity.npc;

import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Archer sentry. Runs only while a bow is in the main hand and hold is on,
 * so a melee NPC never sticks to this post. Keeps the move flag so follow
 * and the stroll do not pull the archer off the spot.
 */
public class NpcHoldPositionGoal extends Goal
{
    private final NpcEntity npc;
    private final double speed;
    private int repath;

    public NpcHoldPositionGoal(NpcEntity npc, double speed)
    {
        this.npc = npc;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        return this.npc.getHold().archerHolding();
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.npc.getHold().archerHolding();
    }

    @Override
    public void start()
    {
        this.repath = 0;
    }

    @Override
    public void stop()
    {
        this.npc.getNavigation().stop();
    }

    @Override
    public void tick()
    {
        if (this.npc.getHold().anchor() == null)
        {
            this.npc.getHold().capture();
        }
        if (!this.npc.getHold().outside())
        {
            this.npc.getNavigation().stop();
            return;
        }
        if (--this.repath <= 0)
        {
            this.repath = this.adjustedTickDelay(10);
            this.npc.getHold().moveToward(this.speed);
        }
    }
}
