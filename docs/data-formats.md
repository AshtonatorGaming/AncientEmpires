# Data a pack maker can edit

This page covers every data file Ancient Empires reads, where it lives, and how to change it. There are two kinds:

- **Datapack data** (`data/...`): structure remaps, structure templates, loot tables, tags, and recipes. Change these with an ordinary datapack, the same way you change vanilla data. `/reload` picks up changes.
- **Built-in tables** (`assets/ancientempires/npc/...`): the faction table, the NPC roster, and the skin catalog. These are read **once at startup, straight from the mod's jar**. A datapack or resource pack cannot replace them. To change them you have to edit the copies inside the jar (or build the mod yourself). The skin *pictures* are the exception: they load like any texture, so a resource pack can replace them.

In the tables below, "id" means a namespaced id such as `minecraft:iron_sword`.

---

## Built-in tables

### factions.json

`assets/ancientempires/npc/factions.json`: who fights whom. Ported from Ancient Warfare 2 Tweaked 1.6.1. There are 44 factions.

```json
{
  "mob_lists": { "townsfolk": ["minecraft:villager", "minecraft:iron_golem", "..."] },
  "factions": [
    {
      "id": "norska",
      "ranks": { "sergeant": "Huscarl", "captain": "Hersir", "general": "Jarl" },
      "standing": -1000,
      "kill": -100,
      "trade": 2,
      "hunts": "monsters",
      "hostile": ["empire", "sealsker", "..."]
    }
  ]
}
```

| Field | Meaning | If missing |
|---|---|---|
| `id` | The faction id, lower case. Roster rows and NPCs refer to it. | required |
| `hostile` | The factions this one attacks. **One-way**: Norska listing Sealsker does not make Sealsker attack Norska. | required |
| `standing` | A new player's standing with this faction. Below 0, the faction attacks that player and the player's NPCs on sight. | 0 |
| `kill` | Standing change each time a player (or the player's NPCs) kills one of this faction's NPCs. Usually negative. | 0 |
| `trade` | Standing change per trade. Trading is not implemented yet. | 0 |
| `hunts` | `monsters`: attacks hostile mobs (zombies, skeletons, any mob that counts as an enemy). `townsfolk`: leaves monsters alone and attacks the mobs in `mob_lists.townsfolk` (villagers, golems, and modded townsfolk) instead. | monsters |
| `nightLights` | `false` stops the faction's guards carrying torches and lanterns at night (beasts, demons, giants...). | true |
| `fearless` | `true`: its soldiers never waver or rout (undead, demons, monsters). | false |
| `ranks` | The faction's own names for `sergeant`, `captain`, and `general`. | Sergeant, Captain, General |
| `command` | How the faction is led: `ranks` (commanders inspire nearby troops), `pack` (beasts: the leaders are alphas), or `none` (no commanders). | ranks |
| `sunlight` | `burn`: burns in daylight like a zombie (a helmet takes the wear) and seeks shade once alight. `avoid`: also keeps to shade by day before catching fire (vampires). `none`: unaffected. | none |

`mob_lists.townsfolk` lists mob ids; ids from mods that are not installed are skipped.

A faction's display name comes from the language key `faction.ancientempires.<id>` (a resource pack can set it); without one, the id is shown capitalized.

### roster.json

`assets/ancientempires/npc/roster.json`: every NPC that has a scroll (spawn item). The game creates one item per row at startup, so rows cannot be added without a restart, and removing a row removes its item from existing worlds.

```json
{
  "owned": [
    { "id": "owned.farmer", "subtype": "farmer", "name": "Farmer", "skin": "worker.farmer",
      "item": "owned_farmer_spawn_egg", "mainhand": "minecraft:iron_hoe" }
  ],
  "factions": [
    { "id": "empire", "name": "Empire", "entries": [
      { "id": "empire.soldier", "subtype": "soldier", "name": "Empire Spearman", "skin": "empire.soldier",
        "item": "empire_faction_soldier_spawn_egg", "mainhand": "spartanweaponry:iron_spear",
        "mainFallback": "minecraft:iron_sword", "offhand": "minecraft:shield", "health": 20 }
    ]}
  ]
}
```

`owned` rows make NPCs that belong to the player who places them. `factions` rows make faction NPCs: they belong to the faction named by the group's `id`, have no owner, and only creative players can open or command them.

| Field | Meaning |
|---|---|
| `id` | Row id. Saved on the NPC (`AeRoster`) so it can be repacked into the same scroll. |
| `subtype` | The NPC's rank in Ancient Warfare's terms: `soldier`, `archer`, `leader`, `cavalry`, `mounted_archer`, `priest`, `trader`, `civilian.female`, `spellcaster.2`, and so on. A `.elite` ending makes an elite. See below for what it decides. |
| `name` | The NPC's name. |
| `skin` | A skin type from skins.json (such as `empire.soldier`). A random skin of that type is picked when the NPC is first placed. A full skin id (`empire.soldier#empire_soldier_3`) pins one. |
| `item` | The scroll's item name (becomes `ancientempires:<item>`). Must be unique. |
| `mainhand`, `offhand` | Starting gear. An id, optionally with a count: `ancientempires:bandage*16`. |
| `mainFallback`, `offFallback` | Used when the first choice's mod is not installed (Spartan Weaponry spears fall back to iron swords). |
| `health` | Maximum health. Omit for the default 20. |
| `scale` | Body size, 1.0 normal. |
| `mount` | A mob id the NPC spawns riding (`minecraft:polar_bear`). Rows whose subtype starts with `cavalry` or `mounted_archer` ride a `minecraft:horse` unless this names another. A mount id that is not a mob is logged as a warning. |
| `glyph` | Kept from the source data; not read. |

What `subtype` decides:

- **Path**: `civilian...` and the worker trades (worker, farmer, miner, woodcutter, craftsman, researcher, courier, trader, priest, bard) are Workers, which flee from fights. `commoner` and `settler` are Settlers. Everything else (soldiers, archers, leaders, cavalry, medics, spellcasters...) fights.
- **Rank and starting level**: `leader` rows are captains, `leader.elite` generals. Elite and leader rows start at a higher level (`levels.factionMinByTier`, `factionMaxByTier`).
- **Death loot**: soldier, archer, leader (and their elites), cavalry, mounted_archer, priest, and siege_engineer rows drop Ancient Warfare's table for their type (`loot_tables/aw/entities/`, with an `undead_` version for the undead faction). Owned NPCs only drop their gear.

Each faction's scroll color is fixed in the code (`NpcRoster`), not in this file.

### skins.json

`assets/ancientempires/npc/skins.json`: the skin catalog the skin selector shows and the renderer draws.

```json
{
  "factions": [
    { "id": "empire", "name": "Empire", "classes": [
      { "id": "soldier", "name": "Soldier", "skins": [
        { "id": "empire.soldier#empire_soldier_1", "type": "empire.soldier",
          "name": "Empire Spearman 1", "file": "empire_soldier_1.png" }
      ]}
    ]}
  ]
}
```

| Field | Meaning |
|---|---|
| `factions[].id`, `name` | A tab in the skin selector. |
| `classes[].id`, `name` | A class within the faction (the selector's second level). Classes named `spellcaster` or `spellcaster.<n>` are merged into one Spellcaster class. |
| `skins[].id` | The skin id saved on an NPC (`Skin`). By convention `<type>#<file name>`. |
| `skins[].type` | The type a roster row asks for. All skins of a type form the random pool for new NPCs. Defaults to the id. |
| `skins[].name` | Shown in the selector. |
| `skins[].file` | The texture file, in `assets/ancientempires/textures/entity/npc/`. Upper-case letters and characters not allowed in a texture path are lowered or replaced with `_`; a name that still is not valid is skipped with a "Skipping NPC skin texture" warning. |

The `misc` faction is special: its classes for the player's own jobs (`worker...`, `combat...`, `bard`, `courier`, `priest`, `siege_engineer`, `trader`) become the **Player Default** tab, and every other `misc` class is merged into one **Custom** tab.

The same picture listed twice in one class is shown once. Many roster rows (217 of 606 when this was written) have a type with a single skin, so every NPC of that row looks alike; adding more skins with the same `type` fixes that.

Skin textures are 64x64 player skins (the outer layer is used, and drawn in 3D with 3D Skin Layers). Because they load like any texture, a resource pack can replace any of them by file name.

---

## Datapack data

### ae_remap: what old blocks become

`data/<namespace>/ae_remap/*.json`. Ancient Warfare's structures were built in Minecraft 1.12 with many mods. When a structure is placed, each block, entity, and item it names is looked up in this order:

1. The id as it is, if this game has it (a mod you install later just works).
2. The remap tables below.
3. A namespace rename (a mod that changed its id).
4. Blocks: `structures.missingBlock` (air by default). Spawner mobs: `structures.missingEntity` (a zombie by default).

All files in all datapacks are merged. Files are read in order of their id, and a later entry with the same key wins. To replace one of the mod's own files, put a file at the same path in your datapack (`data/ancientempires/ae_remap/mods.json`).

```json
{
  "namespaces": { "xreliquary": "reliquary" },
  "blocks": {
    "ancientwarfarestructure:chair": "minecraft:oak_stairs",
    "ancientwarfarestructure:stool": { "Name": "minecraft:oak_slab", "Properties": { "type": "bottom" } }
  },
  "entities": { "iceandfire:if_troll": "iceandfire:troll" },
  "items": { "ancientwarfarenpc:wooden_spear": ["spartanweaponry:wooden_spear", "minecraft:wooden_sword"] }
}
```

| Key | Maps | Notes |
|---|---|---|
| `namespaces` | old mod id to new mod id | Applies to blocks, entities, and items not found otherwise. |
| `blocks` | block id to a block id, or to `{"Name": ..., "Properties": {...}}` | With a plain id, the old block's properties (facing, axis, lit...) carry over where the new block has them. With an object, the given properties are set on top. |
| `entities` | entity id to entity id | Used for mobs in spawners and mobs placed directly. |
| `items` | item id to an id, or a list of ids | A list is tried in order; the first one installed wins. Used in loot and spawner gear. |

The mod ships two files: `ancientwarfare.json` (stand-ins for Ancient Warfare's own blocks and items) and `mods.json` (renamed mods). `/ancientempires structure check <template>` shows what each of a template's blocks resolves to and why.

### ae_templates: the structure library

`data/ancientempires/ae_templates/<category>/.../<name>.nbt` plus a `<name>.json` sidecar next to it. These are Ancient Warfare's 1,471 structures, converted once from the original `.aws` files by `./gradlew convertAwsTemplates`. The `.nbt` files are this mod's own compact format (not vanilla's structure format), so they cannot be made by hand; `/ancientempires structure export` turns one into a vanilla structure file.

The sidecars are read at every `/reload`; a template's blocks are only loaded when it is placed. A datapack can change a sidecar by putting a file at the same path, or hide a template from the library by replacing its sidecar with `[]` (anything that is not a JSON object is skipped).

```json
{
  "name": "CovenMushroomVillage",
  "source": "normal/AWNationCoven/CovenMushroomVillage.aws",
  "size": [43, 24, 44],
  "offset": [22, 3, 43],
  "requiredMods": [],
  "usesNamespaces": ["ancientwarfarestructure", "ebwizardry", "primitivemobs"],
  "validation": { "type": "ground", "biomeList": ["minecraft:swamp", "..."], "territoryName": "coven", "...": "..." },
  "header": { "version": "2.11", "...": "..." }
}
```

| Field | Meaning |
|---|---|
| `name`, `source` | The original name and file. |
| `size` | Width, height, length in blocks. |
| `offset` | Where the build key sits inside the template. The build key goes at the player's feet when placed; its height is the first layer above ground. |
| `requiredMods` | Mods the template needs. `structure check` reports whether each is installed. |
| `usesNamespaces` | Every mod id its blocks mention (for reports). |
| `validation` | Ancient Warfare's own placement rules, kept as written (strings): `type` (ground, water, island, underground...), `biomeList` (biomes upgraded to modern ids) and `biomeWhiteList`, `territoryName`, `selectionWeight`, `clusterValue`, `minDuplicateDistance`, `unique`, `border`, `leveling`, `fill`, `survival`, `worldGenEnabled`, and others. World generation reads these; see the note below. |
| `header` | The original file's header. |

World generation honors `worldGenEnabled`, `type` (`ground`, `water`, `island`; others are not generated yet), `biomeList` / `biomeWhiteList`, `dimensionList` / `dimensionWhiteList` (empty: overworld only), `territoryName`, `selectionWeight`, `clusterValue`, `unique`, `minDuplicateDistance`, `leveling`, `fill`, `border`, `minWaterDepth` / `maxWaterDepth` (islands), `minGenerationHeight` / `maxGenerationHeight` (land), and `requiredMods`. See [World generation](config.md#world-generation). Templates saved with the Structure Scanner (in the game folder's `ancientempires/scanned` pack) start with `worldGenEnabled` false; edit their sidecar to let them generate.

### Territory settings

`data/<namespace>/ae_territories/*.json`: Ancient Warfare's per-territory tuning in its own format (`defaults` plus a `territories` map). See [World generation](config.md#world-generation).

### Loot tables

`data/ancientempires/loot_tables/aw/`: Ancient Warfare's 185 loot tables, converted.

- `aw/chests/...`: the tables structures' chests, barrels, and pots roll. A structure's container keeps the table name it was built with.
- `aw/entities/<type>` and `aw/entities/undead_<type>`: what faction NPCs drop (see roster.json above).

These are ordinary loot tables; override one by putting a file at the same path in a datapack. They use one extra entry type for items from mods that may not be installed:

```json
{ "type": "ancientempires:item", "name": "ancientwarfarenpc:iron_spear", "fallback": "minecraft:iron_sword", "weight": 5 }
```

`name` is resolved when the loot is rolled, through the `ae_remap` item tables and namespace renames. If it still does not exist, `fallback` is given, or nothing. (A vanilla `minecraft:item` entry naming a missing item would break the whole table.)

### Tags

Ordinary tags; add to them with a datapack (`"replace": false`).

| Tag | Kind | Used for |
|---|---|---|
| `ancientempires:night_lights` | items | What soldiers hold up at night. Torches and lanterns by default. Faction guards are given a torch or lantern; owned soldiers use one from their backpack. |
| `ancientempires:ranch_feed` | items | What a rancher feeds animals to breed them, and fetches from its Animal Pen. |
| `ancientempires:stair_stone` | items | Cheap stone a miner lays as the quarry's spiral stair steps, and keeps as a supply. |
| `ancientempires:stair_stone` | blocks | Blocks a quarry stair step may already be. Steps of anything else (ore, dirt, gravel, air) are dug out and relaid. |
| `ancientempires:hunter_game` | entity types | Extra creatures a hunter may hunt, beyond ordinary animals. Empty by default. |
| `ancientempires:hunter_ignores` | entity types | Animals a hunter never hunts: wolves, bears, horses, pets, and others. |
| `spartanshields:shields_with_bash` | items | Shields NPCs may bash with, when `shield.bashMode` is `SPARTAN` (the default). Comes from Spartan Shields. |
| `spartanshields:tower_shields` | items | Bashes with these have the longer `shield.towerBashCooldownTicks`. |

### Recipes

`data/ancientempires/recipes/`: ordinary crafting recipes (the Settler scroll, food bundle, bandages, rank insignia, War Horn, coins and coin stacks, the worksite blocks, the Town Hall, the Settlers' Post, and worksite upgrades). Override or remove them with a datapack as usual.

### Better Combat weapon data for Spartan spears

`data/spartanweaponry/weapon_attributes/base/spear.json` gives Spartan Weaponry's spears the trident's Better Combat attacks (with reach 3.5, one-handed) instead of Spartan's single stab. The mod loads after Spartan Weaponry so this file wins. A datapack can override it in turn. It does nothing unless both Better Combat and Spartan Weaponry are installed.
