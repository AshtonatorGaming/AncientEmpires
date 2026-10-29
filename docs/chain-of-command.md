# Chain of command (plan)

Status: phases 1 to 5 built (2026-10-28). Garrisons were revised with the user: no formation while defending (they
keep their builder's posts), formation only after a sally. Still open: the banner as a stand-in leader while the real
commander is unspawned (needs virtual NPCs, with the director); phase 6 items. Formation layout revised with the user: soldiers form
the front, a leader stands just behind its own front rank, a captain behind its squads, a general behind its captains. Sources: VISION.md (Architectural Primitives, Military
Hierarchy and Scale, Garrison Rules, Charge Bonus, Companions) and DESIGN.md (Tier 1 command aura and succession,
Tier 2 doctrines and squad rules, Tier 4 living chain of command).

## What exists today

- Ranks (`NpcRank`): soldier, sergeant, captain, general. Players promote with insignia once the level allows;
  faction leaders are captains, elite leaders generals; factions name their ranks or use `pack` / `none`.
- Command aura (`NpcCommand`): every rank above soldier inspires allied soldiers nearby (damage, armour, morale).
  Succession: the best allied soldier nearby inherits a weakened aura and turns on the killer.
- Orders an owner gives one NPC at a time: follow, hold position (ranged only), home, inventory commands; the
  War Horn gives area calls (Rally, Call to Arms, Stand Down).
- Morale with shocks on commander death, inspired by the aura; one shared target scan (`NpcTargetCache`).

What is missing: nobody knows who their leader is. The aura is "whoever is near", orders go to one NPC or to
everyone in horn range, and soldiers never move or fight as a unit.

## The rules it keeps (VISION's primitives)

- **One leader link and a short list.** Every soldier stores its leader's UUID; a leader stores its direct
  subordinates (capped). No tree walks on tick: a soldier only ever looks one link up.
- **Leaders decide, soldiers mirror.** A leader holds the order, the formation, and (in a fight) the target list;
  its soldiers read them. Soldiers in a formed unit do not scan for targets or plan routes of their own.
- **Lobotomised grunts (the point of it all).** A soldier in a unit runs one cheap goal: keep its slot, strike the
  target it was handed when that target is in reach, block and step back by its own skill (what keeps it from
  being useless). Its own target scan, threat scoring, chasing, flanking, duel footwork, and route choices are off.
  Sergeants, captains, and generals make every decision: where to stand, whom to fight, when to release, when to
  fall back. The work moves up the chain; the many pay almost nothing.
- **Data, not classes.** Formation spacing, squad sizes, how fast units release into a fight, and the shock of
  losing a leader are config and faction data. No new AI per faction.
- **Absent-safe.** An unloaded or dead leader means "no orders": the soldier falls back to today's behaviour
  until the chain is mended.

## The shape

```
Player (owned NPCs)   or   Faction anchor (structure banner) for garrisons
  └─ General            (any number of captains)
       └─ Captain       (up to 4 sergeants: a company)
            └─ Sergeant (up to 16 soldiers: a squad)
                 └─ Soldiers
```

- A squad is a sergeant and its soldiers. Any rank can lead directly if there is no one between (a captain with
  three soldiers is fine); a soldier always answers to the nearest rank above it in the chain.
- Workers, settlers, and levies stay out of the military chain (VISION keeps a separate block-centric civilian
  chain: Governor, later Foreman and Overseer).
- Companions / personal guards (VISION) answer to the player directly, never to a sergeant; capped. Later phase.

## Orders

One small set, held by a leader and inherited by everyone under it unless they were given their own:

| Order | What the unit does |
|---|---|
| Follow | Forms up on its leader (or the player) and moves with it. |
| Hold | Holds a spot in formation, facing a direction; fights only what comes within reach. |
| Defend | Guards an area or a block (a gate, a Town Hall); goes after threats inside it, then returns. |
| Attack | Moves on a target or area, then releases into the fight (staggered), with a charge bonus later. |
| Fall back | Retreats to a rally point (the leader, a banner, home), fighting only when caught. |
| Free | No order: today's behaviour (wander near home, defend the owner, chase threats). |

Unit types read the same order differently (VISION): melee releases into combat; archers keep a wider, rear
formation and shoot until told otherwise (hold position becomes "archers hold"); skirmishers kite around their
sergeant as a rally point; cavalry spaces wider and releases as a charge.

## Formations

Soldiers form the front: a sergeant stands just behind its own front rank (its men between it and the enemy, still
seeing the line), a captain behind its squads, a general behind its captains; the higher the rank, the further back.
A leader works out a slot for each soldier (an offset from itself and its facing) when it moves a block or turns,
and at most every half second. A soldier walks only when it is out of its slot by more than a block or two, so a
standing unit costs almost nothing. First set: **line** (default; shields front, archers a row behind),
**column** (marching, paths and gates), **loose** (skirmishers, wide spacing). Wedge and square later.

## Fighting as a unit

- The sergeant scans once (the shared target cache) and hands targets out: spread across the enemies in front,
  or focused (a doctrine setting later). Its soldiers keep their target until it dies or the sergeant changes it.
  This is the performance bet: one scan per squad, not per soldier.
- Release from formation is staggered (a few ticks apart), and a released soldier fights with all of today's
  combat (shields, duels, morale). When the fight ends it returns to its slot.
- Morale: being in formation near your sergeant steadies; your sergeant breaking or dying shakes the squad (the
  shock exists). A routing squad falls back to its captain.
- Succession, extended: when a leader dies, the best soldier under it takes its place in the chain (its list,
  its order) at once; today's inherited aura stays.

## How the player commands

To decide (see questions): a held order item, a command screen, or both. Proposed:

- **Command screen** (a key, default unbound or `K`): your army as a tree (generals, captains, squads), each
  with its order and formation; drag to reassign, buttons to give orders, rename squads.
- **Signal Flag** (built): on a block Hold there facing your way, on the same spot again Defend it, sneaking Fall
  back to it; on a creature Attack it; in the air Follow / Free (a sub-unit rejoins its leader), sneaking the
  formation. Giving a unit under a captain its own order detaches it until it rejoins.
- **Point-and-order item** for the field (agreed: screen and item, horn kept): with a unit selected, use on a block to move or hold there, on a mob
  to attack it, on a structure to defend it; sneak to cycle the selected unit. Not a baton (you dislike those): a
  signal flag, or the War Horn's next mode.
- The War Horn stays as the area shortcut (Rally now means "everyone follow me in formation").

## Faction garrisons (structures)

- Each spawner's people form a squad when they come out; the spawner's leader row (a captain or general) leads,
  else the highest level among them is the acting sergeant.
- The structure's protection banner is the anchor (VISION Garrison Rules): it is the top of the chain while the
  real commander is unspawned, gives owner-faction units a defensive morale boost, and orders "Defend" over the
  structure. Units hold or patrol a short loop, form a line when attacked, then return.
- When the top commander falls or drops low, the rest get a sally-out (attack the invaders).

## Phases

1. **Links and squads (owned NPCs).** Leader UUID and subordinate list saved on the NPC; joining a squad only
   when assigned (until the screen, by the order item: select a leader, then click soldiers to add them);
   succession rewrites the links; Follow in line and column formations. `/ancientempires squad` to inspect.
2. **Orders down the chain.** Hold, Defend, Attack, Fall back, Free, held by leaders and inherited; the
   point-and-order item; the horn reworked onto the chain.
3. **Fighting as a unit.** Sergeant target hand-out, staggered release, return to slot, unit-type rules
   (archers, skirmishers, cavalry), squad morale.
4. **Command screen.** The tree, reassignment, orders, formations, names.
5. **Garrisons.** Spawner squads, the banner as anchor, defend and sally-out.
6. **Later (VISION/DESIGN):** charge bonus and anti-charge, doctrines (Aggressive, Defensive, Skirmish), wedge
   and square, companions, the director's war parties marching under the same chain.

Each phase is playable on its own and adds config under a new `chain` section, with `chain.enabled` off
returning today's behaviour.

## Decided

1. Orders: a command screen and a point-and-order item; the War Horn stays as the area shortcut.
2. Sizes: config throughout (`chain.squadSize`, `chain.squadsPerCaptain`, `chain.captainsPerGeneral`);
   squads default to 16 soldiers, since formations need mass. Captains 4 squads, generals any number.
3. Joining: only when the player assigns a soldier. Nobody joins on their own.
4. Faction garrisons come after owned armies feel right (phase 5 stays last).
5. Grunts are lobotomised in formation (above); leaders carry the AI.
