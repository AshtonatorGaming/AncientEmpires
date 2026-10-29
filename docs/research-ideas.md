# Research and advancement: ideas (not decided)

Raw ideas for our own research system (AW's recipe-locking research is dropped; docs/aw-parity.md, Decisions). The
user's direction: scholars and holy men slowly pushing toward advancements, Crusader Kings or Europa Universalis style.
Designed after AW parity. Nothing here is decided.

## The user's direction (2026-10-29)

- Scholars (and holy men) dedicated to slowly advancing; the player chooses what they work toward.
- Advancements grant buffs or other things, not crafting recipes.

## Ideas brainstormed by the user with Gemini (raw; the user is unsure of several)

1. **Salvage-driven research.** Scholars need a catalyst: a high-tier weapon, armour, or piece of a fortification
   taken from a faction's army, handed to a scholar at a library or the Town Hall, studied for some days, unlocking
   that faction's design for the player's engineers. The user is unsure about blueprints and salvage, but does not
   rule it out.
2. **Cartographer.** A scholar at a map table pushes back the fog of war of the strategic layer: tier 1 sees raids
   within 500 blocks, tier 3 the whole continent and marching armies' paths. Needs the world director first.
3. **Points from scribes.** A scribe at a library or the Town Hall adds to one integer on the town (cheap, no block
   edits); the player spends it at the War Council table on advancements (larger squads, new constructions such as
   stake walls or caltrops, faction templates).
4. **Functions over stats.** Advancements should give options and agency (a feigned retreat order, mounting infantry
   as cavalry, siege ladders) rather than "+10% arrow damage". Note the tension with the user's "buffs or other
   stuff": likely a mix, mostly new options with some buffs.
5. **Mod bridges.** A scholar translates a spell scroll or book (Iron's Spells, Wizardry) so a settler can be made an
   apprentice or battlemage casting that spell; datapacks add whole trees (a Warhammer pack studying warpstone).

## Claude's take, for when this is designed

- Fits VISION's primitives: one integer per town, a slow tick, data-driven trees in a datapack folder.
- Start from 3 (scribes and scholars filling a pool at a study block, the player choosing) with 4 as the guide;
  add 1 (salvage) as one optional way to open a branch, and 2 once a director exists.
- 5 overlaps the spellcaster port (Wizardry): build the spellcaster first, then let research hand out spells.
