package com.ancientempires.combat;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks the swing the NPC is about to commit to.
 * Both hands empty use Better Combat's fist preset on each hand, so the combo
 * alternates the way dual-wielding does. Any held item, including a two-handed
 * weapon, keeps the weapon path. A held weapon uses Better Combat attributes
 * when they exist, and otherwise a cooldown taken from attack speed.
 */
public final class AttackProfiles
{
    /**
     * Vanilla sword pace: base attack speed 4 plus the sword's -2.4 modifier.
     * The NPC's own attack speed is 4, which is a fist's pace and is too fast here.
     */
    private static final float ONE_HANDED_WEAPON_SPEED = 1.6F;

    private AttackProfiles()
    {
    }

    public static AttackSwing resolve(NpcEntity npc)
    {
        ItemStack stack = npc.getMainHandItem();
        if (stack.isEmpty())
        {
            if (npc.getOffhandItem().isEmpty())
            {
                return fists(npc);
            }
            return bare(npc);
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

    /**
     * Both hands empty. The fist preset is applied to each hand. A two-handed
     * weapon is a held item, so it never reaches this method.
     */
    private static AttackSwing fists(NpcEntity npc)
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
        return bare(npc);
    }

    private static AttackSwing bare(NpcEntity npc)
    {
        int cooldown = Math.max(AncientEmpiresConfig.unarmedWindupTicks + 1, AncientEmpiresConfig.unarmedCooldownTicks);
        int windup = Mth.clamp(AncientEmpiresConfig.unarmedWindupTicks, 1, cooldown - 1);
        return new AttackSwing(
                effectiveRange(npc, AncientEmpiresConfig.unarmedRange),
                windup,
                cooldown - windup,
                (float) AncientEmpiresConfig.unarmedDamageMultiplier,
                AncientEmpiresConfig.forwardAngle,
                null,
                0.0F,
                null,
                1.0F,
                1.0F,
                0.0F,
                true,
                false,
                "unarmed"
        );
    }

    private static AttackSwing vanilla(NpcEntity npc)
    {
        int cooldown = cooldownTicks(attackSpeed(npc), AncientEmpiresConfig.vanillaMinCooldownTicks);
        int windup = windupTicks(cooldown, AncientEmpiresConfig.vanillaWindupFraction);
        return new AttackSwing(
                effectiveRange(npc, AncientEmpiresConfig.vanillaRange),
                windup,
                cooldown - windup,
                1.0F,
                AncientEmpiresConfig.forwardAngle,
                null,
                0.0F,
                null,
                1.0F,
                1.0F,
                0.0F,
                false,
                false,
                "vanilla"
        );
    }

    @Nullable
    private static AttackSwing fromBetterCombat(NpcEntity npc, BetterCombatBridge.WeaponSnapshot weapon, boolean unarmed)
    {
        int combo = npc.getComboIndex();
        boolean offHand = unarmed && Math.floorMod(combo, 2) == 1;
        int step = unarmed ? Math.floorDiv(combo, 2) : combo;
        List<BetterCombatBridge.AttackSnapshot> usable = usableAttacks(npc, weapon, unarmed, offHand);
        if (usable.isEmpty())
        {
            return null;
        }
        BetterCombatBridge.AttackSnapshot attack = usable.get(Math.floorMod(step, usable.size()));

        double range = weapon.range();
        if (range <= 0.0D)
        {
            range = unarmed ? AncientEmpiresConfig.unarmedRange : AncientEmpiresConfig.vanillaRange;
        }

        int minimum = Math.max(1, BetterCombatBridge.attackIntervalCap(AncientEmpiresConfig.minAttackInterval));
        float speed = unarmed ? ONE_HANDED_WEAPON_SPEED : attackSpeed(npc);
        int cooldown = cooldownTicks(speed, minimum);
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
                effectiveRange(npc, range),
                windup,
                cooldown - windup,
                damageMultiplier,
                angleFor(attack),
                attack.animationId(),
                (float) Mth.clamp(attack.upswing(), 0.0D, 1.0D),
                attack.swingSoundId(),
                attack.swingVolume(),
                attack.swingPitch(),
                attack.swingPitchRandomness(),
                unarmed,
                offHand,
                unarmed ? "bettercombat_fist" : "bettercombat"
        );
    }

    private static List<BetterCombatBridge.AttackSnapshot> usableAttacks(NpcEntity npc, BetterCombatBridge.WeaponSnapshot weapon, boolean dualFist, boolean offHand)
    {
        boolean mounted = npc.isPassenger();
        boolean shield = npc.getOffhandItem().getItem() instanceof ShieldItem;
        boolean offhandEmpty = npc.getOffhandItem().isEmpty();
        List<BetterCombatBridge.AttackSnapshot> usable = new ArrayList<>();
        for (BetterCombatBridge.AttackSnapshot attack : weapon.attacks())
        {
            if (accepts(attack.conditions(), mounted, shield, offhandEmpty, dualFist, offHand))
            {
                usable.add(attack);
            }
        }
        return usable.isEmpty() ? weapon.attacks() : usable;
    }

    /**
     * Weapon swings skip dual-wield and off-hand steps. Empty-hand fists are the
     * exception: both hands carry the fist preset, so those conditions match and
     * the combo alternates hands.
     */
    private static boolean accepts(List<String> conditions, boolean mounted, boolean shield, boolean offhandEmpty, boolean dualFist, boolean offHand)
    {
        for (String condition : conditions)
        {
            switch (condition)
            {
                case "OFF_HAND_ONLY":
                    if (!dualFist || !offHand)
                    {
                        return false;
                    }
                    break;
                case "MAIN_HAND_ONLY":
                    if (dualFist && offHand)
                    {
                        return false;
                    }
                    break;
                case "DUAL_WIELDING_ANY":
                case "DUAL_WIELDING_SAME":
                case "DUAL_WIELDING_SAME_CATEGORY":
                    if (!dualFist)
                    {
                        return false;
                    }
                    break;
                case "NOT_DUAL_WIELDING":
                    if (dualFist)
                    {
                        return false;
                    }
                    break;
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
                    if (dualFist || !offhandEmpty)
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

    /**
     * Weapon reach times the NPC's height relative to a 1.8-block body.
     * A normal soldier stays at about 1.0x. Shorter and taller bodies clamp
     * into the configured scale, with no size categories.
     */
    public static double effectiveRange(LivingEntity npc, double weaponRange)
    {
        double baseline = AncientEmpiresConfig.rangeScaleBaseline;
        if (baseline < 0.01D)
        {
            baseline = 1.8D;
        }
        double min = Math.min(AncientEmpiresConfig.rangeScaleMin, AncientEmpiresConfig.rangeScaleMax);
        double max = Math.max(AncientEmpiresConfig.rangeScaleMin, AncientEmpiresConfig.rangeScaleMax);
        double scale = Mth.clamp(npc.getBbHeight() / baseline, min, max);
        return weaponRange * scale;
    }

    /**
     * Eye height, unless this body is significantly taller than the target.
     * Then the aim drops to the target's center, plus the configured pitch.
     * Equal heights do not qualify.
     */
    public static double aimY(LivingEntity npc, LivingEntity target)
    {
        double eyes = target.getEyeY();
        double factor = AncientEmpiresConfig.tallAimFactor;
        if (factor <= 0.0D || npc.getBbHeight() <= target.getBbHeight() * factor)
        {
            return eyes;
        }
        double aim = target.getY() + target.getBbHeight() * 0.5D;
        double offset = AncientEmpiresConfig.tallPitchOffset;
        if (offset > 0.0D)
        {
            double dx = target.getX() - npc.getX();
            double dz = target.getZ() - npc.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            aim -= horizontal * Math.tan(Math.toRadians(offset));
        }
        return Math.max(target.getY(), aim);
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
