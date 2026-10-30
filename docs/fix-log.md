# Fix log

Bugs that were found and fixed, each with what went wrong, why, and the rule that would have prevented it. It exists
so later sessions do not make the same mistakes. Read the **Rules** first when writing new code; add an entry (and a
rule, if the mistake could happen again) whenever you fix a bug that was not a one-off typo. Newest first.

## Rules

These are also summarized in CLAUDE.md under "Working style".

1. **Never read a stored position without checking it can be read.** On the server, `Level.getBlockEntity` and
   `getBlockState` load (and if needed generate) the chunk on the main thread. Any position that came from NBT, an
   item, an order, a packet, or memory (`AeWorksite`, order stops, homes, beds) must pass a dimension check *first* and
   then `level.isLoaded(pos)` / `hasChunkAt(pos)`. Use `NpcStores.at` / `NpcStores.readable` for order stops. Search
   with `getChunkNow` when walking chunks.
2. **Packets check cheap things before touching the world.** Order: sender present, permission (owner, creative,
   op), reach (`distanceToSqr`), then `isLoaded`, then read blocks or entities. A block read before the reach check lets
   a modified client load chunks anywhere.
3. **Never trust floats from a client.** `NaN` fails every comparison, so it passes `x > max` range checks. Reject
   non-finite values (`Double.isFinite`) when decoding.
4. **Expensive answers need a rate limit.** A packet whose reply walks every entity or sends kilobytes (the army list)
   must be paced per player (`ArmyThrottle`); coalesce requests instead of answering each.
5. **The client must agree with the server on interactions.** `mobInteract` runs on both sides. If the server decides
   from unsynced state (an NPC's trades, its orders slot), the client answers `PASS` and vanilla goes on to *use the
   held item*. Sync a flag for any state that decides an interaction's result (`DATA_TRADER`).
6. **Placing blocks for a player never deletes.** Anything a survival action replaces must drop (block and container
   contents), and the area must be checked first for block entities, other structures, and `level.mayInteract`
   (spawn protection, world border). `Clearable.tryClear` is for world generation and admin commands only.
7. **A survival action must not hand out what it was not paid for.** Template data carries loot tables, container
   contents, garrison spawners, and entities: strip them when a player builds (`AePlacer.placeSurvival`).
8. **NPC workers obey the same locks as players.** A container an NPC uses by order must pass `NpcStores.mayUse`
   (not another player's worksite, not inside a faction structure still held). Hoppers bypass locks in vanilla; NPCs
   should not add another way.
9. **Admin-only blocks check permission on both the block and its packet.** Structure pieces (sound blocks) were
   editable by anyone because only reach was checked.
10. **Synced entity data is for what other players see.** Values that change often and only the NPC's own screen shows
    (XP) go through the menu's container data (`NpcMenu.Stats`), not `SynchedEntityData`.
11. **Do not re-parse NBT every tick.** A goal that reads an item's list each tick caches it, keyed by the tag object
    (`OrderItem.StopCache`: every edit writes a new list tag, so identity is a safe key).
12. **Goal state that matters across a reload is saved.** A route position kept only in a goal restarts at a reload;
    store it on the order or the NPC (`OrderItem.progress`).
13. **A worker handing over "everything" keeps its own meals** (`NpcStores.keptFood`), or it walks to the hall at every
    meal and stops working while hungry.
14. **Timers that restart on every use starve.** Only start a refill when none is pending (`NpcTrades.perform`).
15. **Never discard a remainder.** Every insert that can fail (`HopperBlockEntity.addItem`, `Inventory.add`) handles
    what is left: put it back, or drop it in the world.
16. **Greedy matching picks the pickiest first.** When several ingredients or slots compete for items, sort by how many
    items each accepts (fewest first) before assigning.
17. **Before claiming done, run `./gradlew build` and `./gradlew runGameTestServer`.** All game tests must pass; say so
    with the count.

## 2026-10-30: the analysis sweep (docs/analysis-2026-10.md)

All findings of the networking, bug, and performance sweep, fixed in one pass. Build and all 33 game tests pass.
`ModNetwork.PROTOCOL` went to 17 (new synced entity data, removed `DATA_XP`, menu data grew).

### B1. Survival building wiped its footprint (rules 6, 7)
- Symptom: a Structure Builder replaced every cell of its template, emptying chests (`Clearable.tryClear`) and
  deleting blocks without drops; a blueprint could be placed against anyone's base.
- Cause: survival building reused world generation's placement (`AePlacer.place`), which clears silently, and the
  Blueprint only checked its own key cell.
- Fix: `AeBuilds.refusal` checks the whole footprint before the builder is set (loaded, outside every structure via
  `AwSites.overlaps`, `level.mayInteract` at the key and corners, no block entity inside). The builder lays layers with
  `AePlacer.placeSurvival`, which drops what stood in a cell (its container contents too) instead of clearing it.
- Files: `structure/AeBuilds.java`, `structure/AePlacer.java`, `item/BlueprintItem.java`,
  `block/StructureBuilderBlockEntity.java`, lang `item.ancientempires.blueprint.{structure,forbidden,unloaded,blocked}`.

### B5. Survival templates could give free loot, garrisons, and entities (rule 7)
- Cause: `placeAw` and block entity data ran for player builds as for world generation; `layerCost` charges nothing
  for blocks without an item.
- Fix: `placeSurvival` sets AW garrison spawners to air, leaves coin stacks at one layer, strips every placed block
  entity of items, loot table, and `AeLoot` extras (`stripLoot`), and places no template entities.

### B2. Order lookups loaded chunks every two seconds (rule 1)
- Symptom (expected): stutter with workers whose remembered worksite or ordered pantry was out of view; an Upkeep Order
  made in the Nether loaded the same coordinates in the overworld.
- Cause: `getBlockEntity` on stored positions without a loaded check, and in `NpcUpkeepGoal.pantry` before the
  dimension check. The older code (`NpcUpkeep.hallFor`, `NpcEntity.spawner`) already guarded; the new order code
  did not follow it.
- Fix: new `entity/npc/NpcStores` (`readable`, `at`, `handler`, `mayUse`); used by `NpcUpkeepGoal.pantry` (also capped
  at `upkeep.range`, as halls are) and `hall`, `NpcWorkGoal.findSite` (remembered site) and `listedSite`,
  `NpcCourierGoal.container`.

### N1. Drafting request loaded chunks anywhere (rule 2)
- Cause: `DraftingPacket.Request.handle` read the station's block before checking reach.
- Fix: reach first. (Commit 9dd7b87.)

### B3. Right-clicking a trader also used the held item (rule 5)
- Symptom: an ender pearl thrown, food eaten, a War Horn blown, or a Trade Order's editor opened along with the trade
  screen.
- Cause: the client's `mobInteract` could not see `NpcTrades` or the orders slot, returned `PASS`, and vanilla then
  sent a use-item packet.
- Fix: synced `DATA_TRADER` byte (faction trades, or a player's shop), set in `updateStatus` every 10 ticks;
  `NpcEntity.isTrader` reads it on the client and works it out on the server.

### N2. Army list rebuilt for every army packet (rule 4)
- Cause: each `ArmyActionPacket` and `UnitOrderPacket` walked every entity in the dimension and sent up to 256
  soldiers, with no limit on how often.
- Fix: `network/ArmyThrottle`: a list at most every 5 ticks per player (requests in between answered together), at most
  8 actions a tick; state dropped on logout.

### N3. Anyone could re-skin or retune sound blocks (rule 9)
- Fix: `AwSoundBlock.use` and `SoundBlockPacket.handle` require `NpcEntity.mayToggleCreative`.

### N4. NaN placements passed the unit order range check (rule 3)
- Fix: `UnitOrderPacket.decode` drops a placement with non-finite coordinates or facing (the order falls back to where
  the player looks).

### B4. Order workers took from any container (rule 8)
- Cause: only another player's worksite was refused, so couriers, traders, and upkeep pantries could empty unclaimed
  faction loot chests (skipping the loot lock and AW's loot extras).
- Fix: `NpcStores.mayUse` also refuses containers inside a structure `StructureClaims.held` reports (now public).

### B6. Traders and couriers banked their own food (rule 13)
- Fix: `NpcStores.keptFood` keeps whole food stacks up to `upkeep.carryRations`; `NpcShop.bank` keeps them, and a
  courier's deposit keeps them unless the stop's filter names that food.

### P1, B11. Couriers and patrols re-parsed their order every tick and lost their place on reload (rules 11, 12)
- Fix: `OrderItem.StopCache` (keyed by the stop list tag's identity); `OrderItem.progress`/`setProgress` keep the
  current stop on the order as `AeStopAt`, read in `start()` and written in `next()`.

### P2. The builder recomputed a layer's cost on every check
- Fix: `StructureBuilderBlockEntity.layerCost` caches it per template object and layer.

### B7. Trade Order editor packed ghosts to the front after a page turn
- Fix: `TradeOrderItem.write` saves each stack's editor `Slot`; `TradeOrderItem.editorSlots` lays them back where they
  were (older orders fill from the first slot). Trading still reads the packed lists.

### B8. Every sale pushed a faction trade's refill back (rule 14)
- Fix: `NpcTrades.perform` sets `refillAt` only when no refill is pending.

### B9. A bard breaking off silenced other bards' same song
- Cause: `ClientboundStopSoundPacket` stops by sound id, every source.
- Fix: `NpcEntity.playingSong` (not saved) marks a playing bard; the stop is not sent to a listener with another bard
  playing that song within 64 blocks.

### B10. Hall food that did not fit back vanished (rule 15)
- Fix: `NpcUpkeepGoal.takeFrom(hall)` drops what the store refuses above the hall.

### B12. Alarm shelter ignored the home's dimension
- Fix: `NpcAlarmGoal` shelters at home only when `getHomeDimension` is empty or this dimension, else at the hall.

### B13. Craftsman matching was greedy (rule 16)
- Fix: `CraftingStationBlockEntity.craft` assigns ingredients fewest accepted items first.

### B14, P3. Fruit picker took placed melons and pumpkins; its scan read about five blocks per air cell
- Fix: `FruitFarmBlockEntity.scan` reads the area plus a one-block rim once into a grid; melons and pumpkins count only
  beside an attached stem facing them (`isGourd`, `stemGrown`, also checked by the picker at the block). Cocoa still
  goes on any jungle log in the area, as in AW (left as it was).

### P4. XP synced to every tracking player on every gain (rule 10)
- Fix: `DATA_XP` removed; `NpcProgress` keeps XP in a field (still saved as `AeXp`), sent to the NPC's open screen as
  `NpcMenu.STAT_XP` and filled in on the client by `NpcMenu.setData` to `NpcProgress.showXp`.

### Not changed
- P5 (the NPC screen re-reading order lines each frame): negligible, two orders of at most 16 stops.
