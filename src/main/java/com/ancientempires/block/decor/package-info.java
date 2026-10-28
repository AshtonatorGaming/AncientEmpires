/**
 * Ancient Warfare's decor: furniture, gravestones, totems, braziers, altars,
 * cages, coffins, statues, stakes, and sound blocks.
 *
 * <p>The 31 plain pieces are one generic
 * {@link com.ancientempires.block.decor.AwDecorBlock} each, described by
 * {@link com.ancientempires.block.decor.AwDecor} from
 * {@code assets/ancientempires/aw_decor/blocks.json} (their properties and
 * collision boxes). That file, their blockstates, and their models are
 * generated from AW's own assets by {@code tools/aw-assets}; regenerate rather
 * than hand-edit. Tall pieces are placed and broken whole, seats are ridden
 * through {@link com.ancientempires.block.decor.SeatEntity}, and lit pieces
 * light and douse.
 *
 * <p>Pieces with data have block entities:
 * {@link com.ancientempires.block.decor.CoffinBlockEntity} (way, lid, and loot
 * through {@code structure.AwLoot}),
 * {@link com.ancientempires.block.decor.AwDecorBlockEntity} (gravestone loot,
 * a stake's or statue's mob), and
 * {@link com.ancientempires.block.decor.AwSoundBlockEntity} (disguise and
 * tune). Structures fill them in {@code structure.AePlacer} from AW's tile
 * data. Drawing is {@code client.CoffinRenderer} and
 * {@code client.DecorBlockRenderers}.
 */
package com.ancientempires.block.decor;
