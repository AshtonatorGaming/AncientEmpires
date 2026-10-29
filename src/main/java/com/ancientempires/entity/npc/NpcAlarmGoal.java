package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Ancient Warfare's alarm response (NpcAIPlayerOwnedAlarmResponse): while its
 * owner's Town Hall sounds the alarm ({@link NpcEntity#alarmHall}), a civilian
 * runs to shelter (its home, or the hall: {@code upkeep.alarmShelter}), and a
 * soldier musters around the hall and stands, fighting what comes through its
 * usual scan. A fight, or following the owner, comes first. Later: an inner
 * sanctum (a keep) for great threats.
 */
final class NpcAlarmGoal extends Goal
{
    /** Blocks from the hall a civilian shelters within, and a soldier musters within. */
    private static final double SHELTER = 3.0D;
    private static final double MUSTER = 8.0D;
    /** Blocks from its home a civilian shelters within (home is a spot it stands on). */
    private static final double HOME_REACH = 1.5D;
    private static final int REPATH_TICKS = 20;

    private final NpcEntity npc;
    private int repath;

    NpcAlarmGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    private boolean answers()
    {
        return this.npc.alarmHall() != null && this.npc.getTarget() == null && !this.npc.isFollowing();
    }

    @Override
    public boolean canUse()
    {
        return this.answers();
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.answers();
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
        BlockPos hall = this.npc.alarmHall();
        if (hall == null)
        {
            return;
        }
        boolean soldier = this.npc.getNpcPath().combat() && !this.npc.avoidsFights();
        BlockPos home = this.npc.getHome();
        BlockPos shelter = !soldier && AncientEmpiresConfig.upkeepAlarmShelter == AncientEmpiresConfig.Shelter.HOME && home != null ? home : hall;
        double near = soldier ? MUSTER : shelter == home ? HOME_REACH : SHELTER;
        if (this.npc.distanceToSqr(shelter.getX() + 0.5D, this.npc.getY(), shelter.getZ() + 0.5D) <= near * near)
        {
            this.npc.getNavigation().stop();
            return;
        }
        if (--this.repath <= 0)
        {
            this.repath = REPATH_TICKS;
            // The hall is solid: the path ends near it. Civilians run.
            this.npc.getNavigation().moveTo(this.npc.getNavigation().createPath(shelter, Math.max(0, (int) near - 1)), this.npc.avoidsFights() ? 1.12D : 1.0D);
        }
    }
}
