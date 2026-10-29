# NPC screen redesign

Decided with the user 2026-10-29: layout A, a tab strip left of the panel, then the rest layered on.

## Tabs

- **Gear**: the inventory as it was (equipment cross untouched, the preview, the 3x3 backpack, the name, the command
  row, the player's inventory), plus AW's two order slots centred under the backpack: Orders (a Routing, and later
  Work, Combat, Trade order) and Upkeep (where to eat). Empty slots say what they take on hover. The empty equipment
  slots show a faint outline (vanilla's sprites at full strength looked burned into the dark cells).
- **Sheet**: the NPC drawn beside its sheet (what the stats popup showed: rank, level and XP, morale with its
  reasons, guard, fed or hungry). The popup still opens from the role label.
- **Orders**: the orders and upkeep orders, each with its stops (a courier's held Routing Order counts).

After the first look (2026-10-29): the Sheet and Orders pages take the whole panel (the player's inventory and the
commands belong to Gear only); the Sheet has a tall portrait and who the NPC is (path and trade, owner or faction,
home); Orders is a duty page (what it is doing now, home, a card per order with its icon and stops); the order slots
are labelled like the equipment cross, with a faint paper (orders) and bread (upkeep).

Other tabs hide the NPC's slots through vanilla's `Slot.isActive` (creative tabs' way), so they neither draw nor take
clicks; the player's inventory slots hide the same way. Only the name stays on every tab.

## Layers to come (in this order)

1. Service record and kill tracking (kills, battles, wounds, routs, times raised, best kill); the War Table reads it too.
2. A journal: the last 8 to 10 notable events, as translation keys and arguments, written when they happen.
3. Earned epithets ("the Bold", "Ironside"), data-driven rules, appended to the name.
4. A mood line from state already tracked (content, hungry, weary, shaken, homeless).
5. Origin and service length, set on first placement.
6. Traits (one or two, small real effects, data-driven), in their own step: they need balancing.

All of it is written on events and read when the screen opens: nothing per tick, nothing synced in battle (VISION's
server-side identity). Later, not now: bonds between NPCs, lineage, a favourite weapon.
