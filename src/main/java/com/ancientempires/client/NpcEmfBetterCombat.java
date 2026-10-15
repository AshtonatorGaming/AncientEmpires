package com.ancientempires.client;

import strm.emfcompat.bettercombat.EMFCompatBetterCombatMod;
import strm.emfcompat.bettercombat.compat.AttackPauseOverride;

import java.util.UUID;

/**
 * Loaded only when emf_compat_better_combat is present. Uses that mod's own
 * enable flag and the same attack linger players get.
 */
final class NpcEmfBetterCombat
{
    private NpcEmfBetterCombat()
    {
    }

    static boolean enabled()
    {
        return EMFCompatBetterCombatMod.isEnabled();
    }

    static boolean bodyFollow()
    {
        return EMFCompatBetterCombatMod.isBodyFollow();
    }

    static boolean attackLegs()
    {
        return EMFCompatBetterCombatMod.isAttackLegs();
    }

    static void mark(UUID uuid)
    {
        AttackPauseOverride.markAttackActive(uuid);
    }

    static void tick(UUID uuid)
    {
        AttackPauseOverride.tickCooldown(uuid);
    }

    static boolean holding(UUID uuid)
    {
        return AttackPauseOverride.isUnpaused(uuid);
    }
}
