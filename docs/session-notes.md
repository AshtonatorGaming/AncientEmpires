# Session notes (working cache)

Short state of the work, so a compacted or fresh session picks up without losing the plot. Keep it brief; the
detail lives in CLAUDE.md (architecture, done/not done), STRUCTURES.md (structure plan and findings), DESIGN.md and
VISION.md (vision). Update at the end of each chunk of work.

## Where things stand (2026-10-28)

- Branch `structures` (from perf-and-sidearm, with the vanilla-screen NPC inventory merged: ModularUI gone, EMI works).
  The user's uncommitted `skins.json` edit is theirs: never stage it.
- Done: S1 converter and placement; S2a garrisons and capture wand; S2b coins and loot; S2c flags, records,
  claiming, protection, loot lock; S3 gates (AW's own models) and Gate Spawners; NPC gate use (off by default).
- Test instance: `C:\Users\coolg\AppData\Roaming\.sklauncher\instances\final-testing\mods`. Copy
  `build/libs/ancientempires-0.1.0.jar` there after a build, only when no game is running (`tasklist` for java).
  It holds 27 compat mods plus libraries; EMI on, ModularUI disabled.
- Scratch sources: AW 1.12 jar in `libs/ancientwarfare-1.12.2-2.10.2.jar`; decompile with ForgeFlower from
  `~/.gradle/caches/forge_gradle/maven_downloader/net/minecraftforge/forgeflower/2.0.629.0/`.

## Next, in order

1. S4: rotation polish, overlap registry, then world generation (off by default). The user switches to high effort.
   Open problems: uniqueness and minimum duplicate distance across worldgen threads; terrain prep (AW's leveling,
   fill, border) under huge footprints; one structure id per kind so /locate stays sane; towns from `.awt`.
2. S5: real AW decor blocks, placeholder siege engines, the Scanner (save in our format).
3. Chain of command (sergeant as the brain; DESIGN/VISION), after garrisons.

## Later (user's notes)

- NPC screen visual overhaul (EMI's search bar overlaps the NPC inventory); a character sheet.
- AW's Loot Chest Placer for builders; AW shields as items (not AW weapons' old look).
- Per-mod remap tables for blocks and items (ae_remap), for the user to review.

## Testing habits

- `./gradlew test` (unit), `./gradlew runGameTestServer` (game tests in `StructureGameTests`; the test world keeps
  entities and saved data between runs and has solid ground near tests, so tests clear their own space and stale
  entities; entities tick only near the test origin).
- `./gradlew convertAwsTemplates` re-converts the library and loot tables (about a minute).
