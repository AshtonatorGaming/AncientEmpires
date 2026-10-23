package com.ancientempires.entity.npc;

import com.ancientempires.combat.Parry;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * When an NPC without a shield tries a parry. Against an NPC it watches the
 * wind-up: once per swing it decides (its tier's {@code parry.chanceByTier}),
 * and times the parry on the hit, off by up to its tier's
 * {@code timingErrorByTier} early or late. An early one is what a feint baits.
 * A player shows no wind-up to the server, so against a player it reads a
 * charged attack: in reach, facing it, cooldown full. A charged player who
 * holds back can bait that the same way.
 */
final class NpcParryPlanner
{
    /** Reach, in blocks, within which a charged player is read as about to swing. */
    private static final double PLAYER_THREAT_RANGE = 4.0D;
    /** About 25 degrees: the player is looking at this NPC. */
    private static final double PLAYER_FACING_DOT = 0.9D;
    /** Per tick, a fraction of the tier chance, so a read comes at some random moment. */
    private static final double PLAYER_READ_RATE = 0.1D;

    private NpcParryPlanner()
    {
    }

    static void tick(NpcEntity npc)
    {
        if (!AncientEmpiresConfig.parryEnabled || npc.hasShieldInOffhand() || npc.isHoldingBow() || npc.isAttackWindowOpen()
                || npc.isStaggered() || npc.getMainHandItem().isEmpty() || !Parry.ready(npc))
        {
            return;
        }
        LivingEntity target = npc.getTarget();
        if (target instanceof NpcEntity attacker)
        {
            readWindup(npc, attacker);
        }
        else if (target instanceof Player player)
        {
            readPlayer(npc, player);
        }
    }

    private static void readWindup(NpcEntity npc, NpcEntity attacker)
    {
        if (!attacker.isAttackWindowOpen() || attacker.getTarget() != npc)
        {
            return;
        }
        int serial = attacker.getAttackWindowSerial();
        if (npc.parryPlanFor != attacker.getId() || npc.parryPlanSerial != serial)
        {
            // A new swing: decide once whether to parry it, and when.
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
        if (npc.parryAt != Integer.MIN_VALUE && attacker.getAttackWindupRemaining() <= npc.parryAt)
        {
            npc.parryAt = Integer.MIN_VALUE;
            Parry.start(npc);
        }
    }

    private static void readPlayer(NpcEntity npc, Player player)
    {
        if (player.isCreative() || player.isSpectator() || npc.distanceToSqr(player) > PLAYER_THREAT_RANGE * PLAYER_THREAT_RANGE
                || player.getAttackStrengthScale(0.5F) < 0.95F)
        {
            return;
        }
        Vec3 toNpc = npc.getEyePosition().subtract(player.getEyePosition()).normalize();
        if (player.getViewVector(1.0F).dot(toNpc) < PLAYER_FACING_DOT)
        {
            return;
        }
        if (npc.getRandom().nextDouble() < npc.getParryChance() * PLAYER_READ_RATE)
        {
            Parry.start(npc);
        }
    }
}
