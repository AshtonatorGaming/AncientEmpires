# Commands

Every command starts with `/ancientempires`. Tab completion suggests the sub-commands, faction ids, rank names, morale states, and template names.

## Who may use what

- **Anyone** can run the commands marked "everyone".
- **Operators** (permission level 2, the level `/op` gives) can run the rest. In single player, that means cheats are on.
- Several commands act on **the NPC you are looking at**, within 8 blocks. You must also be allowed to command that NPC: you own it, or you are in creative mode. A faction NPC (one with no owner, such as a structure's guard) can only be targeted from creative mode. If nothing suitable is in view, the command answers "Look at one of your NPCs, within 8 blocks".

## Summary

| Command | Who | What it does |
|---|---|---|
| `/ancientempires standing` | everyone | Opens the standings screen: every faction's banner and your standing with it. Same as the `J` key. |
| `/ancientempires standing <player>` | operators | Lists that player's standings in chat: factions at peace, then hostile ones. |
| `/ancientempires standing <player> set <faction> <value>` | operators | Sets a player's standing with one faction. |
| `/ancientempires standing <player> add <faction> <value>` | operators | Adds to (or, with a negative number, takes from) a player's standing. |
| `/ancientempires standing <player> reset` | operators | Puts all of a player's standings back to each faction's starting value. |
| `/ancientempires work` | everyone | For the looked-at NPC: a checklist of every condition its job needs, green `[ok]` or red `[NO]`. |
| `/ancientempires level` | everyone | For the looked-at NPC: its band (Recruit, Veteran, ...), level, and XP toward the next level. |
| `/ancientempires level <1-100>` | operators | Sets the looked-at NPC's level (capped at `levels.maxLevel`). |
| `/ancientempires rank <rank>` | operators | Sets the looked-at NPC's rank: `soldier`, `sergeant`, `captain`, or `general`. |
| `/ancientempires morale <state> [radius]` | operators | Forces morale, for testing (below). |
| `/ancientempires stress <perSide> <factionA> <factionB>` | operators | Spawns two armies to fight, for performance testing (below). |
| `/ancientempires stress report` | operators | How the stress armies are doing, and the server's tick time. |
| `/ancientempires stress clear` | operators | Removes every stress-test NPC. |
| `/ancientempires structure list [filter]` | operators | Lists structure template categories, or the templates in one. |
| `/ancientempires structure place <template> [rotation]` | operators | Builds a structure at your feet. |
| `/ancientempires structure check <template>` | operators | Reports what a template needs and what its blocks turn into here. |
| `/ancientempires structure export <template>` | operators | Saves a template as a vanilla structure file. |

## Standing

Each player has a standing with each of the 44 factions. Below zero, that faction's NPCs attack the player on sight (unless the player is in creative or spectator mode), and also attack the player's own NPCs. Killing a faction's NPCs lowers standing; see [data-formats.md](data-formats.md#factionsjson) for the per-faction numbers.

`<faction>` is a faction id such as `empire`, `norska`, or `undead`. An unknown id answers "Unknown faction". Standing is clamped to `relations.standingLimit` (1000 by default) either way.

## Work

`/ancientempires work` is the first thing to try when a worker stands around. It checks, one line each: the NPC is on the Worker path, it holds a trade tool in its main hand (and which trade that tool picks), it is owned by a player, it is not following, not fighting, not riding, and not hungry, it is daytime, no rain falls on it, and it has found a worksite (its owner's worksite of that trade within `work.searchRange` of its home). The first red line is usually the answer. Two things the check does not cover are a worksite with nothing to do and a worksite out of the supplies the trade needs (seeds, saplings, feed, fuel); see [troubleshooting.md](troubleshooting.md#workers-stand-around).

## Level and rank

Levels run from 1 to `levels.maxLevel` (20). Ranks are separate from levels: a player promotes their own soldier by using a rank insignia on it, once the soldier has the level for that rank (`command.promoteLevels`). The `rank` command skips that requirement.

## Morale (testing)

`/ancientempires morale <state> [radius]` puts the looked-at NPC into `steady`, `wavering`, `routing`, `shattered`, or `surrendered`. With a radius (1 to 128 blocks), every allied NPC on the same side within that radius is changed too. Use it to see what a routing army looks like without fighting a battle. NPCs that morale does not apply to (civilians, fearless factions, and owned NPCs when `morale.ownedNpcs` is off) are skipped; the reply says how many NPCs changed.

## Stress test

`/ancientempires stress <perSide> <factionA> <factionB>` spawns two armies about 20 blocks in front of you, facing each other 24 blocks apart, in rows of ten. Each army uses its faction's soldier, archer, and leader scrolls (normal and elite). `perSide` can be 1 to 500. If the two factions are not hostile to each other the command warns you that they will not fight, but still spawns them.

- `stress report` gives the number alive, the average server milliseconds per tick (50 is the most a server can spend and keep up), and for each side how many still stand, how many broke, their average level, and the health they have left.
- `stress clear` removes every stress-test NPC loaded in your dimension (they carry an `AeStress` flag in their saved data).

Run [Spark](https://spark.lucko.me/) while a stress battle runs to see where the time goes; this mod's work shows up in sections named `ancientempires_...`.

## Structures

These work on the library of Ancient Warfare structures converted to this mod's format (1,471 of them). Templates are known by their short name, the file name, which is unique: `empireemperorstatuewell`, `covenmushroomvillage`, and so on.

- `structure list` shows the categories (Ancient Warfare's folders, such as `awnationempire`) and how many templates each has. `structure list <category>` lists that category; `structure list <text>` lists templates whose name or category contains the text. At most 80 names are shown.
- `structure place <template> [rotation]` builds the template with its entrance where you stand, facing you. The rotation can be `none`, `clockwise_90` (or `90`), `180`, or `counterclockwise_90` (or `270`) to override that. The reply says how many blocks and entities were placed and how long it took, which blocks were swapped for stand-ins, which Ancient Warfare pieces were made real (spawners, gates, flags, loot chests), and which were left out. The same report goes to the server log. Placing a whole structure records it, so it can be claimed (see [architecture.md](architecture.md#structures-flags-and-claims)).
- `structure check <template>` gives the size, how many kinds of blocks, block entities, and entities it holds, which mods it needs (and whether they are installed), and every block that resolves to something else in this game, with the reason: `table` (an `ae_remap` entry), `namespace` (a renamed mod), or `missing` (nothing knows it; it becomes `structures.missingBlock`).
- `structure export <template>` writes `<world>/generated/ancientempires/structures/<path>.nbt`, a vanilla structure file with the blocks resolved for this game, for a Structure Block to load. Only vanilla entities are kept. Vanilla's Structure Block only shows 48 blocks in each direction, and the reply warns when a template is bigger.
