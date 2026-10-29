# Roadmap (reassessment, 2026-10-29)

A step back after a run of feature work, to stop building "willy nilly". DESIGN.md holds the tiers, VISION.md the
long-term space, CLAUDE.md the code as it is; this file is the current plan of attack. Revisit it when a phase ends.

## The promise (VISION.md)

Persistent, faction-aware NPCs that work, form armies, and react to the player, at a scale that feels like a strategy
sandbox, without the performance collapse of "every unit is a full agent".

## Where we are, by pillar

| Pillar | State | Notes |
| --- | --- | --- |
| NPC core and combat | Strong | Own attack system, shields, parries, dodges, duels, morale, levels, ranks, sidearms, cavalry, skirmishers, medics, night lights. Deepest part of the mod. |
| Workers and towns | Good | Every AW trade by hand, worksites with areas and upgrades, upkeep, Town Hall, priests, sleep, governor tax, immigration. No settlement heartbeat, craftsmen, or trading. |
| Factions and the world | Strong | 44 factions, standings, AW's 1,471 structures converted, world generation, garrisons, claiming, loot, gates, decor. Towns (`.awt`), sky/underground types not placed yet. |
| Army command | Working, rough | Order model, formations, stances, wheel, placement, War Table, cohort core. Play finds jank each time; the cohort polish is listed in army-command-design.md. |
| The world acting | Missing | Factions never move. Structures wait to be visited. Nothing comes for the player. |

## What is missing (the honest list)

1. **The loop.** There is no reason yet to raise an army beyond clearing structures: nothing threatens the player's
   town, and standing can only fall. The world director (VISION, "World Director and Warfare") is the biggest gap, and
   it is what makes everything built so far matter. A first slice: a hostile structure near the player's settlement
   sends a composed raid party at it now and then, with warning, using the existing chain, orders, and garrison code.
2. **Standing both ways.** Trading (AW's only way to raise standing) and the nemesis bonus are not done, so relations
   are a one-way ratchet. A trader role and a simple exchange close it.
3. **The survival path, end to end.** Each piece was tested on its own, mostly with admin tools. Nobody has gone from
   a fresh survival world to a settlement, workers, soldiers, a promoted sergeant, a squad, and a cleared fort. That
   path will show the real gaps: recipes, costs, onboarding (a guide or manual item), recruiting faction NPCs.
4. **Scale, measured.** Only idle NPCs were profiled (about 20-28 us each a tick). A 100 v 100 battle with formations,
   marches, hand-outs, and placement has never been measured. Before a director spawns armies, it must be.
5. **AW baseline left over.** Patrols (day and night), lance charges and mounted tactics, bard, trader, courier.
6. **Polish debt.** NPC screen overhaul (EMI overlap, character sheet), skin pools (217 one-skin rows, data), text
   fitting as found, the cohort polish.
7. **Housekeeping.** CLAUDE.md is very long (it is read every session; trim it to rules and pointers, move detail to
   docs/). Multiplayer (two players, a dedicated server) has never been tried; low priority while the user plays
   alone, but client-only code on the server would crash a real server.

## Plan of attack (revised the same day: parity first)

The user: reach parity with Ancient Warfare first, so there is a fixed finish line. The audit and its order are in
docs/aw-parity.md. Phases:

1. **Stabilize.** The cohort polish; the battle stress profile.
2. **AW parity**, in docs/aw-parity.md's order, after the user rules on the big ones (torque, warehouse, research,
   vehicles). This covers most of the missing list above: trading, patrols, the AW roles, the survival path, towns.
3. **Then** the director (raids), step 4 commanders, charge impact, fatigue, settlement heartbeat, and the rest.

Rule while working: a new system starts only when it serves the current phase. Ideas go here or in DESIGN.md, not
into the code.
