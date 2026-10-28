/**
 * The swing: when an NPC attacks, with what timing and reach, and how the hit
 * lands.
 *
 * <p>{@link com.ancientempires.combat.NpcMeleeAttackGoal} closes in, picks a
 * swing from {@link com.ancientempires.combat.AttackProfiles} (an
 * {@link com.ancientempires.combat.AttackSwing}: Better Combat attributes when
 * present, else a cooldown from attack speed, else the fist preset), winds up,
 * and applies one hit. It also owns footwork, duels, dodges, flanking, and
 * feints. {@link com.ancientempires.combat.NpcBowAttackGoal} (a bow in the main
 * hand) and {@link com.ancientempires.combat.NpcSkirmishGoal} (a Spartan
 * throwing weapon) are the ranged goals; the melee goal does not run while
 * they hold the fight.
 *
 * <p>Optional mods are reached only by reflection, so each one is absent-safe:
 * {@link com.ancientempires.combat.BetterCombatBridge} (weapon attributes,
 * cached per item), {@link com.ancientempires.combat.MobCombatBridge} and
 * {@link com.ancientempires.combat.MobCombatHooks} (Better Mob Combat's timing),
 * and {@link com.ancientempires.combat.SpartanThrowing}.
 * {@link com.ancientempires.combat.WeaponClassifier} is the one place that says
 * whether a stack is ranged, thrown, a shield, or melee.
 *
 * <p>{@link com.ancientempires.combat.Parry} is the perfect block, the same for
 * players and NPCs. {@link com.ancientempires.combat.NpcDamage} scales
 * NPC-on-NPC hits. Shield decisions are in {@code entity.npc.NpcGuard}.
 */
package com.ancientempires.combat;
