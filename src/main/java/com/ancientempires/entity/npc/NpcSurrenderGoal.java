package com.ancientempires.entity.npc;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * A surrendered soldier ({@link NpcMorale}) kneels where it gave up: it holds
 * every movement flag, so nothing walks it off, and looks at the ground.
 */
final class NpcSurrenderGoal extends Goal
{
    private final NpcEntity npc;

    NpcSurrenderGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse()
    {
        return this.npc.getMorale().isSurrendered();
    }

    @Override
    public void start()
    {
        this.npc.getNavigation().stop();
    }

    @Override
    public void tick()
    {
        this.npc.getNavigation().stop();
        // A block ahead at the feet: head bowed.
        float yaw = this.npc.yBodyRot * Mth.DEG_TO_RAD;
        this.npc.getLookControl().setLookAt(this.npc.getX() - Mth.sin(yaw), this.npc.getY(), this.npc.getZ() + Mth.cos(yaw));
    }
}
