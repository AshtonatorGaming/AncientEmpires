# Session notes (working cache)

Short state of the work, so a compacted or fresh session picks up without losing the plot. Keep it brief; the
detail lives in CLAUDE.md (architecture, done/not done), STRUCTURES.md (structure plan and findings), DESIGN.md and
VISION.md (vision). Update at the end of each chunk of work.

## Where things stand (2026-10-28)

- Branch `structures` (from perf-and-sidearm, with the vanilla-screen NPC inventory merged: ModularUI gone, EMI works).
  The user's uncommitted `skins.json` edit is theirs: never stage it.
- Done: S1 converter and placement; S2a garrisons and capture wand; S2b coins and loot; S2c flags, records,
  claiming, protection, loot lock; S3 gates (AW's own models) and Gate Spawners; NPC gate use (off by default);
  S4 world generation (on by default, `structures.worldGen`), AW territories and their settings, the overlap registry;
  S5 decor blocks, coffins, sound blocks, stakes and statues, siege engine placeholders, the Structure Scanner.
- The documentation pass is committed (0e0fa0b): docs/ for pack makers, package-info, architecture; world generation,
  decor, and siege are documented since. Keep docs/ current with each stage.
- Test instance: `C:\Users\coolg\AppData\Roaming\.sklauncher\instances\final-testing\mods`. Copy
  `build/libs/ancientempires-0.1.0.jar` there after a build, only when no game is running (`tasklist` for java).
  It holds 27 compat mods plus libraries; EMI on, ModularUI disabled.
- Scratch sources: AW 1.12 jar in `libs/ancientwarfare-1.12.2-2.10.2.jar`; decompile with ForgeFlower from
  `~/.gradle/caches/forge_gradle/maven_downloader/net/minecraftforge/forgeflower/2.0.629.0/`.

## S4 shape (worldgen package)

- One structure type `ancientempires:aw_site` with `surface` land (beard_thin) or sea (none); structure set
  `aw_sites`, random_spread spacing 1, so every chunk is a candidate and the code rolls AW's chance.
- `AwSiteIndex` (sidecars, width cap 256), `Territories` (Voronoi cells, AW's territory weights),
  `TerritorySettings` (AW's territory_settings.json as `data/*/ae_territories`), `AwSites` (placed sites by region,
  generator to level, atomic `claim` in the pieces builder so /locate claims nothing), `AwSitePiece` (per chunk via
  `AePlacer.placeInChunk`).
- Not yet: AW's 28 islands wider than 256 (vanilla's 8-chunk reference reach), sky/underground/harbor/lava types,
  towns from `.awt`, AW's preventNaturalHostileSpawns.

## In-game testing (2026-10-28, latest commit 6b83ccb, jar installed)

Fixed from the user's first play test:
- No structures: `AwSites.level` missed every lookup (a mod in the pack changes ChunkGenerator hashes). Now an
  IdentityHashMap plus a server fallback. `/ancientempires structure survey [radius]` (`AwSurvey`) counts why sites
  are turned down, without building; use it first when generation looks wrong.
- Generation crash: a template's command block had 1.12 plain-text CustomName. `AePlacer` now cleans that and
  catches any bad block or entity data per cell (logged, skipped), so generation never crashes on AW data.
- Banners: faction flags are vanilla's banner model with AW's textures (`textures/entity/aw/banner`), swaying; the
  flag item draws the banner (`FlagItemRenderer`, builtin/entity).
- Decor: items show a diagonal block view, one icon per wood (`ancientempires:variant` item property in `DecorClient`);
  1.20 woods added (mangrove, cherry, bamboo, crimson, warped); golden idol top hidden; altar candles and cloths keep
  AW's colour (`AwDecorBlockEntity` Color, block and item tints, 16 dye colours in the tab).
- Altar blockstates without properties use multipart without `when`.

Still to check by eye: whether structures now appear often enough, siege engine orientation, coffin orientation, seat
height, sound blocks, the Scanner. Not done: coloured candle flames, statue poses, totem multi-block hand placement,
coffins' hidden parts on hand placement, item icons leave out turned submodels (table shows one leg).

Second play test (2026-10-28): decor icons now turn every piece to show its front (AW's models face north or south
unturned), draw turned parts, and stack tall pieces' tops (shrunk into one block's space: vanilla refuses elements
past -16..32); missing faces in AW's Blockbench models are filled from the opposite face (the stake's see-through
post); altar candles and cloths keep their colour when broken (copy_nbt); the sound block is usable (held block =
disguise, empty hand = tune editor); structures cluster (`clusterChanceBoost` 4 within `clusterRange` 96).
Still unseen: whether the icons look right in game, whether the rate now feels right.

Third play test (2026-10-28, commit after 33e35fd; jar NOT installed, the game was running): sound block disguises
were drawn black (light inside a solid block is 0; now the brightest side's); doors laid from their upper half (AW's
rule), so double doors pair; tables and benches take legs from neighbours (AW's getActualState); gates play AW's own
gate sound once per move (and AW's break sounds); water/island/harbor templates fill empty cells below the ground
layer with water (AW's handleClearAction); textureless faces in AW's idol and totem models dropped. Admin nametag
over NPC spawners (33e35fd). Open: terrain blending around land sites (beard_thin cuts pits into slopes; AW smoothed
its borders), not yet changed.

## Next, in order

1. Finish the play test above; fix what the user finds.
2. Towns and cities (`.awt`), which also need a way past vanilla's 8-chunk reach for the giant islands.
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
- Bash heredocs with apostrophes in the body fail in this shell; write scripts with the Write tool instead.
- Decor and vehicle assets are generated by `tools/aw-assets` (see its README) from AW's jar; regenerate
  rather than hand-edit aw_decor/blocks.json, aw_vehicles/models.json, the decor blockstates and models.
