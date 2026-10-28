# Troubleshooting

Symptoms first, then likely causes and what to change. Setting names refer to the server config (`<world>/serverconfig/ancientempires-server.toml`) unless marked client; see [config.md](config.md).

## Start with the log

Open `logs/latest.log` (the client's for visual problems, the server's for gameplay) and search for `ancientempires` or for the messages below. Ancient Empires writes one line at startup for each integration that started, and a warning for each that failed.

| Log message | Meaning | What to do |
|---|---|---|
| `Ancient Empires ready. Better Combat loaded: true/false` | The mod loaded. | Nothing. |
| `Better Combat weapon attributes are available for NPC attacks.` | NPCs read Better Combat weapon data. | Nothing. |
| `Better Combat is loaded, but its weapon attribute API could not be read. NPCs will use the internal attack profile.` | Better Combat's version is not one Ancient Empires understands. NPCs fight with vanilla-style timing and reach. | Try another Better Combat version for 1.20.1 Forge. The stack trace after the line names what was missing. |
| `Better Combat attack and idle animations are available for NPCs.` (client) | NPC animations will play. | Nothing. |
| `Better Combat animations could not be attached to NPCs. They will use the vanilla arm swing.` (client) | Player Animator's version does not match. | Update or change Player Animator. |
| `Better Mob Combat owns NPC attack timing. This mod still applies the hit.` | Better Mob Combat is driving NPC swings. | Nothing. If NPC timing feels wrong, try without Better Mob Combat; it is optional. |
| `Better Mob Combat could not drive NPC attacks. The internal wind-up will be used.` | Version mismatch; NPCs still fight. | Optional: update Better Mob Combat, or remove it. |
| `EMF will keep NPC idle movement and yield arms to Better Combat poses.` (client) | EMF integration started. | Nothing. |
| `EMF compatibility for NPC poses could not start. Better Combat still poses the vanilla model.` (client) | EMF Compat version mismatch. | Update EMF Compat, or ignore: only idle movement is lost. |
| `3D Skin Layers is loaded, but NPC outer layers stayed on the vanilla skin.` / `...could not attach to the NPC model...` (client) | 3D Skin Layers version mismatch. | Update 3D Skin Layers, or ignore: outer layers stay flat. |
| `Moonlight held-item compat is off for this session` (client) | Moonlight/Amendments version mismatch. | Ignore: torches are held the plain way. |
| `Better Combat Particle is loaded, but NPC weapon trails could not start.` (client) | bc_particle version mismatch. | Ignore, or update it: NPCs just have no trails. |
| `Missing faction table ...` / `Could not read the faction table` | `factions.json` inside the jar is missing or is not valid JSON. Every faction becomes neutral to every other. | Restore the original jar, or fix the JSON you edited (a missing comma is the usual cause; the stack trace gives the line). |
| `Missing NPC roster ...` / `Could not read the NPC roster` | `roster.json` is broken. No NPC scrolls exist. | As above. |
| `Missing NPC skin catalog ...` / `Could not read the NPC skin catalog` | `skins.json` is broken. NPCs show the default skin and the selector is empty. | As above. |
| `Skipping NPC skin texture <file>` | A skin's file name cannot be a texture path. That one skin is left out. | Rename the file in `skins.json` and the texture folder to lower-case letters, digits, `_`, `-`, `.`. |
| `Roster row <id> names mount <id>, which is not a mob` | A roster row's `mount` is wrong or its mod is missing. That NPC spawns on foot. | Fix the row, or install the mod. |
| `Structure templates: <n> indexed` | Number of structure templates found at the last `/reload`. 0 means none. | If 0: the mod's data did not load. Check for datapack errors earlier in the log. |
| `Structure remap: <n> blocks, <n> entities, <n> items, <n> namespaces` | The remap tables loaded. | Nothing. |
| `Could not read structure template <file>` | One template file is damaged. That structure cannot be placed. | Restore the mod's jar, or the datapack that supplies it. |
| `Placed <template> at ...` | A structure was placed by command, with what was swapped or left out. | Read the "substituted" list for missing mods. |
| `NPC target scan: <n>/s scans, ...` | Only with `targeting.debugStats = true`: how much target searching costs. | Turn `debugStats` off again when done. |
| `Could not dispense an NPC spawn egg at ...` | A dispenser could not place an NPC scroll. | Report it, with the stack trace. |

## Startup and crashes

**"Timed out trying to setup the Game Window" while the game starts.** Forge's early loading window failed. This is a graphics driver problem and happens before any mod is read. Update the graphics driver, or set `earlyWindowControl=false` in `config/fml.toml`.

**`NoClassDefFoundError` naming an `com.ancientempires` class, partway through a session.** The mod jar was replaced while the game was running. Close the game fully before swapping jars.

**A crash mentioning ModularUI and EMI.** That came from an old build that used ModularUI. Use a current build of Ancient Empires.

## Fighting

**NPCs swing with a stiff vanilla arm, no weapon animations.** Better Combat or Player Animator is missing, or the client log has one of the "could not" warnings above. NPCs still fight correctly; it is only the look.

**NPCs use the wrong reach or timing for modded weapons.** Without Better Combat data, every weapon uses `combat.vanilla.range` (2.5 blocks) and timing from its attack speed. Install Better Combat, or give the weapon Better Combat data with a datapack. If Better Combat is installed, check `combat.betterCombat.enabled`.

**Two factions do not fight each other.** Hostility in `factions.json` is one-way. Check both factions' `hostile` lists. `/ancientempires stress 1 <a> <b>` warns when two factions are at peace.

**A faction attacks me on sight.** Your standing with it is below zero. 27 of the 44 factions start below zero (`standing` in `factions.json`; most at -1000), the Empire and Norska among them. Press `J` to see your standings. An operator can change them: `/ancientempires standing <player> set <faction> 0`. `relations.enabled = false` turns player standing off entirely.

**My own NPCs attack a faction, or ignore one.** Owned NPCs look for monsters and for faction NPCs hostile to their owner (the owner's standing with that faction is below zero). They also turn on anyone who hurts them or their owner, or whom the owner attacks. With `relations.enabled = false` they attack every faction NPC.

**Battles are over in seconds / last forever.** NPC-against-NPC damage is scaled by `combat.npcVsNpcDamage` (0.7). Lower it for longer battles. Morale also ends battles: soldiers rout below `morale.routAt`. To see the effect, `/ancientempires morale routing 20` makes one side run.

**Soldiers run away too easily.** Raise `morale.baseByTier`, lower `morale.oddsWeight` or `morale.woundsWeight`, or set `morale.enabled = false`. Players' own soldiers are steadier (`morale.ownedBaseBonus`) and steadier still with their owner near (`ownerPresenceBonus`).

**NPCs block everything; fights stall.** Shields have guard (stamina): `shield.guardMax` and `shield.guardRegen`. Lower `guardMax` or raise `heavyGuardDrain` so shield walls break sooner. Axes and shield-breaking weapons disable shields.

**NPCs never bash with shields.** With the default `shield.bashMode = SPARTAN`, only shields in Spartan Shields' bash tag bash. Without Spartan Shields, set `bashMode = ALL`.

**NPCs walk off cliffs, or refuse to cross water.** Path safety is in `[hazard]`: `maxFall` (3 blocks) and the water and fire costs.

**Archers stand in one place.** Faction archers keep a 3-block post (`home.factionArcherPost`), and owned archers can be told to hold position. That is by design. Set `factionArcherPost = 0` to let faction archers roam.

**NPCs burn in daylight.** Factions with `"sunlight": "burn"` in `factions.json` (the undead) burn like zombies. A helmet protects them. `sunlight.enabled = false` turns it off.

## Big battles and lag

1. Use [Spark](https://spark.lucko.me/) (`/spark profiler start`, fight, `/spark profiler stop`). This mod's work shows under sections named `ancientempires_...`.
2. `/ancientempires stress <perSide> <a> <b>` spawns a test battle; `stress report` shows the average milliseconds per tick (50 is the limit).
3. `targeting.debugStats = true` logs how many target scans and sight checks run each second.

Settings that cost the most: `targeting.scanRange` (smaller is cheaper), `targeting.scanInterval` (larger is cheaper), `targeting.maxSightChecks`, `targeting.cacheTicks`. Keep `targeting.legacyScan = false`; the vanilla scan it restores is much slower in crowds. On clients, `statusIconsOwnOnly = true` (client config) reduces clutter in big fights.

## NPC access and control

**Right-clicking an NPC does nothing.** You are not allowed to command it. Faction NPCs (no owner: structure guards, faction scrolls) can only be opened in creative mode. Owned NPCs answer only their owner and creative players. The click passes to the item in your hand instead, so a shield still raises.

**The path chooser will not let me change path again.** A player chooses Worker or Soldier once. Only creative players can change it afterwards.

**I cannot promote my soldier.** Using an insignia needs the soldier to have the level for that rank: 6 for sergeant, 12 for captain, 18 for general (`command.promoteLevels`).

**NPCs stay home at night and will not come out.** Faction NPCs keep night hours near home (`home.nightRange`), and doors stay shut at night unless an NPC is following or fighting (`navigation.nightDoorRestriction`). Rain also sends NPCs home (`home.rainShelter`).

## Workers, food, and towns

### Workers stand around

Look at the worker and run `/ancientempires work`. The first red `[NO]` line is the reason. Common ones:

- **Worker path**: a new NPC is a Settler. Click the gold role label in its inventory and choose Worker.
- **Trade tool**: the tool in the main hand picks the trade: hoe (farmer), axe (woodcutter), pickaxe (miner), shears (rancher), bow (hunter), fishing rod (fisher), bowl (cook). The governor (map) and priest (book) are separate jobs that this check does not cover.
- **Daytime / No rain**: workers work by day in dry weather.
- **Not hungry**: see "Hungry NPCs" below.
- **Worksite found**: the owner needs a worksite of that trade (Crop Farm, Tree Farm, Quarry, Animal Pen, Hunting Lodge, Fishing Dock, Kitchen) within `work.searchRange` (32 blocks) of the worker's home.

If every line is green and it still idles, the site has nothing to do or lacks supplies:

- Farmers need seeds; woodcutters need saplings. They fetch them from the worksite's supply slots.
- Ranchers cull each kind of animal down to `work.ranchHerdSize`, shear, and breed below that size with feed (`ancientempires:ranch_feed`) from the Animal Pen's store. Without feed, a pen at or under the herd size with nothing to shear leaves them idle.
- Hunters ignore pets, tamed, named, leashed, and penned animals and the `hunter_ignores` tag, and leave `work.huntKeepPerKind` of each kind. New game wanders in every `work.huntSpawnInterval`.
- Miners at a Quarry need cheap stone (`ancientempires:stair_stone` items) to relay the spiral stair; a dug layer waits for it. `work.quarryStairStone = false` skips that.
- Cooks need raw food and fuel in the Kitchen.
- A full worksite store stops the worker unloading. Empty it.

### Hungry NPCs

Owned NPCs eat a ration every `upkeep.mealInterval` (one in-game day). They eat food from their backpack first, then walk to their owner's nearest Town Hall within `upkeep.range` (100 blocks) for rations. A Town Hall with no food leaves them hungry, and hungry workers do not work. Put food in the Town Hall's store. With no Town Hall in range, NPCs feed themselves unless `upkeep.requireHall = true`. `upkeep.enabled = false` turns upkeep off.

### Settlers do not arrive at the Settlers' Post

Once per in-game day, each open post in a loaded chunk has a `immigration.chance` (50%) to draw settlers. The group is capped by free beds within `immigration.bedRadius` (beds not already claimed by the owner's NPCs there) and by food bundles stocked in the post (`immigration.foodPerSettler` each). Sneak-click the post to see its status; right-click empty-handed to open or close it.

### The priest does not revive anyone

Only NPCs that died within `upkeep.range` of their owner's Town Hall are recorded there (sneak-click the hall to see the fallen). The priest (a Worker holding a book) needs the revival cost in the hall's store: `priest.costCount` of `priest.costItem` (2 gold ingots) while `priest.costEnabled`.

## Structures

**Blocks inside a placed structure are air or the wrong block.** The template used a block from a mod that is not installed. `/ancientempires structure check <template>` lists every substitution and why. Add a mapping in an `ae_remap` datapack file, install the mod, or change `structures.missingBlock`.

**Spawner mobs are zombies.** Same cause, for mobs: `structures.missingEntity`.

**A garrison spawns its soldiers right on top of me.** Raise `structures.triggerMinRange` or `structures.triggerRangeScale`.

**I cannot see the spawners.** By design: hidden spawners are invisible to survival players. Creative players see a placeholder.

**I cannot break blocks / open chests in a faction structure.** Unclaimed faction structures are protected: blocks take `structures.protectionMultiplier` (100) times longer to mine, and containers stay shut while one of the faction can see you (`lootLock`, `allowStealing`). Claim the structure at its protection flag, or change those settings.

**Claiming the flag fails.** The defenders still inside score at least `structures.claimThreshold` (5): ordinary soldiers 1 point, elites 2, leaders 5, each spawner still holding people 1. After a failed claim, the ones in the way glow for `claimWitnessTicks`.

**My NPCs will not go through my gates.** NPCs using gates is off by default: `structures.npcUse = true` turns it on. Even then they never use a drawbridge, a garrisoned fort's gate, or any gate while fighting or with an enemy within `structures.npcHostileRange`.

**A gate cannot be broken.** Gates have `structures.gateHealth` (80). Arrows do nothing; explosions do ten times their damage.

## Looks

**Every NPC of a kind looks the same.** That row's skin type has only one skin in `skins.json`. Add skins with the same `type`. See [data-formats.md](data-formats.md#skinsjson).

**NPCs show a purple and black texture.** The skin's PNG is missing from `assets/ancientempires/textures/entity/npc/`, or a resource pack removed it.

**Icons over NPC heads clutter the screen.** Client config: `showStatusIcons = false`, a smaller `statusIconRange`, or `statusIconsOwnOnly = true`.

**EMI's search bar covers part of the NPC inventory.** Known; it will be fixed with the NPC screen redesign.
