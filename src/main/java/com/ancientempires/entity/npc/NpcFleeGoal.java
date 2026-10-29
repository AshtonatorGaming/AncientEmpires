package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Ancient Warfare's NpcAIFleeHostiles and NpcAIFactionPanic for civilians
 * ({@link NpcEntity#avoidsFights()}), and for routing soldiers ({@link NpcMorale}). Every few ticks a civilian looks for a
 * visible threat within {@code civilians.fleeRange}: a hostile mob, an NPC that
 * would target it, or whatever it was about to fight (an attacker, an ally's
 * alert). It runs home if the danger is not at home, otherwise
 * {@code fleeDistance} blocks away from the threat, then stays away for
 * {@code stayAwayTicks}, less {@code soldierCalmTicks} for each allied soldier
 * close by. A threat still in sight sends it running again. A player is a
 * threat on sight only when the faction is hostile to them (standing or a
 * grudge); otherwise one that hits a civilian counts only once it is really
 * hurting it ({@link NpcEntity#civilianFears}).
 */
public final class NpcFleeGoal extends Goal
{
    private static final int SCAN_INTERVAL = 4;

    private final NpcEntity npc;
    @Nullable
    private LivingEntity threat;
    private int stayAway;
    private int repath;
    /** Ticks spent cornered with nowhere to run. */
    private int cornered;

    public NpcFleeGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        if (!this.npc.avoidsFights() || this.npc.getMorale().isSurrendered())
        {
            this.npc.takeFearSource();
            return false;
        }
        LivingEntity fear = this.npc.takeFearSource();
        if (fear != null && fear.isAlive() && this.npc.civilianFears(fear) && this.npc.distanceToSqr(fear) <= this.rangeSqr())
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
        return this.npc.avoidsFights() && (this.stayAway > 0 || !this.npc.getNavigation().isDone());
    }

    @Override
    public void start()
    {
        this.stayAway = this.calmTicks();
        this.repath = 0;
        this.cornered = 0;
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
        if (this.checkCornered())
        {
            return;
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
        Vec3 away = this.spotAwayFrom(danger, distance);
        if (away != null)
        {
            this.npc.getNavigation().moveTo(away.x, away.y, away.z, speed);
        }
    }

    /**
     * A spot about {@code distance} blocks away from the danger. Vanilla's helper
     * keeps a mob with a home restriction inside its home area once it is near
     * home, which leaves a civilian at home no spot to run to, so the
     * restriction is lifted for this one search.
     */
    @Nullable
    private Vec3 spotAwayFrom(LivingEntity danger, int distance)
    {
        if (!this.npc.hasRestriction())
        {
            return NpcSafety.posAway(this.npc, distance, 7, danger.position());
        }
        BlockPos center = this.npc.getRestrictCenter();
        int radius = (int) this.npc.getRestrictRadius();
        this.npc.clearRestriction();
        try
        {
            return NpcSafety.posAway(this.npc, distance, 7, danger.position());
        }
        finally
        {
            this.npc.restrictTo(center, radius);
        }
    }

    /**
     * Cornered: the threat is on top of the civilian and it is not getting away
     * (no path, or the path has run out). After {@code cornerTicks} of that it
     * stands its ground and fights for a while, or, routing and badly hurt, may surrender.
     */
    private boolean checkCornered()
    {
        LivingEntity danger = this.threat;
        double reach = AncientEmpiresConfig.civilianCornerRange;
        if (danger == null || !danger.isAlive() || this.npc.distanceToSqr(danger) > reach * reach)
        {
            this.cornered = 0;
            return false;
        }
        if (!this.npc.getNavigation().isDone())
        {
            this.cornered = Math.max(0, this.cornered - 1);
            return false;
        }
        // tick() runs every other tick, so each step counts two.
        this.cornered += 2;
        if (this.cornered < AncientEmpiresConfig.civilianCornerTicks)
        {
            this.runFrom(danger);
            return false;
        }
        this.npc.cornered(danger);
        return true;
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
        if (attacker != null && attacker.isAlive() && this.npc.civilianFears(attacker) && this.npc.distanceToSqr(attacker) <= range * range)
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
        if (other instanceof Player player)
        {
            // A player the faction is at odds with (standing below zero, or a fresh grudge).
            return this.npc.getRelations().hostileTo(player);
        }
        if (other instanceof Monster)
        {
            // Evil factions live alongside monsters; everyone else runs from them.
            return !this.npc.isFactionNpc() || !AncientEmpiresConfig.relationsEnabled || NpcFactions.hunts(this.npc.getFaction(), other);
        }
        return other instanceof NpcEntity npc && !npc.isCivilian() && !npc.getMorale().isBroken() && npc.getRelations().scanTarget(this.npc);
    }

    /** Ancient Warfare's fear level: nearby allied soldiers shorten the time spent hiding. */
    private int calmTicks()
    {
        double range = AncientEmpiresConfig.civilianFleeRange;
        int soldiers = this.npc.level().getEntitiesOfClass(NpcEntity.class, this.npc.getBoundingBox().inflate(range, 4.0D, range),
                other -> this.npc.getRelations().isAlly(other) && !other.isCivilian() && !other.getMorale().isBroken()).size();
        return Math.max(AncientEmpiresConfig.civilianSoldierCalmTicks, AncientEmpiresConfig.civilianStayAwayTicks - soldiers * AncientEmpiresConfig.civilianSoldierCalmTicks);
    }

    private double rangeSqr()
    {
        double range = AncientEmpiresConfig.civilianFleeRange * 2.0D;
        return range * range;
    }
}
