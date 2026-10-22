package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.phys.AABB;

/**
 * The vanilla hurt-by goal, with the same-owner check applied before it starts.
 * Like Ancient Warfare, a hit also alerts nearby allies (same faction, or same
 * owner) that have no target yet, so a squad turns on its attacker together.
 * Vanilla's own alert would reach every NPC nearby, enemies included, so this
 * replaces it with an ally-only version.
 */
public final class NpcHurtByTargetGoal extends HurtByTargetGoal
{
    private final NpcEntity npc;
    /** Hurt timestamp a civilian last called for help on. */
    private int calledForHelpAt;

    public NpcHurtByTargetGoal(NpcEntity npc)
    {
        super(npc);
        this.npc = npc;
        this.setAlertOthers();
    }

    @Override
    public boolean canUse()
    {
        if (!super.canUse())
        {
            return false;
        }
        LivingEntity attacker = this.mob.getLastHurtByMob();
        if (this.npc.avoidsFights())
        {
            // A civilian does not fight back, but its allies hear about it once per hit.
            int timestamp = this.mob.getLastHurtByMobTimestamp();
            if (timestamp != this.calledForHelpAt)
            {
                this.calledForHelpAt = timestamp;
                this.alertOthers();
            }
            return false;
        }
        return attacker == null || this.mob.canAttack(attacker);
    }

    @Override
    protected void alertOthers()
    {
        LivingEntity attacker = this.mob.getLastHurtByMob();
        double range = AncientEmpiresConfig.allyAlertRange;
        if (attacker == null || range <= 0.0D)
        {
            return;
        }
        AABB area = this.mob.getBoundingBox().inflate(range, AncientEmpiresConfig.allyAlertHeight, range);
        for (NpcEntity ally : this.mob.level().getEntitiesOfClass(NpcEntity.class, area, this.npc::isAlly))
        {
            if (ally.getTarget() == null && ally.isAlive() && ally.canAttack(attacker) && !ally.isAlliedTo(attacker))
            {
                ally.setTarget(attacker);
            }
        }
    }
}
