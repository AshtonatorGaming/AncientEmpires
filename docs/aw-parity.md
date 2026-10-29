# Ancient Warfare parity (audit, 2026-10-29)

The finish line before new systems (user, 2026-10-29: "focus on parity with AW first"). Read from the class list of
AW 2's 1.12 jar (`libs/ancientwarfare-1.12.2-2.10.2.jar`) and AW 1.6.1, against our code. "Ours" can differ in how
(hand work instead of worksites working themselves, the wheel instead of a baton) as long as the player can do the
same thing. Decisions marked **?** are the user's to make before building.

## NPCs (AW's `npc` module)

| AW | Ours | Status |
| --- | --- | --- |
| Combat, worker, priest, medic NPCs; faction soldiers, archers, leaders, elites, mounted, civilians, priests | Paths, equipment roles, roster | Done |
| Command baton (move, attack, hold) | Battle wheel, War Table, cohorts | Done (beyond AW) |
| Combat Order: patrol points for soldiers (`NpcAIPlayerOwnedPatrol`) | Nothing | **Missing**: patrols |
| Work Order: a list of worksites with times | Nearest own site, automatic | Partial: no assigning a worker to a chosen site or a rota |
| Upkeep Order: where to eat | Nearest Town Hall in range | Partial: no chosen upkeep spot |
| Routing Order + Courier: carries items between chests on a route | Nothing | **Missing**: courier |
| Trade Order + Trader: player-owned trader runs a trade route (POTrade) | Nothing | **Missing**: owned trader |
| Faction traders (FactionTrade): buy and sell; the only way to raise standing | Nothing | **Missing**: faction trading |
| Bard (instrument, sings: morale) | Role label only | **Missing**: bard |
| Town Hall alarm (`AlarmResponse`): civilians hide, soldiers muster | War Horn calls | Partial: no hall alarm |
| Recruiting Station (craft NPCs from a station) | Settler recipe, Settlers' Post | Different; **?** add a recruiting station |
| Siege engineer (NPC crews a vehicle; `ai/vehicle`) | Nothing | Missing, needs vehicles |
| Spellcaster (Electroblob's Wizardry compat) | Nothing | Skip unless a magic mod is in the pack **?** |
| AW weapons (club, ice spear, macuahuitl, pitchfork, scythe, sickle, shields, reach weapons) | Remapped to Spartan / vanilla | Done by remap (decided earlier) |
| Leveling, food bundle, coins, horses | Ours | Done (beyond AW) |

## Worksites and automation (AW's `automation` module)

| AW | Ours | Status |
| --- | --- | --- |
| Crop farm, tree farm, animal farm, fish farm, quarry | By hand, with areas and upgrades | Done |
| Fruit farm (cocoa, melons and pumpkins picked, berries) | Nothing | **Missing** (small) |
| Auto crafting station (a worker crafts a set recipe) | Cook only | **Missing**: craftsman |
| Ore processor | Nothing | Missing (small) |
| Worksite upgrades | Ours | Done |
| Torque power (hand crank, Stirling, waterwheel, windmill, shafts, flywheels) running worksites without NPCs | Nothing | **?** Port, or leave power to Create and keep worksites NPC-only |
| Warehouse (storage, interface, stock viewer, crafting station, stock linker): one networked store workers fill and players order from | Nothing | **?** Port, or lean on Town Hall stores and storage mods |
| Mailbox (routed item delivery) | Nothing | With couriers **?** |
| Chunk loaders | Nothing | **?** Port, or leave to FTB Chunks |

## Core (AW's `core` module)

| AW | Ours | Status |
| --- | --- | --- |
| Research (research station, book, notes, quill) gating recipes | Nothing (upgrades without research) | **?** Port, or keep everything open |
| Engineering station (crafting bench that remembers recipes) | Nothing | Goes with research **?** |
| Manual (in-game guide) | Nothing | **Missing**: onboarding needs one |
| Backpack item | Nothing | Missing (small; storage mods cover it) **?** |
| Hammer (lock and turn worksites), info tool | Sneak-click actions | Partial |

## Structures (AW's `structure` module)

| AW | Ours | Status |
| --- | --- | --- |
| Templates, world generation, scanner, gates, advanced spawner, loot, coins, flags, claiming, decor | Ours (S1 to S5) | Done |
| Survival structure builder (Structure Builder block plus Drafting Station: build a template from supplied resources) | Admin `/structure place` only | **Missing**: the survival way to build a template |
| Town builder and town generation (`.awt`) | Nothing | **Missing**: towns |
| Sky, underground, harbor, lava site types | Not placed | Missing |
| Loot Chest Placer, Spawner Placer | Capture wand only | Missing (small; builder tools) |
| Construction tools (fill, lakes) | Nothing | Skip (WorldEdit) **?** |

## Vehicles (AW's `vehicle` module)

| AW | Ours | Status |
| --- | --- | --- |
| Siege engines (catapult, ballista, trebuchet, ram, cannons), ammo, upgrades, armour; NPC crews | Placeholders from templates, drawn but inert | **?** Port (large), or a smaller set later |

## DESIGN.md items jumped past

Tier 1 is done. Tier 2 was skipped over for the army work; still open there: reach-weapon swaps (pikeman), banner
planted as a rally point, taunt, quartermaster, field repair, scout, medic triage, patrol routes, adaptive styles,
loadouts, emergent squad rules. Tier 3 and beyond are untouched, rightly.

## Proposed parity order

Small and clear first, big decisions after the user rules on them:

1. Patrols (Combat Order), the Town Hall alarm, the bard.
2. Faction trading and the owned trader (standing both ways), then courier and routing (with or without mailboxes).
3. Fruit farm, craftsman (auto crafting), ore processor.
4. Survival structure builder and drafting station; the manual.
5. Towns and the missing site types.
6. Whatever the user picks of torque, warehouse, research, vehicles, chunk loaders, backpack.
