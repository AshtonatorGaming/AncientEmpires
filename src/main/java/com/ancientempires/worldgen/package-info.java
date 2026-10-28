/**
 * Ancient Warfare's structures generating in new chunks, as one vanilla
 * structure type so {@code /locate}, structure checks, and terrain blending
 * work.
 *
 * <p>{@link com.ancientempires.worldgen.AwSiteStructure} is the structure
 * ({@code ancientempires:aw_site} for land, {@code aw_sea_site} for water and
 * islands). Its structure set makes every chunk a candidate; finding a spot
 * follows Ancient Warfare's steps: the chance roll, the chunk's territory
 * ({@link com.ancientempires.worldgen.Territories}: jittered cells, one
 * faction's region each), a template from
 * {@link com.ancientempires.worldgen.AwSiteIndex} (the sidecars' validation),
 * the territory's tuning ({@link com.ancientempires.worldgen.TerritorySettings},
 * AW's {@code territory_settings.json} as datapack data), and the ground
 * around it.
 *
 * <p>Generation is threaded. Finding a spot claims nothing (so {@code /locate}
 * is free); the pieces builder, which runs only for real generation, takes the
 * site through {@link com.ancientempires.worldgen.AwSites#claim}, which checks
 * overlap, uniqueness, duplicate distance, and budget in one step per
 * dimension. Records reach {@code structure.StructureRecords} on the main
 * thread. {@link com.ancientempires.worldgen.AwSitePiece} places its template a
 * chunk at a time through {@code structure.AePlacer.placeInChunk}.
 */
package com.ancientempires.worldgen;
