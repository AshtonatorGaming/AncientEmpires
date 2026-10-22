package com.ancientempires.combat;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import com.google.common.collect.Multimap;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

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
        return slowWindup(resolveBase(npc), npc);
    }

    /**
     * NPCs wind up slower than a player with the same weapon, so a swing can be
     * read and answered. The wind-up grows by {@code npcWindupScale} and the
     * recovery stays the same, so the whole swing is longer too. The upswing
     * stays Better Combat's own; the client gets the real wind-up ticks with the
     * presentation and slows the whole clip evenly to land on them.
     */
    private static AttackSwing slowWindup(AttackSwing swing, NpcEntity npc)
    {
        // A levy is untrained: its wind-ups are slower again than a soldier's.
        double scale = AncientEmpiresConfig.npcWindupScale * (npc.isLevied() ? AncientEmpiresConfig.levyWindupScale : 1.0D);
        if (Math.abs(scale - 1.0D) < 1.0E-3D)
        {
            return swing;
        }
        int windup = Math.max(1, (int) Math.round(swing.windupTicks() * scale));
        return new AttackSwing(
                swing.range(),
                windup,
                swing.recoveryTicks(),
                swing.damageMultiplier(),
                swing.angleDegrees(),
                swing.animationId(),
                swing.animationUpswing(),
                swing.swingSoundId(),
                swing.swingVolume(),
                swing.swingPitch(),
                swing.swingPitchRandomness(),
                swing.unarmed(),
                swing.offHand(),
                swing.source()
        );
    }

    private static AttackSwing resolveBase(NpcEntity npc)
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
                AttackSwing swing = fromBetterCombat(npc, weapon.get(), dualWieldPartner(npc, weapon.get()), false);
                if (swing != null)
                {
                    return swing;
                }
            }
        }
        return vanilla(npc);
    }

    /**
     * The off-hand weapon when the NPC dual-wields, the way Better Combat
     * decides it for a player: both hands hold weapons with attributes and
     * neither is two-handed. A shield is not a weapon here.
     */
    @Nullable
    private static BetterCombatBridge.WeaponSnapshot dualWieldPartner(NpcEntity npc, BetterCombatBridge.WeaponSnapshot main)
    {
        ItemStack offhand = npc.getOffhandItem();
        if (!AncientEmpiresConfig.dualWield || main.twoHanded() || offhand.isEmpty() || WeaponClassifier.isShield(offhand))
        {
            return null;
        }
        var off = BetterCombatBridge.lookup(offhand);
        if (off.isEmpty() || off.get().twoHanded())
        {
            return null;
        }
        return off.get();
    }

    /**
     * Runs {@code read} with the off-hand weapon's main-hand modifiers in place
     * of the main hand's, then puts them back. Better Combat does the same for
     * a player's off-hand swing, so damage and speed come from that weapon.
     * Equipment modifiers are transient, so this never reaches a save.
     */
    public static <T> T withOffHandModifiers(LivingEntity npc, Supplier<T> read)
    {
        Multimap<Attribute, AttributeModifier> main = npc.getMainHandItem().getAttributeModifiers(EquipmentSlot.MAINHAND);
        Multimap<Attribute, AttributeModifier> off = npc.getOffhandItem().getAttributeModifiers(EquipmentSlot.MAINHAND);
        AttributeMap attributes = npc.getAttributes();
        attributes.removeAttributeModifiers(main);
        attributes.addTransientAttributeModifiers(off);
        try
        {
            return read.get();
        }
        finally
        {
            attributes.removeAttributeModifiers(off);
            attributes.addTransientAttributeModifiers(main);
        }
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
                AttackSwing swing = fromBetterCombat(npc, fist.get(), null, true);
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

    /**
     * Empty hands and a dual-wield pair alternate hands: even combo steps are
     * the main hand, odd steps the off hand, and each hand counts its own
     * attack sequence. {@code offWeapon} is set only for a dual-wield pair.
     */
    @Nullable
    private static AttackSwing fromBetterCombat(NpcEntity npc, BetterCombatBridge.WeaponSnapshot mainWeapon, @Nullable BetterCombatBridge.WeaponSnapshot offWeapon, boolean unarmed)
    {
        int combo = npc.getComboIndex();
        boolean dual = offWeapon != null;
        boolean alternating = unarmed || dual;
        boolean offHand = alternating && Math.floorMod(combo, 2) == 1;
        int step = alternating ? Math.floorDiv(combo, 2) : combo;
        BetterCombatBridge.WeaponSnapshot weapon = dual && offHand ? offWeapon : mainWeapon;
        boolean sameItem = dual && npc.getMainHandItem().getItem() == npc.getOffhandItem().getItem();
        boolean sameCategory = dual && mainWeapon.category() != null && mainWeapon.category().equals(offWeapon.category());
        List<BetterCombatBridge.AttackSnapshot> usable = usableAttacks(npc, weapon, alternating, offHand, unarmed || sameItem, unarmed || sameItem || sameCategory);
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
        float speed;
        if (unarmed)
        {
            speed = ONE_HANDED_WEAPON_SPEED;
        }
        else if (dual && offHand)
        {
            speed = withOffHandModifiers(npc, () -> attackSpeed(npc));
        }
        else
        {
            speed = attackSpeed(npc);
        }
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
                unarmed ? "bettercombat_fist" : dual ? "bettercombat_dual" : "bettercombat"
        );
    }

    private static List<BetterCombatBridge.AttackSnapshot> usableAttacks(NpcEntity npc, BetterCombatBridge.WeaponSnapshot weapon, boolean dual, boolean offHand, boolean sameItem, boolean sameCategory)
    {
        boolean mounted = npc.isPassenger();
        boolean shield = WeaponClassifier.isShield(npc.getOffhandItem());
        boolean offhandEmpty = npc.getOffhandItem().isEmpty();
        List<BetterCombatBridge.AttackSnapshot> usable = new ArrayList<>();
        for (BetterCombatBridge.AttackSnapshot attack : weapon.attacks())
        {
            if (accepts(attack.conditions(), mounted, shield, offhandEmpty, dual, offHand, sameItem, sameCategory))
            {
                usable.add(attack);
            }
        }
        return usable.isEmpty() ? weapon.attacks() : usable;
    }

    /**
     * Dual-wield and off-hand steps need both hands armed alike: a dual-wield
     * pair, or empty hands, which both carry the fist preset. A single weapon
     * skips them. Same-item and same-category steps also check the pair.
     */
    private static boolean accepts(List<String> conditions, boolean mounted, boolean shield, boolean offhandEmpty, boolean dual, boolean offHand, boolean sameItem, boolean sameCategory)
    {
        for (String condition : conditions)
        {
            switch (condition)
            {
                case "OFF_HAND_ONLY":
                    if (!dual || !offHand)
                    {
                        return false;
                    }
                    break;
                case "MAIN_HAND_ONLY":
                    if (dual && offHand)
                    {
                        return false;
                    }
                    break;
                case "DUAL_WIELDING_ANY":
                    if (!dual)
                    {
                        return false;
                    }
                    break;
                case "DUAL_WIELDING_SAME":
                    if (!dual || !sameItem)
                    {
                        return false;
                    }
                    break;
                case "DUAL_WIELDING_SAME_CATEGORY":
                    if (!dual || !sameCategory)
                    {
                        return false;
                    }
                    break;
                case "NOT_DUAL_WIELDING":
                    if (dual)
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
                    if (dual || !offhandEmpty)
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
