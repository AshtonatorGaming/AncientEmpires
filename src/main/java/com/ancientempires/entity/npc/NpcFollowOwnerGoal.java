package com.ancientempires.entity.npc;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Walks toward the owner while follow is on. Stays active when already close
 * so the stroll goal does not pull the NPC away.
 */
public class NpcFollowOwnerGoal extends Goal
{
    private final NpcEntity npc;
    private final double speed;
    private final float stopDistance;
    private Player owner;
    private int repath;

    public NpcFollowOwnerGoal(NpcEntity npc, double speed, float stopDistance)
    {
        this.npc = npc;
        this.speed = speed;
        this.stopDistance = stopDistance;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        this.owner = this.findOwner();
        return this.npc.isFollowing() && this.owner != null;
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.npc.isFollowing() && this.owner != null && this.owner.isAlive() && !this.owner.isSpectator();
    }

    @Override
    public void start()
    {
        this.repath = 0;
    }

    @Override
    public void stop()
    {
        this.owner = null;
        this.npc.getNavigation().stop();
    }

    @Override
    public void tick()
    {
        if (this.owner == null)
        {
            return;
        }
        this.npc.getLookControl().setLookAt(this.owner, 10.0F, this.npc.getMaxHeadXRot());
        if (this.npc.distanceToSqr(this.owner) <= (double) this.stopDistance * this.stopDistance)
        {
            this.npc.getNavigation().stop();
            return;
        }
        if (--this.repath <= 0)
        {
            this.repath = this.adjustedTickDelay(10);
            this.npc.getNavigation().moveTo(this.owner, this.speed);
        }
    }

    private Player findOwner()
    {
        if (this.npc.getOwner() == null)
        {
            return null;
        }
        Player player = this.npc.level().getPlayerByUUID(this.npc.getOwner());
        if (player == null || player.isSpectator())
        {
            return null;
        }
        return player;
    }
}
