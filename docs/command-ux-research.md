# Command UX research: Hundred Years War and Recruits

Read from the decompiled jars in the test instance (Hundred Years War 0.7.1r, Recruits 1.15.2), for ideas only.
Nothing here is to be copied; our own versions go through `NpcChain`, `NpcArmy`, and the V wheel.

## Hundred Years War (HYW)

The fullest system of the two. It is an RTS mode bolted onto Minecraft.

- **Free camera** (`client/freecam`). A key detaches the camera into a top-down RTS view: drag a box to select,
  click to select one, double-select "same type" or "everyone near what I'm looking at". Selection is a client-side
  list of entities. Groups 0-9 ("squads") are created and recalled like RTS control groups.
- **Command wheel** (`CommandWheelTree`, `CommandWheelUI`, `TrapezoidButton`). A ring (inner radius 40, outer 120)
  of annular segments, up to 10 per page, with pages. It is a tree. The root holds Move, Attack, Cancel, Hold,
  Follow, Attack strategy (submenu), Formation (submenu), Formation facing, Select squad / Create squad (submenus of
  0-9), Select same type, Select near look, Dismiss, Dismount, plus menu links. Every submenu has Back as a slice.
  - Nodes can be **dynamic**: their label changes with state, such as Attack vs. Attack-move, or Select entity vs.
    Select nearby.
  - Selected options get a **coloured border**, for example the current formation spacing.
  - **Hovering a squad slice previews it**: the units it would select light up before you commit.
  - **Letting go over a submenu keeps the wheel open** to browse. Letting go over an order gives it.
  - Each order prints a short coloured action-bar line ("Move", "Attack target").
- **Attack strategies**: cease fire, default, free fight, free roam, indiscriminate. Combat form: auto / ranged /
  melee (for units that carry both).
- **Drag to form** (`FormationDragHandler`, `DraggedFormationLayout`). In the free camera, hold the button on
  the ground and drag. The drag is the front line: its length is the frontage and the facing is perpendicular to it.
  - It needs a 0.1 s hold and 2 blocks of drag before it counts. A plain click stays a plain move.
  - While dragging, each soldier's spot is previewed on the ground, snapped to a safe surface within 10 blocks up or
    down. The preview is only rebuilt when the end block or the selection changes.
  - Layout: units are sorted by category, then by size. Melee rows come first and ranged rows behind, each kind in its
    own rows. Groups are separated by a gap scaled to body width. Spacing multiplier 1, 2, or 4 comes from the wheel.
  - "Combined formations" keep several groups' relative positions and just move and turn the whole block.
- **Two-stage marching** (`FormationManager.formMove`, `FormMoveGoal`).
  1. Stage one: everyone forms up near where they are, facing the destination, moving at 1.2x speed. Stuck units
     time out and are marked ready.
  2. Stage two: when all are ready (or the time runs out), the formation sets off together at its slowest
     member's speed (horses' speed counted for riders). Final targets are handed out 2 per tick.
- **Facing as its own order**: "Formation facing" turns the formation to the camera's yaw in place.
- **Path budget** (`PathingTaskManager`): path requests are queued per owner and only so many run per tick.
- **Formation shapes**: line (about 10 wide per row), tight/loose blocks (about 3:1), circle and hollow circle, wedge.
- **Command staff item**: a held item that remembers its team and draws markers for the current move, attack-move,
  or attack target (`CommandStaffTargetRenderer`, `FormationPreviewRenderer`).

## Recruits

Simpler, and closer to vanilla play (no free camera).

- **Command screen** (one key, opens around the crosshair). Group toggle buttons along the bottom, with each group
  on or off. Three categories by icon: Movement, Combat, Other. Each category's buttons sit in a cross or ring
  around the centre, greyed out when they cannot apply (Move needs a block under the crosshair).
- **Movement**: Move (to the block looked at), Forward and Backward (the group's centre moves a few blocks along the
  player's look direction), Follow, Wander, Hold position, Back to position, Hold my position, Face (turn to the
  player's yaw), Tight. Formation picker: none, line up, square, triangle, hollow square, hollow circle, V, circle,
  plus "hold formation".
- **Combat**: Aggressive, Neutral, Passive, Raid; Attack; Protect; Clear targets; Shields up/down; Fire at will,
  Hold fire, Strategic fire; Mount, Dismount, Back to mount.
- **Other**: Upkeep, Rest, Team.
- **Follow in formation**: Follow with a formation chosen re-lays the formation around the player as they move.
- **Sticky slots**: each recruit keeps its index (`formationPos`) and takes it back when it is still free, so
  re-forming does not reshuffle the line.
- Facing is always the player's yaw. Rows are 1.75 x spacing apart. A line's row is the whole group up to 20,
  else half.

## What to take, in our terms

1. **Drag the line** without a free camera. Hold V, click and hold on the ground at one end, and let go at the other.
   The line is the front rank and faces away from you. While dragging, ghost markers show where each soldier will
   stand, cached until the end block changes. A plain click keeps today's click-to-place. This is HYW's best
   feature, and it fits first person.
2. **A wheel tree** with Back slices, state borders, and dynamic labels:
   - Root: Move / Hold, Charge, Follow, Fall back, Facing, Formation (submenu), Fire (submenu), Behaviour (submenu),
     Units (submenu).
   - Hovering the Units submenu previews: the picked units glow.
3. **Form, then march** (HYW's two stages). On a move order a unit first forms up facing the destination, then
   advances together at its slowest member's pace. That gives the grunts-in-front-of-the-leader march for free.
   While marching, the leader paths and the grunts steer to their slots; only the leader's path is costly.
4. **Maneuvers** from Recruits: Forward and Back a few paces, Face my way, Tight and Loose. These are small changes to
   the order the unit already holds.
5. **Fire and stance** orders: hold fire, fire at will, shields up, and a stance (aggressive, defensive, passive)
   mapped onto our `allowsTarget` leashes.
6. **Selection glow**: units picked with 1-9 glow while the wheel is open, as our `BlockOutlines` do for spawners.
7. **Path budget**: cap path requests per owner per tick, as HYW does, when many units get orders at once.

Not worth taking: HYW's free camera (a whole RTS client, and a large maintenance surface), and its combined-formation
bookkeeping. Ours are the chain of command: a captain's squads already move as one.

## The odd corners (units and systems beyond commanding)

### Hundred Years War

- **One class per unit**, each with its own trick, and a tag set used for unit descriptions (light, heavy, ranged,
  cavalry, siege; "counters light / heavy / ranged / cavalry / siege").
  - Bandit Beastmaster: summons a pack of 8 war hounds one at a time, every 15 s; they vanish after a minute.
  - Bandit Demolitionist: walks up, primes for 3 s with smoke, explodes. Can be aimed at a spot as a position attack.
  - Bandit Executioner, Desert Raider Sand Dancer: spinning whirlwind attacks. The dancer leaps in from 9 blocks
    spinning, and gets a spin back every 3 hits. Both do extra damage to light or heavy units.
  - Desert Raider Assassin: goes invisible after standing still 5 s, is revealed when it strikes, dodges, and has
    off-hand follow-up stabs.
  - Bandit Knife Thrower: throws fans of knives with predicted aim, both hands.
  - Dual-form archers (skeletons): switch between bow and melee by range, and switch to the bow when they cannot path
    to melee (a capped reachability search every second).
  - Gunpowder: handgonne and matchlock men, with TACZ guns as an optional dependency.
  - Mounted: lancers and light lancers, mounted archers, camel cavalry, heavy and "golden" cavalry.
  - Mages: iron mage, skeleton mages, wood elf mage.
- **Weapons:**
  - Pike: a sword with extra reach and charge damage by tier (6 to 12).
  - Lance: charge damage 15 to 30, usable only while riding one of their mounts.
  - Long-handled blade: an active thrust that launches the player forward (5 s cooldown).
  - Charging riders hold their horse still while they have a target, so the horse stops for the hit.
- **Siege:** trebuchet, mangonel, springald, bombard, great bombard, culverin, cannon, ribauldequin, battering ram,
  siege tower, and the Nest of Bees.
  - Nest of Bees: 36 rockets fired 5 ticks apart, with predicted flight time, at sky-exposed targets.
  - Siege tower: carries 15 soldiers and unloads them within 6 blocks and 15 up.
  - Engines are crewed or engineer-operated, and turn only within a rotation limit.
- **Recruitment flag:** an entity, the player's banner on a pole, with an order behind it. When it is destroyed the
  recruitment order fails. Recruiting has categories, unlock conditions, and material costs. A "memento" item comes
  from killing named enemies.
- **Spoiler protection:** a boss's gear, scrolls, and memento show as "unknown" placeholders until you have killed
  that boss. Also: creative tab spoilers.
- **Supply:** every soldier eats supply from a Supply Point within 100 blocks (holding up to 10,000; a player
  carries up to 2,000). Food converts to supply. Out of supply, after a delay, soldiers get Weakness III and
  Slowness, then take damage down to 1 heart. Daily consumption and days left are shown.
- **Combat alerts:** a soldier with a target shares the spot with up to 2 allies within 30 blocks after 3 s, who may
  pass it on at half chance. Hurt soldiers retaliate. Soldiers go back to their posts 3 s after the target is gone.
- **Temporary hostility:** attacking someone makes you temporarily hostile to their side.
- **Structures generated near players at runtime:** the chunks are hidden from the player (chunk visibility,
  "materialisation") while the build is placed, then revealed. Building templates have attributes and tags. A
  terrain footprint smoother. A road network between settlements.
- **Workers:** workstation blocks for farming, lumber, mining, fishing, breeding, crafting, and transport. A
  Transport Order item binds porters to a route of workstations.
- **Puppets:** bow, crossbow, and melee training dummies.
- **No chain of command:** groups and squads 0-9 are selections, not ranks. The Desert Raider Commander is only a
  boss unit.

### Recruits

- **Patrol leaders (Captain, Commander) are a one-level AI general**, the closest thing to a chain:
  - A leader owns an "army" (its recruits), walks a saved route of waypoints (up to 512, each with a wait), and
    resupplies at the first waypoint.
  - Spotting an enemy army, it sizes both sides up every 20 s: size, average morale and health, and counts of ranged,
    cavalry, and shields. Then it picks a plan:
    - Overwhelming (twice the size, or healthy): charge.
    - Morale edge: advance.
    - Enemy ranged beats our cavalry and shields: fall back.
    - Otherwise attack in lines: infantry forward, ranged behind, cavalry free.
    - At a disadvantage: retreat along the route.
  - Each decision goes to the owner in chat ("We have a morale advantage! Advancing!").
  - If half its army is scattered, it regroups first. Shields go up when the enemy has the archers, and down up close.
  - It retreats when the army's average health is under 25% or average morale under 40.
- **Captains sail ships** (Small Ships mod): sail points, cannon refill and repair, strategic fire from the ship.
- **Messenger:** carries a written message, or a treaty proposal with a duration, to another player, teleporting
  near them; they answer on a screen and it brings the answer back.
- **Scout:** goes out, counts non-friendly units, and reports their direction and numbers to the owner.
- **Assassin Leader:** hire assassins against a player, priced per assassin.
- **Villager Noble:** hires recruits by trades. Levels up as a trader, and restocks while sleeping or after a while.
- **Nomads:** mounted archers that remember their horse.
- **Strategic fire:** archers shoot at a chosen block (area fire), not at a target.
- **Small goals:** drink potions from the inventory when hurt (quaff), dodge, flee lit TNT and fire, eat, rest, pick
  up wanted items, protect a chosen entity, upkeep at a chest.
- **Chunk-loading units:** leaders and messengers keep the chunks around them loaded while on a mission.
- **Factions and diplomacy:** player factions, claims, diplomacy states, treaties, embargoes, and siege events on
  claims. Pillager raids start when a recruit burns an ominous banner.
- **Async pathfinding:** paths are computed off the main thread (an async navigation and path processor).

### Worth a thought for us

- **Tier 2 commanders, from Recruits:** let a captain or general without orders read the field the same way:
  compare sides (size, morale, health, ranged vs. shields and cavalry), pick charge, advance, hold, or fall back, and
  report to the owner in a line of chat. We have the morale numbers and the chain to carry it out. AI generals would
  start here.
- **Area fire:** Strategic fire / "fire at this block" as a wheel order for archers.
- **Units:** demolitionist (sappers for gates), beastmaster, the assassin's stand-still stealth, and the
  thrower's fan. These are AW-flavoured fits as roster rows with an equipment or role check, not new classes.
- **Supply in the field:** dropped (user). Upkeep stays as it is.
- **Unit types:** not taken one to one (user). Common ideas such as sappers are fine; their niche units stay theirs.
- **Scouts and messengers:** fit DESIGN's couriers.
- **Spoiler placeholders** for faction leaders' gear and scrolls.
- **Alert sharing with a cap** (2 recipients, then a half chance to pass on): cheaper than our radius alert in big
  battles.
- **Pike and lance charge damage by weapon:** our charge is per rider; theirs is per weapon. Fits anti-charge pikes.
