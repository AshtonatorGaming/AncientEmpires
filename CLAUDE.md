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

- `entity/npc/NpcEntity` - the NPC. Goal priorities: 0 Float, 1 `NpcBowAttackGoal`,
  2 `NpcMeleeAttackGoal`, 3 hold position, 4 follow owner, 5 return home, 6+ idle.
  Targets: hurt-by (priority 1), then `NpcTargetGoal` (priority 2): one staggered scan over
  a shared per-cell candidate list (`NpcTargetCache`), faction filter first, sight checks
  nearest first. `targeting.legacyScan` restores the two vanilla goals.
- Bow vs melee are two separate goals split by `isHoldingBow()`. The melee goal only runs
  when it is false. A bow in the main hand keeps the archer goal for the whole fight.
  Crossbows are not bows here.
- `combat/AttackProfiles` resolves the next swing (Better Combat attributes if present,
  vanilla cooldown otherwise, fist preset for empty hands). `AttackSwing` is the result.
- `combat/BetterCombatBridge`, `MobCombatBridge`, `MobCombatHooks` are reflection bridges.
- `NpcSkins` + `assets/ancientempires/npc/skins.json` - skin catalog (45 factions plus a
  large `misc` group). `NpcRoster` maps old AW NPC types to equipment/skins.
  `NpcSkinSelector` is the three-level selector screen (faction, class, skin).
- Client: `NpcRenderer`, `NpcSkinLayers` (outer layers, optional 3D Skin Layers),
  `NpcBetterCombatDriver` (per-NPC animation session), ModularUI inventory screen.
- Libs in `libs/` are jars wired through `fg.deobf` in `build.gradle`. Soft deps are
  `compileOnly`; ModularUI is `implementation`.

## Done

ModularUI inventory (equipment cross, backpack, name field, skin button, command buttons);
unarmed dual-fist combo via the Better Combat fist preset; inventory persistence and repack
(item keeps name, equipment, skin, home, owner, flags); faction/owned split with the mass AW
import; three-level skin selector with search; Player Default and Custom/Misc tabs; archers
keep shooting at close range and never punch with a bow; home set/clear and return-home;
outer skin layers; sidearm fallback (`NpcSidearm`, `WeaponClassifier`, `sidearm` config
section, `AeSidearmBowSlot` NBT marker undone on load and before repack).

## Not done yet

1. Target memory (remember the last target a few seconds after line of sight breaks).
2. Spawner block that stores a full NPC NBT snapshot.
3. Faction relations and revenge lists, upkeep, worker AI, worksites, town hall, orders,
   the structure template port, and Tier 2+ roles.

## Sidearm fallback plan

- Trigger inside `NpcBowAttackGoal.tick()`: target within a new, smaller `CORNERED_RANGE`
  (about 3 blocks; `MELEE_RANGE` is 4.0) and the backpack holds a usable melee weapon
  (has attack damage, not a bow, crossbow, throwing weapon, or shield).
- Swap by exchanging the main-hand bow with that backpack slot through `NpcInventory`
  (slot-to-slot, no merging). Once the bow leaves the main hand, `isHoldingBow()` is false
  and `NpcMeleeAttackGoal` takes over with no changes to it.
- Swap back when the target dies or stays beyond about 6 blocks for a short while, into the
  same slot it came from. Use a cooldown (about 40 ticks) so it does not flip-flop.
- Persist a small "primary is ranged" marker in NBT so a save mid-fight reloads with the
  bow back as primary. Carry it through repack.
- Bow holders only. Hold-position, owned/faction split, and unarmed behavior are unchanged.

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
  per-frame cost is in `NpcSkinLayers` when 3D Skin Layers is loaded: about ten reflective
  config reads, a `View` allocation, and six reflective `setInjectedMesh` calls per NPC.
  Not changed yet.
- Client `NpcBetterCombatDriver.updatePose`: overstated. It calls `WeaponRegistry` directly,
  which is two map gets for a stack without NBT. Not changed.
- Not audited before, now checked: `NpcEmfCompat.afterPose` allocates two to four
  `PoseSnapshot`s, a `Vector3f`, and a `HashMap` per attacking NPC per frame, and checks
  `ModList` each call. `NpcHeldItemLayer` asks `NpcEmfCompat.placesArm` per arm per frame,
  three reflective calls each. `MobCombatBridge.owns` runs Better Mob Combat's weapon check
  every melee tick when that mod is loaded. `MobCombatHooks.cancel` walked fields with
  exceptions (fixed). UI screens are dirty-flag driven and fine.

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
  `AncientEmpiresConfig`, not as hard-coded numbers.
