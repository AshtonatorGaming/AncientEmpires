/**
 * Ancient Warfare's gates.
 *
 * <p>{@link com.ancientempires.gate.GateEntity} is one gate, of a
 * {@link com.ancientempires.gate.GateType}. While closed it fills its opening
 * with {@link com.ancientempires.gate.GateProxyBlock}s, so collision and
 * pathfinding see a wall without asking the entity; clicks and blows on a
 * proxy go to the gate. {@link com.ancientempires.gate.GateSpawnerItem} builds
 * one from two corners; structures build theirs through
 * {@code structure.AePlacer}.
 *
 * <p>NPCs: with {@code structures.npcUse} on, a player's own NPCs plan paths
 * through their owner's gates ({@link com.ancientempires.gate.NpcGateNavigation}
 * reads a permitted gate's proxies as a door) and
 * {@link com.ancientempires.gate.NpcGateGoal} opens the gate ahead and closes it
 * behind. Drawing is {@code client.GateRenderer}.
 */
package com.ancientempires.gate;
