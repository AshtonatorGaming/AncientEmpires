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
