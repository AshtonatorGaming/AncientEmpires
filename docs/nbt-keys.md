# NPC and item NBT keys

What Ancient Empires saves on its NPCs, items, and blocks. Useful with `/data get entity @e[type=ancientempires:npc,limit=1,sort=nearest]`, with `/summon`, and when editing worlds with NBT tools.

"Packed" means the key is also written into the scroll an NPC becomes when it is repacked (from its inventory screen) or picked up with the Capture Wand. A packed scroll holds the NPC's data under `EntityTag`, the same as a vanilla spawn egg, and placing the scroll restores it.

## NPC keys

The NPC entity is `ancientempires:npc`. Besides vanilla's mob keys (`Health`, `HandItems`, `ArmorItems`, `Attributes`, `CustomName`, ...), it saves:

### Identity

| Key | Type | Packed | Meaning |
|---|---|---|---|
| `AeRoster` | text | yes | The roster row the NPC came from, such as `empire.soldier`. Repacking returns it to that row's scroll. |
| `Faction` | text | yes | Faction id. Empty for owned NPCs. |
| `Owner` | UUID | yes | The owning player. Absent for faction NPCs. |
| `Skin` | text | yes | Skin id from skins.json, such as `empire.soldier#empire_soldier_3`. |
| `BodyScale` | float | yes | Body size; 1.0 normal. |
| `AePath` | text | yes | `settler`, `worker`, or `soldier`. Older saves used `AeCalling` or `AeRole`; those are still read. |
| `AeLevel` | int | yes | Level, 1 to `levels.maxLevel`. |
| `AeXp` | float | yes | XP toward the next level. |
| `AeRank` | text | yes | `soldier`, `sergeant`, `captain`, or `general`, when set by insignia or command. Without it, the rank comes from the roster row. |
| `AeSkill` | int | yes | Pins the NPC's skill to one entry (0 to 3) of every `skill` list, instead of reading the lists by level. For testing or special NPCs. |
| `AeFeintChance` | float | yes | This NPC's own chance to feint, replacing `skill.feintChance`. |

### Orders and home

| Key | Type | Packed | Meaning |
|---|---|---|---|
| `Following` | bool | yes | Following its owner. |
| `HoldPosition` | bool | yes | Holding position (archers only). |
| `HoldPos`, `HoldDim` | long, text | yes | The held spot and its dimension. |
| `Home`, `HomeDim` | long, text | no | Its home and the home's dimension. Not packed: a placed scroll calls its new spot home. |
| `Creative` | bool | yes | The NPC's creative mode (toggled in its inventory by a creative player): it does not eat, gain XP, wear its shield, drop its gear, or get recorded for revival. |
| `AeLevy` | bool | yes | A worker or settler called up to fight. |
| `AeWorksite` | long | no | The worksite it works at. |
| `AeBed` | long | no | The bed it has claimed. |

### Inventory

| Key | Type | Packed | Meaning |
|---|---|---|---|
| `Inventory` | list | yes | The 3x3 backpack. Each entry is an item with a `Slot` byte. |
| `AeSidearmBowSlot` | byte | yes | Set while an archer or skirmisher has swapped its ranged weapon for a backpack sidearm: the backpack slot its ranged weapon went to. The swap is undone on load and before repacking. |

### Upkeep

| Key | Type | Packed | Meaning |
|---|---|---|---|
| `AeNextMeal` | long | no | Game time of the next ration due. |
| `AeMealCredit` | double | no | Rations eaten ahead (a food bundle counts as several). |
| `AeHungry` | bool | no | Out of food; hungry workers do not work. |

### Fighting and morale

| Key | Type | Packed | Meaning |
|---|---|---|---|
| `AeMorale` | float | no | Morale, 0 to 100. |
| `AeMoraleState` | byte | no | 0 steady, 1 wavering, 2 routing, 3 shattered, 4 surrendered. |
| `AeRouts` | int | no | How many times it has routed. |
| `AeCommand` | float | no | Command strength inherited from a fallen commander. |
| `AeRevenge` | list | no | Grudges against players who killed its faction-mates: `Player` (UUID) and `Until` (game time). |

### Mounts

| Key | Type | Packed | Meaning |
|---|---|---|---|
| `AeMountUuid` | UUID | no | The mount it rides or walks back to. |
| `AeMountLost` | bool | yes | Its roster mount died; it is not replaced. |

When an NPC that rides a mount this mod spawned is packed, the mount goes along and is respawned when the scroll is placed.

### Spawner link

| Key | Type | Packed | Meaning |
|---|---|---|---|
| `AeSpawner` | long | no | The NPC spawner it came from. |
| `AeSpawnerSlot` | int | no | Which of the spawner's nine slots. |
| `AeGarrison` | bool | no | Part of a garrison: goes back into the spawner when every player has left. |

### Scroll-only keys

These appear only inside a packed scroll's `EntityTag`, to undo what placing a scroll would otherwise set:

| Key | Meaning |
|---|---|
| `AeNoOwner` | Place without an owner (a faction NPC), even though the player placing it would normally become the owner. |
| `AeNoHome` | Place without a home. |
| `AeUnnamed` | The NPC had no name of its own; do not keep the scroll's name on it. |

### Persistent data (`ForgeData`)

| Key | On | Meaning |
|---|---|---|
| `AeStress` | NPC | Spawned by `/ancientempires stress`; removed by `stress clear`. |
| `AeSpawnedMount` | a mount | This mod spawned it for a rider, so repacking the rider takes it along. |

## Item keys

| Key | On | Meaning |
|---|---|---|
| `AeNightLight` | a torch or lantern | Issued to a faction guard for the night. Taken back at dawn, before death drops, and before repacking. |
| `AeGathered` | any item | A worker gathered it (a fished bow, a saddle), so it unloads it at the worksite instead of keeping it as its own gear. |
| `AeHornCall` | War Horn | The selected call: rally, call to arms, or stand down. |
| `AeGate` | Gate Spawner | The first corner clicked, while choosing a gate's opening. |
| `AeMinCount` | a scroll in a spawner | The fewest of this scroll a garrison sends out (the stack size is the most). From Ancient Warfare's spawners. |

## Block keys

| Key | On | Meaning |
|---|---|---|
| `AeLoot` | a chest, barrel, or pot from a structure (persistent data) | Ancient Warfare's extras for this container (extra rolls, a trapped mob, a message...), done the first time it is opened, then removed. |
| `aeRule` | a block entity inside a structure template | An Ancient Warfare block that is resolved when placed. Only in templates, never in the world. |
