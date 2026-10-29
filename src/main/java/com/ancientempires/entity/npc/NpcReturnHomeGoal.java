package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

/**
 * Ancient Warfare's NpcAIMoveHome and NpcAIFactionArcherStayAtHome. Out of
 * combat, an NPC that strays past its range walks back until it is inside its
 * leash, then wanders again near home (the home is a vanilla restriction, so
 * the stroll stays close). The numbers depend on the hour and the NPC:
 * <ul>
 * <li>by day, past {@code dayRange} it returns to {@code ownedDayLeash} or {@code factionDayLeash};</li>
 * <li>faction NPCs keep night hours: at night past {@code nightRange} they return to {@code nightLeash}.
 *     Owned soldiers do not, as in Ancient Warfare;</li>
 * <li>an NPC caught in the rain shelters at home (night numbers) until the rain stops;</li>
 * <li>a faction archer holds a post within {@code factionArcherPost} of home.</li>
 * </ul>
 * Follow, archer hold, a live target, or a home in another dimension turn it off.
 */
public class NpcReturnHomeGoal extends Goal
{
    private final NpcEntity npc;
    private final double speed;
    private int repath;
    /** Set when rain hits the NPC; cleared when the rain stops. */
    private boolean rainedOn;

    public NpcReturnHomeGoal(NpcEntity npc, double speed)
    {
        this.npc = npc;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        if (!this.eligible())
        {
            return false;
        }
        double distance = this.distanceToHomeSqr();
        double start = this.startRange();
        return distance > start * start;
    }

    @Override
    public boolean canContinueToUse()
    {
        if (!this.eligible())
        {
            return false;
        }
        double leash = this.leash();
        return this.distanceToHomeSqr() > leash * leash;
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
        if (--this.repath <= 0)
        {
            this.repath = this.adjustedTickDelay(10);
            this.npc.getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, this.speed);
        }
    }

    /**
     * Past this distance the walk home starts. An NPC that should be home (night
     * hours, rain, an archer's post) starts as soon as it is outside its leash.
     */
    private double startRange()
    {
        if (this.archerPost())
        {
            return AncientEmpiresConfig.factionArcherPost;
        }
        if (this.sheltering())
        {
            return AncientEmpiresConfig.homeNightLeash;
        }
        return this.nightHours() ? AncientEmpiresConfig.homeNightRange : AncientEmpiresConfig.homeDayRange;
    }

    /** Inside this distance the walk home ends. */
    private double leash()
    {
        if (this.archerPost())
        {
            return AncientEmpiresConfig.factionArcherPost;
        }
        if (this.nightHours() || this.sheltering())
        {
            return AncientEmpiresConfig.homeNightLeash;
        }
        return this.npc.isFactionNpc() ? AncientEmpiresConfig.factionDayLeash : AncientEmpiresConfig.ownedDayLeash;
    }

    private boolean nightHours()
    {
        return this.npc.isFactionNpc() && this.npc.level().isNight();
    }

    private boolean archerPost()
    {
        return AncientEmpiresConfig.factionArcherPost > 0.0D && this.npc.isFactionNpc() && this.npc.isHoldingBow();
    }

    /**
     * Ancient Warfare's rain shelter: once rained on, stay home until the rain
     * has stopped, not merely until the NPC is under a roof.
     */
    private boolean sheltering()
    {
        if (!AncientEmpiresConfig.homeRainShelter)
        {
            this.rainedOn = false;
            return false;
        }
        Level level = this.npc.level();
        if (!level.isRaining())
        {
            this.rainedOn = false;
            return false;
        }
        if (!this.rainedOn && level.isRainingAt(this.npc.blockPosition()))
        {
            this.rainedOn = true;
        }
        return this.rainedOn;
    }

    private boolean eligible()
    {
        if (this.npc.isFollowing() || this.npc.getHold().archerHolding() || this.npc.getHome() == null)
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

    private double distanceToHomeSqr()
    {
        BlockPos home = this.npc.getHome();
        return home == null ? 0.0D : this.npc.distanceToSqr(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D);
    }
}
