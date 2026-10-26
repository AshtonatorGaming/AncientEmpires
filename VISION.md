# VISION.md

## AncientEmpires — Long-Term Vision

**Status:** Aspirational design space. Not a requirements list.  
**Companion to:** `DESIGN.md` (practical tiers and implementation order) and `CLAUDE.md` (current codebase state).  
**Target:** Minecraft 1.20.1 Forge, with later ports possible.  
**Core promise:** Persistent, faction-aware NPCs that can form armies, work, and react to the player at a scale that feels like a strategy sandbox, without the performance collapse that usually accompanies “every unit is a full agent.”

This document records the broader ideas explored beyond the current implementation plan. Most of them are optional levers that can be added later *if* they can be expressed with the same small set of primitives. Nothing here is required for the mod to be complete or fun.

---

## Architectural Primitives (the only things that scale)

Almost every idea below reduces to one of these. If a proposal cannot be built from them, it does not belong in core.

- **One leader UUID + short subordinate list.** Grunts know their sergeant; sergeants know their grunts and their captain. No deep tree walks on tick.
- **Flags and integers that executors read.** Morale band, release/charge, hold, captive, cabinet role, veteran trait, ultimate active. Grunts and workers do not decide these; they mirror them.
- **Anchors that hand out work.** Quarry stake, farm station, structure spawner, or Town Hall gives a `BlockPos` or an objective. Workers and garrison units ask; they do not scan.
- **Slow director.** A world-level (or player-general-level) system that occasionally spawns a composed party, sets an objective type (assault, raid, defend, breach), or adjusts standing/era. Ticks rarely.
- **Data templates.** Faction, origin, religion, racial modifiers, and traits live in JSON/datapacks and are read at spawn, on GUI open, or when a modifier is needed. No per-faction AI branches.
- **Slow economy.** Tax, ledger materialization, and upkeep resolve on a long interval (midnight or every N minutes), not per container insertion.
- **Server-side identity.** Lineage, parents, prestige, full lore, and kill memory stay on the server. Battle sync is health, team/color, and morale band. Profile data is sent only when a GUI opens.
- **Config and datapack gates.** Every numeric threshold, composition table, and optional behavior can be turned off or replaced without code changes.

These rules are why the large idea space remains coherent. They are also the filter for anything new.

---

## Combat Layer (current + light extensions)

The mod already owns its attack system. Better Combat is a soft dependency for attributes and animations. Shields, bash, feints, backstep dodge, footwork, duel stance, skill-scaled perfect blocks, and guard points exist. High-rank/high-level NPCs already beat higher-HP but lower-skill opponents through these weights.

**Light additions that fit (no new combat engine):**

- Weapon block window for swords / one-handers (no shield), reusing the existing perfect-block presentation and camera stagger.
- Kick as a non-shield or light-weapon interrupt (short wind-up, shield disable or swing cancel, brief advantage window), skill-gated. Reuse bash-style lunge or parry stagger.
- Optional fast jab/interrupt profile for high skill (cancels early wind-up, low damage).
- Rare forward lunge only at the edge of reach, skill-gated. Not on every swing.
- Rank-difference bonus to parry/feint/bash chance in addition to level.

**Explicitly out of core:** Full directional attacks, held-block directions, Chivalry-style stamina movesets, or a from-scratch animation pipeline. Richer melee remains an integration point (Better Combat + packs) or a separate mod. The modpack can deliver the full feel; AncientEmpires does not own the animation authoring.

---

## Military Hierarchy and Scale

**Field forces (later, once numbers demand it):**  
Sergeants assign targets from the shared cache and broadcast one morale integer. Grunts attack what they are given and read the integer. Captains set local objectives (hold, push, screen). Generals (rare) set stance. Release from any tether is staggered. Succession rewrites local lists only. Faction flavor is weights (formation strictness, shock on leader death, early tether break), not new AI classes.

**Garrisons (lighter, sooner):**  
A structure anchor holds the list. Units hold or use a short patrol. They form a temporary line only when attacked, then return. No long-distance tether required.

**Player as general:**  
When nearby, the player counts as a high-rank aura source and optional follow target. War-horn rally is a short-lived tether centered on the player. Player origin and vassal/independent flags are just more data the director and standing system already read.

**Unit-type differences under the same chain:**  
Melee releases into combat. Archers keep a longer/wider formation and shoot until ordered otherwise. Skirmishers use the sergeant as a rally point and kite inside a radius. Cavalry uses wider spacing and a charge release. Same data shape; different release and movement rules.

This is the performance bet: most units do not pathfind or scan while formed or while holding an assigned target. It is unnecessary until structures and garrisons actually produce large groups.

---

## World Director and Warfare

A slow Strategic Director (world data or capability) watches structures, standing, recent fights, and player activity. When aggression crosses a threshold it spawns a pre-composed war party at a hostile fortress and gives it an objective. The party uses the normal hierarchy to march. Players can see it coming.

**Minimum that enables raids and sieges:**

- Raid objective: nearest worker, outer chest, or soft building (not the main keep).
- Assault objective: gate, wall section, or Town Hall.
- One specialist goal: “attack this block / place a single charge” so closed gates are not permanent walls.
- Optional envoy or banner that delays the spawn (configurable countdown) for multiplayer/offline fairness.

**Later specialist attachments (not required for the first director):**

- Sappers that place one charge on a pre-chosen point, then fight.
- Short-lived debris or snapshot rollback only for those charges (containers immune; player-placed blocks cancel the entry).
- Field engineers that channel on debris to restore faster.
- Stationary artillery as a rooted, crewed entity that lobs at a density point.
- Supply buffer: armies far from a Town Hall or depot slowly lose a morale/food modifier; assassins can be given “priority: supply” as an objective filter.

Occupation, room capture, volumetric tunneling, and full Millénaire-style self-expanding nations stay out of core. The director can later “upgrade a structure” or flip a territorial tag; it does not simulate abstract village economies or paint the map by itself.

---

## Workers and Economy

Same coordinator pattern as sergeants. Anchors (quarry stake, farm station, Town Hall) own the list of positions or the ledger. Workers ask for one `BlockPos`, act, and ask again. They do not scan. While on a known transit path their combat and item goals stay off. Drops go into inventory or a buffer, then into the ledger or chest on delivery. Materialization of the ledger into real items happens on a slow tick.

Social tiers, tax sliders, and “blood money” are modifiers on the existing Town Hall pulse. They are optional and configurable.

---

## Identity, Lineage, and Politics

**Lineage component:** generation, names, parent UUIDs, short trait list, prestige integer. Read on GUI open, death, promotion, or birth. Zero tick cost.

**Succession:** On general/captain (or player) death, look up the highest-prestige eligible child or family and rewrite leader links. Optional civil-war split if no heir: some captains flip hostile and receive “capture Town Hall.”

**Cabinet flags:** Marshall, Chancellor, Steward, Spymaster, Justiciar, Herald — each sets one global modifier while active and can remove the NPC from the field chain. Noble children can inherit a starting trait or minimum rank.

**Religion:** Another JSON template (blessing, taboo). Chaplain is a squad-slot flag that raises the morale floor. Holy-war boost is a temporary director flag.

**Nemesis:** On killing blow against the player or a legend, store the killer, give a title, promote, and anchor at a fortress. Director can later include them.

**Veteran trait:** Survive low health → small mechanical flag + optional client scar overlay.

**Origins and careers:** Player selects a faction template at start (or later). Writes standing and racial modifiers. Vassal flag = can receive objectives from that faction’s structures. Independent flag = own Town Hall. Switching is a flag flip that updates standing. Magic (Iron’s Spells or similar) is an event listener: big player cast → short morale pulse; settler can consume a scroll to gain a mage role flag. No spell code in core.

**Heraldry:** Team/player banner colors as a client tint/overlay on standards, shields, and capes.

All of the above are data, flags, or slow lookups. They become visible when succession fires, a GUI opens, or the director reads a multiplier. They do not drive the combat or worker tick.

---

## Phased Unlock (player-facing, not a hard code gate)

Physical milestones can hide *management* complexity without lobotomizing cheap systems:

- Early: Town Hall, settlers, worker anchors, basic combat and morale (already mostly present).
- Mid: First sergeant or Armory unlocks squad screen / simple assignment. Light director raids begin.
- Later: War Council or high prestige wakes fuller director compositions, cabinet flags, and territorial tags.
- Endgame levers (optional): Succession crises, specialist siege tools, era integer changing composition tables, nemesis appearances.

Do not disable morale, ranks, or basic assignment until late. Only gate the screens and the heavier director behaviors.

---

## Modpack and Extension Model

AncientEmpires core = persistent NPCs, hierarchy primitives, director, structures, worker anchors, standing, config/datapack templates.  
Better Combat (+ animation packs) = melee feel and combos.  
Optional companion mods or datapacks = extra faction templates, magic listeners, siege specialist behaviors, UI themes, deeper directional combat if ever desired.

Every soft dependency must be absent-safe. Features that need art or animation pipelines the core team does not own stay external.

---

## What This Document Is Not

- A promise that Millénaire-style living empires, full Crusader Kings simulation, volumetric block rollback, or 40 unique racial AIs will be built.
- A replacement for `DESIGN.md`’s practical order.
- Permission to add per-tick scans, deep tree walks, or client-synced dynasty data during battle.

It is a map of the design space that stays coherent *because* it refuses to leave the primitives. Record ideas here. Implement only what can be expressed as a flag, an objective, a template, an anchor, or a slow director decision. Everything else waits, becomes an addon, or stays in this file.

---

*Last synthesized from extended design discussions. Update when a major primitive changes or a large idea is explicitly promoted into `DESIGN.md`.*
