package com.ancientempires.entity.npc;

import com.ancientempires.combat.NpcMeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.List;

/**
 * What an NPC is doing, at a glance, shown as Ancient Warfare's task icon
 * above its head. Worked out on the server from its state and running goals
 * every {@link #UPDATE_TICKS}, and synced as one byte.
 */
public enum NpcStatus
{
    NONE(null),
    SLEEP("sleep"),
    FLEE("flee"),
    ATTACK("attack"),
    FOLLOW("follow"),
    GUARD("guard"),
    UPKEEP("upkeep"),
    WORK("work"),
    HOME("home"),
    RAIN("rain"),
    HUNGRY("upkeep2"),
    ALARM("alarm");

    public static final int UPDATE_TICKS = 10;

    private static final NpcStatus[] VALUES = values();

    /** The icon's file under {@code textures/gui/status}, or null for none. */
    private final String icon;

    NpcStatus(String icon)
    {
        this.icon = icon;
    }

    public String icon()
    {
        return this.icon;
    }

    public static NpcStatus byId(int id)
    {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }

    /** The status for an NPC, from its state first, then its running goals in order of importance. */
    static NpcStatus of(NpcEntity npc, List<Goal> running)
    {
        if (npc.getMorale().isSurrendered())
        {
            // The white flag badge says it all.
            return NONE;
        }
        if (npc.isSleeping())
        {
            return SLEEP;
        }
        if (runs(running, NpcFleeGoal.class) || runs(running, NpcBurningGoal.class))
        {
            return FLEE;
        }
        if (npc.getTarget() != null || runs(running, NpcMeleeAttackGoal.class))
        {
            return ATTACK;
        }
        if (npc.isRallied())
        {
            return ALARM;
        }
        // Orders are read from their flags: the goals behind them stop running once
        // the NPC is in place (beside its leader, at its post), which hid the icon.
        if (npc.isFollowing() || runs(running, NpcFollowOwnerGoal.class))
        {
            return FOLLOW;
        }
        if (npc.getHold().holding() || runs(running, NpcHoldPositionGoal.class))
        {
            return GUARD;
        }
        if (runs(running, NpcUpkeepGoal.class))
        {
            return UPKEEP;
        }
        if (runs(running, NpcJobGoal.class) || runs(running, NpcPriestGoal.class) || runs(running, NpcMedicGoal.class))
        {
            return WORK;
        }
        if (runs(running, NpcReturnHomeGoal.class))
        {
            return npc.level().isRainingAt(npc.blockPosition().above()) ? RAIN : HOME;
        }
        if (runs(running, NpcSleepGoal.class))
        {
            return SLEEP;
        }
        if (npc.isHungry())
        {
            return HUNGRY;
        }
        return NONE;
    }

    private static boolean runs(List<Goal> running, Class<? extends Goal> type)
    {
        for (Goal goal : running)
        {
            if (type.isInstance(goal))
            {
                return true;
            }
        }
        return false;
    }
}
