# Other mods (soft dependencies)

Ancient Empires needs only Minecraft 1.20.1 and Forge. Every mod on this page is optional: the game runs without each one, and NPCs still fight, work, and render. When one is installed, Ancient Empires notices it at startup and uses it.

Mods this page does **not** need: Ancient Warfare itself (Ancient Empires replaces it), and bridge mods such as Better Mob Combat's add-ons (Ancient Empires talks to Better Combat itself).

To see which integrations started in a given game, search `logs/latest.log` for `ancientempires` or for the messages quoted below. Client-side messages appear in the client's log, not the server's.

## Quick table

| Mod | Side | With it | Without it |
|---|---|---|---|
| Better Combat | both | NPCs use each weapon's Better Combat reach, combos, and timing; two one-handed weapons dual-wield; empty hands use Better Combat's fist combo. | Every weapon uses the vanilla fallback in `combat.vanilla` and `combat.unarmed`. |
| Player Animator | client | NPCs play Better Combat's attack and weapon-hold animations. | NPCs use the plain vanilla arm swing. |
| Better Mob Combat (+ Mob Player Animator) | both | That mod times NPC swings and plays their animations; Ancient Empires still lands the hit. | Ancient Empires times swings itself. |
| Entity Model Features (EMF) + EMF Compat | client | NPCs keep EMF's idle body movement, and EMF hands the arms over to attack and shield poses. | Vanilla player model movement. |
| Entity Texture Features (ETF) | client | Nothing of its own here; EMF requires it. | |
| 3D Skin Layers | client | NPC skins' outer layer (hat, jacket, sleeves) is drawn in 3D up close, as for players. | The outer layer is flat, as vanilla draws it. |
| Spartan Weaponry | both | Faction NPCs carry spears, halberds, and the like; throwing weapons make skirmishers; spears get trident-style Better Combat attacks. | Those rows carry their fallback (usually an iron sword). |
| Spartan Shields | both | Its shields work for NPCs, and NPCs shield-bash with the shields that bash for players. | Vanilla shields; no NPC bashes unless `shield.bashMode` is `ALL`. |
| Amendments (with Moonlight) | client | NPCs hold torches and lanterns with Amendments' pose and 3D models. | The light is held upright on a level arm. |
| Not Enough Animations | client | NPCs raise food to the mouth when eating. With its EMF compat add-on, burning NPCs flail and freezing ones shiver. | No special poses. |
| Better Combat Particle (`bc_particle`) | client | NPC weapon swings leave its trails. | No trails. |
| EMI | client | Works alongside the NPC screens. | |
| Dynamic light mods | client | Torches and lanterns NPCs carry light up the area. | |

## Details

### Better Combat

Mod id `bettercombat`. Ancient Empires reads Better Combat's weapon data for every weapon an NPC swings: reach, attack angle, the combo of attacks, upswing (how long before the hit lands), and whether the weapon is two-handed. This drives NPC melee.

- Turn it off without removing the mod: `combat.betterCombat.enabled = false`.
- Empty hands use Better Combat's fist preset when `combat.unarmed.preferBetterCombatProfile` is on.
- Two one-handed weapons with Better Combat data (main and off hand) dual-wield: the combo alternates hands, as for players (`combat.dualWield`).
- Better Combat's upswing setting is read from its own server config when `combat.betterCombat.readUpswingFromMod` is on.
- Weapons Better Combat counts as two-handed drain a shield's guard faster (`shield.heavyGuardDrain`).

Logs: `Better Combat weapon attributes are available for NPC attacks.` when it works. `Better Combat is loaded, but its weapon attribute API could not be read. NPCs will use the internal attack profile.` when the installed version does not match what Ancient Empires expects; NPCs then fight with the fallback.

### Player Animator

Mod id `playeranimator`, client only. Better Combat uses it for player animations; Ancient Empires uses it to play the same attack and hold animations on NPCs, including body twist and waist bend.

Logs: `Better Combat attack and idle animations are available for NPCs.` or, on failure, `Better Combat animations could not be attached to NPCs. They will use the vanilla arm swing.`

### Better Mob Combat

Mod id `bettermobcombat`, with Mob Player Animator (`mobplayeranimator`). Optional and not needed. When it, Better Combat, and both animator mods are installed, Better Mob Combat decides when NPC swings start and land and plays their animations; Ancient Empires still applies the hit and its own rules (blocks, perfect blocks, NPC-vs-NPC damage).

Better Mob Combat's default `mobs_check_for_same_entity_type` setting stops a mob from hitting its own entity type, and all Ancient Empires NPCs are one entity type. Ancient Empires lands those hits itself, so NPC-against-NPC fights still work.

Logs: server `Better Mob Combat owns NPC attack timing. This mod still applies the hit.`; client `Better Mob Combat and Mob Player Animator are posing NPCs. The fallback stack stays idle.` On failure: `Better Mob Combat could not drive NPC attacks. The internal wind-up will be used.`

### Entity Model Features (EMF) and EMF Compat

Mod ids `entity_model_features`, `emf_compat_core`, `emf_compat_better_combat`, client only. EMF animates NPCs like players (idle sway and so on). The compat add-on lets Better Combat poses take over the arms during an attack. Ancient Empires also hands EMF its own poses (raised shield, held light, perfect-block recoil) so EMF does not undo them.

Log: `EMF will keep NPC idle movement and yield arms to Better Combat poses.` or `EMF compatibility for NPC poses could not start. Better Combat still poses the vanilla model.`

### 3D Skin Layers

Mod id `skinlayers3d`, client only. NPCs get the same 3D outer-layer meshes players get, within the same distance. Without it, the outer layer is drawn flat; parts of the outer layer the skin leaves empty are hidden either way.

Logs on failure: `3D Skin Layers is loaded, but NPC outer layers stayed on the vanilla skin.` or `3D Skin Layers could not attach to the NPC model. Outer layers stay on the vanilla skin.` Usually a version mismatch.

### Spartan Weaponry

Mod id `spartanweaponry`.

- **Roster gear**: many faction rows carry Spartan weapons (`spartanweaponry:iron_spear`, halberds, ...). Without the mod, each row's `mainFallback` or `offFallback` is used instead.
- **Skirmishers**: an NPC holding a Spartan throwing weapon (javelin, tomahawk, throwing knife, boomerang) throws it from range, keeps its distance, and draws a melee weapon from its backpack when cornered (`skirmish` config section).
- **Shield breakers**: Spartan weapons with Shield Breach disable shields, as axes do, and drain guard faster.
- **Spears**: this mod's data gives Spartan spears the trident's Better Combat attacks. With an animation datapack such as Malfu's Combat Animation, that is a long combo of stabs and sweeps. Needs Better Combat.
- **Structures**: Ancient Warfare's own spears and halberds in loot and spawners become Spartan's, or a vanilla sword without it (the `items` table in `ae_remap`).

### Spartan Shields

Mod id `spartanshields`. NPCs recognize any shield that can block (Forge's shield-block action), so Spartan's shields work like vanilla ones. With `shield.bashMode = SPARTAN` (the default), NPCs bash only with shields in the `spartanshields:shields_with_bash` tag, as players can; tower shields use the longer cooldown. Spartan's Payback and Firebrand enchantments work on NPC bashes.

### Amendments and Moonlight

Mod ids `amendments` (the poses and 3D models) and `moonlight` (the library it uses), client only. Night-watch soldiers holding a torch or lantern get Amendments' arm pose and 3D lantern, which Amendments otherwise only gives players.

Log on failure: `Moonlight held-item compat is off for this session`.

### Not Enough Animations

Mod id `notenoughanimations`, client only. Eating NPCs raise food to the mouth. With `emf_compat_not_enough_animations` as well, NPCs on fire flail and freezing NPCs shiver.

### Better Combat Particle

Mod id `bc_particle`, client only. That mod draws swing trails for players and Better Mob Combat mobs. Ancient Empires triggers the same trails at the hit frame of every NPC swing it times itself.

Logs: `Better Combat Particle trails are enabled for NPC swings.` or `Better Combat Particle is loaded, but NPC weapon trails could not start.`

### EMI

No integration code. The NPC screens are built on the vanilla inventory screen and work with EMI. EMI's search bar can overlap the NPC inventory; this is known and will be fixed with a later screen redesign. (An earlier build used ModularUI, which crashed games that had EMI; it was removed.)

### Responsive Shields and similar

Vanilla shields only block five ticks after being raised. Ancient Empires makes them block at once (`shield.instantBlock`), for players and NPCs. Mods such as Responsive Shields do the same thing; with them installed this setting has nothing left to do.

### Dynamic light mods

Any mod that lights held torches on living entities lights NPCs' night-watch torches and lanterns. No integration code is involved.

### Mods that structures were built with

Ancient Warfare's structures used blocks and mobs from many 1.12 mods. Those that exist in your game are placed as they are (the remap tables handle mods that changed id). Those that do not become stand-ins: `structures.missingBlock` for blocks, `structures.missingEntity` for spawner mobs. `/ancientempires structure check <template>` lists what a template needs. See [data-formats.md](data-formats.md#ae_remap-what-old-blocks-become).
