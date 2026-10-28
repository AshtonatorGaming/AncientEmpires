// Ports Ancient Warfare's decor blocks: Forge v1 blockstates -> vanilla multipart, models and textures copied,
// collision boxes worked out from the model elements, and a property table for the generic Java block.
import fs from "fs"; import path from "path"; import crypto from "crypto";
const [AW, REPO] = process.argv.slice(2);
const OUT = path.join(REPO, "src/main/resources/assets/ancientempires");
const BLOCKS = ["altar_candle", "altar_lectern", "altar_long_cloth", "altar_short_cloth", "altar_sun", "bench", "brazier_ember", "brazier_flame",
  "chair", "fire_pit", "gibbet", "goblin_totem_1", "goblin_totem_2", "goblin_totem_2_lit", "golden_idol", "golden_throne", "gravestone", "iron_cage",
  "orc_totem_1", "orc_totem_2", "orc_totem_2_lit", "scissor_seat", "stake", "stool", "stretching_rack", "table", "totem_part", "tribal_chair",
  "tribal_stool", "wooden_post", "wooden_throne"];
const VANILLA = { cobblestone: "cobblestone", fire_layer_0: "fire_0", log_oak: "oak_log", log_oak_top: "oak_log_top", planks_oak: "oak_planks",
  planks_spruce: "spruce_planks", planks_birch: "birch_planks", planks_jungle: "jungle_planks", planks_acacia: "acacia_planks",
  planks_big_oak: "dark_oak_planks", wool_colored_red: "red_wool", stone: "stone", gold_block: "gold_block", iron_bars: "iron_bars" };
const usedTextures = new Set(); const problems = [];
function tex(t) {
  if (t.startsWith("#")) return t;
  let m = t.match(/^(?:minecraft:)?blocks\/(.+)$/);
  if (m) { const v = VANILLA[m[1]]; if (!v) problems.push("vanilla texture " + t); return "minecraft:block/" + (v || m[1]); }
  m = t.match(/^ancientwarfare:blocks\/(.+)$/);
  if (m) { usedTextures.add(m[1]); return "ancientempires:block/aw/" + m[1]; }
  problems.push("texture " + t); return t;
}
function modelRef(m) { // a forge blockstate model name -> [file, our id]
  let mm = m.match(/^ancientwarfare:(.+)$/);
  if (mm) return { file: path.join(AW, "models/block", mm[1] + ".json"), id: "ancientempires:block/aw/" + mm[1].replace(/^structure\//, "") };
  mm = m.match(/^minecraft:(.+)$/) || m.match(/^([a-z_0-9]+)$/);
  if (mm) return { file: null, id: "minecraft:block/" + mm[1] };
  problems.push("model " + m); return { file: null, id: m };
}
const models = new Map(); // our id -> json written
function copyModel(ref) {
  if (!ref.file || models.has(ref.id)) return;
  const json = JSON.parse(fs.readFileSync(ref.file, "utf8"));
  delete json.credit; delete json.__comment;
  if (json.parent) { if (json.parent.startsWith("ancientwarfare:")) { const p = modelRef(json.parent.replace(/^ancientwarfare:block\//, "ancientwarfare:")); copyModel(p); json.parent = p.id; } else json.parent = "minecraft:" + json.parent.replace(/^minecraft:/, ""); }
  if (json.textures) for (const k of Object.keys(json.textures)) json.textures[k] = tex(json.textures[k]);
  json.render_type = "minecraft:cutout";
  models.set(ref.id, { json, file: ref.file });
}
function elementsOf(id) { // resolved elements of a model (own or a parent's)
  const m = models.get(id); if (!m) return [];
  if (m.json.elements) return m.json.elements;
  return m.json.parent ? elementsOf(m.json.parent) : [];
}
const children = new Map(); // key -> id
function withTextures(baseId, textures) {
  if (!textures || !Object.keys(textures).length) return baseId;
  const t = {}; for (const [k, v] of Object.entries(textures)) t[k] = tex(v);
  const key = baseId + JSON.stringify(t);
  if (!children.has(key)) {
    const id = "ancientempires:block/aw/v/" + baseId.split("/").pop() + "_" + crypto.createHash("md5").update(key).digest("hex").slice(0, 6);
    children.set(key, id); models.set(id, { json: { parent: baseId, textures: t, render_type: "minecraft:cutout" } });
  }
  return children.get(key);
}
function merge(into, part) {
  if (!part) return;
  if (Array.isArray(part)) part = part[0] || {};
  if (part.model) into.model = part.model;
  if (part.textures) Object.assign(into.textures, part.textures);
  if (part.x !== undefined) into.x += part.x; if (part.y !== undefined) into.y += part.y;
  if (part.uvlock) into.uvlock = true;
  if (part.submodel) { if (typeof part.submodel === "string") into.submodels.__main_sub = { model: part.submodel }; else Object.assign(into.submodels, part.submodel); }
}
function rot(box, x, y) { // rotate a box [x1,y1,z1,x2,y2,z2] in pixels: x about the X axis, then y about Y (vanilla's direction)
  let [a, b, c, d, e, f] = box;
  for (let i = 0; i < ((x / 90) % 4 + 4) % 4; i++) { [b, c, e, f] = [16 - f, b, 16 - c, e]; }
  for (let i = 0; i < ((y / 90) % 4 + 4) % 4; i++) { [a, c, d, f] = [16 - f, a, 16 - c, d]; }
  return [Math.min(a, d), Math.min(b, e), Math.min(c, f), Math.max(a, d), Math.max(b, e), Math.max(c, f)];
}
const table = {};
for (const name of BLOCKS) {
  const bs = JSON.parse(fs.readFileSync(path.join(AW, "blockstates/structure", name + ".json"), "utf8"));
  const props = {}; const full = [];
  for (const [k, v] of Object.entries(bs.variants || {})) {
    if (k === "normal" || k === "inventory") continue;
    if (k.includes("=")) { full.push([Object.fromEntries(k.split(",").map(s => s.split("="))), v]); for (const s of k.split(",")) { const [p, val] = s.split("="); (props[p] ??= new Set()).add(val); } }
    else props[k] = new Set(Object.keys(v));
  }
  const names = Object.keys(props).sort();
  const combos = [{}];
  for (const p of names) { const next = []; for (const c of combos) for (const v of props[p]) next.push({ ...c, [p]: v }); combos.splice(0, combos.length, ...next); }
  const multipart = []; const shapes = {};
  for (const c of combos) {
    const st = { model: null, textures: {}, x: 0, y: 0, uvlock: false, submodels: {} };
    merge(st, bs.defaults);
    for (const p of names) if (!full.length || !Object.keys(full[0][0]).includes(p)) merge(st, bs.variants[p]?.[c[p]]);
    for (const [cond, v] of full) if (Object.entries(cond).every(([p, val]) => c[p] === val)) merge(st, v);
    const when = Object.fromEntries(names.map(p => [p, c[p]]));
    const key = names.map(p => p + "=" + c[p]).join(",");
    if (c.visible === "false" || c.invisible === "true") { shapes[key] = null; multipart.push({ when, apply: { model: "ancientempires:block/aw/empty" } }); continue; }
    const parts = [];
    if (st.model) { const ref = modelRef(st.model); copyModel(ref); parts.push({ id: withTextures(ref.id, st.textures), x: st.x, y: st.y }); }
    for (const sub of Object.values(st.submodels)) {
      const ref = modelRef(sub.model); copyModel(ref);
      parts.push({ id: ref.file ? withTextures(ref.id, { ...st.textures, ...(sub.textures || {}) }) : ref.id, x: st.x + (sub.x || 0), y: st.y + (sub.y || 0) });
    }
    const boxes = [];
    for (const part of parts) {
      const apply = { model: part.id }; const x = ((part.x % 360) + 360) % 360, y = ((part.y % 360) + 360) % 360;
      if (x) apply.x = x; if (y) apply.y = y; if (st.uvlock) apply.uvlock = true;
      multipart.push({ when, apply });
      for (const el of elementsOf(part.id)) boxes.push(rot([...el.from, ...el.to], x, y));
    }
    shapes[key] = boxes;
  }
  // Hidden parts collide like the visible part they belong to, a block lower down.
  for (const key of Object.keys(shapes)) {
    if (shapes[key] !== null) continue;
    const shown = key.replace("visible=false", "visible=true").replace("invisible=true", "invisible=false");
    const source = shapes[shown] ?? shapes[shown.replace("part=top", "part=bottom")] ?? [];
    if (name === 'stretching_rack') {
      // The rack lies flat: its hidden parts are the visible one's neighbours toward the front (facing).
      const facing = key.match(/facing=([a-z]+)/)[1]; const [dx, dz] = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[facing];
      shapes[key] = source.map(b => [b[0] + 16 * dx, b[1], b[2] + 16 * dz, b[3] + 16 * dx, b[4], b[5] + 16 * dz]);
      continue;
    }
    shapes[key] = source.map(b => [b[0], b[1] - 16, b[2], b[3], b[4] - 16, b[5]]);
  }
  for (const key of Object.keys(shapes)) {
    shapes[key] = shapes[key].map(b => [Math.max(0, b[0]), Math.max(0, b[1]), Math.max(0, b[2]), Math.min(16, b[3]), Math.min(16, b[4]), Math.min(16, b[5])])
      .filter(b => b[0] < b[3] && b[1] < b[4] && b[2] < b[5]).map(b => b.map(v => Math.round(v * 100) / 100));
  }
  table[name] = { properties: Object.fromEntries(names.map(p => [p, [...props[p]]])), shapes };
  fs.mkdirSync(path.join(OUT, "blockstates"), { recursive: true });
  fs.writeFileSync(path.join(OUT, "blockstates", name + ".json"), JSON.stringify({ multipart }, null, 1) + "\n");
  // The item shows the first combination's look.
  const first = multipart.find(p => p.apply.model !== "ancientempires:block/aw/empty");
  fs.mkdirSync(path.join(OUT, "models/item"), { recursive: true });
  fs.writeFileSync(path.join(OUT, "models/item", name + ".json"), JSON.stringify({ parent: first.apply.model }, null, 2) + "\n");
}
models.set("ancientempires:block/aw/empty", { json: { textures: { particle: "minecraft:block/oak_planks" } } });
for (const [id, m] of models) {
  const file = path.join(OUT, "models", id.replace("ancientempires:", "") + ".json");
  fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, JSON.stringify(m.json, null, 1) + "\n");
}
for (const t of usedTextures) {
  const from = path.join(AW, "textures/blocks", t + ".png"); const to = path.join(OUT, "textures/block/aw", t + ".png");
  if (!fs.existsSync(from)) { problems.push("missing texture file " + t); continue; }
  fs.mkdirSync(path.dirname(to), { recursive: true }); fs.copyFileSync(from, to);
  if (fs.existsSync(from + ".mcmeta")) fs.copyFileSync(from + ".mcmeta", to + ".mcmeta");
}
fs.mkdirSync(path.join(OUT, "aw_decor"), { recursive: true });
fs.writeFileSync(path.join(OUT, "aw_decor/blocks.json"), JSON.stringify(table) + "\n");
console.log("blocks", BLOCKS.length, "models", models.size, "textures", usedTextures.size);
console.log([...new Set(problems)].join("\n"));
for (const [n, t] of Object.entries(table)) console.log(n, JSON.stringify(t.properties), Object.keys(t.shapes).length);
