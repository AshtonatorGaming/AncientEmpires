# Handoff (2026-10-30)

Start of a new chat: read this, then `docs/session-notes.md`, `docs/aw-parity.md`, and `docs/roadmap.md`. `CLAUDE.md`
holds the architecture and rules and is loaded on its own.

## Where things stand

- Branch `structures`, pushed. Latest: `ae2491a` (survival building). 33 game tests pass on a fresh test world (the old
  one was moved aside; tests clear their own ground).
- The jar is installed in the test instance (`C:\Users\coolg\AppData\Roaming\.sklauncher\instances\final-testing\mods`).
- Phase: **AW parity before polish** (user's call). Done this stretch: orders (Routing, Work, Upkeep, Combat, Trade),
  Town Hall alarm, faction traders and the Coin Purse, the owned trader, the bard, the fruit farm, the craftsman, survival
  building, the NPC screen tabs, service record and journal, weapon-named infantry.
- Not seen in game: everything in the checklist below.

## Play-test checklist

Each line: what to do, and what should happen. Note anything off (screenshots help) and bring it to a fix chat.

1. **Orders slots.** Open an owned NPC, Orders tab: two slots, gold-labelled. Only order items go in.
2. **Work Order.** List a Crop Farm with it, put it in a farmer's orders slot: the farmer works that farm, not the nearest.
3. **Upkeep Order.** Click a chest with it (food inside), put it in the upkeep slot: a hungry NPC walks to that chest and takes food.
4. **Combat Order.** Click 2 or 3 blocks, give it to a soldier: it walks them in turn; "stand watch" stops pause ~10 s.
5. **Routing Order (courier).** Two chests, take at one, deposit at the other: the worker carries between them by day.
6. **Town Hall alarm.** Lever on a hall: bell, message; workers go home (or to the hall with `upkeep.alarmShelter = HALL`), soldiers gather by the hall. Lever off: back to work within ~5 s.
7. **Faction trader.** Find a structure trader (e.g. a dwarf smithy): right-click opens the trade screen; a coin trade pays from a Coin Purse with change.
8. **Coin Purse.** Use it: gathers loose coins. Sneak-use: pours them out.
9. **Owned trader.** Trade Order in the air: goods editor (click sets, right click +1, empty hand clears/-1, New/Drop pages). Give it to a worker with stock in its backpack: another player (or you, sneaking) can buy. Its round: banks coins, restocks from a chest, stands at the stop a minute.
10. **Creative trade editor.** In creative, Trade Order on a faction trader writes the goods in; sneaking copies them out.
11. **Bard.** Worker holding a lute/flute/harp/drum, you within 24 blocks: plays an AW song, strums, faces you. Nearby soldiers' sheet shows "Cheered by a song". Players can use an instrument for a note.
12. **Fruit farm.** Fruit Farm near berry bushes / cocoa on jungle logs, worker with a hoe and no crop farm: picks into its backpack, bushes left growing, cocoa replanted.
13. **Craftsman.** Crafting Station, sneak-click with sticks; planks in its store; worker holding a crafting table crafts sticks.
14. **Survival building.** Drafting Station: list and search work, Draft costs 4 paper, gives a Blueprint. Use it on the ground: a scaffolding-looking Structure Builder, front toward you. Sneak-click: layer and what it needs. Fill the store; a worker with a shovel lays it layer by layer; at the end the builder disappears and drops leftovers.
15. **NPC screen.** Gear/Sheet/Orders/Journal tabs; title reads like "Veteran Archer Sergeant"; journal fills with kills, battles, wounds.

## Open decisions (defaults chosen, easy to change)

- Craftsman's tool: a crafting table in hand. Builder's tool: any shovel. Fruit picker shares the hoe with the farmer.
- Survival building pays materials per layer at the site (AW paid all up front at the drafting station).
- Bard cheer numbers: morale +6, work +10%, within 16 blocks.

## Next, in order (docs/aw-parity.md)

1. Fixes from the play test.
2. The manual (in-game guide; onboarding needs it).
3. Towns (`.awt`) and the missing site types (sky, underground, harbor, lava).
4. The warehouse.
5. Siege vehicles and NPC crews.
6. Spellcasters (Electroblob's Wizardry and Iron's Spells; read Hundred Years War's Iron's compat first).
7. Chunk loaders (config-gated); look up AW's recruiting station.
Deferred polish: cohort command posts, mounted commanders, order slot outlines, more sheet layers.

## Working rules (the user's, still in force)

- Never stage `src/main/resources/assets/ancientempires/npc/skins.json` (the user's edit). Stage by path; never sweep `src/main`.
- Don't push to `main`; don't edit `libs/`. Commits end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Never replace the jar while the game runs (check for `javaw`).
- No python here; scripts in node. Bash heredocs with apostrophes fail; write scripts with the Write tool. Files may be CRLF.
- Config: three edits per setting in `AncientEmpiresConfig`. Bump `ModNetwork.PROTOCOL` (now "17") when packets change.
- Game tests: see CLAUDE.md's Tests section (run tag, origin range, own batch for big footprints).
- Parity first; new systems only if they serve the current phase. Port ideas, not code. Breaking changes are fine.
