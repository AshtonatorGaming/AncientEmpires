/**
 * The NPC and its behaviour.
 *
 * <p>{@link com.ancientempires.entity.npc.NpcEntity} is the only NPC class. What
 * an NPC is comes from data, not subclasses: its roster row
 * ({@link com.ancientempires.entity.npc.NpcRoster}, {@code roster.json}), its
 * faction ({@link com.ancientempires.entity.npc.NpcFactions},
 * {@code factions.json}) or its owner, its path
 * ({@link com.ancientempires.entity.npc.NpcPath}: settler, worker, soldier), and
 * what it holds. Its role label ({@code getRoleId}) is read from its equipment.
 *
 * <p>Behaviour is vanilla goals, registered in {@code NpcEntity.registerGoals}
 * by priority: floating, surrender, doors, and gates (0); bow, skirmish, shield,
 * medic, flee, burning, and sun (1); melee (2, in {@code combat}); hold position
 * and upkeep (3); follow, sleep, priest, governor, and
 * {@link com.ancientempires.entity.npc.NpcJobGoal} (4: one goal for every
 * hands-on trade, which the main-hand tool picks); return home (5); idle (6+).
 * Targets: owner defense (1, 2), hurt-by with the ally alert (3), then one
 * shared scan ({@link com.ancientempires.entity.npc.NpcTargetGoal} over
 * {@link com.ancientempires.entity.npc.NpcTargetCache}, scored by
 * {@link com.ancientempires.entity.npc.NpcThreat}).
 *
 * <p>Helpers owned per NPC keep the larger systems out of the entity:
 * {@link com.ancientempires.entity.npc.NpcGuard} (all shield state),
 * {@link com.ancientempires.entity.npc.NpcMorale},
 * {@link com.ancientempires.entity.npc.NpcMount},
 * {@link com.ancientempires.entity.npc.NpcSidearm},
 * {@link com.ancientempires.entity.npc.NpcTargetMemory}, and
 * {@link com.ancientempires.entity.npc.NpcInventory}. Rules with no state of
 * their own ({@link com.ancientempires.entity.npc.NpcLeveling},
 * {@link com.ancientempires.entity.npc.NpcCommand},
 * {@link com.ancientempires.entity.npc.NpcSafety},
 * {@link com.ancientempires.entity.npc.NpcNightLight}) are static, and their
 * pure parts are unit tested.
 *
 * <p>Trades: {@link com.ancientempires.entity.npc.NpcWorkGoal} is the walk,
 * swing, carry, and unload loop every trade shares; each subclass (farmer,
 * woodcutter, miner, rancher, hunter, fisher, cook) names its tool, its
 * worksite type (in {@code block}), and its jobs. World-level data lives here
 * too: {@link com.ancientempires.entity.npc.NpcStandings} (each player's
 * standing per faction) and {@link com.ancientempires.entity.npc.NpcSkins} (the
 * skin catalog).
 */
package com.ancientempires.entity.npc;
