# Architecture

A walk through how Ancient Empires works, for people reading the code. It follows the main flows end to end and names the classes involved. Each package also has a `package-info.java` with a shorter map. `CLAUDE.md` is the dense design record (with the reasons behind many numbers); this page is the guided tour.

All code is under `src/main/java/com/ancientempires/`. Every tunable number is in `config/AncientEmpiresConfig` (see [config.md](config.md)).

## Contents

1. [The big picture](#the-big-picture)
2. [The NPC](#the-npc)
3. [The goal stack](#the-goal-stack)
4. [Targeting](#targeting)
5. [Combat](#combat)
6. [Shields, morale, and command](#shields-morale-and-command)
7. [Workers and worksites](#workers-and-worksites)
8. [Upkeep and towns](#upkeep-and-towns)
9. [Structures](#structures)
10. [Garrisons: the NPC spawner](#garrisons-the-npc-spawner)
11. [Structures, flags, and claims](#structures-flags-and-claims)
12. [Gates](#gates)
13. [Client: rendering and animation](#client-rendering-and-animation)
14. [Soft dependencies](#soft-dependencies)
15. [Performance habits](#performance-habits)
16. [Tests](#tests)

## The big picture

```
AncientEmpires (entry point)
 ├─ registry/      registers everything below with Forge
 ├─ entity/npc/    NpcEntity + its goals and helpers ──┐
 ├─ combat/        melee, bow, thrown swings; Parry    │ uses
 ├─ block/         worksites, Town Hall, spawner, flags ◄┘
 ├─ structure/     AW templates: convert, load, place, records, claims
 ├─ gate/          AW gates
 ├─ worldgen/      structures in new chunks (in progress)
 ├─ item/, inventory/, network/, command/, config/
 └─ client/        renderer, animation bridges, screens
```

Three ideas run through the whole mod:

- **One NPC class.** `entity/npc/NpcEntity` is every NPC: soldiers, archers, farmers, priests, Norska jarls, zombies of the undead faction. What one does comes from data (its roster row, faction, owner, path) and equipment, never from a subclass.
- **Behaviour is goals.** Vanilla's goal selector runs everything an NPC does. A goal that cannot run costs one cheap check.
- **Optional mods by reflection.** Better Combat, Better Mob Combat, Spartan Weaponry, EMF, and the rest are never required. Each is touched by one bridge class that does nothing when its mod is absent.

## The NPC

`NpcEntity` holds state, saving, and the vanilla hooks other parts need. Its identity:

| What | Where it comes from | Code |
|---|---|---|
| Roster row | the scroll it was placed from (`roster.json`) | `NpcRoster`, saved as `AeRoster` |
| Faction or owner | faction rows set a faction and no owner; owned rows claim the placer | `assignFaction`, `getOwner` |
| Path | settler, worker, or soldier; chosen once for owned NPCs, from the roster rank for faction NPCs | `NpcPath` |
| Role label | the main-hand item (bow archer, hoe farmer...), else the rank's trade, else the path | `NpcPath`, `getRoleId` |
| Skill | level along the four-entry `skill` lists, or pinned by `AeSkill` | `NpcLeveling`, `skillValue` |
| Rank | soldier to general, from the roster tier or insignia | `NpcRank` |

**Owned vs faction NPCs** stay distinct everywhere. An owned NPC has an owner, a home, follow and hold orders, and commands from its inventory screen. A faction NPC has a faction id, no owner, and only creative players may open or command it (`canOpenInventory`).

**Inventory** (`NpcInventory`): slots 0 to 5 are vanilla equipment (so attributes, combat, and rendering read them as usual); a 3x3 backpack follows, saved by slot index. Sidearms live in the backpack (`NpcSidearm` swaps a bow or thrower for the best melee weapon when cornered, and back).

**Repacking**: from its inventory an owner turns an NPC back into its roster scroll, with gear, skin, name, level, path, and flags in the item's `EntityTag` (`createPackedItem`). The home is dropped so a placed NPC calls its new spot home. See [nbt-keys.md](nbt-keys.md) for what is and is not packed.

**Helpers per NPC**: `NpcGuard` (all shield state), `NpcMorale`, `NpcMount`, `NpcSidearm`, `NpcTargetMemory`. They keep big systems out of the entity class while sharing its lifetime and saving.

## The goal stack

Registered in `NpcEntity.registerGoals`. Lower numbers win when two goals want the same control flag (move, look, jump).

| Priority | Goals | Notes |
|---|---|---|
| 0 | `FloatGoal`, `NpcSurrenderGoal`, `OpenDoorGoal`, `NpcNightDoorsGoal`, `NpcGateGoal` | Doors and gates use no flags, so they run beside whatever moves the NPC. |
| 1 | `NpcBowAttackGoal`, `NpcSkirmishGoal`, `NpcShieldGoal`, `NpcMedicGoal`, `NpcFleeGoal`, `NpcBurningGoal`, `NpcSunGoal` | Ranged fighters keep their goal the whole fight. The shield goal has no flags. Flee covers civilians and routing soldiers. |
| 2 | `NpcMeleeAttackGoal` | Only when not holding a bow. |
| 3 | `NpcHoldPositionGoal`, `NpcUpkeepGoal` | Hold is for ranged NPCs only. |
| 4 | `NpcFollowOwnerGoal`, `NpcSleepGoal`, `NpcPriestGoal`, `NpcGovernorGoal`, `NpcJobGoal` | `NpcJobGoal` is every hands-on trade in one goal. |
| 5 | `NpcReturnHomeGoal` | Day and night ranges, rain shelter, archer posts. |
| 6+ | stroll, look at player, look around | |

Target goals:

| Priority | Goal |
|---|---|
| 1 | `NpcOwnerDefenseGoal` DEFEND: whoever hurt the owner. |
| 2 | `NpcOwnerDefenseGoal` ASSIST: whatever the owner hit. |
| 3 | `NpcHurtByTargetGoal`: whoever hurt this NPC, and it alerts allies. |
| 4 | `NpcTargetGoal`: the shared scan (or vanilla's two goals with `targeting.legacyScan`). |

Why one `NpcJobGoal`: the selector asks every idle goal "can you run?" about ten times a second. Seven trade goals meant seven checks on every NPC; one goal that looks at the main-hand tool and asks only that trade costs one.

## Targeting

Who an NPC fights is decided in two places:

1. **Who counts as hostile**: `NpcEntity.acceptsTarget` (NPC targets) and the mob check next to it. Faction NPCs attack factions on their `hostile` list in `factions.json` (one-way), and owned NPCs of players whose standing is below zero (`NpcStandings`). Owned NPCs attack monsters and faction NPCs hostile to their owner. Good factions hunt monsters; evil ones hunt townsfolk instead (`NpcFactions.hunts`).
2. **Which one**: `NpcTargetGoal`, staggered by entity id:
   - `NpcTargetCache` gives a candidate list shared by every NPC in the same 16x8x16 cell, valid for `targeting.cacheTicks`. One box query serves a whole army.
   - The cheap hostility test runs first, then range and vanilla's combat checks. No raycasts yet.
   - `NpcThreat` scores each survivor in "effective blocks": distance, less bonuses for attacking the owner, an ally, or this NPC, being wounded, standing in front, and (for archers) being an archer. Line of sight is checked best-first, stopping at the first visible one, at most `maxSightChecks`.
   - While a target is held, a slower rescan switches only for a clearly better score (`switchMargin`).

**Losing a target**: `NpcTargetMemory` makes one sight check per tick while a target is held. Out of sight, the NPC goes to the last sighting and searches there for `memoryTicks`, giving up after `pursuitTicks` without seeing it. Gaining a target counts as a sighting, so an ally's alert sends helpers to where the fight was.

**Ally alert**: when an NPC is hurt, `NpcHurtByTargetGoal` hands the attacker to allies (same faction, or same owner) within `allies.alertRange` that have no target.

## Combat

### Melee

`combat/NpcMeleeAttackGoal` runs a small state machine: approach, wind up, hit, recover.

1. **Pick the swing.** `AttackProfiles.resolve` returns an `AttackSwing` (reach, wind-up ticks, recovery, angle, animation). Sources, in order: Better Combat's weapon attributes through `BetterCombatBridge` (cached per item); with empty hands, Better Combat's fist preset; otherwise vanilla timing from attack speed (`combat.vanilla`). Two one-handed Better Combat weapons dual-wield: combo steps alternate hands. Every swing's wind-up is scaled by `combat.npcWindupScale` (`slowWindup`), so players can read NPC attacks.
2. **Close in.** A swing starts within `engageRangeFactor` of reach. During the wind-up the NPC keeps closing to `windupKeepRange`. Every movement mode keeps a minimum gap (`minGapReach`, `minGap`) so fighters do not walk into each other.
3. **Land the hit** at the end of the wind-up, checking full reach and the attack's arc. Better Mob Combat, when installed, owns this clock instead (`MobCombatBridge`, `MobCombatHooks`), but the hit is still applied here; when its callback never fires (it skips same-type mobs, and every NPC is one type) the goal lands the hit itself.
4. **Recover**, then `combat.swingDelay` extra ticks.

Between swings: **footwork**. In a duel (no other NPC within `duelRange`) the NPC drifts and backpedals, ToroQuest style. In a line it mostly stands, with a rare sidestep, none when crowded. Also here: **dodges** (a backstep from an NPC's visible wind-up, by skill), **flanking** (circling a raised shield), and **feints** (breaking off half way against a guarded target).

### Ranged

- `NpcBowAttackGoal`: a bow in the main hand keeps this goal for the whole fight, so the NPC never punches with a bow. It draws, aims with a gravity lift, and repositions to safe spots (`NpcSafety`).
- `NpcSkirmishGoal`: a Spartan throwing weapon. Throws between `skirmish.minRange` and `maxRange`, kites inside `dangerRange`. Spartan only throws for players, so `SpartanThrowing` builds the projectile by reflection.
- Cornered, both swap to a backpack melee weapon (`NpcSidearm`) and the melee goal takes over.

`WeaponClassifier` is the single answer to "is this ranged, thrown, a shield, or melee", cached per item class.

### Damage

`NpcDamage` scales NPC-on-NPC hits by `combat.npcVsNpcDamage` so battles last long enough for morale to matter; fights involving players keep their numbers. NPC knockback against NPCs is scaled too.

## Shields, morale, and command

**Shields** (`NpcShieldGoal` decides when; `NpcGuard` holds all the state). A block is a commitment: an NPC never swings with its shield up, keeps it up at least `minBlockTicks`, then strikes after `strikeDelayTicks` or ripostes after a blocked hit. Between two shield fighters, whoever starts readying a strike first goes (`NpcGuard.strikesFirst`, unit tested). Guard is stamina: blocked damage drains it, heavy weapons drain it faster, and at zero the shield is knocked down. A raised shield blocks from its first tick (`shield.instantBlock`). Against mobs a shield NPC baits a blow, then ripostes. `NpcShieldBash` counters a blocked hit with a telegraphed bash.

**Perfect block** (`combat/Parry`): the same rule for players and NPCs. A melee hit within `perfectBlock.windowTicks` of raising a shield is turned aside whole and staggers the attacker. NPCs plan such raises against NPC wind-ups (`NpcParryPlanner`, by skill).

**Morale** (`NpcMorale`, once a second per soldier): morale drifts toward a target worked out from the rank's base, a commander's aura, the odds nearby (weighed by health), wounds, a hit from behind, hunger, routing allies, and shocks (an ally or commander dying, a kill). Below `routBelow` the soldier routs: it counts as a civilian (`avoidsFights`) and `NpcFleeGoal` runs it. It rallies later, or shatters after `shatterAfterRouts`. Cornered and badly hurt, a routing faction soldier may surrender (`NpcSurrenderGoal`). The math is pure methods with unit tests (`target`, `drift`, `decay`, `fromBehind`).

**Command** (`NpcCommand`): sergeants, captains, and generals pulse an aura that adds damage, armour, and morale to allied soldiers nearby. When one falls, command passes to the best soldier near it, weakened (`NpcCommand.inherited`, unit tested).

**Levels** (`NpcLeveling`): XP from damage, kills, blocks, and work. A level is mostly skill: every `skill` list is read along the levels.

## Workers and worksites

Workers do their trade by hand, walking and swinging, rather than standing beside a block that works by itself as in Ancient Warfare.

```
NpcJobGoal ── main-hand tool ──► NpcFarmerGoal / NpcWoodcutterGoal / NpcMinerGoal /
                                 NpcRancherGoal / NpcHunterGoal / NpcFisherGoal / NpcCookGoal
                                        │ extends NpcWorkGoal<T extends WorksiteBlockEntity>
                                        ▼
                            CropFarmBlockEntity / TreeFarmBlockEntity / QuarryBlockEntity / ...
                                        │ extends WorksiteBlockEntity
```

**The worksite** (`block/WorksiteBlockEntity`) does no work. It has an owner, an area (`WorksiteArea`, set in the bounds screen, limited by size upgrades), a 54-slot store plus filtered supply slots, and a **to-do list** the subclass builds in `scan()`: ripe crops, trees, the next quarry layer, animals to cull. The list is rebuilt at most every `work.scanTicks`, and only when a worker asks, so an idle worksite costs nothing. Workers **claim** the tile or creature they walk to, so two workers never take the same one.

**The worker** (`entity/npc/NpcWorkGoal`) runs the shared loop: find the owner's nearest worksite of its trade (within `work.searchRange` of home, remembered as `AeWorksite`), take a job, walk to it, swing for `actionTicks` (digging shows break progress at a player's mining speed), act, and put what it gathered in its backpack. When the backpack is full, or there is nothing left to do while carrying a harvest, it walks back and unloads; when out of seeds or saplings, it fetches them. Work is by day, in dry weather, when not fighting, following, or hungry (`mayWork`; `/ancientempires work` prints the same checks via `NpcJobGoal.diagnose`). Tools wear through `wearTool`.

Each trade subclass adds only its own jobs: the farmer tills, plants, harvests; the woodcutter fells top-down and replants on a grid; the miner digs layer by layer and walks a spiral stair (`QuarryStair`, pure arithmetic, unit tested); the rancher culls, shears, breeds; the hunter stalks and shoots (and new game wanders in at the edge); the fisher casts and rolls vanilla's fishing loot; the cook smokes food and packs bundles.

**Governor and priest** are separate goals at priority 4: a worker with a map makes nearby workers faster and collects tax at the Town Hall; a worker with a book revives the fallen recorded there.

## Upkeep and towns

- `NpcUpkeepGoal` (priority 3): an owned NPC needs a ration every `upkeep.mealInterval`. It eats rations eaten ahead, then backpack food (the vanilla way, so animation mods show it), then walks to its owner's nearest Town Hall for more. No hall in reach: it lives off the land unless `requireHall`.
- `block/NpcUpkeep` is just the list of Town Halls per dimension, so an NPC finds its owner's nearest one without scanning blocks.
- The Town Hall (`TownHallBlockEntity`) stores food and records owned NPCs that die nearby (packed scroll, death message, kept gear) for `NpcPriestGoal`.
- `block/NpcImmigration`: once per in-game day, each open Settlers' Post in a loaded chunk may draw settlers, capped by free beds (vanilla's bed index) and stored food bundles.
- `NpcSleepGoal` claims a bed through vanilla's point-of-interest index, as villagers do.

## Structures

Ancient Warfare's 1,471 structure templates are converted **once, offline**, and shipped as data.

```
AW 1.12 jar (.aws)                       build time: ./gradlew convertAwsTemplates
   │  AwsTemplate (reads .aws, no game needed)
   │  AwsConverter + LegacyFixer (Mojang's DataFixer + hand-filled gaps)
   ▼
data/ancientempires/ae_templates/<path>.nbt  (AeTemplate: palette + 16x16 column tiles)
data/ancientempires/ae_templates/<path>.json (sidecar: AW header + validation)
data/ancientempires/loot_tables/aw/...       (AwsLootConverter)
```

`AeTemplate` is our own format: vanilla's `.nbt` lists every block, which would need about 2 GB for the largest islands. Blocks are packed palette indices in 16x16 column tiles; palette entry 0 is "leave the world alone".

**At runtime**:

1. `AeTemplates` (a datapack reload listener, hooked in `StructureReloads`) indexes every sidecar at each reload. The heavy `.nbt` is read only when placed, and cached up to `structures.templateCacheMb`.
2. `AePlacer.place` sets blocks without neighbour updates, then recomputes each block's shape from its neighbours (fences, panes, walls, stairs), because Ancient Warfare saved states unconnected. With a limit box it touches one chunk's slice, which is how world generation places a structure.
3. Unknown blocks, entities, and items resolve through `AeRemap`: the live registry first (a mod installed later just works), then the `ae_remap` tables, then namespace renames, then `structures.missingBlock` / `missingEntity`. Ancient Warfare's own blocks keep their original rule in the template (`aeRule`) and are resolved at this point.
4. Ancient Warfare's pieces become real: advanced spawners become `NpcSpawnerBlock`s (`AwSpawnerImport`), loot chests, baskets, and urns become vanilla containers with the converted tables plus `AeLoot` extras (`AwLoot`), gates become `GateEntity`s, flags become `FlagBlock`s.
5. A whole placement is recorded in `StructureRecords` (below).

World generation (`worldgen/`) is being built: territories per faction, sites chosen from sidecar `validation` data, and placement chunk by chunk through `AePlacer`.

## Garrisons: the NPC spawner

`block/NpcSpawnerBlockEntity` is Ancient Warfare's advanced spawner, simplified. Its nine slots hold scrolls (roster scrolls, repacked NPCs, `MobScrollItem`s, vanilla eggs); the stack size is how many.

- **GARRISON** (structures): when a non-creative player comes within `playerRange`, everyone comes out, homed on the spawner (`NpcEntity.linkSpawner`). Killed ones shrink their slot for good. When every player is past `returnRange`, survivors go back in, so the fort is manned again next visit. When all are dead, the block removes itself.
- **REPEATING**: one at a time on a delay, like vanilla's spawner.
- **REDSTONE**: one per pulse.

A spawner no player is near costs one distance check a second. Imported trigger ranges are scaled by `structures.triggerRangeScale` and floored at `triggerMinRange`, keeping Ancient Warfare's staging (walls first, leaders last). The admin `CaptureWandItem` picks mobs up into scrolls to build spawners by hand.

## Structures, flags, and claims

```
AePlacer ──records──► StructureRecords (per dimension, saved; chunk index → record)
                         record: bounds, owning faction, protection flag, claimer
                              ▲                    ▲
FlagBlock.use ──► StructureClaims.tryClaim    StructureClaims events:
                                                BreakSpeed (protection), RightClickBlock (loot lock)
GateEntity ──► asks the record who may open it
```

- **Records** (`StructureRecords`): the owning faction is the protection flag's, or else the most common faction among its garrisons. A chunk index answers "which structure is this block in" without scanning, for every block break and container click.
- **Protection** (`StructureClaims.onBreakSpeed`): blocks of an unclaimed faction structure mine `protectionMultiplier` times slower.
- **Loot lock** (`StructureClaims.onRightClick`): containers stay shut while an NPC of the faction can see the player (`allowStealing` lets you open them unseen).
- **Claiming** (`StructureClaims.tryClaim`, from right-clicking the protection flag): the hostile NPCs inside score points (1 ordinary, 2 elite, 5 leader, 1 per spawner still holding people). Under `claimThreshold`, the claim succeeds, costs standing with the faction, and the flag shows the claimer's colours. Otherwise the defenders in the way glow.
- A hand-placed protection flag makes its own small record (16 blocks around it).

## Gates

`gate/GateEntity` is one gate of one of seven `GateType`s (vertical wood or iron, single, double, drawbridge). It spans its opening one block thick. While closed it fills the opening with `GateProxyBlock`s: invisible, solid, unmineable blocks whose clicks and damage go to the gate. Collision and pathfinding therefore treat a closed gate as a wall with no special code; opening removes the proxies and the wall moves along its track (`edge`).

- **Who may use it**: creative players, its owner, for an unclaimed faction structure's gate anyone not hostile to the faction, and after a claim the claimer (looked up through `StructureRecords`).
- **Damage**: `structures.gateHealth`; arrows nothing, explosions ten times, axes and pickaxes by material.
- **NPCs** (off by default, `structures.npcUse`): `NpcGateNavigation` plans through a permitted gate's proxies as if they were a wooden door, and `NpcGateGoal` opens it ahead and closes it behind, never while fighting or with an enemy near.
- `client/GateRenderer` draws Ancient Warfare's own gate models rebuilt box for box.

## Client: rendering and animation

- `NpcRenderer` and `NpcModel` draw the NPC on the wide player model with its skin (`NpcSkins` catalog, preloaded off the render thread by `NpcSkinPrefetch`), its outer layer (`NpcSkinLayers`, 3D with 3D Skin Layers), held items (`NpcHeldItemLayer`), a standard bearer's banner (`NpcBannerLayer`), and the status icon and badge (`NpcStatus`, `NpcBadge`, computed on the server and synced as one byte each).
- **Who poses an NPC** (`NpcCombatAnimations`): with Better Mob Combat and Mob Player Animator, their stack (`NpcMobCombatClient`). Otherwise `NpcBetterCombatDriver` plays Better Combat's clips by hand: Player Animator only animates real players, so the driver applies part rotations, the body channel (`NpcRenderer.setupRotations`), and the waist bend itself. The server syncs the real wind-up so the clip and trails (`NpcTrailParticles`) land on the hit.
- EMF is handed the poses it must not override (attack, shield raise, held light, recoil) through `NpcEmfCompat`.
- **Screens**: `NpcScreen` (the NPC inventory, a vanilla container screen over `inventory/NpcMenu`), with popups (`NpcPopupScreen`) for the path chooser, stats sheet (`NpcStatsSheet`), and skin selector (`NpcSkinSelector`). Also `WorksiteScreen`, `WorksiteBoundsScreen`, `NpcSpawnerScreen`, `StandingsScreen`. Only what slot clicks cannot carry goes through `network/` packets, and the server re-checks each one.

## Soft dependencies

Each optional mod has one place in the code that knows about it:

| Mod | Class |
|---|---|
| Better Combat | `combat/BetterCombatBridge` (server), `client/NpcBetterCombatDriver` |
| Better Mob Combat | `combat/MobCombatBridge`, `combat/MobCombatHooks`, `client/NpcMobCombatClient` |
| Spartan Weaponry | `combat/SpartanThrowing`, `combat/WeaponClassifier`, roster fallbacks in `NpcRoster` |
| Spartan Shields | tags and enchantments in `entity/npc/NpcShieldBash` |
| EMF / EMF Compat | `client/NpcEmfCompat`, `client/NpcEmfBetterCombat` |
| 3D Skin Layers | `client/NpcSkinLayers` |
| Moonlight / Amendments | `client/NpcMoonlightCompat` |
| Not Enough Animations | `client/NpcModel` |
| Better Combat Particle | `client/NpcTrailParticles` |

They check `ModList.isLoaded` and use reflection or `compileOnly` APIs, so a missing mod never loads a missing class. A failure logs one warning and falls back. Player-facing details: [soft-dependencies.md](soft-dependencies.md).

## Performance habits

Big battles are the stress case. Patterns used throughout:

- **Shared work**: one candidate list per area for target scans (`NpcTargetCache`), one job goal per NPC (`NpcJobGoal`), one list of Town Halls per dimension (`NpcUpkeep`).
- **Lazy work**: worksites rebuild their to-do list only when a worker asks; spawners check distance once a second; immigration runs once a day.
- **Caches keyed on change**: Better Combat data per item, weapon kinds per item class, hand changes by a version counter (`getHandsVersion`) instead of comparing stacks every tick.
- **Staggering**: scans are offset by entity id so a thousand NPCs do not all scan on one tick.
- **Profiler sections**: per-tick work sits in `level.getProfiler().push("ancientempires_...")`, so Spark and `/debug` show it. `/ancientempires stress` spawns a test battle.

## Tests

- `./gradlew test`: JUnit 5 on rules kept as pure methods so no game is needed: worksite areas, the quarry stair, the perfect-block window, shield turn-taking, command inheritance, morale math, the level curve, and the structure format (`AwsTemplate` parsing, `AeTemplate` round trips, chest pairing, door mending). Put a new rule in such a method and test it there; the game calls the same method.
- `./gradlew runGameTestServer`: `StructureGameTests` places real templates on a headless server and checks blocks, fences, chests, spawners, loot, flags, claims, and gates.
