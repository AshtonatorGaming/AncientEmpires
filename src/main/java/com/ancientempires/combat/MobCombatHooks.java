package com.ancientempires.combat;

import me.Thelnfamous1.bettermobcombat.api.MobAttackWindup;
import me.Thelnfamous1.bettermobcombat.logic.MobCombatHelper;
import me.Thelnfamous1.bettermobcombat.platform.Services;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.AnimatedHand;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.lang.reflect.Field;
import java.util.function.BiConsumer;

/**
 * Loaded only when Better Mob Combat is present. That mod owns the upswing
 * clock and the attack clip. The consumer is our hit, so its own damage path
 * does not run.
 */
final class MobCombatHooks
{
    private MobCombatHooks()
    {
    }

    static boolean canUse(Mob mob)
    {
        return MobCombatHelper.canUseBetterCombatWeapon(mob, (holder, weapon) -> weapon.attacks() != null && weapon.attacks().length > 0);
    }

    static boolean attackReady(Mob mob)
    {
        return MobCombatHelper.isAttackReady(mob);
    }

    static boolean tryStart(Mob mob, BiConsumer<Mob, Entity> hit)
    {
        WeaponAttributes weapon = WeaponRegistry.getAttributes(mob.getMainHandItem());
        if (weapon == null)
        {
            return false;
        }
        ((MobAttackWindup) mob).bettermobcombat$startUpswing(weapon, hit);
        return ((MobAttackWindup) mob).bettermobcombat$getUpswingTicks() > 0;
    }

    /**
     * Plays the animation id already chosen from a weapon preset. Empty hands
     * pass the id from {@code bettercombat:fist}, so a pack that replaces that
     * preset's attack replaces the clip too. This does not invent an id.
     * {@code offHand} is the dual-wield off-hand beat. A held weapon stays on the main hand.
     */
    static void playAttackAnimation(Mob mob, String animationId, float length, float upswing, boolean offHand)
    {
        AnimatedHand hand = offHand ? AnimatedHand.OFF_HAND : AnimatedHand.MAIN_HAND;
        Services.PLATFORM.playMobAttackAnimation(mob, hand, animationId, length, upswing);
    }

    static void stopAttackAnimation(Mob mob, int fadeTicks)
    {
        Services.PLATFORM.stopMobAttackAnimation(mob, Math.max(0, fadeTicks));
    }

    static boolean upswinging(Mob mob)
    {
        MobAttackWindup windup = (MobAttackWindup) mob;
        return windup.bettermobcombat$getUpswingTicks() > 0 || windup.bettermobcombat$hasDelayedUpswing();
    }

    static int attackCooldown(Mob mob)
    {
        return ((MobAttackWindup) mob).bettermobcombat$getAttackCooldown();
    }

    static void cancel(Mob mob)
    {
        ((MobAttackWindup) mob).bettermobcombat$cancelUpswing();
        // cancelUpswing stops the clip but leaves the upswing counter, which
        // would still fire the hit on a later tick.
        set(mob, "bettermobcombat$upswingTicks", 0);
        set(mob, "bettermobcombat$customDamageApplicator", null);
    }

    private static void set(Mob mob, String name, Object value)
    {
        Field field = find(mob.getClass(), name);
        if (field == null)
        {
            return;
        }
        try
        {
            if (value instanceof Integer number)
            {
                field.setInt(mob, number);
            }
            else
            {
                field.set(mob, value);
            }
        }
        catch (IllegalAccessException ignored)
        {
        }
    }

    private static Field find(Class<?> type, String name)
    {
        while (type != null)
        {
            try
            {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            }
            catch (NoSuchFieldException ignored)
            {
                type = type.getSuperclass();
            }
        }
        return null;
    }
}
