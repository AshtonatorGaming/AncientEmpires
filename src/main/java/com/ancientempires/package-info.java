/**
 * Ancient Empires: Ancient Warfare 2's NPCs and structures, rebuilt for Forge
 * 1.20.1. {@link com.ancientempires.AncientEmpires} is the entry point.
 *
 * <p>How the packages fit together:
 * <ul>
 * <li>{@code entity.npc}: the NPC ({@code NpcEntity}, one class for every role
 * and faction) and everything it does that is not a swing: goals for work,
 * upkeep, fleeing, sleep, targeting, shields, morale, levels, factions.</li>
 * <li>{@code combat}: the swing itself (melee, bow, thrown), weapon data
 * (the Better Combat bridges), and the perfect block.</li>
 * <li>{@code block}: worksites, the Town Hall, the Settlers' Post, the NPC
 * spawner, flags, and coin stacks.</li>
 * <li>{@code structure}: Ancient Warfare's structure library, converted, and
 * placing it; structure records and claiming.</li>
 * <li>{@code gate}: Ancient Warfare's gates.</li>
 * <li>{@code worldgen}: structures generating in new chunks (in progress).</li>
 * <li>{@code item}, {@code inventory}, {@code network}, {@code registry}: items,
 * container menus, packets, and registration.</li>
 * <li>{@code client}: rendering, animation bridges, and screens.</li>
 * <li>{@code command}: {@code /ancientempires}.</li>
 * <li>{@code config}: every tunable number (server) and visual preference
 * (client).</li>
 * </ul>
 * {@code docs/architecture.md} walks through the main flows; {@code docs/README.md}
 * indexes the pack maker's docs.
 */
package com.ancientempires;
