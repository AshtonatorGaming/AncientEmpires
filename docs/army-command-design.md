# Army command, second pass (design)

Status: proposal, 2026-10-29, for the user to review before code. Builds on docs/chain-of-command.md (phases 1 to 5
built) and the research in docs/command-ux-research.md. Our own implementation throughout.

The user's brief: formations that maneuver, grunts marching in front of their leader, picking facing, a nicer
radial menu with submenus (formations, behaviours), commanders that try to win when not micromanaged, and a
good-looking management screen with the army as a hierarchy. The Signal Flag and the K screen may be rewritten.

## Three tools, three jobs

| Tool | Job | When |
|---|---|---|
| **War Table** (K) | Organise: who is in which unit, unit names, numbers, standing orders and stance | In camp, before a fight |
| **Battle wheel** (hold V) | Fight: orders to the picked units, aimed into the world | During a fight, seconds count |
| **Placement** (from the wheel) | Where and how: click a spot, or drag the front line; ghosts show every soldier's spot | During a fight |

The Signal Flag is retired. Everything it did is in the War Table (assigning) or the wheel (orders). The one
in-world shortcut it gave, "this soldier, into that unit", becomes: look at your soldier and press K, and the War
Table opens with it picked up, ready to drop onto a unit. Old flags in inventories stay as a plain item (or are
removed; breaking changes are fine).

## Look and feel (the brief: it should feel like commanding a real army)

Three directions were mocked up for the user (2026-10-29):

- **A. Banner hall.** Each unit is a heraldic banner in the hierarchy: the general's large banner, captains' banners,
  and squads' pennants. Losses tatter and darken the cloth, and a unit fighting is ringed red. References: Total War
  unit cards, Crusader Kings heraldry, Bannerlord clan banners.
- **B. Campaign table.** A parchment map of the land around the player (vanilla map colours), with carved blocks for
  units where they stand, inked arrows for their orders, and pins and string for the chain. Drag a block to order it
  or to put it under another. References: Total War's campaign map, Manor Lords, the film war-room table. It is the
  most immersive and the most work (map rendering).
- **C. Company ledger.** A leather muster roll with a tab per company. Each man has a face, name, trade, level,
  morale, kills, and a line about how he fell. References: Battle Brothers, Darkest Dungeon, XCOM.

Recommended: A as the War Table's chart and C as its detail panel and roster now, with B later as a Map tab on the
same data. The user picks.

## 1. The War Table (K)

A full-screen parchment map table, in the style of the NPC inventory and the worksite screens.

```
┌───────────────────────────────────────────────────────────────────────────────┐
│ WAR TABLE                     [ Nearby | All loaded ]   [search]    Army: 41  │
├───────────────────────────────────────────────────┬───────────────────────────┤
│                    ┌──────────┐                   │  [portrait]  Captain      │
│                    │ (you)    │                   │   Aldric, Lv 14           │
│                    └────┬─────┘                   │   "The Iron Company"  [2] │
│          ┌──────────────┴──────────────┐          │   Health ████████░░       │
│    ┌─────┴──────┐                ┌─────┴──────┐   │   Morale ██████░░░░ steady│
│    │◆ Captain   │                │◆ Captain   │   │   Order  Hold (spot)      │
│    │ Aldric [2] │                │ Bryn   [5] │   │   Form   Line   Stance Def│
│    │ 3 sq · 39  │                │ 1 sq · 9   │   │ ───────────────────────── │
│    └─────┬──────┘                └─────┬──────┘   │  Roster (own soldiers)    │
│   ┌──────┼───────┐                     │          │  ☺ Hal   spear  Lv 6 ▇▇▇  │
│ ┌─┴──┐ ┌─┴──┐ ┌──┴─┐                ┌──┴─┐        │  ☺ Wynn  bow    Lv 4 ▇▇░  │
│ │▲Sgt│ │▲Sgt│ │▲Sgt│                │▲Sgt│        │  ...                      │
│ │12/16│ │16/16│ │8/16│               │9/16│        │ [Order▾][Form▾][Stance▾]  │
│ └────┘ └────┘ └────┘                └────┘        │ [Rename] [Number] [Locate]│
├───────────────────────────────────────────────────┴───────────────────────────┤
│ RESERVES  ☺ ☺ ☺ ☺ ☺ ☺   (soldiers in no unit: drag onto a card to enlist)      │
└───────────────────────────────────────────────────────────────────────────────┘
```

- **The org chart** (left, most of the screen): you at the top, then generals, captains, and sergeants as
  **cards** joined by ink lines. A card is a unit: the leader's face (drawn from its skin), rank insignia, name,
  unit name and number badge, soldiers over the cap (12/16), a health strip and a morale strip (unit averages), and
  small icons for its order, formation, and stance. Soldiers are not cards (too many). They are in the roster.
  Pan by dragging the background, zoom with the scroll wheel, collapse a branch by clicking its line. A card glows
  gold while any of its soldiers is fighting, and red while broken or routing.
- **The detail panel** (right): the picked card's leader as a 3D portrait (as in the inventory), its numbers,
  then the roster of its own soldiers (face, name, weapon role icon, level, health). Under it, dropdowns for the
  standing order, formation, and stance, and Rename, Number, Locate (the unit glows in the world for 10 seconds,
  and the screen closes).
- **Reserves** (the bottom strip): your soldiers in no unit, as faces. Any soldier of rank sergeant or higher
  also gets a card (an empty unit) so it can take people.
- **Drag and drop** does all the organising:
  - a face from reserves or a roster onto a card enlists it (or moves it between units);
  - a face onto the reserves strip takes it out of its unit;
  - a card onto a higher card attaches the unit under that leader; onto the empty table it detaches it;
  - an invalid drop (cap full, rank too low, a loop) shows the card red with the reason under the cursor, and
    nothing is sent.
- **Hover = glow in the world**: hovering a card or a face outlines those soldiers (client-side glow), so you can
  find "which one is Hal" by looking past the screen. This also helps the wheel (below).
- **Nearby / All loaded**: nearby is within 64 blocks, all loaded is today's list (at most 256). Units with nobody
  loaded show as greyed cards from the ledger ("away").

The data needed is mostly in `ArmyPacket.Entry` already. It adds the skin, average morale and its state, the weapon
role, stance, and the cap. One edit packet replaces the Signal Flag's work:

- `ENLIST(soldier, unit)`;
- `DISMISS(soldier)`;
- `ATTACH(unit, under)`;
- `DETACH(unit)`;
- the existing rename, number, order, and formation actions.

Each is checked on the server in `NpcArmy.act`, as now.

## 2. The battle wheel (hold V)

The wheel is drawn as a real ring: annular slices built from triangles, each with an icon, and the hovered slice's
name and a one-line hint in the middle. The view stays frozen as aimed. It is a tree:

```
Root:   Move ▸ · Charge · Follow me · Fall back · Facing ▸ · Formation ▸ · Stance ▸ · Maneuver ▸
Facing:     Face my way · About turn · Left · Right · Back
Formation:  Line · Column · Shield wall · Wedge · Square · Skirmish · Back      (current one bordered)
Stance:     Aggressive · Hold the line · Passive · Fire at will · Hold fire · Volley · Shields up · Back
Maneuver:   Advance 5 · Fall back 5 · Close ranks · Spread out · Release (free fight) · Back
```

- **Units bar** across the top of the screen while the wheel is open: one small banner per numbered unit (number,
  name, size, order icon, and a morale pip). 1-9 toggle units, 0 picks all, and units can be clicked as well. Picked
  units **glow in the world** while the wheel is open, so you see who will move.
- **Letting go of V** over an order gives it, over a submenu keeps the wheel open on that submenu, and in the middle
  gives nothing. Right-click or Back goes up a level. A click gives the order or opens the submenu.
- **Dynamic labels**: Charge reads "Charge Brigand Chief" when a creature is aimed at, else "Charge (engage
  freely)". Current formation and stance slices are bordered gold.
- The action bar confirms ("2, 5: Shield wall").

## 3. Placement: click a spot or drag the line

Choosing **Move** closes the wheel into placement mode. The crosshair is live again, so you look around.

- A ghost marker sits under the crosshair for every soldier of the picked units: their exact slots, the leaders'
  as banners, in a faint tint per unit. They are snapped to the ground within 10 blocks up or down, and rebuilt only
  when the aimed block changes.
- **Click**: the units hold at that spot, facing away from you, in their formation (today's Hold, with a preview).
- **Click and drag** (hold the button, turn your view, let go): the drag is the **front rank**. Its length sets the
  frontage (soldiers per row = length over spacing, at least 2), and the facing is perpendicular to it, away from
  you. With several units picked, the line is shared out along its length by each unit's size. This is Bannerlord's
  and HYW's best idea, in first person.
- **Scroll** while placing turns the facing in 15-degree steps (for a click). **Right-click or V cancels**.
- The client lays the ghosts out with the same pure `NpcFormation` code the server uses, so the preview is the
  result. The order packet carries the units, the two ends (or one spot), the facing, and the frontage.

## 4. How units move: form, then march

A move further than `chain.formUpDistance` (12) takes two stages (HYW's idea, our version):

1. **Form up**: the leader stops, and the slots are laid out where the unit stands, facing the destination. The
   grunts go to them. The unit is ready when `formUpReady` (80%) of it is in its slots, or after `formUpTicks` (100).
2. **March**: the leader walks the path at the **slowest member's pace** (riders count their horse). The slots are
   re-laid along the path's heading, so the front rank walks **ahead of the leader**, as the layout already puts it.
   If cohesion drops (more than a quarter of the unit `marchStraggle` (4) blocks off its slots), the leader slows to
   a halt until they catch up.
3. **Deploy**: at the spot the unit turns into its ordered formation and facing.

Only the leader paths the whole way. Grunts steer to their slots, as now, so a marching company costs a few paths.
A path budget per owner (`chain.pathsPerTick`, 4) queues leaders' path requests when many units get orders at once.

A captain marching its squads: its sergeants are slots in its layout, so the company marches as one block, its
squads abreast in a line, one behind another in a column.

## 5. Formations, maneuvers, stances

**Formations** (pure layout in `NpcFormation`, tested without the game):

| Formation | Shape | What it changes |
|---|---|---|
| Line | wide rows (today) | default |
| Column | 2 wide (today) | marching, gates; deploys to line on arrival if ordered |
| Shield wall | line at 0.75 spacing | shield bearers in front with shields up while held, advance at shield pace, +morale while formed |
| Wedge | a point of the best melee, widening back | charges and breaking a line; riders use it for the charge |
| Square | hollow square facing out, archers inside | holds against riders (anti-charge, with pikes later) |
| Skirmish | loose (today's double spacing) | throwers and archers kite around the leader as a rally point |

**Maneuvers** are small edits to the order the unit already holds, so they work under any order:

- Advance and Fall back 5 move the held spot along the facing.
- Face my way, About turn, Left, and Right turn the layout in place.
- Close ranks and Spread out set a spacing multiplier (0.75, 1, or 2).
- Release lets the unit fight freely without dropping its spot, so it reforms there when the fight ends.

**Stances** (the behaviour submenu) are two settings held beside the order and inherited like it:

- **Engagement:**
  - Aggressive: release into any enemy within `engageRange`, chase routers.
  - Hold the line (default): fight only inside the leash, back to the slots after.
  - Passive: only fight back when struck.

  These map onto the existing `allowsTarget` leash and `plan()`.
- **Fire:**
  - Fire at will: today's behaviour.
  - Hold fire: archers draw nothing.
  - Volley: the leader times the draw, and the unit looses together every N seconds. It is cheap, since one timer
    per unit is checked by its archers.

  Shields up is a flag that keeps shields raised while formed.

## 6. Commanders decide (decision 6)

**The rule:** an AI commander is a player without a wheel. It gives the same orders, formations, and stances to its
sub-units that the player can, through the same `setOrder` calls, so everything above is also its vocabulary and is
tested once.

- **Who thinks:** a captain or general with sub-units, when the order it holds leaves it room: Free, Defend, Attack,
  or the new top-level order **"As you see fit"**. A direct order from the player to a unit pins that unit until it
  is given "As you see fit" again. Sergeants keep today's target hand-out. Faction commanders (garrison captains, and
  the director's war parties later) run the same brain.
- **When:** every `command.thinkTicks` (100) while enemies are within `command.battleRange` (32) of the army, one
  scan of the shared target cache per commander.
- **The read:** for each side, the following.
  - Strength: health times a level factor times a morale factor.
  - Counts of melee, shields, archers, and riders.
  - Average morale and how many are broken.
  - Whether the enemy is formed or scattered.
  - Height (our average y against theirs).
  - Our cohesion: how many are off their slots.
- **The plans**, picked by score with hysteresis (a plan is kept at least `minPlanTicks` (200) unless the read swings
  hard):

| Plan | When it scores high | Orders it gives |
|---|---|---|
| Charge | strength at least 1.6 times theirs, or they are breaking | melee squads Attack in wedge, aggressive; archers fire at will |
| Advance in line | an edge in morale or melee, they are formed | line, hold the line, march to 6 blocks off their front, then release |
| Hold and shoot | more archers than they have, or the high ground | melee in shield wall in front, archers volley, riders hold back |
| Flank | we have riders and they are engaged to the front | riders swing wide to their side or rear and charge |
| Refuse and regroup | cohesion low, or a flank is caving | fall back 10, close ranks, then re-read |
| Retreat | average health under 25% or morale under 40 | Fall back to the rally point (owner, home, structure), passive |

- **Telling the owner**: one line of chat per plan change, in the commander's voice ("Aldric: their archers
  outnumber ours. Closing the distance!"). It is throttled and never repeats the same plan. A client option turns it
  off.
- The numbers (ratios, health and morale floors, think rate) are config, and every faction uses the same brain.
  Later, faction data can lean it (a `doctrine`: Norska charge sooner, Empire hold and shoot).

## 7. Under the hood

- `NpcOrder` gains a stance pair and a spacing multiplier, saved with the order. "As you see fit" is a flag on Free
  (`AeOrderAi`).
- `NpcFormation` gains the new shapes as pure slot functions, unit-tested like today's.
- `NpcChain` gains the march stages (FORMING, MARCHING, DEPLOYED), and the slots are laid along the march heading.
- A new `NpcBattleBrain` (one per thinking commander, created when the battle starts and dropped after) holds the
  read and the plan.
- Client: `WarTableScreen` (replaces `CommandScreen`), `BattleWheelScreen` (replaces `CommandWheelScreen`),
  `PlacementMode` (client tick and render hooks for the ghosts), and `ClientGlow` (an entity set that
  `NpcEntity.isCurrentlyGlowing` checks on the client).
- Packets: `ArmyPacket` carries more per entry, `ArmyEditPacket` (enlist, dismiss, attach, detach), and
  `UnitOrderPacket` with spot or line, facing, frontage, formation, and stance. `PROTOCOL` is bumped.
- Config: new keys under `chain` (formUp*, march*, pathsPerTick, the formation spacings) and `command` (think, the
  plan thresholds).

## Build order

1. **Order model**: stances, maneuvers, facing, new formations, form then march, and the path budget. This is
   server-side, covered by game tests (a unit forms then marches with its front rank ahead; a shield wall holds; a
   stance leashes).
2. **Battle wheel and placement**: the ring tree, the units bar, the glow, and click or drag the line with ghosts.
3. **War Table**: the org chart, detail panel, reserves, and drag and drop; then retire the Signal Flag.
4. **Commanders decide**: the battle brain, its plans, and the chat; faction garrisons' captains use it too.

Each step is playable on its own. Step 1 makes the existing wheel better straight away.

## Answers (user, 2026-10-29)

- Look: A (banner hall) for the chart and C (ledger) for the roster, with B as a Map tab later.
- Formations as proposed, and the proposed UI in general, approved.
- Signal Flag: not retired outright, but its use is unclear. It may be repurposed later. The War Table takes over
  assigning regardless.
- The War Horn (or something like it) suits "planned operations": calling your men in from afar while you scout
  alone, say, or triggering an operation laid out beforehand. This is for later, after the War Table.
- Commander chat: configurable. Commanders' personalities (who talks, and how) come later.

## Progress

- Step 1, the order model: built 2026-10-29 (see CLAUDE.md, chain of command). The current V wheel has submenus for
  Formation, Stance, and Maneuver as a stand-in until step 2's ring.
- Step 2, the battle wheel and placement: built 2026-10-29. The ring tree, units bar, gold glow on picked units, and
  placement with ghosts (click a spot, drag the front line, scroll to turn). Facing is its own Maneuver entries rather
  than a Facing submenu. Not yet: hovering a unit in the bar to preview just that unit's glow.
