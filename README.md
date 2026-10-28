# Ancient Empires

A Minecraft 1.20.1 Forge mod: Ancient Warfare 2's NPCs and structures, rebuilt to be cleaner and faster. Soldiers that fight with real timing, shields, morale, and ranks; workers who farm, chop, mine, ranch, hunt, fish, and cook by hand; 44 factions with their own relations; and Ancient Warfare's library of forts, villages, and garrisons.

Licensed under GPL-3.0, derived from Ancient Warfare 2 (see `LICENSE.txt` and `CREDITS.txt`).

## Documentation

See **[docs/README.md](docs/README.md)** for the full index:

- Pack makers and server owners: [configuration](docs/config.md), [commands](docs/commands.md), [editable data](docs/data-formats.md), [other mods](docs/soft-dependencies.md), [troubleshooting](docs/troubleshooting.md), [NBT keys](docs/nbt-keys.md).
- Developers: [architecture](docs/architecture.md) and the `package-info.java` in each package.

## Building

```bash
./gradlew build
```

Needs network access to Forge's and Mojang's servers. `./gradlew test` runs the unit tests. `README.txt` is Forge's generic setup guide for modders.
