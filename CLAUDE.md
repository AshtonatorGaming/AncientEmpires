# CLAUDE.md

Ancient Empires is a Minecraft 1.20.1 Forge (47.4.28) mod. It is a cleaner, more
performant reimplementation of Ancient Warfare 2's NPCs and structures. GPL-3.0,
derived from AW2. Assets (skins, structures, spawner ideas) are reused where practical.
Java package: `com.ancientempires`. Mod id: `ancientempires`.

`DESIGN.md` holds the long-term vision (Tiers 1-5). Work the foundation before the tiers.
The older AncientCombat and AncientTactics repos (same GitHub account) were compat mods
that mixed into AW2. Their features are meant to be native here. Port ideas and numbers
from them, not their code. Worth porting, rewritten: `FleeNavigation` (cliff-safe retreat),
`Ballistics` (aim lead; compare with `NpcBowAttackGoal.gravityLift` and keep the better one),
`WeaponClassifier` (one place for ranged/throwing/melee, but cache it per item class
instead of reflecting per call), Skirmisher thresholds (about 4.5 to 28 blocks).

## Rules that must hold

- One combat NPC base class (`NpcEntity`). Roles (archer, soldier, ...) are equipment
  checks or data, never separate entity classes.
- Owned NPCs (have an owner, home, follow, inventory commands) and faction NPCs
  (faction id, no owner, creative-editable) stay distinct.
- The mod owns the attack system. Better Combat is a soft dependency for weapon
  attributes and animations. EMF/ETF is soft, for player-style animation. Do NOT require
  Better Mob Combat or similar bridge mods. Every soft dependency must be absent-safe.
- Sidearms live in the backpack, not a dedicated slot.
- Hold position is ranged-only. Melee NPCs do not get it.
- Backpack persistence is correct. Do not reintroduce merge-and-slide. Slots are saved
  by index (`Slot` key) in `NpcInventory`. Slots 0-5 are vanilla equipment; the 3x3
  backpack starts at slot 6.
- Prefer Goals, NBT flags, capabilities, datapack JSON, equipment-driven behavior.
  Performance first. See the performance rules below.

## Architecture (as read from the code)

- `entity/npc/NpcEntity` - the NPC. Goals come in sets by path (`refreshGoalSet`, checked each tick, swapped when the
  path or a levy changes): every NPC keeps float, doors, gates, flee, burning, sun, melee (a cornered civilian's stand),
  upkeep, follow, sleep, home, wander, looks, and the hurt-by target; a fighter (soldier path or levied) adds surrender,
  bow, skirmish, shield, medic, order, formation, hold, owner defense, and the target scan; a civilian adds job, priest,
  governor. Priorities as listed next. Goals: 0 Float, `OpenDoorGoal`, `NpcNightDoorsGoal`;
  1 `NpcBowAttackGoal`, `NpcShieldGoal` (no flags), `NpcFleeGoal` (civilians only);
  2 `NpcMeleeAttackGoal`; 3 hold position, `NpcUpkeepGoal`; 4 follow, sleep, priest, and `NpcJobGoal`
  (every hands-on trade in one goal: the main-hand tool picks the farmer, woodcutter, miner,
  rancher, hunter, fisher, or cook goal, so idle NPCs pay one check, not seven); 5
  `NpcReturnHomeGoal`; 6+ idle.
  Targets: 1 and 2 `NpcOwnerDefenseGoal` (DEFEND, ASSIST), 3 `NpcHurtByTargetGoal` (with
  ally alert), 4 `NpcTargetGoal`: one staggered scan over a shared per-cell candidate list
  (`NpcTargetCache`), faction filter first, sight checks nearest first.
  `targeting.legacyScan` restores the two vanilla goals.
- `NpcTargetMemory` (one sight check per tick while a target is held) decides when an
  unseen target is dropped, instead of vanilla's 3 seconds. Out of sight, melee and
  archers go to the last sighting (archers unless holding), search there for
  `targeting.memoryTicks`, and give up after `pursuitTicks` without a sighting. Gaining a
  target counts as a sighting, so an ally's alert sends helpers to where the fight was.
- Bow vs melee are two separate goals split by `isHoldingBow()`. The melee goal only runs
  when it is false. A bow in the main hand keeps the archer goal for the whole fight.
  Crossbows are not bows here.
- `combat/AttackProfiles` resolves the next swing (Better Combat attributes if present,
  vanilla cooldown otherwise, fist preset for empty hands). `AttackSwing` is the result.
  Two one-handed BC weapons dual-wield: combo steps alternate hands, and an off-hand swing
  swaps that weapon's main-hand modifiers in (`withOffHandModifiers`), as BC does for
  players. Every swing then goes through `slowWindup` (`combat.npcWindupScale`).
- `NpcMeleeAttackGoal` starts a swing inside `engageRangeFactor` of reach and checks the
  full reach at the hit. During the wind-up it closes until the target is inside `windupKeepRange` (0.8)
  of reach, and a wind-up clears any leftover sidestep input (it slid attackers out of reach mid-swing).
  Every movement mode (duel drift, line sidesteps, the wind-up) keeps the foe at least `minGapReach`
  (0.55) of the NPC's own reach away, never closer than both bodies plus `minGap` (0.5); duelists had
  crept into each other. Being per-fighter, a dagger still closes in while a spear keeps its distance. Facing allows for the target's angular width; touching bodies
  always count as facing.
- `combat/BetterCombatBridge`, `MobCombatBridge`, `MobCombatHooks` are reflection bridges.
  BC weapon snapshots are cached per item, stamped on BC's `containers` map identity.
- Better Mob Combat (optional) owns timing and hit finding when installed. Its default
  `mobs_check_for_same_entity_type` drops NPC-on-NPC targets (all NPCs are one entity
  type), so `finishMobCombatSwing` lands the goal's own hit when its callback never fired.
  Without it, the internal wind-up and `NpcBetterCombatDriver` handle everything.
- `NpcSkins` + `assets/ancientempires/npc/skins.json` - skin catalog (45 factions plus a
  large `misc` group). `NpcRoster` maps old AW NPC types to equipment/skins.
  `NpcSkinSelector` is the three-level selector screen (faction, class, skin).
- Client: `NpcRenderer`, `NpcSkinLayers` (outer layers, optional 3D Skin Layers),
  `NpcBetterCombatDriver` (per-NPC animation session), vanilla inventory screen (`NpcScreen`;
  path chooser, stats sheet, and skin selector are Forge gui layers over it, `NpcPopupScreen`).
  ModularUI was dropped: its EMI mixin targets `RecipeScreen.mouseScrolled` with `remap=false`,
  which only exists in dev, so it crashed production games with EMI. Its jar in `libs/` is unused.
- Player Animation Lib only animates real players. For NPCs the driver does by hand what
  its player hooks do: part rotations and bends (`updatePart`), the `body` channel on the
  render matrix (`NpcRenderer.setupRotations`), the waist bend (filling the model's
  `IMutableModel` emote supplier, which that library clears for non-players every
  `setupAnim`), and tilting the held-item layer by the body bend. The weapon stance stays
  on under attacks, as BC does. The server syncs the real wind-up ticks
  (`getAttackPresentationWindup`) so the clip and trails land on the hit.
- `NpcTrailParticles` triggers Better Combat Particle (`bc_particle`) trails locally at the
  hit frame (that mod only listens to BMC's mob event). `NpcEggColors` and
  `NpcSpawnEggItem.getColor` give faction scroll tints; Forge's own spawn egg handler
  reads `getColor`, so both agree.
- Spartan spears take the trident's Better Combat attacks (`data/spartanweaponry/weapon_attributes/base/spear.json`,
  overriding Spartan's single stab; mods.toml orders this mod after Spartan so its data wins), keeping
  reach 3.5 and one hand. The user's pack has Malfu's Combat Animation datapack, which gives the trident
  an 11-step combo of stabs and sweeps; without it the spear gets Better Combat's plain trident stab.
- Libs in `libs/` are jars wired through `fg.deobf` in `build.gradle`. Soft deps are
  `compileOnly`.
- Structures (`structure/`, plan in STRUCTURES.md; S1 done). AW's 1,471 templates (1.12 data, from
  `libs/ancientwarfare-1.12.2-2.10.2.jar`) are converted offline by `./gradlew convertAwsTemplates`
  (`AwsConvertTool`: `AwsTemplate` reads `.aws`, `AwsConverter` + `LegacyFixer` upgrade it through Mojang's
  DataFixer, which runs without the game) into `data/ancientempires/ae_templates/<path>.nbt` plus a `.json`
  sidecar (AW's header and validation, biomes upgraded), with a report in `build/aws-convert-report.txt`.
  `AeTemplate` is our own format: vanilla palette and data version, blocks as packed palette indices in 16x16
  column tiles (vanilla's `.nbt` would need about 2 GB for the 27M-cell islands); palette 0 is structure void.
  The fixer's gaps are filled by hand: 1.12 block entity contents (items, spawners, skulls, banners, signs,
  beds, flower pots) and door halves; AW saved states unconnected, so `AePlacer` recomputes every block's
  shape from its neighbours after placing, except doors: AW built a door from its upper half (the whole state), so the
  lower half is laid from the upper. Water, island, and harbor templates' empty cells below the ground layer are water
  (underwater ones all), as AW's validators cleared them, so the sea does not drop around a boat. Non-vanilla blocks and AW's own keep AW's rule in the block entity
  (`aeRule`) and resolve when placed through `AeRemap` (registry, then `data/*/ae_remap/*.json` tables, then
  namespace renames, then `structures.missingBlock`), so a mod added later just works. `AeTemplates` indexes
  sidecars at datapack reload and caches loaded templates up to `structures.templateCacheMb`. AW's offset y is
  the first layer above ground, so the build key goes at a player's feet. `/ancientempires structure list`,
  `place <name> [rotation]`, `check <name>`, `export <name>` (vanilla `.nbt` in `generated/` for a Structure Block).
  Templates go by their short name (the file name, unique); `structure list` shows the categories (AW's folders),
  `list <category or part of a name>` the templates.
  Gate orientation in templates is a 1.12 EnumFacing index (2 north .. 5 east), read with `from3DDataValue`.
  A flag is two blocks: `FlagTopBlock` above is the banner's upper part (clicks and breaking go to the flag). Using a
  container in an unclaimed faction structure outlines its flag in gold through walls, and a failed claim outlines the spawners still holding it in red (`BlockOutlines`, `OutlinePacket`);
  world-generated records find and tie their flag then. AW gates, NPCs placed directly, vehicles, and flags are kept but not placed yet (S2, S3, S5).
- Garrisons from structures (S2a): an AW advanced spawner becomes our `NpcSpawnerBlock` (`AwSpawnerImport` writes
  the spawner's saved form and loads it): one-shot ones GARRISON, ones with more to give REPEATING, redstone ones
  REDSTONE; AW's per-block `playerRange` is kept (it staged the fight: walls first, leaders last). AW NPC entries
  become roster scrolls (`factionName` + type) with the spawner's custom name, gear, and skin (`NpcSkins.byFile`);
  other mobs a `MobScrollItem` (id plus data, resolved at spawn through `AeRemap`, else
  `structures.missingEntity`). All 35,354 library spawners import; 2 name an AW worker with no roster row. The
  spawner gained AW's options: `ClearXp`, `Pool` (one slot by weight), `Hostile` (blocks claiming), a per-scroll
  least count (`AeMinCount`), and spawn radius 0 (a post: on the spawner's own spot). `CaptureWandItem` (admins):
  right-click a mob to pick it up, removing it from the world as AW did (our NPCs as their scroll via
  `NpcEntity.captureScroll`), then each right-click
  on a block places another one-soldier post, or adds to a spawner clicked; sneak-use clears.
- Coins and loot (S2b): AW's coins (`coin_copper` Copper Owl, `coin_silver` Silver Stag, `coin_gold` Gold Dragon,
  `coin_ancient`; AW's one coin texture tinted in `NpcEggColors`) and `CoinStackBlock` (layers 1 to 8, one stack
  item of 8 coins each, AW's own models; structures map AW's size 8 to 64). The converter also turns AW's 185 loot
  tables into `data/ancientempires/loot_tables/aw/` (`AwsLootConverter`): vanilla items flattened by their 1.12
  data (a random data range becomes one entry per value, weights rescaled), coins by metal, and every other item
  an `ancientempires:item` entry (`AeLootItem`, `ModLoot`) resolved when rolled through `ae_remap` `items` (a
  list is tried in order: AW spears become Spartan's, else a vanilla sword), so a missing mod costs one entry,
  not the table. Faction NPCs drop AW's table for their type (`NpcEntity.getDefaultLootTable`; owned NPCs only
  their gear). AW loot chests, baskets, and urns become chests, barrels, and decorated pots (`AwLoot`): the
  table set for vanilla to roll, and AW's extras (more rolls, the trapped mob, potions, splash within 4, the
  message) kept as `AeLoot` in the block entity's persistent data and done on first right-click; a pot spills
  its loot.
- Flags and claiming (S2c): `FlagBlock` (AW's faction flags, rotation 0 to 15, drawn by `FlagRenderer`: the faction's
  banner art from `textures/gui/banner`, or once claimed a post and cloth in the claimer's two colours). One
  `FlagItem` with `Faction` and `Protection` in its `BlockEntityTag`: the creative tab holds a Flag and a Protection
  Flag per faction; admins sneak-click a flag to cycle its faction. Placing a structure whole records it
  (`StructureRecords`, per-dimension saved data with a chunk index): bounds, owning faction (its protection flag's,
  else its garrisons' most common), flag, claimer; its protection flags are tied to the record. A hand-placed
  protection flag guards 16 blocks around it. `StructureClaims`: AW's conquest (score of soldier-path NPCs of the
  faction or hostile to the player, 1 / elite 2 / leader 5, plus 1 per hostile spawner still holding people, must be
  under `claimThreshold` 5; a failed claim makes them glow and names spawners; a won one costs
  `claimStandingCost`), block protection (`protectionMultiplier` 100 slower mining in an unclaimed structure), and
  the loot lock (containers shut while one of the faction sees you; `allowStealing` otherwise). Imported garrison
  trigger ranges are scaled by `structures.triggerRangeScale` (1.5) and floored at `triggerMinRange` (10): tight
  structures spawned their people on top of the player.
- Gates (S3, `gate/`; read from AW's decompiled 1.12 code): `GateEntity` spans its opening (`pos1`, `pos2` as given,
  one block thick) and, while closed, fills it with `GateProxyBlock`s (invisible, solid, unmineable; clicks and blows go
  to the gate), so collision and paths treat it as a wall; opening clears them. Seven `GateType`s: vertical wood and
  iron (the wall rises), single (slides away from `pos1`), double (halves part), and the drawbridge (turns 90 degrees
  outward, its deck proxies laid flat once down; it lowers only onto open air). Redstone at either bottom corner
  toggles, as AW's did. AW's damage: 80 health (`gateHealth`), arrows nothing, explosions ten times, an axe half on
  wood, a pickaxe a half or third on iron, else a quarter. Who may use one: admins, its owner (or team), for a gate of
  an unclaimed faction structure anyone not hostile to the faction, once claimed the claimer; no owner and no
  structure: anyone (AW's template gates). Owners and admins sneak-click to pack it. `GateRenderer` is AW's own
  ModelGateBasic and ModelGateBridge rebuilt box for box on AW's sheets, with AW's per-cell frame and wall logic in
  AW's flipped space. `GateSpawnerItem`, one per type: click two corners, then again to build. Structures build
  AW's `awGate` entries closed (`AePlacer.placeGate`). `gateMoveSpeed` 0.05 (AW's 0.025 took ten seconds for a
  five-high gate), `gateBridgeSpeed` 1 degree a tick. AW's NPCs never opened gates (only its siege-vehicle
  pathfinder did). `gates.npcUse` (off by default, beyond AW): a player's own NPCs path through their owner's gates
  (built or claimed; `NpcGateNavigation` reads a permitted gate's proxies as a wooden door, so night door rules apply)
  and `NpcGateGoal` opens the gate ahead and closes it behind; never a drawbridge, a held fort's gate, while
  fighting, or with a monster or a faction NPC hostile to the owner within `npcHostileRange` (16). A closing gate
  with someone in the opening reopens instead of shutting them in.
- Template text: 240 of AW's templates are Latin-1, not UTF-8; `AwsConvertTool.text` falls back to Latin-1 (their
  signs' section signs had become question marks), and sign lines drop formatting codes.
- Decor (S5, `block/decor`): AW's 31 decor blocks are one generic `AwDecorBlock` each, described by
  `assets/ancientempires/aw_decor/blocks.json` (properties and per-state collision boxes), with blockstates, models, and
  textures generated from AW's 1.12 assets by a scratch script (Forge v1 blockstates, submodels and texture overrides,
  turned into vanilla multipart). `facing`/`axis`/`lit` are vanilla properties so structures turn them; other values are
  `AwStringProperty`. `visible=false` parts are the hidden tops of tall pieces (`LAYOUTS`: AW's onBlockPlacedBy shapes),
  placed and broken together; part 0 drops. Seats (`SeatEntity`), braziers and fire pits (flint and steel or a torch,
  water bucket), chairs' facing from AW's tile data. Coffins (`CoffinBlock`, `CoffinBlockEntity`, `CoffinRenderer`: AW's
  models part for part, eight ways, lid, loot through `AwLoot.trigger`), gravestones 6 to 8 and stakes and statues
  (`AwDecorBlockEntity`; the mob drawn frozen, statue poses dropped), sound blocks (`AwSoundBlock`: a held block sets the disguise, an empty hand opens AW's tune editor, `SoundBlockPacket`; disguise drawn by
  its renderer, AW's tune, sounds in `sounds/structures`). Siege engines (`siege/`): `SiegeEngineEntity` placeholders from
  templates' `AWVehicle` entries, drawn from `aw_vehicles/models.json` (AW's model classes converted by a scratch script:
  part trees, turret formulas, rough sizes) on AW's textures by material level. Scanner (`StructureScanner`,
  `ScannerItem`): corners and a build key, saved as `AeTemplate` and sidecar into the game folder pack
  `ancientempires/scanned` (`AddPackFindersEvent`, required, every world), turned so the key's facing is the front.
- World generation (S4, `worldgen/`, on by default: `structures.worldGen`; read from AW's decompiled
  `WorldStructureGenerator`, `TerritoryManager`, `WorldGenStructureManager`, and validators). One vanilla structure type,
  `ancientempires:aw_site`, as two structures (`aw_site` land with `beard_thin`, `aw_sea_site` water and island types)
  in one structure set spaced one chunk apart, so every chunk is a candidate and /locate works. `AwSiteStructure`
  does AW's steps: roll `generationChance` (AW 0.075) times the territory's multiplier (times `clusterChanceBoost`
  within `clusterRange` of a standing structure, so they gather as AW's did); the chunk's territory
  (`Territories`: jittered Voronoi cells of `territoryCellChunks`, each picked from the biome at its centre with AW's
  weight, its templates plus the generic ones times `territory_weight_multiplier`); templates of that territory or
  generic that list the biome and dimension, fit the cell's cluster budget (`chunkClusterValue` times the territory's
  multiplier over its chunks, capped by its radius), are not a placed unique or within their minimum duplicate
  distance, and suit the spot (island water depth, ground height band); weighted, big ones less away from the cell's
  centre. Then AW's border check around the template (ground within leveling and fill, at least `groundSlope` 3; water
  deeper than fill; island floor within its depths), clear of other structures by `siteMargin`, not within
  `spawnProtectionRadius` of spawn. AW's per-territory tuning is its own file, `data/*/ae_territories/*.json`
  (`TerritorySettings`). The site is taken in the pieces builder, which runs only for real generation (not /locate):
  `AwSites.claim` checks and takes it in one step per dimension, since generation is threaded, and the record is
  written on the main thread. On land `AwSitePiece` first shapes the ground as AW's ground validator did (no vanilla
  terrain adaptation): cleared above the footprint, filled up to the floor, and a border ring (template border, 3 to 16)
  eased from the floor to the noise height just past it (`BorderCurve`, AW's SmoothingMatrixBuilder curve). It
  places its template a chunk at a time (`AePlacer.placeInChunk`): chunk
  edges are marked for vanilla's post-processing, which reshapes them once neighbours exist; an air cell never opens
  a gate filled from the next chunk; protection flags guard record 0 and find their record by position when claimed.
  Every whole placement is now recorded, factionless ones too (only so world generation keeps clear;
  `StructureRecords.at` answers faction structures only). `/ancientempires structure place` warns on overlap.
  `AwSiteIndex.MAX_WIDTH` 256 leaves out AW's 28 widest islands: vanilla links a structure only to chunks within 8 of
  its start. Sky, underground, harbor, and lava types are not placed yet.

## Done

NPC inventory screen (equipment cross, backpack, name field, skin button, command buttons);
unarmed dual-fist combo via the Better Combat fist preset; inventory persistence and repack
(item keeps name, equipment, skin, owner, flags; not the home, so a placed NPC calls its new spot
home; priest revival keeps it); faction/owned split with the mass AW
import; three-level skin selector with search; Player Default and Custom/Misc tabs; archers
keep shooting at close range and never punch with a bow; home set/clear and return-home;
outer skin layers; sidearm fallback (`NpcSidearm`, `WeaponClassifier`, `sidearm` config
section, `AeSidearmBowSlot` NBT marker undone on load and before repack); shared target
scan; Better Combat snapshot cache; dual wielding; engage range and NPC wind-up scale;
full-body attack animation (body channel, waist bend, stance kept under attacks);
bc_particle trails on native swings; NPC-on-NPC hits under Better Mob Combat; repack into
the NPC's own roster scroll; the Ancient Warfare baseline below (owner defense, ally
alerts, admin escort, doors, day/night home, shields, bash, feints, skill tiers,
civilians); target memory; cavalry; faction relations and player standing; the Settler recipe (2 gold, a food bundle, a bed; `data/ancientempires/recipes`) and AW's food bundle; Settlers' Post immigration; hands-on farmer, woodcutter, and miner (Crop Farm, Tree Farm, Quarry); self-fed upkeep and the Town Hall's fallen records; burning panic and mod-gated reaction poses; medics, priests, and sleeping in beds; ranchers (Animal Pen), hunters (Hunting Lodge), fishers (Fishing Dock), and cooks (Kitchen); the standings screen with AW banners; paths, settlers, equipment roles, emergency levy, and proficiency.

## Not done yet

Agreed order (user, after the combat pass; DESIGN.md Tier 1 first, combat tweaks only from play):
A. Governor: done (`NpcGovernorGoal`, `governor` config). An owned Worker-path NPC holding a map (role
   label Governor) walks by day to its owner's nearest Town Hall (`NpcUpkeep.hallFor`) and stays within
   6 blocks; every `pulseTicks` the owner's workers within `auraRadius` (24) work `workBonus` (15%)
   faster (`NpcEntity.govern`, read in `getWorkSpeed`), and the hall gets `taxItem` (gold nugget), one
   per worker per `taxTicksPerWorker` (2400). Later: settlement heartbeat (Tier 4), tax by production.
B. Structure templates: AW structures generating in the world with garrison spawners (Tier 1; the
   spawner exists). First milestone: one faction structure with a working garrison.
C. Skirmisher: done (`combat/NpcSkirmishGoal`, `SpartanThrowing`, `skirmish` config; AncientTactics'
   NpcAISkirmishAttack rebuilt). Holding a Spartan throwing weapon, an NPC throws from `minRange` 4.5 to
   `maxRange` 28, winding up only while still (Spartan's charge ticks), `throwCooldownTicks` 40 apart,
   aimed with the archers' gravity lift; inside `dangerRange` (6) it kites to a safe spot. Cornered
   (sidearm range) or out of throws it draws its best backpack melee weapon (`NpcSidearm`, now for any
   ranged primary: bow or thrower) and the melee goal takes over. Spartan only throws for players, so
   the projectile is built by reflection (item `XItem` to entity `XEntity`), ammo is Spartan's own
   `AmmoUsed` count, restocked one per `restockTicks` out of a fight; thrown ones are not pickups.
D. Banner Bearer: done. A soldier with a banner in its off hand or anywhere in its backpack (`NpcEntity.getCarriedBanner`,
   synced) is the standard bearer; the banner is worn on its back like a sashimono (`client/NpcBannerLayer`: vanilla's
   banner model at half size with its patterns, never the small item in hand), so its hands stay free. It adds `morale.bannerBonus` (10) to allies
   within `morale.range`, shown as a reason; its death shocks them `bannerDeathShock` (15). Role label
   Standard Bearer. Later: faction banner rows, planting the banner as a rally point.
E. Squads under leaders and a command screen (groups, formations, orders), then squad morale and patrols.
F. Combat, player-doable: done. Backstep dodge (`NpcMeleeAttackGoal.dodging`): an NPC without a shield,
   `combat.dodgeLeadTicks` (6) before an NPC's visible wind-up lands, backsteps for `dodgeTicks` (7) at
   `skill.dodgeChance` (0.1 to 0.4 by level), once per swing; players show no wind-up, so are not dodged.
   Flanking (`shouldFlank`, `flank`): a fighter without a shield facing a raised shield (within
   `flankAngle` 70 of its facing) circles the shorter way to its side, at striking distance; footwork
   only runs while waiting, so a lowered shield is still struck at once.
Done since: daylight (`NpcSunGoal`, `sunlight` config, factions.json `sunlight`): undead (`burn`) burn in
   daylight like zombies, a helmet taking the wear (`NpcEntity.burnInSun`, vanilla `isSunBurnTick`), and seek
   shade once alight; vampires (`avoid`, Ancient Warfare's) keep to shade by day whenever not fighting.
   By day the burning panic (water) stands aside for these factions, since the two goals fought; vampires'
   paths avoid the sun (vanilla `setAvoidSun`), so wandering or going home does not walk them into it; the
   shade search lifts the home leash and tries 24 spots.
   Night Watch (`NpcNightLight.onWatch`, `nightLight` config): soldiers awake at night or in the dark (the
   lights' test) hit `watchDamageBonus` (10%) harder (a transient modifier) and keep `watchMorale` (5) more
   morale (a named reason). Sight range is untouched: the target scan shares one candidate list per area.
   Skins: randomizing works; 217 of 606 roster rows have a one-skin pool (beast, monster, many spellcasters,
   bards, traders, smaller factions), which reads as spotty. Fix is data in skins.json (the user curates it).H. Sidearms beyond ranged (later, the user wants this): a pikeman with a reach weapon swapping to a shorter one
   in close quarters, and similar weapon-for-the-distance swaps; `NpcSidearm` already does it for bows and
   throwers.

I. Later, from the user's testing: a visual overhaul of the NPC screens (EMI's search bar overlaps the NPC inventory now);
   a character sheet ("Champion Sergeant Halberdier, Lv 17": rank, band, role, level, and the like); AW's Loot Chest
   Placer for builders to set a chest's loot table (structures keep AW's per-chest tables already).

1. Ancient Warfare baseline still missing: lance charges and other mounted tactics,
   day and night patrols, and the
   behavior behind the remaining roles (bard singing, trader, courier, worker
   jobs beyond the farmer, woodcutter, and miner, commander, patrol). Roles are labels from
   equipment until that behavior exists.
4. Relations follow-ups: trading (AW's only way to raise standing), the nemesis bonus
   (killing one faction raises standing with its rival), teams sharing their worst
   standing. Then worksites, town hall, orders, the structure
   template port, and Tier 2+ roles.

## Ancient Warfare baseline

Ported from Ancient Warfare 1.6.1's goals, read from its bytecode in `libs/` (numbers are
its own unless noted). Before adding behavior, check what AW did first.

- Owner defense: owned NPCs attack whoever hurts their owner and whatever the owner hits,
  once per new hit, within `allies.ownerDefenseRange`. An owned NPC never targets its owner.
- Ally alert: a hurt NPC sets the attacker as target on allies (`isAlly`: same faction, or
  same owner) in `allies.alertRange` that have none. Vanilla's alert hit enemies too, so it
  is replaced. `NpcTargetGoal` keeps such a handed-over target while it is valid.
- Admin escort: a creative player can make a faction NPC follow them (`getFollowLeader`).
  The leader is not saved.
- Doors: navigation opens hand-openable doors (`DoorBlock`, modded included); vanilla
  `OpenDoorGoal` opens and closes them. At night doors stay shut unless following or fighting.
- Home: a vanilla restriction (`home.wanderRadius`, 5) keeps wandering near home. By day,
  past 50 blocks walk back to 20 (owned) or 30 (faction). Faction NPCs keep night hours
  (back within 5). Rain sends NPCs home until it stops. Faction archers hold a 3-block post.
- `NpcGuard` (`npc.getGuard()`) owns all shield state and decisions: raise and lower, the strike
  commitment and turn-taking (`readyToStrike`), riposte, guard points and breaks, axe disable,
  brace and bash readiness, stagger, and the perfect-block plan. `NpcEntity` keeps only the
  vanilla hooks (`blockUsingShield`, `hurtCurrentlyUsedShield`, `knockback`) that call into it.
- Shields: `NpcShieldGoal` blocks against the target swinging in range or aiming a bow, and
  (beyond AW) anyone within `shield.aimWatchRange` aiming at the NPC. Shields are found by
  Forge's `SHIELD_BLOCK` action (`WeaponClassifier.isShield`), so Spartan Shields work.
  Blocked hits wear the shield; axes and Spartan's Shield Breach (`canDisableShield`)
  disable it. Guard stance (`shield.guardInRange`): the shield stays up while an enemy is
  in `blockRange`, since vanilla shields only block after 5 ticks raised (blocking only on
  the swing played like a parry). A block is a commitment (`NpcEntity.readyToStrike`): an NPC
  never swings with its shield up; the shield comes down only after `minBlockTicks` (12)
  raised, stays down through the swing, and the swing starts `strikeDelayTicks` (6) later.
  As in AW (its block goal held the attack AI's control flag), blocking and attacking never
  overlap: the shield is never lowered while the target is mid-swing, and a strike being
  readied is cancelled if the target starts one. A blocked hit opens `riposteTicks` to strike
  without the minimum. Behind a raised shield the NPC advances at `advanceSpeed` (AW: half).
  Between two guards, whoever starts readying a strike first goes (`strikesBefore`); the other
  holds its guard, blocks, and ripostes. Only between two guards: against a foe with no shield (always "readying")
  deferring meant a shield NPC never got its turn. Against a foe that cannot block (no shield, or
  shield knocked out) a shield NPC presses: `shield.pressMinBlockTicks` (4) and `pressStrikeChance` (0.5)
  replace the patient 12 and 0.15, so it still guards but goes for the kill. Heavy weapons (axes, shield
  breachers like Spartan halberds, Better Combat two-handers; `NpcGuard.heavy`) drain guard
  `shield.heavyGuardDrain` (1.75) times as fast, players' too. Next, agreed with the user (all things a
  player can also do): a backstep dodge of visible NPC wind-ups by skill, and flanking raised shields
  while still taking frontal openings. Past the minimum, a strike comes at a random beat
  (`strikeChance` per tick). Guard (`guardPoints`, like Chivalry stamina): blocked damage
  drains it, it recovers at `guardRegen`, and at zero the shield is knocked down for
  `guardBreakTicks`, so shield walls do not stalemate and players can break a turtle.
  Footwork (`combat.footwork`): inside reach NPCs mostly stand; after each 1.5 to 3.5 second
  pause, `sidestepChance` (0.35) of one quick sidestep at `sidestepSpeed` (fast enough that the
  legs walk; slow drifts slid), closing at the edge of reach or giving ground when pressed (vanilla
  strafe, which refuses unwalkable steps). Two shield fighters sidestep `shieldSidestepScale` as
  often; bouncier footwork is for a later duelist. Against a raised shield an NPC without one starts
  a swing only at `pressureChance` per tick. A hit taken on the shield never knocks the NPC
  back (`NpcEntity.knockback`), as a blocking player stands firm. Guard is `shield.guardMax` (30) times
  `skill.guardByTier` (1 to 1.75). Footwork comes in steps with rests, not a constant drift (which
  slid). Every swing is followed by `combat.swingDelay` (6; the field is `attackPause`) extra ticks; AW used only the weapon
  cooldown (20 over attack speed), so 0 matches it. A vanilla mob or player has no wind-up, so
  only an NPC target's open attack window holds a guard; right after their swing is an opening,
  and a riposte waits half the strike delay. Bash chance per blocked hit is `skill.bashChancePerBlock` (0.08 to 0.25 by tier).
  EMF is handed the shield and night-light arm poses so its own animation does not undo them.
- Bash: `NpcShieldBash` mirrors Spartan Shields' player bash as a counter after a blocked
  hit. It is telegraphed: the NPC braces `bashWindupTicks` (6) behind the raised shield with
  a heavy creak (only when bashing a player, so NPC battles stay quiet), shoves with a short lunge, and only then lowers the shield for recovery. No
  swing follows during `bashRecoveryTicks`, and the bash spends the riposte. `shield.bashMode`: SPARTAN (its `shields_with_bash` tag), ALL, OFF.
- Feint: Better Combat style, break off halfway against a guarded target, never twice in
  a row. Internal attack path only.
- Skill comes from level (below); the roster rank (`getSkillTier`: 0 ordinary, 1 elite, 2 leader,
  3 elite leader) is now only rank: who commands (`NpcCommand`) and the level a faction NPC
  starts at. `AeSkill` still pins an NPC to one list entry.
- Levels (`NpcLeveling`, `levels` config; AW's `NpcLevelingStats` capped at 10, XP only from
  kills at 5 each, and gave +5% damage and +1 health per level): 1 to `maxLevel` (20), saved as
  `AeLevel` and `AeXp` and packed with the NPC, synced for the inventory. XP to the next level is
  `xpBase` plus `xpStep` per level (720 to level 10, 2945 to 20), from damage dealt to foes
  (`LivingDamageEvent`, after armour), kills (plus the victim's level), blocked hits
  (`NpcGuard.onBlocked`), perfect blocks, and work actions (`NpcWorkGoal.wearTool`). Settlers
  do not level. A level is mostly skill: every `skill` list (parry, timing error, guard, feint,
  bash, shield reaction) and `morale.baseByTier` is read along the levels
  (`NpcLeveling.position`, `lerp`: entry 1 at level 1, entry 4 at the cap), so each level counts.
  Stats stay small: `healthPerLevel` (a saved max-health modifier, so a reload keeps the health),
  `damagePerLevel` (in the melee and ranged proficiency), and for workers `workSpeedPerLevel`
  (shorter actions) and `toolWearCutPerLevel`. Bands of five: soldiers Recruit, Regular,
  Veteran, Champion; workers Apprentice, Journeyman, Expert, Master (`NpcEntity.bandKey`).
  Faction NPCs roll a level by rank on first placement (`factionMinByTier` 1/8/12/16 to
  `factionMaxByTier` 5/12/16/20); older saves roll on load. Owned NPCs start at 1. Level-ups
  sparkle, chime, and tell the owner (`announce`). The inventory role label reads
  "Archer, Lv 13", its tooltip the band and XP. `/ancientempires level` shows the looked-at
  NPC's; admins add a number to set it. Next: squads under leaders and squad morale.
- Paths (`NpcPath`, saved as `AePath`; old `AeCalling` and `AeRole` saves still load): the locked
  path. SETTLER (flavor, undecided), WORKER (trades, traders, priests, bards, faction
  townsfolk; runs), SOLDIER (fights). Old saves' `commoner` and `civilian` map to the
  first two. Faction NPCs take theirs from the roster rank and never change it in play.
  A player's NPC starts as a settler (the Settler scroll; roster id and item stay
  `owned.commoner` so existing scrolls work; also the generic scroll). Its owner may
  choose worker or soldier once: the gold role label in the inventory opens a chooser
  with item icons (pickaxe worker, campfire settler in the middle, sword soldier) and a
  hover description per path; picking the current path just closes it. That
  is final for players; only admins (creative) can set any path, settler included.
  Owned preset scrolls (archer, farmer, ...) are admin conveniences and come
  with their path, as AW's crafted NPCs did. Repack always writes the path.
  Later: retirement and conscription items between the two paths, recruiting faction NPCs.
- Role (`getRoleId`, shown in the inventory, no behavior of its own): what the equipment
  makes the NPC within its path, as in Albion Online. Bow archer, crossbow crossbowman,
  thrown weapon skirmisher; hoe farmer, pickaxe miner, axe woodcutter, rod fisher. Without
  such an item, the trade the rank names (medic, trader, ...), then the path.
- Access: only admins can open a faction NPC's inventory or command it (a plain right-click). An NPC
  the player may not command takes no right-click at all (the click
  passes to the held item, so a shield raised at an enemy blocks; it used to show a "locked" message and
  eat the click). AW Tweaked's talking may use right-click later. Owned NPCs answer
  to their owner and admins.
- Civilians (`avoidsFights`): never hold a target (`setTarget` turns it into a fear
  source), flee home or away even when armed, stay away less per nearby soldier, alert
  allies when hurt, and fight only when cornered (`civilians.cornerRange`, `cornerTicks`,
  `standGroundTicks`). The flee spot search lifts the home restriction, which otherwise
  leaves no spot near home. This covers settlers, workers, and faction townsfolk alike.
  A stand (`isStandingGround`) fights only what cornered it: no target scans, no owner
  defense, and it ends once the threat backs off past `standBreakRange`. Players are
  never scanned for; a player who hits a civilian is ignored (no fleeing, fighting back,
  or calls for help) until its health is under `playerThreatHealth` (`civilianFears`).
- Workers by hand (beyond AW, whose worksites did the work while the NPC stood by; AW3's
  NPCs work the same way). A Worker-path NPC holding a hoe farms its owner's nearest Crop
  Farm (`CropFarmBlock`, within `work.searchRange` of its home, remembered as
  `AeWorksite`) by day in dry weather: it walks to one tile at a time, swings, and after
  `actionTicks` harvests a ripe crop (or pumpkin, melon) into its backpack, plants a seed
  it carries, or tills grass and dirt. A full backpack, or no work while carrying a
  harvest, means a walk back to the farm block to unload (keeping `seedReserve` seeds);
  out of seeds, it fetches some there. Gear (damageable or unstackable items) is never
  unloaded. The farm caches harvest, plant, and till lists rebuilt at most every
  `scanTicks` and only while a farmer asks, and claims tiles so farmers do not collide.
  Ground from two below to level with the block. Every later worker should follow this
  carry-it-back pattern: `WorksiteBlock`/`WorksiteBlockEntity` (owner, area, 18-slot
  store, cached lists, claims) and `NpcWorkGoal` (walk, swing, act, carry, unload, fetch
  supplies, find the owner's nearest site; digging uses the player mining time and
  shows break progress) are the base for every trade.
- Worksite areas (AW's bounds screen): a rectangle (`WorksiteArea`, relative to the block,
  saved as `Area`) set in `WorksiteBoundsScreen` (sneak-click empty-handed): move it
  N/S/E/W, set width and length, shift for 5; outlined in the world while open; the server
  re-checks every change (`WorksiteBoundsSetPacket`). Limits per site are three-entry
  lists (`farmMaxSize` ...: no upgrade, medium, large), and the area must stay within
  `work.areaReach` of the block. AW's size upgrades (`WorksiteUpgradeItem`, AW textures
  and recipes without research): bounds upgrades for every site with an area, quarry
  upgrades (AW's 16/32/64) for the quarry only; right-click the block to fit one, large
  replaces medium (returned), breaking the block drops it (`SizeTier`). Kitchen and Town
  Hall have no area (`UpgradeKind.NONE`).
- Worksite storage (`WorksiteMenu`, `WorksiteScreen`, parchment style like the NPC inventory): a
  double chest (`STORE_SIZE` 54) plus filtered supply slots after it (`Supply` groups: crop farm
  seeds and bone meal, tree farm saplings and bone meal, pen feed, quarry stair stone, kitchen
  fuel and raw food, Town Hall food and the priest's offering), each labelled with a ghost
  icon. `deposit` puts supplies in their slots first; hoppers obey the filters. Workers keep
  `supplyReserve` of each supply item, unload when one backpack slot is left, and unload
  gathered gear too (marked `AeGathered` by `pocket`). Farmers and woodcutters give each new
  planting a dose of carried bone meal.
- Tool wear: every trade wears its tool through `NpcWorkGoal.wearTool`, at
  `work.toolWear` (0.2) of a player's rate; AW's workers did not wear tools at all.
- Woodcutter: a Worker-path NPC with an axe at its owner's Tree Farm. It stands at a
  tree (a log on soil in the square) and chops it log by log from the top down
  (`TreeFarmBlockEntity.trunk`: connected logs within `work.treeReach` of the base, at
  most `treeMaxLogs`), picks up the saplings, apples, and sticks that fall as the leaves
  decay, replants saplings on a `treeSpacing` grid, and carries the rest back.
- Miner: a Worker-path NPC with a pickaxe at its owner's Quarry. The pit starts in
  front of the block (placer's facing, `defaultArea`) and may not cover the block's own
  column; it is dug one layer at a time from just below the block down to
  `work.quarryMaxDepth`. Each layer keeps a spiral stair step (`stairColumns`: one rim
  tile, one further clockwise per layer from the tile nearest the block, plus tiles inward
  to `quarryStairWidth`, 2; corner tiles under the two steps above are dropped for
  headroom). Steps that are not `ancientempires:stair_stone` blocks (ore, dirt, sand,
  gravel, gaps) are dug out and relaid with cheap stone from the item tag of the same name
  (cobblestone, sandstone, ...; never smooth stone), which miners keep as a supply; a dug
  layer waits three scans for that before moving on (`quarryStairStone`). Miners walk the
  stair a step at a time (`QuarryStair.waypoint`, through `NpcWorkGoal.waypoint`): one path to the
  top of a deep pit ran out of the pathfinder's search (its budget comes from follow range 32) and
  ended at the nearest point, the wall. Each new step resets the give-up count, and the next leg is
  planned on arrival. Left standing: blocks
  touching lava or water, block entities, unbreakable blocks, and blocks the pickaxe
  cannot harvest (`skip`). The size locks once digging starts (`canResize`).
- Upkeep (`NpcUpkeepGoal`, priority 3) and the Town Hall (`TownHallBlock`; `NpcUpkeep` is
  just the dimension's list of halls). NPCs feed themselves, nothing runs on a timer: an
  owned NPC needs a ration every `upkeep.mealInterval` (a new one arrives fed). It eats
  rations eaten ahead first (`AeMealCredit`; a food bundle, now edible, is
  `bundleRations` rations), then food in its backpack the vanilla way (swapped to the
  main hand and used, so animation mods show it; `NpcEntity.eat` counts it), so soldiers
  on campaign carry their own. With none, it walks to its owner's nearest hall within
  `range` (100) and takes `carryRations` (or `delivery` TELEPORT puts it in the
  backpack). No hall in reach: fed anyway (lives off the land) unless `requireHall`. A hall
  with no food: hungry (`AeHungry`), retried later; hungry workers do not
  work. An owned NPC dying within range of its owner's hall is recorded there the moment
  it dies: its packed scroll, the death message, where, and when, up to
  `fallenRecords`, for priest revival later. `keepOnDeath` (AW Tweaked's option: NONE,
  EQUIPMENT, BACKPACK, ALL) keeps that gear in the record instead of dropping it.
  Sneak-click lists NPCs in reach, hungry ones, food in store, and the latest fallen.
- Rancher: a Worker-path NPC with shears at its owner's Animal Pen (square, two below
  to three above the block). It walks up to each animal and culls adults past
  `work.ranchHerdSize` of a kind with one clean stroke (no beating them down), shears
  (any `IForgeShearable`), feeds ready pairs to breed below the herd size from feed it
  carries (`ancientempires:ranch_feed` tag, fetched from the pen's store), picks up eggs,
  feathers, and wool, and carries it back. Named, leashed, and tamed animals are left
  alone. `NpcWorkGoal` supports creature targets (`assignEntity`, claimed per creature by
  `claimNearestEntity`) and `collectAt` for pickups.
- Hunter: a Worker-path NPC with a bow at its owner's Hunting Lodge (ground of
  `work.huntDefaultRadius` 24, 16 up and down). Game: adult animals (or
  `ancientempires:hunter_game` creatures) not named, leashed, tamed, inside any Animal Pen
  area, or in `hunter_ignores` (wolves, bears, llamas, horses, pets...), leaving
  `huntKeepPerKind` of each kind. It stalks to a clear shot within `huntShootRange`,
  draws, and looses real arrows (not picked up) until the animal falls, then picks up the
  drops and carries them back; still a civilian, it runs from monsters. Vanilla barely
  respawns animals, so while a hunter works the ground, every `huntSpawnInterval` below
  `huntGameCap` one creature from the biome's CREATURE spawn list (modded animals
  included) wanders in at the edge, where vanilla's spawn rules allow. That interval is
  the hunter's yield.
- Fisher: a Worker-path NPC with a fishing rod at its owner's Fishing Dock (open water in
  the square, three below the dock to its level). It walks to a bank spot
  (`FishingDockBlockEntity.shoreFor`), casts (vanilla's `FishingHook` only works for
  players, so the water stirs where the line went in), waits vanilla's 5 to 30 seconds
  (less with Lure), and rolls vanilla's fishing loot table (luck from the rod, modded
  entries included), recasting until its backpack fills (`work.fishRecast`). The worker
  base's give-up timer only counts while walking, so long actions are not cut off.
- Cook: a Worker-path NPC with a bowl at its owner's Kitchen (one 18-slot store). It
  stands there and smokes raw food by vanilla's smoker recipes (`work.cookTicks` each,
  burning fuel from the store), and packs cooked food worth `cookBundleNutrition` (24)
  hunger into a food bundle (the crafting recipe takes about 37 and a water bottle), up
  to `cookBundleTarget` bundles. It carries nothing. Craftsmen (any recipe) are separate,
  later.
- Medic (`NpcMedicGoal`, priority 1): a soldier with bandages (`BandageItem`, 2 paper and
  string make 4) in its off hand. It goes to the most hurt ally in `medic.range` (same
  faction or owner, its owner, itself) under `healBelow`, even mid-fight, binds the wound
  (`bandageHeal`, hearts), and waits `cooldownTicks`; the off hand restocks from the
  backpack. A Bandage Roll (8 paper around a string) holds 32 uses as durability, since
  vanilla never stacks worn items (`BandageItem.useUp`).  backpack. Its cost: no shield, and `medic.meleeProficiency` (0.75). The role label reads
  the off hand. Players can bind their own wounds with one. Roster items take a count
  (`ancientempires:bandage*16`).
- Priest (`NpcPriestGoal`, priority 4): a Worker-path NPC holding any book (the role label
  says Priest). When its owner's Town Hall in reach has fallen recorded, it walks there,
  prays `priest.reviveTicks` (swings, enchant particles), and the one fallen longest rises
  beside the hall (`NpcSpawnEggItem.spawnFrom`, the scroll's data and kept gear, owner
  re-claimed), paying `costCount` of `costItem` (2 gold) from the hall's store when
  `costEnabled`.
- Sleep (`NpcSleepGoal`, priority 4): owned workers and settlers (soldiers only with
  `sleep.soldiers`; they stay on watch for future patrols) claim a free bed within
  `sleep.bedRange` of home through vanilla's bed index (`PoiManager.take`, the villager
  claim, so no two share a bed; saved as `AeBed`), walk there at night, lie down, and get
  up at dawn. Hurt wakes them (vanilla); threats, fights, and orders come first. The
  claim is released when the bed is gone, or on death or repack.
- Threat scoring (`NpcThreat`, DESIGN Tier 1): `NpcTargetGoal` ranks candidates by a score in
  effective blocks, distance less `targeting` weights for attacking the owner (16), an ally
  (8), or the NPC (4), missing health (6), being in front (2), and an archer facing enemy
  archers (6); sight is checked best-first, and a rescan switches only for a score
  `switchMargin` better. `threatScoring` off restores nearest-first.
- Hazards (`NpcSafety`, the old FleeNavigation idea): paths keep `hazard.maxFall` (3) in and
  out of a fight (vanilla lets a healthy fighting mob take big drops, so mobs leap off
  cliffs), and water and fire path costs come from `hazard`. Spots NPCs pick for themselves
  (fleeing, archer repositioning, running while on fire) must pass `isSafeSpot`: no harmful
  path type at the spot (vanilla's lava, fire, cactus, powder snow checks), ground within
  the safe fall, not deep water; up to `safeSpotTries` tries, else stay put.
- Status icons (AW's task icons, `textures/gui/status`): `NpcStatus` is worked out on the
  server every 10 ticks from state and running goals (sleep, flee, attack, follow, guard,
  upkeep, work, home or rain, hungry, levied) and synced as one byte; `NpcRenderer`
  draws it over the head (client `showStatusIcons`, `statusIconRange`).
  A badge beside it (`NpcBadge`, its own synced byte): a levy's pitchfork (`levied.png`), or
  a soldier's morale (wavering, routing, shattered, surrendered, and inspired while fighting
  in a commander's aura). NPCs that answered a horn rally show the alarm icon for
  `horn.rallyTicks`.
- War Horn (`WarHornItem`; goat horn, gold, leather): blown, the owner's NPCs within
  `horn.range` answer its call (`NpcEntity.answerHorn`): Rally wakes soldiers and makes them
  follow (archers leave their posts), Call to Arms also levies settlers and workers to follow,
  Stand Down ends levies, following, and fights. Sneak-use cycles the call (`AeHornCall`).
- Speed: a mob's speed counts twice (`Mob.setSpeed` also sets the forward input), so pace
  goes with the square of attribute times multiplier. `BASE_SPEED` 0.32 walks at a player's
  pace at 1.0; chasing (`approachSpeed`) and following use 1.12, a sprint. Saves with the
  old 0.28 base are updated on load. No NPC goes faster than a player sprints: `NpcEntity.setSpeed`
  caps the multiplier at `combat.maxSpeed` (1.14) for every goal (fleeing had run at 1.2).
- Pace of NPC battles: `combat.npcVsNpcDamage` (0.7, `NpcDamage`) scales NPC-on-NPC hits only, so
  battles last and morale has time to work; fights with players keep their numbers. An NPC's
  axe disables a shield `shield.npcAxeDisableChance` (0.25, a mob's vanilla chance) of the time;
  a player's always does. With every axe hit disabling shields, Brigand axemen beat Empire
  shield spearmen. `/ancientempires stress report` lists each side: standing of fielded,
  broken, average level, and health left.
- Battles stay readable: footwork is off when `combat.crowdLimit` (3) other NPCs are within
  2.5 blocks (`footworkChance` 0.35, steps of 5 to 8 ticks; 0.2 and 2 felt dead), so lines do not jostle; foot soldiers drop a routing target
  past `morale.chaseRange` (8) and hold the field (`NpcEntity.chases`), riders run them down. After
  its own swing lands an NPC keeps the shield down `shield.followThroughTicks` (8) while the arm
  follows through (it was coming up mid-animation). A blocked hit plays only the shield's sound
  (`playHurtSound` skipped: vanilla played the hurt sound on blocked hits too).
  NPC blocks are heard: `NpcGuard.onShieldHit` plays the shield sound from the server (vanilla's
  block event plays an entity's sound on the client only for the local player, so NPC blocks
  were silent). `NpcEntity.isDamageSourceBlocked` checks the front by head yaw, as `Parry`
  does; vanilla used the mob's walking heading, so turned NPCs blocked from behind and took
  hits from the front.
  For the same reason a fighting NPC standing still (no path) takes its heading from its head each
  tick, or the body swung toward the last walk whenever a knock or sidestep moved it.
  An NPC hit by an NPC takes `combat.npcVsNpcKnockback` (0.3) of the knockback: full knockback shoved
  a fighter out of reach mid-swing, so its swing missed and whoever hit first won. After its own
  swing the shield stays down for the rest of the attack animation (the swing's recovery, at least
  `followThroughTicks`). An NPC perfect-blocks only on a planned raise (`NpcGuard.isPlannedRaise`,
  at its skill's chance); any other raise that happened to land just before a hit (very common
  against fast spears) is an ordinary block.
  Against players (no wind-up to time) a raised shield turns a player's blow into a perfect block
  at `perfectBlock.vsPlayerScale` (0.35) of the level's parry chance, at most once per
  `cooldownTicks` (`NpcGuard.rollsPerfectAgainstPlayer`).
- Two stances (`NpcMeleeAttackGoal.isDuel`, judged every 0.7 s): duel mode when no other NPC is within
  `combat.duelRange` (5) of the NPC and its foe (a player counts as a foe), done ToroQuest's way
  (Rebirth-of-the-Night/ToroQuestRevamped `EntityGuard`): the NPC never stands still, drifting
  sideways at `duelDrift` (0.25, a fifth more behind a raised shield; 0.45 with full-speed closing looked
  frantic) in a stance direction re-rolled a quarter of the time each second, closing unhurried to the
  edge of reach; `backpedalChance` (0.15) stances, and half its own blows, backpedal out of reach. Steering is set every tick, so it walks rather than
  glides. The shield goes up in spells (`NpcGuard.blockFor`, `inSpell`): once the foe has hit it,
  `shield.duelBlockMin` to `duelBlockMax` ticks up (shorter up close), then `duelOpenMin` to `duelOpenMax`
  down while it attacks; half the time a blow on the shield cuts the spell to 10 ticks (the riposte). No shove (tried; the user did not want the extra knockback). No guard stance in a duel, but a
  visible NPC wind-up still raises the shield. Line mode otherwise: the old rare sidestep, none when
  crowded, shield wall. Any shield NPC raises for an arrow in flight at it from a foe
  (`arrowBlockTicks`). The user found fights most fluid when NPCs danced; that belongs to duels.
- Against mobs (no wind-up to read) a shield NPC baits (`NpcGuard.baitsMob`): shield up until the
  mob's blow lands, then the riposte while the mob recovers; after `shield.mobPatienceTicks` (40)
  without a blow it takes the opening anyway. Mobs not after the NPC are not a shield threat at
  all, so helpless or distracted ones are simply attacked. Lowering on a timer gave zombies free hits.
  `/ancientempires morale <state> [radius]` (op) forces the looked-at NPC, or its side within
  the radius, into steady, wavering, routing, shattered, or surrendered, for testing.
- Tuning history (user, first battle tests): routs were too eager (now `routAt` 12, `oddsWeight`
  15, `woundsWeight` 20, `fallSpeed` 0.15, `panicPerRouter` 4), XP came too fast (the `levels`
  section, about 720 XP to level 10), and faction level-ups were heard (now silent; only owned
  NPCs sparkle and chime). Keys were renamed so saved world configs take the new defaults.
- Perfect block (`combat/Parry`, `perfectBlock` config), the same for players and NPCs: a melee hit from
  the front within `windowTicks` (6) of raising a shield is turned aside whole: no guard cost, and
  the attacker is staggered (a louder clang over the thud, "Perfect block!" or "Staggered!" on a
  player's screen; slowness, no swing for `staggerTicks`; `NpcEntity.stagger` breaks off a
  wind-up and shows a recoil); the NPC defender's shield arm snaps forward. A raise counts only
  `cooldownTicks` after the last, so tapping does not farm them. Held longer it is an ordinary
  block. `shield.instantBlock`: a raised shield blocks from its first tick (vanilla waits five), so
  what looks raised is raised; with Responsive Shields the game already says it blocks, so this
  never runs. Shield NPCs with the shield down may hold the raise for a perfect block against an
  NPC's wind-up (`NpcParryPlanner`, `chanceByTier`, `timingErrorByTier`); feints bait that raise.
  The old separate parry key was dropped: blocking is the parry. NPC hits restore the target's hit
  immunity instead of granting one, so they never eat a player's swing. `NpcModel.guardPose` puts
  the raised shield back only when a combat stance overrode it (vanilla's pose is relative; posing
  twice raised it too high). Sounds follow Player.attack: a whoosh on the swing, the hit sound only
  when it lands, the shield's thud when blocked (vanilla's "no damage" thump after a player's hit
  on a shield is cancelled, `Parry.onSound`). A perfect block also bounces the attacker back a
  step; the recoil is handed to EMF like the shield pose. `NpcMoonlightCompat` caches Moonlight's
  answers per item (it is asked for every held item every frame).
- Fairness against players: NPC attack knockback is 0 (every hit already gives 0.4, as for
  players; the old 0.4 base shoved players out of reach each exchange, updated on load), and
  `bypassHurtCooldown` never applies to players, who keep vanilla's hit immunity.
- Night lights (`NpcNightLight`, `nightLight`): soldiers awake at night or in the dark
  (below `darkness`) with a free off hand hold a torch or lantern (`ancientempires:night_lights`
  tag), held out level in `NpcModel.holdLight`; never with a bow, crossbow, or Better Combat
  two-hander. Factions with `"nightLights": false` in factions.json (beast, demon, ent, giant,
  monster, mindflayer, gremlin, vampire) are not issued lights (Kongs are a human jungle tribe in
  AW's guide, so they carry them). With Amendments (Moonlight's
  held-item API, `NpcMoonlightCompat`, reflection) NPCs get its arm pose and 3D lantern and torch,
  which Moonlight only hooks for players; without it the item is stood upright on the level arm. Owned ones use their backpack's and put it back by
  day; faction guards are issued one (`AeNightLight`, stripped before death drops and
  repack). Dynamic light mods light held items on any living thing, so no compat code.
- Reactions: `NpcBurningGoal` (`upkeep.burningPanic`): burning and not fighting (or any
  civilian) runs for water within 8 blocks. Poses are soft dependencies: with Not Enough
  Animations, eating raises the food to the mouth; with its EMF add-on, burning flails and
  freezing shivers (`NpcModel.reactionPose`). Without them NPCs look as vanilla mobs do.
- NPC spawner (`block/NpcSpawnerBlock`, `NpcSpawnerBlockEntity`, `NpcSpawnerMenu`, parchment
  `NpcSpawnerScreen`), AW's advanced spawner kept to its features: nine slots of scrolls (roster
  scrolls, repacked NPCs, vanilla spawn eggs; the stack is how many). GARRISON (AW's one-shot):
  a non-creative player within `playerRange` sends everyone out, homed on the spawner
  (`NpcEntity.linkSpawner`, `AeSpawner`, `AeSpawnerSlot`, `AeGarrison`); a killed one shrinks its
  slot for good; once every player is past `returnRange` the survivors go back in
  (`NpcEntity.checkDespawn`), so the structure is manned again next visit; when all are dead the
  block removes itself; with none of its NPCs about and nobody near it re-arms. REPEATING: one at
  a time every min to max delay while a player is in range, up to most-at-once and a total. REDSTONE:
  one per pulse. Dark-only toggle. Hidden by default: never in the chunk mesh, no collision, and targetable only
  by admins, who see a static placeholder (`NpcSpawnerRenderer`); survival players never see it. `Shown` (block
  state, AW's non-transparent spawners, the giant scarecrow's) makes it a solid, breakable block everyone sees, with AW's
  red animated spawner texture. Admin-only; one distance
  check a second.
- Ranks (`NpcRank`, leadership apart from level): soldier, sergeant, captain, general, synced
  for the inventory label ("Captain, Lv 14"; the tooltip keeps the role). Faction leaders are
  captains and elite leaders generals; elite soldiers are better troops (a higher starting
  level), not sergeants by row, since many are monsters (squads will pick a sergeant from
  them). A player's soldier is promoted one rank at a time by using that rank's insignia on it
  (`InsigniaItem`: sergeant, captain, general; crafted, used up, no batons; the user dislikes
  batons) once it has the level (`command.promoteLevels` 6/12/18). Saved as
  `AeRank` and packed. Admins: `/ancientempires rank <rank>`, or an insignia in creative on
  anyone. factions.json may name the ranks (`ranks`: Norska Huscarl, Hersir, Jarl) and set
  `command`: `ranks` (default), `pack` (beast, monster, giant: Packleader, Alpha, Great Alpha;
  pack behavior comes with squads), or `none` (no commanders). Commanders use any weapon.
- Chain of command (`NpcChain`, `chain` config; plan and phases in docs/chain-of-command.md). Phase 1 done: an NPC
  holds one leader link (`AeLeader`) and, as a leader, its unit (`AeSquad`, officers apart); only a player's soldiers,
  only when the owner assigns them with the Signal Flag (`SignalFlagItem`: use on a sergeant or above to take up its
  unit, on soldiers to add or remove; on a block Hold, again on the spot Defend, sneaking Fall back; on a creature
  Attack; in the air Follow or Free (a sub-unit rejoins), sneaking the formation). Caps: `squadSize` 16 soldiers per
  leader, `squadsPerCaptain` 4, generals any. Orders (`NpcOrder`): an NPC holds its own (top of a chain, or a
  detached sub-unit) or inherits; the nearest holder above is cached as `holder` each tick, so everyone reads one
  link up. The holder runs `NpcOrderGoal` (to the spot, facing the order's way; Attack ends in Hold where the enemy
  fell, Fall back in Hold at the rally point); Follow at the top sets the vanilla follow flag. The order leashes
  targets (`allowsTarget`, in the target scan and every 10 ticks): Follow within `followLeash` of the holder, Hold
  within `holdRange` of the soldier's own slot, Defend within `defendRadius` of the spot, Fall back only the
  cornered; bows reach `rangedLeash` further. A formed soldier runs only `NpcFormationGoal` (priority 3): its slot
  from the leader's layout (`NpcFormation`: line, column, loose; the front rank just ahead of its leader, the rest
  behind; a captain's squads ahead of it, so higher ranks stand further back), worked out per unit when the leader
  moves or turns; it sets off past `slotTolerance` (or when its slot moves) and steps straight onto the exact spot
  for the last two blocks, faces the unit's heading, and a target takes it out of formation until the fight ends. Death or repack `depart`s: the best member (rank, then level) takes the
  place, order, and the rest of the unit, and the command aura (`NpcCommand.succeed` prefers it); `NpcChainLedger`
  (per-dimension saved data, 7 days) answers unloaded links (away, or gone and to whom). `/ancientempires squad`.
  Phases 1 and 2 done. Next: phase 3, unit fighting with lobotomised grunts.
- Command (`NpcCommand`, `command` config, DESIGN Tier 1): every rank above soldier pulses an
  aura every `auraTicks` at its rank's `strengthByRank` (0.5, 1, 1.25) over allied soldiers
  within `radiusByRank` (8, 12, 16): `damageBonus` (15%) melee damage with any weapon and `armorBonus` armour, as
  transient modifiers that lapse soon after leaving. A fallen commander's command passes to the
  highest-ranked (then highest-level) allied soldier within `successionRange` (none if another commander is there), at
  `successionScale` (floor `minScale`), saved as `AeCommand`, and it turns on the killer.
  The aura also lifts morale (`morale.inspiredBonus`), and a commander's death shocks its army
  by its strength (a sergeant's less than a general's).
- Skins: a roster row assigns a skin type, which resolved to its pool's first skin, so every
  Empire soldier looked alike. `NpcSkins.randomVariant` pins a random skin of the pool on first
  placement; a repacked scroll's saved skin is applied after the row, so it keeps its own.
- Getting settlers in survival: the Settler scroll recipe (2 gold, AW's food bundle, a bed),
  and the Settlers' Post (`block/SettlersPostBlock`, `NpcImmigration`), Dwarf Fortress
  style immigration kept apart from the future Town Hall (upkeep, priest revival within
  ~100 blocks). Nothing ticks per post: once per in-game day per dimension, each open
  post in a loaded chunk may (`immigration.chance`) draw 1 to `maxGroup` settlers for its
  owner, capped by free beds (vanilla HOME points within `bedRadius`, the villager index,
  less the owner's NPCs there) and stored food bundles (`foodPerSettler` each). They
  appear `spawnDistance` out and walk in; home is the post. Right-click with food bundles
  to stock it (hoppers work too), empty-handed to open or close it (owner or admin),
  sneak for status.
- Relations (`NpcFactions`, `assets/ancientempires/npc/factions.json`, ported from AW 2
  Tweaked 1.6.1 with each faction's list resolved over the shared defaults): each of the
  44 factions lists who it attacks (one-directional: Norska attack Sealsker, not the
  reverse), a new player's standing, the kill and trade changes, and what mobs it hunts
  (good factions any `Enemy`; evil ones AW's "friendly" list: villagers, golems).
  `NpcStandings` (overworld saved data) holds each player's standing per faction; below
  zero the faction attacks that player on sight (not creative or spectator) and the
  player's owned NPCs. Killing a faction NPC, or having owned NPCs kill it, applies the
  faction's `kill` change and gives its faction-mates within `relations.revengeRange` a
  grudge (`AeRevenge`) for `revengeTicks`. Owned NPCs attack only faction NPCs hostile to
  their owner. Civilians flee hostile players on sight. The standings screen (J, or
  `/ancientempires standing`; `StandingsScreen`, AW's faction banners in
  `textures/gui/banner`) shows every faction's banner and your standing; the command
  shows your standings; admins can view, `set`, `add`, or `reset` anyone's.
- Cavalry (`NpcMount`): a faction row with a `mount` (cavalry and mounted archers default to
  `minecraft:horse`; AW's odd ones are on their rows: Norska polar bear, gnome chicken,
  gremlin spider, Xoltec llama, undead/coven/icelord skeleton horse, Klown zombie horse and
  pig) spawns on it. A dead mount is not replaced (`AeMountLost`); a rider thrown from a
  living one walks back to it when not fighting (`cavalry.remountRange`). While riding,
  `NpcEntity.getNavigation` returns the mount's navigation, so every goal drives the mount
  at its own size; the mount's own MOVE/JUMP/LOOK goals are held off each tick and it gets
  `cavalry.mountSpeed` (AW 1.5). Melee reach gains `reachBonus` from the saddle. Ridden
  monsters are not scan targets (the rider is), and rider and mount are allied. Repack
  takes a spawned mount along (respawned on placing) and leaves any other mount standing.
  The inventory Mount button seats an owned NPC on its owner's nearest saddled tamed
  horse or dismounts it; admins can seat any NPC on any mob.
- Morale (`NpcMorale`, `morale` config, on by default, Total War style; not in AW): soldiers
  (not civilians, not fearless factions: undead, demon, monster, ent, vampire, the
  `fearless` flag in factions.json; owned ones with `ownedNpcs`) have morale 0 to 100. Once a
  second (`updateTicks`) it drifts toward a target (`NpcMorale.target`): the tier's
  `baseByTier`, plus a commander's aura, plus or minus the odds within `range` (each side
  weighed by health, routing NPCs not counted), less wounds, a hit from outside the 120
  degree front lately (`flankTicks`), hunger, and routing allies (capped), plus shocks that
  wear off (an ally dying close by, a commander dying, a kill). It falls faster than it
  rises. A peaceful steady soldier skips the area count. Wavering below `waverBelow`;
  below `routBelow` it routs: `avoidsFights` is true, so it flees with `NpcFleeGoal` as a
  civilian does and stands only when cornered. It rallies at `rallyAbove` after
  `minRoutTicks`, or on a War Horn rally (`hornRally`). The `shatterAfterRouts`th rout
  shatters: no rallying; a faction soldier deserts (despawns, a garrison counts it dead,
  its roster mount goes too) once no player is within `desertRange`, unless named; an
  owned one recovers after `shatteredRecoverTicks` clear of enemies. Cornered and below
  `surrenderHealth`, a routing faction soldier may surrender (`surrenderChance`): it
  kneels (crouch pose, `NpcSurrenderGoal`), NPCs stop targeting it, a hit shatters it, and
  with no enemy near for `surrenderReleaseTicks` it leaves shattered. Capture and
  recruiting are later. Saved as `AeMorale`, `AeMoraleState`, `AeRouts` (not packed).
  Players' own soldiers are steadier (`ownedBaseBonus` 10) and take heart from their owner
  within `ownerPresenceRange` (`ownerPresenceBonus` 15), since an army running unexpectedly
  feels worse than an enemy routing feels good. Each check records its reasons
  (`NpcMorale.reasons`, `REASON_` bits) for the stats sheet.
- Stats sheet (`NpcStatsSheet`): clicking the role label (right click for a settler, whose left
  click picks a path) opens a panel with rank, band, level and an XP bar (workers: work speed),
  morale with a bar marked at the waver and rout lines, its state, and up to three reasons,
  guard, and fed or hungry. Morale, guard, and hunger come through the menu's container data
  (`NpcMenu.stat`, synced to the viewer only); level and rank are entity data. Wavering and
  inspired badges show only in a fight; client `statusIconsOwnOnly` hides other players' and
  faction NPCs' icons.
- Levy (`AeLevy`): an owner can call a civilian up to fight until dismissed. Debuffs in the
  `proficiency` section: damage 0.7, slower wind-ups, no bash or feint, breaks below 30%
  health.
- Proficiency (from the AncientTactics trial): melee by an archer (bow or crossbow in hand
  or backpack, so sidearm swaps count) times 0.5, by a skirmisher times 0.75. Cached on
  hands, backpack, path, and levy changes.
- NPC NBT keys: `AeRoster` (roster row), `AePath`, `AeLevy`, `AeSkill` (tier override),
  `AeFeintChance`, `AeSidearmBowSlot`, `AeMountLost`, `AeRevenge`, `AeWorksite`, `AeHungry`, `AeNextMeal`, `AeMealCredit`, `AeBed`, `AeSpawner`, `AeSpawnerSlot`, `AeGarrison` (spawner link, not packed), `AeMorale`, `AeMoraleState`, `AeRouts` (not packed), `AeLevel`, `AeXp`, `AeRank` (`AeMountUuid` is not packed). All carried through repack.

## Performance rules and audit findings

Many NPCs in one fight is the stress case. Prefer equipment-change and goal-start driven
updates over per-tick scans. Cache threat and ally queries with short TTLs. The audit was a
read-through, not a profile, so measure before and after.

Server, by expected impact:
1. Two vanilla `NearestAttackableTargetGoal` per NPC (32-block range, line-of-sight per
   candidate) scale poorly. Replace with one custom target goal: jittered scan intervals,
   cheap faction-hostility filter before any raycast, short-lived shared cache per faction.
2. `AttackProfiles.resolve` re-runs reflective Better Combat lookups on every combo or
   equipment change and rebuilds attack lists. Cache parsed weapon data per item and the
   fist data once; clear on datapack reload. Avoid the full-map string-key walk in
   `BetterCombatBridge`.
3. `NpcBowAttackGoal.readDrawTime` walks the class hierarchy with exceptions on every draw.
   Cache per item class.
4. `NpcFollowOwnerGoal.canUse` looks up the owner before checking `isFollowing()`. Reorder.
5. `NpcMeleeAttackGoal.preview` does two stack comparisons per tick. Use a dirty flag set in
   `NpcEntity.onEquipItem`. Cache `isHoldingBow()` the same way.
6. Per-swing `ResourceLocation` parse of the sound id, and the animation id synced as a
   string. Resolve once; sync a small int id.

Client:
- `NpcRenderer.render` calls `NpcSkinLayers.apply` every frame, re-parsing the skin string
  into a new `ResourceLocation` (vanilla repeats it in `getTextureLocation`), with map
  lookups and six `wear()` calls, each a reflective inject when 3D Skin Layers is present.
  Cache the location on the entity (refresh in `onSyncedDataUpdated`), build a per-skin plan
  once, use `MethodHandle`, and skip injects when nothing changed.
- First render of a skin reads and decodes the PNG on the render thread. Precompute or
  prefetch to avoid hitches in mixed armies.
- `NpcBetterCombatDriver`: `sync()` runs on tick and render, and `updatePose` calls
  `WeaponRegistry.getAttributes` every time. Drive pose from equipment changes, cache built
  animations per id, and skip the driver beyond a distance cutoff.

Not audited: `NpcEmfCompat`, `NpcEmfBetterCombat`, `NpcCombatAnimations`, `MobCombatHooks`,
and the UI screens.

Suggested order: (1) the cheap fixes (items 3-5 and the client skin churn); (2) sidearm
fallback, equipment-change driven; (3) Better Combat snapshot caching; (4) shared
target-scan design.

### Measured (2026-10-28, game test `npcGoalCost`, 100 idle soldiers and 100 idle workers)

About 20 to 28 microseconds per NPC per tick, with 20% noise between runs; spreading them out changed nothing, so
crowding is not it. This mod's own per-tick work (`customServerAiStep`: morale, guard, sidearm, target memory,
command, chain) is about 1 us of that (soldiers), 0.5 (workers); the rest is vanilla's mob step (goal selection,
pathfinding, sensing, movement). Registering goals by path made no difference beyond the noise: idle goal checks were
already cheap. What would pay is fewer NPCs pathing and scanning: the chain's lobotomised grunts (phase 3).
`NpcEntity.aiTiming` turns the timer on (benchmark only).

### Verdicts after checking the code (perf-and-sidearm branch)

- Server 1, target scans: confirmed, but the cause is narrower. Vanilla
  `TargetingConditions.test` already runs the `acceptsTarget` selector before the raycast.
  The cost is that every candidate passing it gets a line-of-sight raycast, not only the
  nearest, and the `Monster` goal has no selector at all. Replaced by `NpcTargetGoal`; set
  `targeting.debugStats` to log scans, box queries, and sight checks per second.
- Server 2, Better Combat lookups: confirmed. Per swing, not per tick. The fist preset walked
  the whole containers map every swing. Fixed: per-item cache stamped on BC's containers map.
- Server 3, bow draw time: confirmed, about 5.5 us per shot (two exceptions). Fixed.
- Server 4, follow owner: confirmed, small. Fixed.
- Server 5, stack comparisons: confirmed for the preview, fixed with `getHandsVersion()`.
  Caching `isHoldingBow()` was dropped: it is two `instanceof` checks, as cheap as a flag.
- Server 6, sound id parse and string animation id: confirmed, once per swing. Not changed.
- Client skin parse: mostly overstated. Catalog skins were already a HashMap hit with a
  prebuilt `ResourceLocation`; only raw ids parsed. Cached on the entity anyway. The real
  per-frame cost was in `NpcSkinLayers` with 3D Skin Layers loaded. Fixed: config read
  once per tick, mesh injects skipped when unchanged. Measured on JDK 17: a reflective
  call is 4-7 ns and a field-held MethodHandle about 3.5 ns, so call counts mattered, not
  the call style. MethodHandles were not adopted.
- Client `NpcBetterCombatDriver.updatePose`: overstated. It calls `WeaponRegistry` directly,
  which is two map gets for a stack without NBT. Not changed.
- Not audited before, now checked: `NpcEmfCompat.afterPose` allocates two to four
  `PoseSnapshot`s, a `Vector3f`, and a `HashMap` per attacking NPC per frame, and checks
  `ModList` each call. `NpcHeldItemLayer` asks `NpcEmfCompat.placesArm` per arm per frame,
  three reflective calls each. `MobCombatBridge.owns` runs Better Mob Combat's weapon check
  every melee tick when that mod is loaded. `MobCombatHooks.cancel` walked fields with
  exceptions (fixed). UI screens are dirty-flag driven and fine. `NpcEmfCompat` now caches
  the mod check; its pose snapshots stay per frame because EMF Compat keeps them by
  reference. `placesArm` (about 15-20 ns per arm) was left as is.
- Server 6: the swing sound is now resolved once per id (`SWING_SOUNDS`). The animation id stays a
  synced string: entity data only sends on change, once per swing, so an int registry would not pay
  for itself. First-render PNG hitch: `NpcSkinPrefetch` preloads a skin on the texture manager's
  background executor when an NPC arrives on the client or its skin changes.

## Testing

- Working cache: `docs/session-notes.md` (state, next steps, later notes). Read it first; update it after each chunk.

- The user tests in a separate game instance (see their notes); check its `logs/latest.log`
  to see which combat and animation path is live (Better Mob Combat, the fallback driver,
  EMF, bc_particle) before guessing.
- Never replace the mod jar while that game is running. A jar swapped mid-session made a
  not-yet-loaded class fail with `NoClassDefFoundError`, which looked like a mod bug.
- Forge's early loading window failing ("Timed out trying to setup the Game Window") is a
  graphics driver problem; it happens before any mod jar is read.

## Tests

- `./gradlew test` (also run by `build`) runs JUnit 5 tests in `src/test/java` on rules kept as pure
  methods so no game is needed: `WorksiteArea`, `QuarryStair.columns`, `Parry.perfectWindow`,
  `NpcGuard.strikesFirst`. Put a new rule in such a method and test it there; the game code calls
  the same method. Also `NpcCommand.inherited` and the morale math (`NpcMorale.target`, `drift`,
  `decay`, `fromBehind`), and the level curve and skill reading (`NpcLeveling`). Structures: `AwsTemplate`
  parsing and 1.12 position packing, `AeTemplate` round trips, chest pairing and door/leaf mending.
- `./gradlew runGameTestServer` runs `StructureGameTests` on a headless server (arena
  `structures/gametest_arena.nbt`): real templates placed, blocks resolved, fences connected, timings logged
  as `GAMETEST`. Dev runs remap library mixin refmaps (`mixin.env.remapRefMap` in `build.gradle`).

## Building

`./gradlew build` and `./gradlew runClient` need network access to the Forge and Mojang
servers. They do not work in the restricted cloud sandbox, so build and test on a machine
that can reach them. If you cannot build, say so and write the change carefully rather than
guessing it compiles.

## Working style

- Don't edit files in `libs/`. Don't push to `main` unless asked.
- Keep changes small and match the existing code style (Allman braces, `this.` prefixes,
  javadoc on non-obvious behavior).
- Everything tunable (ranges, wind-up, thresholds, scan radii) belongs in
  `AncientEmpiresConfig`, not as hard-coded numbers. It is a SERVER config (per world,
  `serverconfig/ancientempires-server.toml`, synced to clients); visuals live in
  `AncientEmpiresClientConfig`. Adding a setting means three edits there (the spec
  field, the public value, the load line); keep them together.
- Bump `ModNetwork.PROTOCOL` whenever a packet is added or changes shape.
- Import classes; do not write fully qualified names inline (one deliberate exception:
  `StandingsPacket`'s client-only screen call).
- New per-tick or per-scan work goes inside a profiler section
  (`level.getProfiler().push("ancientempires_...")`) so `/debug` and Spark show it.
- Staging: name files; never sweep `src/main` (the user keeps local edits there).
- Never start a "last time" field at `Long.MIN_VALUE` and test `now - last < interval`: the
  subtraction overflows and the check never passes (this kept every worksite from ever
  scanning). Use -1 for "never" and test it first.
- `/ancientempires work`, looking at an NPC, lists every condition its work needs.
- `/ancientempires stress <perSide> <factionA> <factionB>` (op) spawns two faction armies 24 blocks
  apart in front of you (soldier, archer, leader rows; tagged `AeStress`) for profiling with Spark;
  `stress report` gives the count alive and average ms per tick, `stress clear` removes them.
- Arm swings: vanilla advances `swingTime` only in `Monster` and `Player`, so `NpcEntity.aiStep`
  calls `updateSwingTime` (without it `swing()` never animates). Any action a player would
  swing for (work, deposits, bandaging) calls `swing`. Melee sends the vanilla swing at the
  hit; `NpcModel` drops it only while a Better Combat clip is playing
  (`NpcCombatAnimations.attackClipPlaying`), so a missing or failed clip falls back to it.
