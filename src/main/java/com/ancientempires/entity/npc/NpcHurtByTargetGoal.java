package com.ancientempires.entity.npc;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;

/**
 * The vanilla hurt-by goal, with the same-owner check applied before it starts.
 * Nearby allies are not alerted, and there is no separate revenge list.
 */
public final class NpcHurtByTargetGoal extends HurtByTargetGoal
{
    public NpcHurtByTargetGoal(NpcEntity npc)
    {
        super(npc);
    }

    @Override
    public boolean canUse()
    {
        if (!super.canUse())
        {
            return false;
        }
        LivingEntity attacker = this.mob.getLastHurtByMob();
        return attacker == null || this.mob.canAttack(attacker);
    }
}
