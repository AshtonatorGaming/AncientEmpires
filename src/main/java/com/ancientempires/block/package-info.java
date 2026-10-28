/**
 * Blocks with behaviour, and their block entities.
 *
 * <p>Worksites: {@link com.ancientempires.block.WorksiteBlock} and
 * {@link com.ancientempires.block.WorksiteBlockEntity} are the base for the crop
 * farm, tree farm, quarry, animal pen, hunting lodge, fishing dock, kitchen,
 * and Town Hall: an owner, an area ({@link com.ancientempires.block.WorksiteArea}),
 * a store with supply slots, and a to-do list rebuilt only when a worker asks.
 * A worksite does no work itself; the matching trade goal in {@code entity.npc}
 * does it by hand. {@link com.ancientempires.block.QuarryStair} is the quarry's
 * spiral stair as pure arithmetic.
 *
 * <p>Towns: the Town Hall feeds owned NPCs and records the fallen for priests
 * ({@link com.ancientempires.block.NpcUpkeep} lists the halls per dimension, so
 * no NPC scans for one). The Settlers' Post draws settlers once a day
 * ({@link com.ancientempires.block.NpcImmigration}).
 *
 * <p>Structures: {@link com.ancientempires.block.NpcSpawnerBlockEntity} is
 * Ancient Warfare's advanced spawner (garrison, repeating, redstone), which
 * structures import their people into; {@link com.ancientempires.block.FlagBlock}
 * is a faction flag, and a structure's protection flag is where it is claimed
 * ({@code structure.StructureClaims}); {@link com.ancientempires.block.CoinStackBlock}
 * holds coins.
 */
package com.ancientempires.block;
