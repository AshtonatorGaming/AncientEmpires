# Networking, bug, and performance analysis (2026-10-30)

A read-through of `src/main/java/com/ancientempires` on branch `structures` (after `26af88b`), weighted toward the code
added in the last two sessions and never seen in game: orders, the Town Hall alarm, trading and coins, the bard, the
fruit farm, the craftsman, survival building, and the NPC screen's order slots. Not a profile: performance items are
reasoned from the code, so measure before and after any change.

Fixed here (one line, trivially safe): **N1** below. Everything else is left for the user to choose.

Severity: **High** = item loss, server stalls, or an exploit reachable in normal play; **Medium** = a visible bug, an
exploit needing another player or a modified client, or a cost that grows with play; **Low** = cosmetic, edge case, or
small cost.

## Summary

| # | Sev. | Area | Finding |
|---|------|------|---------|
| B1 | High | Bug | Survival building wipes every block and container in its footprint, without drops or permission checks |
| B2 | High | Perf/Bug | Order lookups load chunks synchronously, about every 2 s per NPC (upkeep pantry, upkeep hall, work order, remembered worksite) |
| N1 | High | Network | Drafting request read the block before the reach check (chunk load/generation anywhere) - **fixed** |
| B3 | Medium | Bug | Right-clicking a trader also uses the held item (the client does not know it is a trader) |
| N2 | Medium | Network/Perf | Army list rebuilt (whole-level entity walk, up to 256 entries) for every army packet, no rate limit |
| B4 | Medium | Exploit | Couriers, upkeep orders, and trader restocks take from any container: other players' chests and unclaimed structure loot |
| N3 | Medium | Network | Any player can retune or disguise any sound block, structure ones included |
| B5 | Medium | Bug | Survival templates' AW rules (spawners, loot, NPCs, gates) may be placed for free; verify the survival set |
| P1 | Low-Med | Perf | Couriers and patrols re-parse their order's NBT twice a tick |
| B6 | Low | Bug | Owned traders bank their own food each round, then walk to the hall for the next meal |
| P2 | Low | Perf | Builder recomputes a layer's whole cost (palette remap and layer scan) on every check |
| N4 | Low | Network | Unit order placement with NaN coordinates passes the range check |
| B7 | Low | Bug | Trade Order editor: ghost items shift to the first slots after a page turn |
| B8 | Low | Bug | Faction trades: every purchase pushes the pending refill back |
| B9 | Low | Bug | A bard that breaks off stops the same song from every bard for nearby players |
| B10 | Low | Bug | Food taken from a hall that does not fit back is lost |
| B11 | Low | Bug | Courier and patrol positions on their routes are not saved |
| B12 | Low | Bug | Alarm shelter at home ignores the home's dimension |
| B13 | Low | Bug | Craftsman ingredient matching is greedy and can miss a valid assignment |
| B14 | Low | Design | Fruit picker harvests any melon or pumpkin and plants cocoa on any jungle log face in its area |
| P3 | Low | Perf | Fruit farm scan reads about five blocks per air cell |
| P4 | Low | Perf | XP synced to every tracking player on every gain |
| P5 | Info | Perf | NPC screen re-parses order lines every frame (negligible) |

---

## High

### B1. Survival building wipes the footprint (item loss, griefing)

- `block/StructureBuilderBlockEntity.java:175` lays each layer with `AePlacer.place(..., box)`.
- `structure/AePlacer.java:205-209`: for every template cell in the box, `Clearable.tryClear(old)` empties any block
  entity there, then `setBlock` replaces the block. Air cells overwrite too. Nothing drops.
- `item/BlueprintItem.java:72` checks only that the key cell is replaceable. No check of the footprint, of other
  players' blocks, of `StructureClaims` protection, or of spawn protection.

Failure: a player sets a Blueprint beside their storage room; when the builder reaches that layer, the chests are
cleared and replaced by template blocks, their contents gone. In multiplayer anyone can draft a blueprint next to
someone else's base and delete it, including blocks in protected faction structures (`protectionMultiplier` only slows
players' mining).

Fix, smallest first: (a) skip cells whose current block is not air or replaceable (AW's survival builder needed a
clear site); or (b) before `start`, check the whole footprint (`AePlacer.bounds`) and refuse when any cell holds a
block entity, a block another player could claim, or lies in a `StructureRecords` structure; and (c) if clearing is
kept, drop the old block's drops and container contents (`Block.dropResources`, `Containers.dropContents`) instead of
`Clearable.tryClear`. Also call `level.mayInteract(player, pos)` for each corner when placing the blueprint.

### B2. Order lookups load chunks on the main thread

`Level.getBlockEntity` on the server goes through `getChunkAt`, which loads (and if needed generates) a FULL chunk
synchronously. The older code guards this (`NpcUpkeep.hallFor` checks `hasChunkAt`, `NpcEntity.spawner` checks
`isLoaded`, `findSite`'s search uses `getChunkNow`); the new order code does not:

- `entity/npc/NpcUpkeepGoal.java:265` (`pantry()`): `getBlockEntity(stop.pos())` runs *before* the dimension check on
  the next line, so an Upkeep Order made in the Nether loads the chunk at the same coordinates in the overworld. No
  distance limit either (halls have `upkeep.range`).
- `entity/npc/NpcUpkeepGoal.java:388` (`hall()` with an Upkeep Order): no loaded check.
- `entity/npc/NpcWorkGoal.java:778` (`findSite`, the remembered `AeWorksite`) and `:834` (`listedSite`, Work Order
  stops): no loaded check.

How often: `NpcUpkeepGoal.canUse` every 40 to 55 ticks while a meal is due and the NPC carries no food;
`NpcWorkGoal.canUse` every 40 to 55 ticks by day for every worker holding its tool (`NO_SITE_RETRY` only when nothing
is found). A chunk loaded this way gets only a short ticket, so it unloads and is loaded again on the next check.

Failure: a farmer remembers a Crop Farm 300 blocks away (it was moved, or the farm's area is out of view distance);
every two seconds the server loads that chunk from disk. Ten such workers is a steady stutter. With
`upkeep.delivery = TELEPORT`, an Upkeep Order chest anywhere in the dimension also feeds the NPC at any distance
(the walk mode gives up after 600 ticks and retries forever, loading the chunk each time).

Fix: test `stop.dimension()` first, then `level.hasChunkAt(pos)` (or `isLoaded`) before every `getBlockEntity` on a
stored position; treat an unloaded pantry like a hall out of range. Optionally cap the pantry at `upkeep.range` as halls
are. Consider clearing `AeWorksite` when the remembered site is in a loaded chunk but gone.

### N1. Drafting request loaded chunks anywhere (fixed)

`network/DraftingPacket.java` (`Request.handle`) read `getBlockState(request.station)` before checking reach, so a
modified client could send far positions and make the server load or generate chunks on demand. Reordered: reach
first, then the block. Built and game-tested (see the end).

---

## Medium

### B3. Right-clicking a trader also uses the held item

`entity/npc/NpcEntity.java:2389` and `:2398` decide on the trade screen from `NpcShop.isShop(this)` and
`!this.trades.isEmpty()`. Neither is known on the client: `NpcTrades` is never synced, and the orders slot (where a
Trade Order usually sits) is not synced either (only hands and armour are). So on the client a faction trader, or an
owned trader whose order is in its orders slot, falls through to `canOpenInventory` (false for non-admins) and returns
`PASS`. Vanilla's `Minecraft.startUseItem` sends the interact packet first, then, on `PASS`, goes on to use the held
item and sends that too.

Failure: right-clicking a dwarf trader with an ender pearl in hand opens the trade screen and throws the pearl; with
food, starts eating; with a Trade Order, also opens the goods editor; with a War Horn, blows it.

Fix: sync one flag (a bit in a new `DATA_FLAGS` byte, or reuse `DATA_TRADE`): "trades with players", set by the server
when `trades` changes or `NpcShop.isShop` changes (it can be refreshed in `updateStatus`, every 10 ticks). The client
branch then returns `SUCCESS` as the server does.

### N2. Army list rebuilt for every army packet, no rate limit

- `network/ArmyActionPacket.java:84` and `network/UnitOrderPacket.java:90` answer every packet with
  `ModNetwork.sendArmy`, which runs `NpcArmy.list` (`entity/npc/NpcArmy.java:87`): `level.getAllEntities()` (every
  entity in the dimension), a sort, and up to 256 entries of about 150 bytes (two component JSON strings, a skin id,
  role, band) - around 40 KB. `NpcArmy.command` (`:267`) and `freeNumber` (`:187`) walk the whole level again.
- Nothing limits how often a client may send these.

Failure: in normal play this is a few packets per order and fine. A modified client (or a stuck key) sending a few
hundred `ArmyActionPacket`s a second walks every entity a few hundred times a tick and floods its own connection.

Fix: rate-limit per player (one list per 5 ticks; drop or coalesce the rest), and keep a per-dimension index of owned
soldiers by owner (added on join, removed on leave) instead of `getAllEntities`. Send only when the screen asked
(`open`) or a change was made.

### B4. Order-driven workers take from any container

- `entity/npc/NpcCourierGoal.java:227` (`container`) refuses only another player's *worksite*; any other block entity
  with an item handler is fair game. Same for `NpcShop.restock` (via the courier round) and the Upkeep Order pantry
  (`NpcUpkeepGoal.pantry`).
- Reading a chest through its item handler unpacks its loot table with no player (`RandomizableContainerBlockEntity`),
  so AW's extras kept in `AeLoot` (the trapped mob, potions, the message) never fire, and the `StructureClaims` loot
  lock (`onRightClick`, player clicks only) does not apply.

Failure: a player marks the loot chests of an unclaimed bandit camp on a Routing Order (take everything) and sends a
courier: the chests are emptied without claiming the camp or meeting the garrison. In multiplayer, a courier drains a
neighbour's chests.

Note that vanilla hoppers already bypass the loot lock, so this is parity with hoppers rather than a new hole; it matters
most for multiplayer. Fix: in `container()` and `pantry()`, refuse a position inside a `StructureRecords` record that is
held (not yet claimed by the NPC's owner), and in multiplayer refuse block entities in claims of others (if a claim mod
is present, `level.mayInteract(owner, pos)` with the owner online is the usual check).

### N3. Any player can retune or disguise any sound block

`network/SoundBlockPacket.java:37` checks only distance (8 blocks) and that the chunk is loaded;
`block/decor/AwSoundBlock.java:40` lets any player set the disguise with a held block. Structure sound blocks are part of
AW templates (their disguise hides them in walls).

Failure: a survival player walks into a faction structure, right-clicks the hidden sound block with dirt and turns it
into a visible dirt block, or sets its tune to loud sounds with a long range. `setTune` caps ranges and counts, so it is
griefing, not a crash.

Fix: require creative (`NpcEntity.mayToggleCreative`) or permission level 2 for both the disguise and the tune, as the
Scanner does; survival players get `PASS`.

### B5. Survival templates may place AW rules for free (verify)

`structure/AeBuilds.java:104` charges nothing for blocks with no item, and `AePlacer.place` runs `placeAw` for every
`aeRule` cell (NPC spawners become garrisons, AW loot chests roll loot, coin stacks), plus the template's entities in
the box (AW NPCs placed directly, gates, siege engines).

Failure, if any survival template carries them: a player drafts it and gets a loot chest's contents for one chest, or
a faction garrison spawner inside their own base.

Check: `/ancientempires structure check <name>` on each survival template (the Drafting Station's list) for spawners,
loot, and entities. Fix if any: a `survival` flag on `AePlacer.place` that skips `placeAw` rules other than doors,
banners, and plain block entities, places no entities, and leaves containers empty.

---

## Low

### P1. Couriers and patrols re-parse their order every tick

`entity/npc/NpcCourierGoal.java:75` (`route()`) is called from `canContinueToUse` (`:99`) and `tick` (`:119`), and
`NpcPatrolGoal.java:44` the same: each parses the order's `AeStops` list and builds an `ItemStack` per stop filter
(`ItemStack.of`, a registry lookup), up to 16 stops, twice a tick per working NPC. Small per NPC; worth fixing before
towns of couriers. Fix: cache the parsed list with the stack and its tag reference (re-parse when `order.getTag()` is a
different object or the stack changed).

### B6. Owned traders bank their own food

`entity/npc/NpcShop.java:163` (`bank`) moves every backpack stack that is not a good it sells, rations included (and a
Routing Order courier set to "deposit all" does the same). After each bank stop the trader carries no food, so at the
next meal it walks to the hall (or goes hungry, which stops its round: `NpcCourierGoal.onDuty` requires not hungry).
Fix: keep food (`TownHallBlockEntity.rations(stack) > 0`) up to `upkeep.carryRations` when banking or depositing all.

### P2. Builder recomputes the layer cost on every check

`block/StructureBuilderBlockEntity.java:151` (`canBuild`) calls `missing()` (`:115`), which calls
`AeBuilds.layerCost` (`structure/AeBuilds.java:85`): it remaps the whole palette through `AeRemap.block` and scans the
layer. A builder NPC's `pickJob` runs it every 40 to 55 ticks, and `buildLayer` runs it twice more. Fine for small
templates; a 64x64 layer with a few hundred palette entries is noticeable. Fix: cache the cost per (template, layer)
in the block entity, cleared when the layer advances or templates reload.

### N4. NaN placements pass the unit order range check

`entity/npc/NpcArmy.java:289` rejects placements farther than `AIM_RANGE + 8`, but `NaN > x` is false, so a placement or
facing of NaN or infinity from a modified client passes, and `ground` floors it to 0. Only the sender's own soldiers are
affected (they march toward the origin). Fix: reject non-finite `from`, `to`, and `facing` in `UnitOrderPacket.decode`.

### B7. Trade Order editor: ghosts shift after a page turn

`item/TradeOrderItem.java:141` (`read`) drops empty stacks, and `inventory/TradeOrderMenu.java:117` (`load`) lays the
remaining ones from slot 0. A stack set in the sixth "pays" slot shows in the first after turning a page. Cosmetic.
Fix: save the slot index with each stack, or accept it as compaction.

### B8. Every purchase pushes a faction trade's refill back

`entity/npc/NpcTrades.java:175` sets `refillAt = now + refill` on every use, even when a refill was already due sooner.
A trade bought every four minutes with a five-minute refill never refills. Check what AW's `FactionTrade` did; if it
counted from the last refill, set `refillAt` only when it is not already pending (`refillAt <= 0`).

### B9. A bard breaking off silences other bards

`entity/npc/NpcBardGoal.java:80` sends `ClientboundStopSoundPacket` for the song's sound id to players within 64 blocks,
which stops that song from every source on their client, including another bard nearby playing the same piece.
Accept it, or skip the stop when another bard within range is playing (bards are few).

### B10. Hall food that does not fit back is lost

`entity/npc/NpcUpkeepGoal.java:334`: the remainder of `HopperBlockEntity.addItem` is ignored. `takeFood` just made room,
so it rarely matters (a supply slot filter can refuse it). Fix: drop what is left at the hall (`Block.popResource`).

### B11. Route positions are not saved

`NpcCourierGoal.stopIndex` and `NpcPatrolGoal.point` live only in the goal, so a reload or a chunk unload restarts the
route at stop 1. A long round with a far first stop then repeats the start. Fix: save them (two ints) with the NPC, or
accept it.

### B12. Alarm shelter ignores the home's dimension

`entity/npc/NpcAlarmGoal.java:74` uses `getHome()` without `DATA_HOME_DIM`. An NPC whose home is in another dimension
walks toward those coordinates here. Rare. Fix: use the hall when the home's dimension differs.

### B13. Craftsman ingredient matching is greedy

`block/CraftingStationBlockEntity.java:159` gives each ingredient the first stack it accepts. When one ingredient accepts
two items and another accepts only one of them, it can take the scarce one first and report the recipe uncraftable
(for example a tag ingredient and a specific item of that tag, with one of each in store). Fix: match the most specific
ingredients first (fewest matching stacks), or retry with the other assignment.

### B14. Fruit picker takes decorative melons and pumpkins, and plants on any jungle log

`block/FruitFarmBlockEntity.java:89` (`scan`) lists every melon and pumpkin block and every air cell beside a jungle log
in the area, so placed decoration and jungle-log houses are harvested and planted. AW-like, but surprising. Fix: only
melons and pumpkins beside an attached stem; cocoa only on logs that already carry or carried a pod, or leave as is.

### P3. Fruit farm scan cost

`block/FruitFarmBlockEntity.java:89`: area times ten heights, and four neighbour reads per air cell
(`cocoaFacing`). At the large size this is tens of thousands of block reads per scan, at most every `work.scanTicks`
and only while a picker asks. Fix: test the four neighbours only when the cell is air and some log is near (or skip
cocoa planting unless the store holds beans).

### P4. XP is synced to every tracking player

`entity/npc/NpcProgress.java:144` sets `DATA_XP` on every gain (each hit dealt, each work action), and entity data goes
to every player tracking the NPC, though only its own screen shows XP. In a large battle this is one extra entity data
update per hit per viewer. Fix: send XP through the menu's container data (`NpcMenu.Stats`) as morale already is, and
drop `DATA_XP`.

### P5. NPC screen parses order lines every frame

`client/NpcScreen.java:687` calls `OrderItem.lines(order)` each frame on the Orders tab. Negligible (two orders, at most
16 stops); cache with the stack if the tab grows.

---

## Checked and fine

- Packet validation: `NpcNamePacket` and `NpcSkinPacket` check `canOpenInventory` and 8 blocks in `NpcEntity`, cap
  lengths, and validate skins; `WorksiteBoundsSetPacket` checks distance before `isLoaded` and `mayManage`;
  `ScannerSavePacket` needs creative and permission 2; `TradePacket.Request` re-checks reach, hostility, stock, and
  payment on the server; `ArmyActionPacket` and `UnitOrderPacket` act only on the sender's soldiers (`NpcArmy.owned`);
  `UnitOrderPacket` checks placement range and line length. Strings are bounded on read everywhere.
- Client-only classes: network classes import screens but call them only inside `DistExecutor.unsafeRunWhenOn`
  lambdas, which a dedicated server never runs (the game test server loads them all). `FlagItem`'s renderer is behind
  `initializeClient`.
- Packet sizes: the NPC screen's open buffer (service record, 10 journal lines) is far under Forge's 32 KB limit;
  `DraftingPacket` lists only survival templates; `TradePacket` at most nine stacks a side per trade.
- Synced entity data: status, banner, rank, and badge are set every 10 ticks only when changed (`updateStatus`; the
  banner compared with `ItemStack.matches`, so no resync from new copies).
- Trading: `NpcShop.perform` works the trade out on a copy of the backpack before touching either side; `Coins.pay`
  is only called after `canPay`; payment and goods use the same `items` list on both checks. No duplication found.
- Order slots: saved apart (`AeOrderSlots`), dropped with the backpack on death and repack, kept with the backpack under
  `keepOnDeath`; slots take one order item; shift-click puts orders there first.
- `TradeOrderMenu`: ghost slots take no pickup, ignore swap, clone, and throw clicks, and are never dropped on close;
  pick-all respects `mayPickup`.
- Alarm: one scheduled tick per hall while powered (a 200x100x200 box query every 40 ticks), nothing when unpowered;
  a broken hall's pending tick does not run.
- Bard: box query only every `bard.pulseTicks` (minimum 10), `canUse` every 40 ticks.
- Worksite scans run only on demand and at most every `work.scanTicks`; `findSite`'s chunk walk uses `getChunkNow`.

## Verification

- `./gradlew build --offline`: passes with the N1 change.
- `./gradlew runGameTestServer --offline`: all 33 required tests pass with the N1 change.
- The installed jar was not replaced (a game was running).
