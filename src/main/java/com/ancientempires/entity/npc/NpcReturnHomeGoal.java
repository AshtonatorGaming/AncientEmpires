package com.ancientempires.entity.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * After a fight, a melee NPC walks back to its home. A bow stays on the archer
 * role and does not use this goal. Stays active once there so the stroll does
 * not wander off the spot.
 */
public class NpcReturnHomeGoal extends Goal
{
    private final NpcEntity npc;
    private final double speed;
    private final float stopDistance;
    private int repath;

    public NpcReturnHomeGoal(NpcEntity npc, double speed, float stopDistance)
    {
        this.npc = npc;
        this.speed = speed;
        this.stopDistance = stopDistance;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        return this.eligible();
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.eligible();
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
        BlockPos home = this.npc.getHome();
        if (home == null)
        {
            return;
        }
        double x = home.getX() + 0.5D;
        double y = home.getY();
        double z = home.getZ() + 0.5D;
        if (this.npc.distanceToSqr(x, y, z) <= (double) this.stopDistance * this.stopDistance)
        {
            this.npc.getNavigation().stop();
            return;
        }
        if (--this.repath <= 0)
        {
            this.repath = this.adjustedTickDelay(10);
            this.npc.getNavigation().moveTo(x, y, z, this.speed);
        }
    }

    private boolean eligible()
    {
        if (this.npc.isFollowing() || this.npc.isHoldingBow() || this.npc.getHome() == null)
        {
            return false;
        }
        LivingEntity target = this.npc.getTarget();
        if (target != null && target.isAlive())
        {
            return false;
        }
        String dimension = this.npc.getHomeDimension();
        return dimension.equals(this.npc.level().dimension().location().toString());
    }
}
