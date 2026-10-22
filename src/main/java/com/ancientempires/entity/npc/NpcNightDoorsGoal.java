package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;

/**
 * Ancient Warfare's NpcAIRestrictOpenDoor: at night an NPC does not open
 * doors, so it stays in or out of a building until morning. An NPC that is
 * following someone or fighting keeps using doors, so an escort can follow its
 * leader indoors after dark. No flags, so it runs beside the other goals.
 */
public final class NpcNightDoorsGoal extends Goal
{
    private final NpcEntity npc;

    public NpcNightDoorsGoal(NpcEntity npc)
    {
        this.npc = npc;
    }

    @Override
    public boolean canUse()
    {
        if (!AncientEmpiresConfig.nightDoorRestriction || !this.npc.level().isNight() || this.npc.isFollowing())
        {
            return false;
        }
        LivingEntity target = this.npc.getTarget();
        return target == null || !target.isAlive();
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.canUse();
    }

    @Override
    public void start()
    {
        this.setDoors(false);
    }

    @Override
    public void stop()
    {
        this.setDoors(AncientEmpiresConfig.openDoors);
    }

    private void setDoors(boolean open)
    {
        if (this.npc.getNavigation() instanceof GroundPathNavigation navigation)
        {
            navigation.setCanOpenDoors(open);
        }
    }
}
