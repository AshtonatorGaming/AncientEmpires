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
| Routing Order + Courier: carries items between chests on a route | `RoutingOrderItem`, `NpcCourierGoal` | Done 2026-10-29: six of AW's twelve modes (all, only, except; each way) |
| Trade Order + Trader: player-owned trader runs a trade route (POTrade) | Nothing | **Missing**: owned trader |
| Faction traders (FactionTrade): buy and sell; the only way to raise standing | `NpcTrades`, `TradeScreen`, the Coin Purse | Done 2026-10-29: template traders keep AW's goods; coins by worth through a purse |
| Bard (instrument, sings: morale) | Role label only | **Missing**: bard |
| Town Hall alarm (`AlarmResponse`): civilians hide, soldiers muster | War Horn calls | Partial: no hall alarm |
| Recruiting Station (`npc/tile/TileRecruitingStation` in the 1.12 jar) | Settler recipe, Settlers' Post | To look up: the user does not remember it; check AW's code before deciding |
| Siege engineer (NPC crews a vehicle; `ai/vehicle`) | Nothing | Missing, needs vehicles |
| Spellcaster (Electroblob's Wizardry compat) | Nothing | **Port** (decided): spellcaster NPCs through a magic-mod bridge |
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
| Torque power (hand crank, Stirling, waterwheel, windmill, shafts, flywheels) running worksites without NPCs | Nothing | Skip (decided): worksites stay NPC-run |
| Warehouse (storage, interface, stock viewer, crafting station, stock linker): one networked store workers fill and players order from | Nothing | **Port** (decided) |
| Mailbox (routed item delivery) | Nothing | With couriers **?** |
| Chunk loaders | Nothing | **Port** (decided), behind a config setting |

## Core (AW's `core` module)

| AW | Ours | Status |
| --- | --- | --- |
| Research (research station, book, notes, quill) gating recipes | Nothing (upgrades without research) | **Reworked** (decided): scholars advancing buffs, not recipe locks; see Decisions |
| Engineering station (crafting bench that remembers recipes) | Nothing | Skip with recipe-gated research |
| Manual (in-game guide) | Nothing | **Missing**: onboarding needs one |
| Backpack item (AW's couriers carried their goods in one) | Every NPC has a 3x3 backpack | Covered: couriers use their own backpack, maybe enlarged by an item |
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
| Siege engines (catapult, ballista, trebuchet, ram, cannons), ammo, upgrades, armour; NPC crews | Placeholders from templates, drawn but inert | **Port** (decided), last; compat with other siege mods later |

## Decisions (user, 2026-10-29)

- **Torque: no.** It always felt odd; worksites stay NPC-run. Kept only if a use turns up (one idea: a mill as a town
  building that speeds nearby workers, no power network).
- **Warehouse: yes.** Very useful: one store workers fill and players order from. Part of parity.
- **Research: reworked, our own.** Not recipe locks. Scholars and holy men (Crusader Kings, Europa Universalis style)
  slowly push toward advancements that grant buffs and new options (better work speed, drill, morale, building
  bonuses, and the like) rather than unlocking crafting. A Worker-path scholar at a study (or a priest at a shrine)
  gains progress over time; the player picks the next advancement. Designed after parity; not a parity item.
- **Spellcasters: port, for both Electroblob's Wizardry and Iron's Spells 'n Books** (both exist on 1.20.1; the user
  corrected Claude). One bridge, an adapter per mod; Hundred Years War has Iron's compat worth reading first.
- **Backpacks:** AW's couriers stored goods in a backpack item; ours carry everything in the backpack every NPC has.
- **Chunk loaders: yes**, behind a setting. **Recruiting station:** to look up in AW's code.
- Research ideas, raw: docs/research-ideas.md.
- **Siege vehicles: port**, so structures' engines work, last in the parity order. Later, compat so NPC crews can use
  other mods' more refined siege equipment.

- **Money (user, 2026-10-29):** AW's stacks of four coins were painful. A Coin Purse holds one sum (Millénaire's
  purse, Mount and Blade's one money count); coin prices draw from purses then loose coins, with change, and coin
  payouts go into the purse. 8 copper a silver, 8 silver a gold; ancient coins 8 gold for now (`trade` config,
  balance untested). Barter (goods for goods, arrows for diamonds) is any trade without coins.

- **Orders (user, 2026-10-29):** AW's orders (Routing, Work, Upkeep, Combat, Trade) were how you told workers which
  site, where to eat, where to patrol, which route; all of them come back, on one base (`item/OrderItem`: click blocks
  to add, cycle, or strike off stops; the off-hand item is a stop's filter). AW kept them in a dedicated orders slot.
  For now a courier holds its Routing Order in its main hand (the order is its tool, as a hoe is a farmer's). Where the
  other orders live (a dedicated orders slot, or read from the backpack) is decided with the NPC screen redesign
  (character sheet, overview), which the user wants anyway. Next: Work, Upkeep, Combat (patrol), then the owned
  trader's Trade Order and its goods editor (shared with the creative faction-trader editor).

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
6. The warehouse.
7. Siege vehicles and NPC crews.
8. Spellcasters (a magic-mod bridge), chunk loaders (config-gated), the recruiting station once looked up.
