import fs from "fs"; import path from "path";
const R = "src/main/resources";
const table = JSON.parse(fs.readFileSync(path.join(R, "assets/ancientempires/aw_decor/blocks.json"), "utf8"));
const ORIGIN = { chair: { visible: "true" }, tribal_chair: { visible: "true" }, wooden_throne: { visible: "true" }, golden_throne: { part: "bottom" },
  golden_idol: { bottom_part: "true" }, stake: { part: "bottom" }, wooden_post: { part: "bottom", visible: "true" }, iron_cage: { part: "bottom", visible: "true" },
  gibbet: { part: "bottom", visible: "true" }, stretching_rack: { part: "south", visible: "true" }, totem_part: { visible: "true" } };
const dir = path.join(R, "data/ancientempires/loot_tables/blocks"); fs.mkdirSync(dir, { recursive: true });
for (const [name, spec] of Object.entries(table)) {
  const conditions = [{ condition: "minecraft:survives_explosion" }];
  if (ORIGIN[name]) conditions.push({ condition: "minecraft:block_state_property", block: "ancientempires:" + name, properties: ORIGIN[name] });
  const entry = { type: "minecraft:item", name: "ancientempires:" + name };
  if (spec.properties.variant) entry.functions = [{ function: "minecraft:copy_state", block: "ancientempires:" + name, properties: ["variant"] }];
  fs.writeFileSync(path.join(dir, name + ".json"), JSON.stringify({ type: "minecraft:block", pools: [{ rolls: 1, entries: [entry], conditions }] }, null, 2) + "\n");
}
console.log("loot tables", Object.keys(table).length);
