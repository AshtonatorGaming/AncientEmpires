package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Ancient Warfare's NpcAIFleeHostiles and NpcAIFactionPanic for civilians
 * ({@link NpcEntity#isCivilian()}). Every few ticks a civilian looks for a
 * visible threat within {@code civilians.fleeRange}: a hostile mob, an NPC that
 * would target it, or whatever it was about to fight (an attacker, an ally's
 * alert). It runs home if the danger is not at home, otherwise
 * {@code fleeDistance} blocks away from the threat, then stays away for
 * {@code stayAwayTicks}, less {@code soldierCalmTicks} for each allied soldier
 * close by. A threat still in sight sends it running again.
 */
public final class NpcFleeGoal extends Goal
{
    private static final int SCAN_INTERVAL = 4;

    private final NpcEntity npc;
    @Nullable
    private LivingEntity threat;
    private int stayAway;
    private int repath;

    public NpcFleeGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        if (!this.npc.isCivilian())
        {
            this.npc.takeFearSource();
            return false;
        }
        LivingEntity fear = this.npc.takeFearSource();
        if (fear != null && fear.isAlive() && this.npc.distanceToSqr(fear) <= this.rangeSqr())
        {
            this.threat = fear;
            return true;
        }
        // Goals are checked on alternate ticks, so a multiple of 2 keeps this to every 4.
        if ((this.npc.tickCount + this.npc.getId()) % SCAN_INTERVAL != 0)
        {
            return false;
        }
        this.threat = this.findThreat();
        return this.threat != null;
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.npc.isCivilian() && (this.stayAway > 0 || !this.npc.getNavigation().isDone());
    }

    @Override
    public void start()
    {
        this.stayAway = this.calmTicks();
        this.repath = 0;
        this.runFrom(this.threat);
    }

    @Override
    public void stop()
    {
        this.threat = null;
        this.stayAway = 0;
        this.npc.getNavigation().stop();
    }

    @Override
    public void tick()
    {
        if (this.stayAway > 0)
        {
            this.stayAway--;
        }
        if (--this.repath > 0)
        {
            return;
        }
        this.repath = SCAN_INTERVAL * 2;
        LivingEntity seen = this.findThreat();
        if (seen != null)
        {
            // Still in sight: keep running, and the fear starts over.
            this.threat = seen;
            this.stayAway = this.calmTicks();
            if (this.npc.getNavigation().isDone())
            {
                this.runFrom(seen);
            }
        }
    }

    private void runFrom(@Nullable LivingEntity danger)
    {
        if (danger == null)
        {
            return;
        }
        double speed = AncientEmpiresConfig.civilianFleeSpeed;
        BlockPos home = this.npc.getHome();
        if (home != null && this.homeIsSafe(home, danger))
        {
            this.npc.getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, speed);
            return;
        }
        int distance = Math.max(2, (int) AncientEmpiresConfig.civilianFleeDistance);
        Vec3 away = DefaultRandomPos.getPosAway(this.npc, distance, 7, danger.position());
        if (away != null)
        {
            this.npc.getNavigation().moveTo(away.x, away.y, away.z, speed);
        }
    }

    /** Home is a refuge unless the danger is already near it, or in another dimension. */
    private boolean homeIsSafe(BlockPos home, LivingEntity danger)
    {
        if (!this.npc.getHomeDimension().equals(this.npc.level().dimension().location().toString()))
        {
            return false;
        }
        double range = AncientEmpiresConfig.civilianFleeRange;
        return danger.distanceToSqr(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D) > range * range;
    }

    /**
     * The nearest visible threat in range: a monster, an NPC that would target
     * this one, or whoever last hurt it.
     */
    @Nullable
    private LivingEntity findThreat()
    {
        double range = AncientEmpiresConfig.civilianFleeRange;
        AABB area = this.npc.getBoundingBox().inflate(range, AncientEmpiresConfig.civilianFleeHeight, range);
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        LivingEntity attacker = this.npc.getLastHurtByMob();
        if (attacker != null && attacker.isAlive() && this.npc.distanceToSqr(attacker) <= range * range)
        {
            nearest = attacker;
            best = this.npc.distanceToSqr(attacker);
        }
        for (LivingEntity other : this.npc.level().getEntitiesOfClass(LivingEntity.class, area, this::frightening))
        {
            double distance = this.npc.distanceToSqr(other);
            if (distance < best && this.npc.getSensing().hasLineOfSight(other))
            {
                best = distance;
                nearest = other;
            }
        }
        return nearest;
    }

    private boolean frightening(LivingEntity other)
    {
        if (other == this.npc || !other.isAlive())
        {
            return false;
        }
        if (other instanceof Monster)
        {
            return true;
        }
        return other instanceof NpcEntity npc && !npc.isCivilian() && npc.isScanTarget(this.npc);
    }

    /** Ancient Warfare's fear level: nearby allied soldiers shorten the time spent hiding. */
    private int calmTicks()
    {
        double range = AncientEmpiresConfig.civilianFleeRange;
        int soldiers = this.npc.level().getEntitiesOfClass(NpcEntity.class, this.npc.getBoundingBox().inflate(range, 4.0D, range),
                other -> this.npc.isAlly(other) && !other.isCivilian()).size();
        return Math.max(AncientEmpiresConfig.civilianSoldierCalmTicks, AncientEmpiresConfig.civilianStayAwayTicks - soldiers * AncientEmpiresConfig.civilianSoldierCalmTicks);
    }

    private double rangeSqr()
    {
        double range = AncientEmpiresConfig.civilianFleeRange * 2.0D;
        return range * range;
    }
}
