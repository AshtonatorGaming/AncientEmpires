# Ancient Empires documentation

Ancient Empires is a Minecraft 1.20.1 Forge mod: a cleaner, faster rebuild of Ancient Warfare 2's NPCs (soldiers, workers, factions) and structures (forts, villages, garrisons, gates).

## For pack makers and server owners

You do not need to read code for any of these.

| Page | What it answers |
|---|---|
| [Configuration](config.md) | Every setting in both config files: what it does, its default, its allowed range. |
| [Commands](commands.md) | Every `/ancientempires` command and who may use it. |
| [Data a pack maker can edit](data-formats.md) | The faction table, NPC roster, skin catalog, structure remaps and templates, loot tables, tags, recipes. Which ones a datapack can change and which live in the jar. |
| [Other mods](soft-dependencies.md) | What each optional mod (Better Combat, Spartan Weaponry, EMF, 3D Skin Layers, ...) changes when present or absent, and the log line that says it started. |
| [Troubleshooting](troubleshooting.md) | Symptoms, their causes, and the setting or fix. Starts with every log message the mod writes. |
| [NBT keys](nbt-keys.md) | What NPCs, items, and blocks save, for `/data`, `/summon`, and world editors. |

**Fixing a problem in a pack?** Start with [Troubleshooting](troubleshooting.md): search `logs/latest.log` for `ancientempires`, then match the symptom.

## For developers

| Page | What it covers |
|---|---|
| [Architecture](architecture.md) | A guided tour: the NPC, the goal stack, targeting, combat, workers and worksites, structures, garrisons, claims, gates, rendering. |
| `package-info.java` in each package | A short map of that package and how it connects to the others. |
| [`CLAUDE.md`](../CLAUDE.md) | The dense design record: rules that must hold, the reasons behind numbers, Ancient Warfare's original behaviour. |
| [`STRUCTURES.md`](../STRUCTURES.md) | The structure port's plan and stages. |
| [`DESIGN.md`](../DESIGN.md) | The long-term vision (Tiers 1 to 5). |

Building: `./gradlew build` (needs network access to Forge's and Mojang's servers) and `./gradlew test`. See the Building and Tests sections of `CLAUDE.md`.
