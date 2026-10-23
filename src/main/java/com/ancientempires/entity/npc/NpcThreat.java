package com.ancientempires.entity.npc;

import com.ancientempires.combat.WeaponClassifier;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Threat scoring for target choice (DESIGN.md Tier 1). A candidate's score is
 * in "effective blocks": its distance, less bonuses for what makes it the
 * better target. The lowest score wins. Bonuses, each a {@code targeting}
 * weight in blocks:
 * <ul>
 * <li>attacking the NPC's owner ({@code ownerThreatWeight}), an ally
 * ({@code allyThreatWeight}), or the NPC itself ({@code selfThreatWeight});</li>
 * <li>badly hurt, scaled by missing health ({@code woundedWeight}): finish it;</li>
 * <li>in front of the NPC ({@code facingWeight}): no turning about;</li>
 * <li>role match ({@code roleWeight}): an archer prefers enemy archers.</li>
 * </ul>
 * No raycasts; the target goal still checks sight best-first.
 */
public final class NpcThreat
{
    /** A player counts as attacking someone for this long after hitting them. */
    private static final int PLAYER_THREAT_TICKS = 100;

    private NpcThreat()
    {
    }

    public static double score(NpcEntity npc, LivingEntity candidate, double distanceSqr)
    {
        double score = Math.sqrt(distanceSqr);
        if (!AncientEmpiresConfig.threatScoring)
        {
            return score;
        }
        LivingEntity victim = victimOf(candidate);
        if (victim != null)
        {
            if (victim instanceof Player player && player.getUUID().equals(npc.getOwner()))
            {
                score -= AncientEmpiresConfig.threatOwnerWeight;
            }
            else if (victim == npc)
            {
                score -= AncientEmpiresConfig.threatSelfWeight;
            }
            else if (victim instanceof NpcEntity other && npc.isAlly(other))
            {
                score -= AncientEmpiresConfig.threatAllyWeight;
            }
        }
        float max = candidate.getMaxHealth();
        if (max > 0.0F)
        {
            score -= AncientEmpiresConfig.threatWoundedWeight * (1.0D - Math.min(1.0D, candidate.getHealth() / max));
        }
        Vec3 look = npc.getViewVector(1.0F);
        Vec3 toward = candidate.position().subtract(npc.position());
        double length = toward.horizontalDistance();
        if (length > 1.0E-4D)
        {
            double dot = (look.x * toward.x + look.z * toward.z) / (length * Math.max(1.0E-4D, look.horizontalDistance()));
            score -= AncientEmpiresConfig.threatFacingWeight * Math.max(0.0D, dot);
        }
        if (npc.isHoldingBow() && isRanged(candidate.getMainHandItem()))
        {
            score -= AncientEmpiresConfig.threatRoleWeight;
        }
        return score;
    }

    /** Who the candidate is fighting: a mob's target, or whoever a player last hit, recently. */
    @Nullable
    private static LivingEntity victimOf(LivingEntity candidate)
    {
        if (candidate instanceof Mob mob)
        {
            return mob.getTarget();
        }
        if (candidate instanceof Player player && player.tickCount - player.getLastHurtMobTimestamp() < PLAYER_THREAT_TICKS)
        {
            return player.getLastHurtMob();
        }
        return null;
    }

    private static boolean isRanged(ItemStack stack)
    {
        WeaponClassifier.Kind kind = WeaponClassifier.kind(stack);
        return kind == WeaponClassifier.Kind.BOW || kind == WeaponClassifier.Kind.OTHER_RANGED || stack.getItem() instanceof CrossbowItem;
    }
}
