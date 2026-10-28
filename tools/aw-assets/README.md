# AW asset generators

Node scripts that build Ancient Empires data from Ancient Warfare's jar (`libs/ancientwarfare-1.12.2-2.10.2.jar`).
Run them from the repo root; regenerate rather than hand-edit their output.

- `gendeco.mjs <AW assets/ancientwarfare> <repo>`: decor blockstates, models, textures, and `aw_decor/blocks.json` (properties, collision boxes). Extract the jar's `blockstates/structure`, `models/block/structure`, `textures/blocks` first.
- `decoloot.mjs`: decor loot tables (only the base part of a tall piece drops).
- `checkmodels.mjs`: checks every generated model reference resolves.
- `genveh.mjs <decompiled vehicle models> <AW textures/model/vehicle> <repo>`: `aw_vehicles/models.json` and vehicle textures. Decompile AW's `vehicle/model` and `vehicle/render` classes with ForgeFlower first.
- `sounds.mjs <assets/ancientempires>`: adds every `.ogg` under `sounds/` to `sounds.json`.
