/**
 * Ancient Warfare's structure library: converting it, loading it, placing it,
 * and holding and claiming what was placed.
 *
 * <p>Offline, at build time ({@code ./gradlew convertAwsTemplates}, no game
 * running): {@link com.ancientempires.structure.AwsConvertTool} reads every
 * {@code .aws} template in Ancient Warfare's 1.12 jar
 * ({@link com.ancientempires.structure.AwsTemplate}), upgrades its blocks, block
 * entities, items, and entities through Mojang's DataFixer plus hand-written
 * gaps ({@link com.ancientempires.structure.LegacyFixer},
 * {@link com.ancientempires.structure.AwsConverter}), and writes our compact
 * {@link com.ancientempires.structure.AeTemplate} ({@code .nbt}) with a
 * {@code .json} sidecar. {@link com.ancientempires.structure.AwsLootConverter}
 * does the loot tables.
 *
 * <p>In game: {@link com.ancientempires.structure.AeTemplates} indexes the
 * sidecars at each datapack reload and caches loaded templates;
 * {@link com.ancientempires.structure.AeRemap} resolves the blocks, entities,
 * and items this game does not have ({@code ae_remap} tables);
 * {@link com.ancientempires.structure.AePlacer} places a template (whole, or one
 * chunk's slice for world generation), recomputes block shapes, and makes
 * Ancient Warfare's pieces real: spawners
 * ({@link com.ancientempires.structure.AwSpawnerImport}), loot containers
 * ({@link com.ancientempires.structure.AwLoot},
 * {@link com.ancientempires.structure.AeLootItem}), gates, and flags.
 *
 * <p>Afterwards: {@link com.ancientempires.structure.StructureRecords} remembers
 * each placed faction structure (bounds, faction, flag, claimer) with a chunk
 * index, and {@link com.ancientempires.structure.StructureClaims} applies
 * conquest, block protection, and the loot lock on top of it.
 * {@link com.ancientempires.structure.StructureGameTests} places real templates
 * on the game test server.
 */
package com.ancientempires.structure;
