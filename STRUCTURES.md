# STRUCTURES.md

## AncientEmpires Structure System

**Status:** Design for the structure loader, placer, world-gen, and spawner integration.  
**Companion to:** `DESIGN.md`, `VISION.md`, `CLAUDE.md`.  
**Goal:** Reuse the existing Ancient Warfare `.aws` template library (thousands of faction structures) while fixing its known placement flaws and making modded content resolve automatically at load/place time.

---

## Core Decision

Keep the `.aws` text format. Do not convert the library to vanilla NBT structures as the primary path.

- The format is simple (header, validation, numeric layers, rule plugins).
- It already encodes biome/dimension filters, unique flags, offsets, and world-gen toggles.
- AW spawners and special blocks are expressed as rules inside it.
- A loader + placer is less work than a mass conversion, and we can still export to vanilla `.nbt` later if needed.

Vanilla structure pieces / jigsaws can be an optional export or a future authoring path. They are not required for the first garrison milestone.

---

## Findings From the Template Library (2026-10-28)

Read from the 1,472 templates in `libs/ancientwarfare-1.6.1.jar` (`MohawkyPackTweaked1.20.1V76`). These change the plan above.

- **The templates are 1.12 data.** Block states use 1.12 names and properties (`minecraft:grass` with `snowy`,
  `minecraft:stonebrick` with `variant:mossy_stonebrick`), biomes are 1.12 ids (`forest_hills`), dimensions are
  numbers (`dimensionList=0`). The registry is not a detector until the data is converted.
- **How the 1.20.1 port converts them, and why it is not enough:** `RegistryTools.fixLegacyName` runs only the block
  *name* through vanilla's DataFixer, then looks it up, else air. The 1.12 variant properties are dropped, so mossy
  stone bricks, colored wool, plank species and similar lose their variant, and `minecraft:grass` survives the
  name pass as the 1.20 tall-grass plant instead of `grass_block`. The idea is right, the input is wrong.
- **Our conversion:** feed the whole state (`{Name, Properties}`) through vanilla's DataFixer as
  `References.BLOCK_STATE` from data version 1343 (1.12.2) to the current one. That is Mojang's own flattening
  table (`BlockStateData`), so vanilla blocks come out right with their variants. Biome names go through the same
  fixer (`References.BIOME`). Do it once in an offline converter that writes our own template files, so the game
  never runs the fixer and the result can be diffed and hand-corrected. AW's own blocks and entities go through
  our mapping table, not the fixer.
- **Source of truth:** AW Tweaked's or classic 1.12.2 AW's own templates, for parity. The 1.20.1 port's copies are
  the same files; its code is only a reference for what not to do.
- **AW content the templates need**, by use count. All of it returns, as placeholders first where needed:
  - Spawners: `advanced_spawner` (35k block uses, 1,172 templates) → our `NpcSpawnerBlock`. Its `entityId`
    (`ancientwarfarenpc:faction.archer`, `.soldier`, `.leader`, `.civilian`, `.elite` variants) plus `factionName`
    (`empire`, `nogg`, `norska`, ...) resolves through `NpcRoster`. Also `AWNpc` rules (219, NPCs placed directly).
  - Faction flags and banners: `decorative_flag` (982), `protection_flag` (279), the `flag` (1,247) and `banner`
    (1,779) rule plugins. The protection flag is AW's claim marker: it protects the structure it stands in and is
    what a player takes to claim it. Port it as the garrison anchor (owner faction, protected bounds, claim),
    with the faction's own flag art.
  - Gates: `gate_proxy` (4,989) and `awGate` rules (266): vertical iron, double wood, vertical wooden,
    drawbridge, single wood, double iron. A gate is an entity spanning a proxy block frame, as in AW.
  - Stationary siege engines: `AWVehicle` (438 rules in 88 templates), mostly `vehType` 5, 10, 9, 4, 17. Placeholder
    entities first (model, health, can be destroyed); firing comes later with siege work.
  - Loot: `advanced_loot_chest` (3,457), `loot_basket` (793), `urn` (2,404). Map to vanilla loot tables.
  - Decor: chair, table, bench, stool, thrones, coffins, gravestone, altar pieces, candles, totems, fire pit,
    braziers, coin stacks, iron cage, gibbet, stake, statue, golden idol, stretching rack, wooden post, sound block.
    AW's models and textures are reused where practical. Until each exists, a mapping table gives a vanilla
    stand-in so the templates still read right.
  - Skip: `structure_scanner_block`, drafting and research stations, automation blocks (windmill blades are kept
    as decor).

### AW's spawner as the library uses it

All 35,355 `advanced_spawner` entries were counted:
- Every one holds one group with one entity (37 are empty). Group weights, random pools, the spawner's own
  inventory, and redstone (1) or light (19) gating are essentially unused.
- 33,756 spawn exactly one (`remainingSpawnCount:1`, delay 10): a one-shot garrison post. About 1,000 spawn
  4 to 10, and 54 roll a count between a min and a max.
- `playerRange` is per block, and builders used it to stage the fight. Elite archers wake at about 29 blocks
  (the walls shoot first), soldiers at 11, leaders at 12, priests at 12, siege engineers at 54. Leader-last
  comes from the data and needs no rule of its own. Keep each spawner's own range.
- Per-entry NPC data: 1,291 entries give custom gear (`hasCustomEquipment:1b`), 2,198 a custom name
  (named bosses), and about 1,800 a fixed skin (`skinTypeSelected:"npc_type"`). Roster id = `factionName` +
  `.` + the type after `faction.` (`empire.soldier.elite`), which matches `roster.json` directly.
- `hostile` (32k true) marks spawners that block claiming. About 600 give XP when broken (`xpToDrop`).

### AW's conquest, loot, and coins

- Claiming (`ConquerHelper`, `TileProtectionFlag`): a structure's protection flag can be claimed once the
  hostiles inside score under `conquer_threshold`. Each living hostile faction NPC counts its resistance
  (normal, elite, leader), and each hostile spawner not yet spawned counts too. A failed claim marks the
  witnesses (glow) and names one. Until then: blocks in the bounds mine `block_protection_multiplier` (100)
  times slower, loot chests are locked (`loot_chest_protection`; `allow_stealing` allows it while unseen),
  and the structure's sound blocks play. Claiming makes the claiming player the owner and silences them.
- Advanced Loot Chest and Basket: filled from a loot table on first open, `lootRolls` times, optionally
  applying potion effects, a message, or spawning a mob (trapped). Set with the Loot Chest Placer. AW ships
  124 chest tables (per faction and value tier).
- Coins: Copper Owl, Silver Stag, Gold Dragon, Ancient Coin (one item with a `metal` tag). Every faction NPC
  type has an entity loot table dropping them (archer: 1 to 8 copper). Coin stack blocks are decor.
- Authoring tools: Structure Scanner (two corners plus facing, exports a template), Structure Builder and
  WorldGen Builder (place one), Survival Builder, Town Builder, Construction and Lake tools (area fill),
  Gate Spawner (two corners), Mob Spawner Placement Tool (right-click a mob to capture its type and data,
  right-click a block to place a spawner of it).

### Decisions (user, 2026-10-28)

- Spawner: keep our 3x3 of scrolls. Add the capture wand (any mob, with its gear, name, and data, becomes a
  scroll; placing it makes a garrison spawner). The converter writes the same scrolls (repacked-style NPC
  scrolls for AW NPCs, so gear, name, and skin carry over). Keep AW's capabilities where they could matter
  even though the library does not use them: a pool option (spawn one slot at random, weighted), a min to
  max count, and per-spawner XP and hostile flag.
- Claiming: AW's rules, and claiming costs standing with the owning faction through `NpcStandings`.
- Coins: port the four coins, AW's faction NPC drop tables, the coin stack blocks, and the chest loot tables
  now. Uses (trade, tax, wages) come with the economy ledger.
- Tools: commands first (`structure place`, `check`, `list`); the Scanner after the garrison milestone,
  saving in our converted format.
- Not ported yet, noted: AW's shields (port them, the look was fine); AW's weapons (not wanted, at least not
  with their old look).

### Source, format, scale, towns, mods (2026-10-28, pending the user's lock)

- Source: AW 1.12.2-2.10.2 (`libs/ancientwarfare-1.12.2-2.10.2.jar`, `MohawkyPackTweaked1.12.2V75`, 1,471
  templates). The 1.20.1 port's copies are the same data; its only real change is a list of entity renames
  (`vindication_illager` to `vindicator`, Ice and Fire's `if_troll` to `troll`, `seaserpent` to `sea_serpent`,
  pixie to allay, ...), which seeds our rename table.
- Proposed format: gzipped vanilla structure `.nbt` per template plus a JSON sidecar (header and validation:
  biomes, dimensions, weight, cluster, territory, unique, min distance, leveling, fill, border, type, author,
  required mods). Vanilla rotation and mirroring then cover every block. Rule 0 (keep the terrain) becomes
  structure void. A readable `.snbt` copy is written in dev for diffs.
- No vanilla flood: the library is not registered as 1,471 vanilla structures. A handful of our own structures
  (a faction site, a town, perhaps per size) each pick a template from our index by AW's rules. Sidecars load
  at startup, block data only when placed. `/locate` gets a few ids (and tags, such as one per faction); a
  `/ancientempires structure locate <template>` can search our record of generated starts.
- Scale: 338 of 1,017 standalone templates are wider than 48 blocks, 153 wider than 96, 62 wider than 160,
  the largest 382. Vanilla places a structure's pieces one chunk at a time as chunks generate, as for ancient
  cities and strongholds, so size is not a problem; AW placed everything at once. Ground height for a start is
  read from the noise (`ChunkGenerator.getBaseHeight`), as villages do, so no neighbour chunk is loaded.
  Leveling, fill, and border are done per chunk while placing. Types: ground 1,269, island 158, water 38,
  lava 6.
- Uniqueness and AW's minimum duplicate distance need global knowledge, but structure starts are computed in
  parallel and should be deterministic. Plan: our structure sets' spacing keeps sites apart; uniqueness and
  duplicate distance come from a thread-safe record of starts, checked when a start is made. Like AW, a
  regenerated chunk may then pick differently. Open.
- Towns (`towns/town_templates/*.awt`, 10: walled and unwalled Empire, Nogg, Norska, Sarkonid, and Undead
  graveyards; 454 town pieces): a town is a layout, not a template. Size in town blocks (`minSize`, `maxSize`
  up to 20 chunks, about 320 blocks for the Empire walled city), `townBlockSize` plots, a road block, lamp
  posts, `uniqueStructures` (the town hall), `mainStructures`, weighted `houseStructures` and
  `cosmeticStructures`, and walls: corner, wall, gate, and side-gate templates laid by `wallPatterns` per side
  length. Port as our town structure: its start lays out the grid, roads, walls, and plots (pure math, no
  world access), then each building is a template piece; vanilla places them chunk by chunk. `.awt` becomes a
  JSON town definition.
- Mods (namespaces in the templates, by templates touched): vampirism 346 (almost all of it is Vampirism's
  saved data on vanilla mobs inside spawner tags, stripped; about 90 real vampire spawns), primitivemobs 285,
  ebwizardry 237, grimoireofgaia 179, rustic 164, mocreatures 122, iceandfire 117, harvestcraft 90,
  dungeonmobs 81, dungeontactics 67, thesummoner 66, eplus 56, mod_lavacow 52, zawa 50, betteranimalsplus 47,
  xreliquary 42, mowziesmobs 42, then a long tail (exoticbirds, explorercraft, twilightforest, charm,
  spartanweaponry, comforts, waystones, quark, ...). Each mod still alive on 1.20.1 gets a rename table
  (blocks, items, entities, biomes) written from its current registry; dead ones get fallback tables
  (to vanilla, to our own content, or to another 1.20.1 mod). Which are alive is to be checked, not assumed.

### Mod status on 1.20.1 Forge (checked 2026-10-28 against Modrinth jars)

IDs used by the templates that still exist under the same name in the current jar (new namespace in brackets):
mocreatures 40/40 (Mo' Creatures Nostalgia Edition), iceandfire 83/96, ebwizardry 62/102 (Electroblob's Wizardry
Redux), grimoireofgaia 41/64 (Grimoire of Gaia 4, alpha), xreliquary 18/21 (`reliquary`), mowziesmobs 11/15, rats
7/8, vampirism 8/12, simplytea 4/5, waystones 2/2, quark 6/21, comforts 2/5, primitivemobs 8/23 (`traveler`,
PrimitiveMobsRevival, partial), astikorcarts 0/3 (`astikorcartsredux`), spartanshields 0/4, silentgems 0/6,
tombstone 0/2. Biomes: biomesoplenty 24/51; byg 0/40 (`biomeswevegone`; 1.12 names carried a `b` prefix, so most
should match once it is stripped).
From CurseForge: harvestcraft 86/120 (now four mods: `pamhc2crops`, `pamhc2trees`, `pamhc2foodcore`,
`pamhc2foodextended`; the misses are mostly the old `...item` suffixes), zawa 7/46 (ZAWA Evolved, alpha; names
gained underscores, `americanbison` to `american_bison` likely), twilightforest 1/2, minecolonies 2/2,
structurize 0/5 (its 1.12 timber frames and shingles were reorganised). Better Animals Plus has no 1.20.1 Forge
build (newest Forge is 1.19.2), so it gets fallbacks. No 1.20.1 version found: rustic, dungeonmobs, dungeontactics,
thesummoner, eplus, mod_lavacow, exoticbirds, explorercraft, familiarfauna, openglider, reptilemod, eerieentities,
pirates, gemsplusplus, and the biome mods `nt`, `environs`, `traverse` (Traverse is Fabric only).
Biome lists become biome tags per template or territory, with modded biomes as optional tag entries
(`"required": false`), so a missing biome mod drops out of the tag instead of failing to load.

### Build order (locked 2026-10-28)

- S1 (done 2026-10-28): offline converter (1.12 → 1.20.1 through vanilla's DataFixer, AW blocks through our table), our template
  reader, commands, unit tests. Converted templates place, AW blocks as stand-ins. Format changed from vanilla
  `.nbt` to our compact `AeTemplate` (vanilla's would need about 2 GB for the largest island); the user agreed.
  All 1,471 convert in about a minute (34 MB); the game test places the Empire church (56k blocks) in 1.7 s and
  a 1.5M-cell island in 2.6 s, chunk generation included. Remap tables so far: AW stand-ins and the moved
  namespaces and port renames; per-mod block and item tables are next, for the user to review.
- S2a (done 2026-10-28): spawners as garrison spawners with roster scrolls and the capture wand (every library spawner
  imports; see CLAUDE.md). S2b (done): coins, coin stacks, AW's loot tables, loot chests with their traps, faction
  NPC coin drops. S2c: protection flag as the anchor with
  claiming, block protection, loot chest lock; coins and loot tables. Milestone: one Empire structure.
- S3: gates as entities (the six AW types) and the Gate Spawner.
- S4: rotation normalization, overlap registry, then world generation (off by default).
- S5: real decor blocks, placeholder siege engines, the Scanner.
- Then chain of command (garrisons are its first real use).

---

## What We Change vs Old AW

Old behavior we do **not** copy:

- Blind placement that overwrites other structures or vanilla features.
- Inconsistent rotation (sideways doors, wrong-facing gates).
- Hard crash or silent corruption on missing modded blocks/entities.
- Spawner that only knows old AW entity class names.

New behavior:

- Bounding-box overlap check before commit (simple placed-structure registry or chunk occupancy set).
- Single rotation applied to the whole template + normalization pass for common directional blocks (doors, gates, stairs, logs, beds).
- Strict fallbacks: missing block → configurable replacement or skip; missing entity → roster lookup, then config fallback, then skip.
- Spawner rule string → current roster / entity registry lookup → spawn our `NpcEntity` (or a loaded mod entity) with correct faction, path, equipment, and skin.
- Optional “required mods” list on the template; world-gen skips the structure if any tagged mod is absent.
- Unknown NBT is stripped or ignored, never crashed on.

---

## Automatic Modded Detection

Yes. This is both feasible and desirable.

### Blocks and block states
- At placement, resolve every rule’s block ID or JSON against the live `BuiltInRegistries.BLOCK`.
- If the ID exists (vanilla or any loaded mod), use it.
- If it does not exist:
  - Use a config fallback (`structures.missingBlockFallback`: air, stone, oak_planks, or “skip”).
  - Log once per missing ID per world (not per block).
- Tile-entity data: if the tile type exists, apply known fields; drop unknown NBT.
- No per-mod plugin is required for ordinary blocks. The registry itself is the detector.

### Entities and spawners
- Spawner / entity rules store a string ID (old AW NPC type, vanilla entity, or mod entity).
- Resolution order:
  1. Exact match in our imported roster → spawn `NpcEntity` with that faction/class/skin/equipment.
  2. Exact match in the current entity registry (vanilla or loaded mod) → spawn that entity.
  3. Config replacement table (`structures.entityReplacements`) or a single default (`structures.missingEntityFallback`: zombie, generic soldier, or none).
- This automatically supports any mod that is loaded at runtime without us hard-coding its IDs.

### Required-mod gate
- Read any mod-ID list present in the `.aws` validation section (or allow a sidecar `.json` next to the template).
- Before world-gen selection, check `ModList.get().isLoaded`. If a required mod is missing, exclude the template.
- Optional stricter mode: if the template contains any block/entity ID whose namespace is not loaded, treat it as requiring that mod and skip (configurable).

### Future-proofing hooks
- A simple reload listener or `/ancientempires structures reload` that re-scans the template folder.
- Datapack or config folder override: users can drop additional `.aws` or replacement JSON without touching the jar.
- Optional “scan and emit” debug command that lists every unresolved block/entity ID in a template so pack makers can fix or map them.

---

## Loader and Placer Outline

1. **Parse** `.aws` into an internal `StructureTemplate`:
   - Header (name, size, offset)
   - Validation (type, worldGen, biomes, dimensions, unique, required mods, weight)
   - Layers (numeric grid)
   - Rules (plugin + data → block state or entity/spawner descriptor)

2. **Resolve once** (cached):
   - Map rule numbers to resolved `BlockState` or entity spawn info.
   - Record missing IDs for the log / fallback.

3. **Placement**:
   - Choose position + rotation.
   - Overlap test against registry.
   - Walk layers, apply rotation, normalize directional states, place blocks.
   - For spawner rules, spawn entities via the resolution order above and (optionally) remove or hide the spawner block.
   - Register the bounding box.

4. **World-gen** (first version can be simple):
   - Chunk or structure-start hook.
   - Filter templates by biome, dimension, required mods, and weight.
   - Place with the overlap check.
   - Config for frequency, min distance, and disable.

---

## Spawner → NPC Wiring

- Old AW spawner stored an entity type string + range/max-nearby settings.
- Our placer:
  - Looks up the string in the roster first.
  - If hit, creates `NpcEntity`, sets faction, role/path, skin, equipment from roster, marks it as structure-spawned (no owner).
  - Copies useful spawner settings (player range, max nearby) onto a lightweight garrison anchor or onto the NPC’s home/aggro if desired.
- Garrison milestone: one structure whose spawners produce the correct faction soldiers that then use existing combat/morale AI.
- Later: structure anchor holds the garrison list for the light hierarchy.

---

## Improvements Worth Adding Early

- Overlap / minimum-distance registry (fixes “eating” other structures).
- Door/gate/stair facing normalization.
- Missing-block and missing-entity fallbacks + one-time logging.
- Required-mod skip.
- Config for fallback blocks, fallback entities, and whether to strip unknown NBT.
- Debug command: place named template at player, report unresolved IDs.

## Improvements That Can Wait

- AW's Loot Chest Placer, so builders can give a chest a loot table fitting the structure (converted templates keep
  AW's per-chest tables, castle treasury, lizardman and so on).

- Full in-game scanner and creative structure tools.
- Jigsaw / vanilla structure export.
- Dynamic block reassignment by biome (old AW feature).
- Complex cluster/unique spacing beyond a simple min-distance check.
- Automatic “this template uses mod X” inference beyond the namespace check.
- Survival builder / drafting station path.

---

## Performance and Safety Notes

- Parse and resolve templates once at load (or on reload), not per chunk.
- Cache “does this block/entity exist” results.
- Placement is infrequent; the cost is acceptable even for large templates.
- Never throw on bad data. Log and fallback.
- Keep structure world-gen frequency configurable and default it conservatively until overlap and performance are proven.

---

## File and Config Sketch

- Templates: `data/ancientempires/structures/` or a config folder override (`.aws` + optional `.json` sidecar).
- Config section `structures`:
  - `enabled`
  - `missingBlockFallback`
  - `missingEntityFallback`
  - `entityReplacements` (map)
  - `skipIfRequiredModMissing`
  - `inferRequiredModsFromNamespace`
  - `minDistance`
  - `maxPerRegion` / frequency
- Commands:
  - `/ancientempires structure place <name>`
  - `/ancientempires structure list`
  - `/ancientempires structure check <name>` (unresolved IDs)

---

## Relation to the Rest of the Mod

- Structures are the missing foundation for faction presence.
- Spawners feed the existing roster and `NpcEntity`.
- Garrison anchor (later) can hold the light leader-UUID list.
- Director (later) can target these structures for raids or reinforcements.
- No change to combat, worker, or UI systems is required for the first working structure.

---

## Success Criteria for the First Milestone

- One real AW `.aws` (e.g. a small empire or bandit camp) loads.
- It places with correct modern block states, no sideways doors, and no overlap on a second attempt nearby.
- Its spawners produce our faction NPCs with the right skins/equipment.
- Missing modded blocks/entities in a test template fall back cleanly and log once.
- World-gen can be toggled and does not crash on unknown data.

That is enough to make the imported factions visible in the world. Everything else is iteration.

---

*This file is the practical structure plan. Update it when the loader lands or when a major placement rule changes.*

---

## Extended Garrison and Binding Notes (appended from later design sessions)

These rules refine how structures interact with the hierarchy, director, and settlement systems once the basic loader exists. They do not change the core placement or resolution logic above.

### Garrison Anchor Behavior

- Each structure that contains spawners or a banner receives a lightweight garrison anchor (the banner itself or a dedicated block). The anchor stores: roster of expected NPCs (as data), owner faction ID, wake/aggro radius, and a dummy-leader flag.
- Owner-faction units bound to the anchor receive a defensive morale buff while inside the structure. Visitors or invaders receive none.
- Higher-ranking commanders (especially the “final” leader of a structure) remain data-only until the player enters the fighting range of the anchor or the anchor/banner is damaged or claimed. This prevents premature pathing out of the intended arena.
- On low health or death of the top commander, the anchor can issue a Sally-out order: remaining roster spawns and engages the current threat.
- Multi-faction structures designate a single owner on the anchor. Other factions present are treated as visitors.

### Foreign Mob Integration

Structures that reference non-AW entities (Grimoire of Gaia, other monster mods, vanilla bosses, etc.) resolve through the standard entity-registry path already described. When the structure is in a defensive state, these entities can optionally receive a temporary-ally flag so they fight alongside the owner faction. No custom AI is required; they use their existing combat behavior.

### Settlement Binding Cross-Reference

Once specialized anchors exist (Town Hall, Fort, Outpost, etc.), structure-spawned NPCs can bind to the nearest appropriate anchor on spawn rather than relying on a large radius. Forts additionally signal the director that normal pathing through their bounding volume is restricted (intrigue/clandestine goals exempt). This is a later integration; the first structure milestone only needs correct spawning and basic combat.

All of the above remain configurable and can be disabled without affecting the loader or placer.
