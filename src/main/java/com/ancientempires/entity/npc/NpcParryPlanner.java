package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.world.entity.LivingEntity;

/**
 * When a shield NPC whose shield is down raises it against an NPC's visible
 * wind-up. Once per swing it decides (its tier's {@code parry.chanceByTier})
 * whether to try for a perfect block: then it holds the raise until just before
 * the hit, off by up to its tier's {@code timingErrorByTier}. Too late and the
 * hit lands; too early and it is an ordinary block. Otherwise it raises at once,
 * the safe ordinary block. Players show no wind-up to the server, so against
 * them it always raises at once.
 */
final class NpcParryPlanner
{
    private NpcParryPlanner()
    {
    }

    /** Clears a plan once the swing it was for is over. */
    static void tick(NpcEntity npc)
    {
        if (npc.parryAt != Integer.MIN_VALUE && !(npc.getTarget() instanceof NpcEntity attacker && attacker.isAttackWindowOpen()))
        {
            npc.parryAt = Integer.MIN_VALUE;
        }
    }

    /** Whether to hold the shield down a little longer, timing a perfect block against {@code threat}. */
    static boolean waitToRaise(NpcEntity npc, LivingEntity threat)
    {
        if (!AncientEmpiresConfig.parryEnabled || npc.isRaisingShield() || !(threat instanceof NpcEntity attacker)
                || !attacker.isAttackWindowOpen() || attacker.getTarget() != npc)
        {
            return false;
        }
        int serial = attacker.getAttackWindowSerial();
        if (npc.parryPlanFor != attacker.getId() || npc.parryPlanSerial != serial)
        {
            // A new swing: decide once whether to try for the perfect block, and when.
            npc.parryPlanFor = attacker.getId();
            npc.parryPlanSerial = serial;
            npc.parryAt = Integer.MIN_VALUE;
            if (npc.getRandom().nextDouble() < npc.getParryChance())
            {
                int error = npc.getParryError();
                int jitter = error <= 0 ? 0 : npc.getRandom().nextInt(error * 2 + 1) - error;
                npc.parryAt = AncientEmpiresConfig.parryWindowTicks / 2 + jitter;
            }
        }
        return npc.parryAt != Integer.MIN_VALUE && attacker.getAttackWindupRemaining() > npc.parryAt;
    }
}
