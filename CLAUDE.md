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

- `entity/npc/NpcEntity` - the NPC. Goals: 0 Float, `OpenDoorGoal`, `NpcNightDoorsGoal`;
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
  full reach at the hit. Facing allows for the target's angular width; touching bodies
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
  `NpcBetterCombatDriver` (per-NPC animation session), ModularUI inventory screen.
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
- Libs in `libs/` are jars wired through `fg.deobf` in `build.gradle`. Soft deps are
  `compileOnly`; ModularUI is `implementation`.

## Done

ModularUI inventory (equipment cross, backpack, name field, skin button, command buttons);
unarmed dual-fist combo via the Better Combat fist preset; inventory persistence and repack
(item keeps name, equipment, skin, home, owner, flags); faction/owned split with the mass AW
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

1. Ancient Warfare baseline still missing: lance charges and other mounted tactics,
   vampire factions avoiding sunlight, day and night patrols, and the
   behavior behind the remaining roles (bard singing, trader, courier, worker
   jobs beyond the farmer, woodcutter, and miner, commander, patrol). Roles are labels from
   equipment until that behavior exists.
2. Skirmishers.
3. Spawner block that stores a full NPC NBT snapshot.
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
  holds its guard, blocks, and ripostes. Past the minimum, a strike comes at a random beat
  (`strikeChance` per tick). Guard (`guardPoints`, like Chivalry stamina): blocked damage
  drains it, it recovers at `guardRegen`, and at zero the shield is knocked down for
  `guardBreakTicks`, so shield walls do not stalemate and players can break a turtle.
  Footwork (`combat.footwork`): inside reach while waiting or recovering, NPCs circle slowly
  (`circleSpeed`, vanilla strafe, which refuses unwalkable steps), close at the edge of reach,
  and give ground when pressed; two shield fighters barely circle (`footworkShieldScale`;
  bouncier footwork is for a later duelist). Against a raised shield an NPC without one starts
  a swing only at `pressureChance` per tick. A hit taken on the shield never knocks the NPC
  back (`NpcEntity.knockback`), as a blocking player stands firm. Guard is `shield.guardMax` (30) times
  `skill.guardByTier` (1 to 1.75). Footwork comes in steps with rests, not a constant drift (which
  slid). Every swing is followed by `combat.attackPause` (4) extra ticks; AW used only the weapon
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
- Skill tiers from roster rank (0 ordinary, 1 elite, 2 leader, 3 elite leader) pick bash
  chance, feint chance, and shield reaction from the `skill` lists.
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
- Access: only admins can open a faction NPC's inventory or command it. Owned NPCs answer
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
  layer waits three scans for that before moving on (`quarryStairStone`). Left standing: blocks
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
  A levied civilian also shows a pitchfork badge (`levied.png`), and NPCs that answered a
  horn rally show the alarm icon for `horn.rallyTicks`.
- War Horn (`WarHornItem`; goat horn, gold, leather): blown, the owner's NPCs within
  `horn.range` answer its call (`NpcEntity.answerHorn`): Rally wakes soldiers and makes them
  follow (archers leave their posts), Call to Arms also levies settlers and workers to follow,
  Stand Down ends levies, following, and fights. Sneak-use cycles the call (`AeHornCall`).
- Speed: a mob's speed counts twice (`Mob.setSpeed` also sets the forward input), so pace
  goes with the square of attribute times multiplier. `BASE_SPEED` 0.32 walks at a player's
  pace at 1.0; chasing (`approachSpeed`) and following use 1.12, a sprint. Saves with the
  old 0.28 base are updated on load.
- Parry (`combat/Parry`, `parry` config), the same for players and NPCs: a `windowTicks` (6) guard
  with the weapon. A melee hit from the front inside it is cancelled and the attacker staggered
  (slowness, no swing for `staggerTicks`; `NpcEntity.stagger` breaks off a wind-up); a parry that
  catches nothing leaves the parrier open for `recoveryTicks` (a player's attack cooldown resets).
  Players press Parry (R, `ParryPacket`; no arm swing, which Better Combat could turn into an
  attack). NPCs without a shield parry in `NpcParryPlanner`: against an NPC, once per swing at
  `chanceByTier`, timed on the hit within `timingErrorByTier` (an early parry is what a feint
  baits; feints now also target NPCs that parry); against a player, which shows no wind-up, it
  reads a charged attack in reach and facing it. NPC hits restore the target's hit immunity
  instead of granting one, so they never eat a player's swing. `NpcModel.guardPose` puts the
  raised shield (and the parry guard) back after any combat stance, so what blocks is what shows.
- Fairness against players: NPC attack knockback is 0 (every hit already gives 0.4, as for
  players; the old 0.4 base shoved players out of reach each exchange, updated on load), and
  `bypassHurtCooldown` never applies to players, who keep vanilla's hit immunity.
- Night lights (`NpcNightLight`, `nightLight`): soldiers awake at night or in the dark
  (below `darkness`) with a free off hand hold a torch or lantern (`ancientempires:night_lights`
  tag), held out level in `NpcModel.holdLight`; never with a bow, crossbow, or Better Combat
  two-hander. Factions with `"nightLights": false` in factions.json (beast, demon, ent, giant,
  kong, monster, mindflayer, gremlin, vampire) are not issued lights. With Amendments (Moonlight's
  held-item API, `NpcMoonlightCompat`, reflection) NPCs get its arm pose and 3D lantern and torch,
  which Moonlight only hooks for players; without it the item is stood upright on the level arm. Owned ones use their backpack's and put it back by
  day; faction guards are issued one (`AeNightLight`, stripped before death drops and
  repack). Dynamic light mods light held items on any living thing, so no compat code.
- Reactions: `NpcBurningGoal` (`upkeep.burningPanic`): burning and not fighting (or any
  civilian) runs for water within 8 blocks. Poses are soft dependencies: with Not Enough
  Animations, eating raises the food to the mouth; with its EMF add-on, burning flails and
  freezing shivers (`NpcModel.reactionPose`). Without them NPCs look as vanilla mobs do.
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
- Levy (`AeLevy`): an owner can call a civilian up to fight until dismissed. Debuffs in the
  `proficiency` section: damage 0.7, slower wind-ups, no bash or feint, breaks below 30%
  health.
- Proficiency (from the AncientTactics trial): melee by an archer (bow or crossbow in hand
  or backpack, so sidearm swaps count) times 0.5, by a skirmisher times 0.75. Cached on
  hands, backpack, path, and levy changes.
- NPC NBT keys: `AeRoster` (roster row), `AePath`, `AeLevy`, `AeSkill` (tier override),
  `AeFeintChance`, `AeSidearmBowSlot`, `AeMountLost`, `AeRevenge`, `AeWorksite`, `AeHungry`, `AeNextMeal`, `AeMealCredit`, `AeBed` (`AeMountUuid` is not packed). All carried through repack.

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
- Still open: server 6 (per-swing sound id parse, string animation id sync) and the
  first-render PNG decode hitch for new skins.

## Testing

- The user tests in a separate game instance (see their notes); check its `logs/latest.log`
  to see which combat and animation path is live (Better Mob Combat, the fallback driver,
  EMF, bc_particle) before guessing.
- Never replace the mod jar while that game is running. A jar swapped mid-session made a
  not-yet-loaded class fail with `NoClassDefFoundError`, which looked like a mod bug.
- Forge's early loading window failing ("Timed out trying to setup the Game Window") is a
  graphics driver problem; it happens before any mod jar is read.

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
- Arm swings: vanilla advances `swingTime` only in `Monster` and `Player`, so `NpcEntity.aiStep`
  calls `updateSwingTime` (without it `swing()` never animates). Any action a player would
  swing for (work, deposits, bandaging) calls `swing`. Melee sends the vanilla swing at the
  hit; `NpcModel` drops it only while a Better Combat clip is playing
  (`NpcCombatAnimations.attackClipPlaying`), so a missing or failed clip falls back to it.
