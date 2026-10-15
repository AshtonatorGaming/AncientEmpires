package com.ancientempires.combat;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks the swing the NPC is about to commit to.
 * Empty hands use an internal unarmed profile, optionally replaced by Better
 * Combat's fist data. A held weapon uses Better Combat attributes when they
 * exist, and otherwise a cooldown taken from attack speed.
 */
public final class AttackProfiles
{
    private AttackProfiles()
    {
    }

    public static AttackSwing resolve(NpcEntity npc)
    {
        ItemStack stack = npc.getMainHandItem();
        if (stack.isEmpty())
        {
            return unarmed(npc);
        }
        if (AncientEmpiresConfig.betterCombatEnabled)
        {
            var weapon = BetterCombatBridge.lookup(stack);
            if (weapon.isPresent())
            {
                AttackSwing swing = fromBetterCombat(npc, weapon.get(), false);
                if (swing != null)
                {
                    return swing;
                }
            }
        }
        return vanilla(npc);
    }

    private static AttackSwing unarmed(NpcEntity npc)
    {
        if (AncientEmpiresConfig.betterCombatEnabled && AncientEmpiresConfig.unarmedPreferBetterCombat)
        {
            var fist = BetterCombatBridge.lookupFist();
            if (fist.isPresent())
            {
                AttackSwing swing = fromBetterCombat(npc, fist.get(), true);
                if (swing != null)
                {
                    return swing;
                }
            }
        }

        int cooldown = Math.max(AncientEmpiresConfig.unarmedWindupTicks + 1, AncientEmpiresConfig.unarmedCooldownTicks);
        int windup = Mth.clamp(AncientEmpiresConfig.unarmedWindupTicks, 1, cooldown - 1);
        return new AttackSwing(
                AncientEmpiresConfig.unarmedRange,
                windup,
                cooldown - windup,
                (float) AncientEmpiresConfig.unarmedDamageMultiplier,
                AncientEmpiresConfig.forwardAngle,
                null,
                null,
                1.0F,
                1.0F,
                0.0F,
                true,
                "unarmed"
        );
    }

    private static AttackSwing vanilla(NpcEntity npc)
    {
        int cooldown = cooldownTicks(attackSpeed(npc), AncientEmpiresConfig.vanillaMinCooldownTicks);
        int windup = windupTicks(cooldown, AncientEmpiresConfig.vanillaWindupFraction);
        return new AttackSwing(
                AncientEmpiresConfig.vanillaRange,
                windup,
                cooldown - windup,
                1.0F,
                AncientEmpiresConfig.forwardAngle,
                null,
                null,
                1.0F,
                1.0F,
                0.0F,
                false,
                "vanilla"
        );
    }

    @Nullable
    private static AttackSwing fromBetterCombat(NpcEntity npc, BetterCombatBridge.WeaponSnapshot weapon, boolean unarmed)
    {
        List<BetterCombatBridge.AttackSnapshot> usable = usableAttacks(npc, weapon);
        if (usable.isEmpty())
        {
            return null;
        }
        BetterCombatBridge.AttackSnapshot attack = usable.get(Math.floorMod(npc.getComboIndex(), usable.size()));

        double range = weapon.range();
        if (range <= 0.0D)
        {
            range = unarmed ? AncientEmpiresConfig.unarmedRange : AncientEmpiresConfig.vanillaRange;
        }

        int minimum = Math.max(1, BetterCombatBridge.attackIntervalCap(AncientEmpiresConfig.minAttackInterval));
        int cooldown = cooldownTicks(attackSpeed(npc), minimum);
        float upswingMultiplier = AncientEmpiresConfig.readUpswingFromMod
                ? BetterCombatBridge.upswingMultiplier((float) AncientEmpiresConfig.upswingMultiplier)
                : (float) AncientEmpiresConfig.upswingMultiplier;
        double upswingRate = Mth.clamp(attack.upswing(), 0.0D, 1.0D) * Mth.clamp(upswingMultiplier, 0.2F, 1.0F);
        int windup = windupTicks(cooldown, upswingRate);

        float damageMultiplier = attack.damageMultiplier() > 0.0D ? (float) attack.damageMultiplier() : 1.0F;
        if (unarmed)
        {
            damageMultiplier *= (float) AncientEmpiresConfig.unarmedDamageMultiplier;
        }

        return new AttackSwing(
                range,
                windup,
                cooldown - windup,
                damageMultiplier,
                angleFor(attack),
                attack.animationId(),
                attack.swingSoundId(),
                attack.swingVolume(),
                attack.swingPitch(),
                attack.swingPitchRandomness(),
                unarmed,
                unarmed ? "bettercombat_fist" : "bettercombat"
        );
    }

    private static List<BetterCombatBridge.AttackSnapshot> usableAttacks(NpcEntity npc, BetterCombatBridge.WeaponSnapshot weapon)
    {
        boolean mounted = npc.isPassenger();
        boolean shield = npc.getOffhandItem().getItem() instanceof ShieldItem;
        boolean offhandEmpty = npc.getOffhandItem().isEmpty();
        List<BetterCombatBridge.AttackSnapshot> usable = new ArrayList<>();
        for (BetterCombatBridge.AttackSnapshot attack : weapon.attacks())
        {
            if (accepts(attack.conditions(), mounted, shield, offhandEmpty))
            {
                usable.add(attack);
            }
        }
        return usable.isEmpty() ? weapon.attacks() : usable;
    }

    /**
     * Dual-wield and off-hand steps are skipped. This NPC swings the main hand
     * only, so those attacks would describe a hand it is not using.
     */
    private static boolean accepts(List<String> conditions, boolean mounted, boolean shield, boolean offhandEmpty)
    {
        for (String condition : conditions)
        {
            switch (condition)
            {
                case "OFF_HAND_ONLY":
                case "DUAL_WIELDING_ANY":
                case "DUAL_WIELDING_SAME":
                case "DUAL_WIELDING_SAME_CATEGORY":
                    return false;
                case "MOUNTED":
                    if (!mounted)
                    {
                        return false;
                    }
                    break;
                case "NOT_MOUNTED":
                    if (mounted)
                    {
                        return false;
                    }
                    break;
                case "OFF_HAND_SHIELD":
                    if (!shield)
                    {
                        return false;
                    }
                    break;
                case "NO_OFFHAND_ITEM":
                    if (!offhandEmpty)
                    {
                        return false;
                    }
                    break;
                default:
                    break;
            }
        }
        return true;
    }

    private static double angleFor(BetterCombatBridge.AttackSnapshot attack)
    {
        if (attack.angle() > 0.0D)
        {
            return attack.angle();
        }
        if ("FORWARD_BOX".equals(attack.hitbox()) || attack.hitbox() == null)
        {
            return AncientEmpiresConfig.forwardAngle;
        }
        return AncientEmpiresConfig.sweepAngle;
    }

    private static float attackSpeed(NpcEntity npc)
    {
        double speed = npc.getAttributeValue(Attributes.ATTACK_SPEED);
        if (speed < 0.05D)
        {
            return 4.0F;
        }
        return (float) speed;
    }

    /**
     * Same length a player waits for the attack-strength meter to fill:
     * {@code ceil(20 / attack speed)}, and never shorter than {@code minimum}.
     */
    static int cooldownTicks(float attackSpeed, int minimum)
    {
        int ticks = (int) Math.ceil(20.0D / attackSpeed);
        return Math.max(minimum, Math.max(2, ticks));
    }

    /**
     * Hit frame inside the cooldown. At least one tick of wind-up, and at least
     * one tick of recovery, so a swing cannot start and finish on the same tick.
     */
    static int windupTicks(int cooldown, double fraction)
    {
        int windup = (int) Math.round(cooldown * fraction);
        int upper = Math.max(1, cooldown - 1);
        return Mth.clamp(windup, 1, upper);
    }
}
