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
  planks_big_oak: "dark_oak_planks", wool_colored_red: "red_wool", stone: "stone", gold_block: "gold_block", iron_bars: "iron_bars",
  planks_mangrove: "mangrove_planks", planks_cherry: "cherry_planks", planks_bamboo: "bamboo_planks", planks_crimson: "crimson_planks", planks_warped: "warped_planks" };
/** Tall pieces whose upper part is a model of its own: how many pixels up the visible top sits (AwDecorBlock.LAYOUTS). */
const TOPS = { stake: 16, wooden_post: 32, iron_cage: 32, gibbet: 32, golden_throne: 16 };
/** Woods 1.20 added, offered wherever AW offered its six. */
const NEW_WOODS = ["mangrove", "cherry", "bamboo", "crimson", "warped"];
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
const OPPOSITE = { north: "south", south: "north", east: "west", west: "east", up: "down", down: "up" };
/**
 * AW's Blockbench exports left faces out, which showed as see-through holes (the stake's post
 * had no south side). A missing face takes its opposite's texture; one missing on both sides
 * (a flat plane's edges) stays missing.
 */
function fillFaces(elements) {
  for (const el of elements) {
    if (!el.faces) continue;
    for (const [face, other] of Object.entries(OPPOSITE)) {
      if (el.faces[face] || !el.faces[other]) continue;
      const copy = { ...el.faces[other] }; delete copy.cullface; el.faces[face] = copy;
    }
  }
}
const TURN = { north: "east", east: "south", south: "west", west: "north" };
/** One element turned a quarter clockwise seen from above, as a blockstate's y: 90 turns it. */
function turnElement(el) {
  const p = ([x, y, z]) => [16 - z, y, x];
  const a = p(el.from), b = p(el.to);
  el.from = [Math.min(a[0], b[0]), a[1], Math.min(a[2], b[2])]; el.to = [Math.max(a[0], b[0]), b[1], Math.max(a[2], b[2])];
  if (el.rotation) {
    el.rotation.origin = p(el.rotation.origin);
    if (el.rotation.axis === "x") el.rotation.axis = "z";
    else if (el.rotation.axis === "z") { el.rotation.axis = "x"; el.rotation.angle = -el.rotation.angle; }
  }
  const faces = {};
  for (const [face, f] of Object.entries(el.faces || {})) {
    const g = { ...f }; if (g.cullface) g.cullface = TURN[g.cullface] ?? g.cullface;
    if (face === "up") g.rotation = ((g.rotation ?? 0) + 90) % 360;
    if (face === "down") g.rotation = ((g.rotation ?? 0) + 270) % 360;
    if (!g.rotation) delete g.rotation;
    faces[TURN[face] ?? face] = g;
  }
  el.faces = faces;
}
function copyModel(ref) {
  if (!ref.file || models.has(ref.id)) return;
  const json = JSON.parse(fs.readFileSync(ref.file, "utf8"));
  delete json.credit; delete json.__comment;
  if (json.elements) for (const el of json.elements) for (const [k, f] of Object.entries(el.faces || {}))
    if (f.texture === "null" || f.texture === "#missing" && !json.textures?.missing) delete el.faces[k];
  if (json.elements) fillFaces(json.elements);
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
/** A model's textures, through its parents. */
function texturesOf(id) {
  const m = models.get(id); if (!m) return {};
  return { ...(m.json.parent ? texturesOf(m.json.parent) : {}), ...(m.json.textures || {}) };
}
function resolveTexture(textures, ref, depth = 0) {
  if (!ref || !ref.startsWith("#") || depth > 8) return ref;
  return resolveTexture(textures, textures[ref.slice(1)], depth + 1);
}
/**
 * One item model from a piece's parts: their elements merged, each part turned as its blockstate
 * turns it and the whole by `turn` more, so the piece's front faces where a vanilla block item
 * shows its front (north); parts tipped over on x are left out. Textures are resolved per part,
 * and the display is a vanilla block's, scaled to fit the piece's size.
 */
function itemModel(parts, turn = 0) {
  const elements = []; const textures = {}; let index = 0;
  for (const part of parts) {
    if (((part.x % 360) + 360) % 360) continue;
    const quarters = ((((part.y + turn) % 360) + 360) % 360) / 90;
    const own = texturesOf(part.id);
    for (const el of elementsOf(part.id)) {
      const copy = JSON.parse(JSON.stringify(el));
      for (let i = 0; i < quarters; i++) turnElement(copy);
      if (part.up) { copy.from[1] += part.up; copy.to[1] += part.up; if (copy.rotation) copy.rotation.origin[1] += part.up; }
      for (const face of Object.values(copy.faces || {})) {
        const real = resolveTexture(own, face.texture);
        if (!real) continue;
        const keyName = "t" + index + "_" + face.texture.replace("#", "");
        textures[keyName] = real; face.texture = "#" + keyName;
      }
      elements.push(copy);
    }
    if (!textures.particle && own.particle) textures.particle = resolveTexture(own, own.particle);
    index++;
  }
  if (!textures.particle) textures.particle = Object.values(textures)[0] ?? "minecraft:block/oak_planks";
  let lo = [16, 16, 16], hi = [0, 0, 0];
  for (const el of elements) for (let i = 0; i < 3; i++) { lo[i] = Math.min(lo[i], el.from[i], el.to[i]); hi[i] = Math.max(hi[i], el.from[i], el.to[i]); }
  const span = Math.max(16, hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]); const k = 16 / span;
  const round = v => Math.round(v * 10000) / 10000;
  // Shrunk into one block's space in the model itself: vanilla refuses elements past -16 to 32 (a stacked stake reaches 48).
  const mid = [(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2];
  const fit = p => p.map((v, i) => round((v - mid[i]) * k + 8));
  for (const el of elements) { el.from = fit(el.from); el.to = fit(el.to); if (el.rotation) el.rotation.origin = fit(el.rotation.origin); }
  const view = (rotation, scale, lift = 0) => ({ rotation, translation: [0, lift, 0], scale: [scale, scale, scale] });
  return { parent: "minecraft:block/block", textures, elements, display: {
    gui: view([30, 225, 0], 0.625), ground: view([0, 0, 0], 0.25, 3), fixed: view([0, 0, 0], 0.5),
    thirdperson_righthand: view([75, 45, 0], 0.375, 2.5), thirdperson_lefthand: view([75, 45, 0], 0.375, 2.5),
    firstperson_righthand: view([0, 45, 0], 0.4), firstperson_lefthand: view([0, 225, 0], 0.4) } };
}
const table = {};
for (const name of BLOCKS) {
  const bs = JSON.parse(fs.readFileSync(path.join(AW, "blockstates/structure", name + ".json"), "utf8"));
  const woods = bs.variants?.variant;
  if (woods && "oak" in woods && "dark_oak" in woods) {
    for (const wood of NEW_WOODS) woods[wood] = { textures: { "0": "minecraft:blocks/planks_" + wood, particle: "minecraft:blocks/planks_" + wood } };
  }
  const props = {}; const full = [];
  for (const [k, v] of Object.entries(bs.variants || {})) {
    if (k === "normal" || k === "inventory") continue;
    if (k.includes("=")) { full.push([Object.fromEntries(k.split(",").map(s => s.split("="))), v]); for (const s of k.split(",")) { const [p, val] = s.split("="); (props[p] ??= new Set()).add(val); } }
    else props[k] = new Set(Object.keys(v));
  }
  const names = Object.keys(props).sort();
  const combos = [{}];
  for (const p of names) { const next = []; for (const c of combos) for (const v of props[p]) next.push({ ...c, [p]: v }); combos.splice(0, combos.length, ...next); }
  const multipart = []; const shapes = {}; const partsByKey = {};
  for (const c of combos) {
    const st = { model: null, textures: {}, x: 0, y: 0, uvlock: false, submodels: {} };
    merge(st, bs.defaults);
    for (const p of names) if (!full.length || !Object.keys(full[0][0]).includes(p)) merge(st, bs.variants[p]?.[c[p]]);
    for (const [cond, v] of full) if (Object.entries(cond).every(([p, val]) => c[p] === val)) merge(st, v);
    const when = Object.fromEntries(names.map(p => [p, c[p]]));
    const key = names.map(p => p + "=" + c[p]).join(",");
    if (c.visible === "false" || c.invisible === "true" || c.bottom_part === "false") { shapes[key] = null; multipart.push({ when, apply: { model: "ancientempires:block/aw/empty" } }); continue; }
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
      multipart.push(names.length ? { when, apply } : { apply });
      for (const el of elementsOf(part.id)) boxes.push(rot([...el.from, ...el.to], x, y));
    }
    shapes[key] = boxes;
    partsByKey[key] = parts;
  }
  // Hidden parts collide like the visible part they belong to, a block lower down.
  for (const key of Object.keys(shapes)) {
    if (shapes[key] !== null) continue;
    const shown = key.replace("visible=false", "visible=true").replace("invisible=true", "invisible=false").replace("bottom_part=false", "bottom_part=true");
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
  // Items: the piece as a block would show it (diagonal, scaled to fit), one model per wood or stone variant.
  const key = c => names.map(p => p + "=" + c[p]).join(",");
  const pick = (variant) => combos.filter(c => (c.visible ?? "true") === "true" && (c.invisible ?? "false") === "false"
      && (c.bottom_part ?? "true") === "true" && (!props.part || c.part === (props.part.has("bottom") ? "bottom" : "south"))
      && (variant === undefined || c.variant === variant)).sort((a, b) => ((a.facing ?? "north") === "north" ? -1 : 0) - ((b.facing ?? "north") === "north" ? -1 : 0))[0] ?? combos[0];
  // A piece faces its facing (toward whoever placed it); turn that to the north, the side a block item shows.
  const turnOf = c => ({ north: 0, east: 270, south: 180, west: 90 })[c.facing] ?? 0;
  fs.mkdirSync(path.join(OUT, "models/item/aw"), { recursive: true });
  const variants = props.variant ? [...props.variant] : [undefined];
  // A piece built on a vanilla shape (a totem on block/cube) has no elements of its own: its block model already has the block view.
  const itemModels = variants.map(v => { const parts = [...(partsByKey[key(pick(v))] ?? [])]; const c = pick(v);
    if (TOPS[name]) {
      const top = combos.find(t => names.every(p => p === "part" ? t.part === "top" : p === "visible" ? t.visible === "true" : t[p] === c[p]));
      if (top) parts.push(...(partsByKey[key(top)] ?? []).map(p => ({ ...p, up: TOPS[name] })));
    }
    const m = itemModel(parts, turnOf(c)); return m.elements.length || !parts.length ? m : { parent: parts[0].id }; });
  const base = { ...itemModels[0] };
  if (variants.length > 1) {
    base.overrides = variants.slice(1).map((v, i) => {
      const id = "ancientempires:item/aw/" + name + "_" + v;
      fs.writeFileSync(path.join(OUT, "models/item/aw", name + "_" + v + ".json"), JSON.stringify(itemModels[i + 1], null, 1) + "\n");
      return { predicate: { "ancientempires:variant": i + 1 }, model: id };
    });
  }
  fs.mkdirSync(path.join(OUT, "models/item"), { recursive: true });
  fs.writeFileSync(path.join(OUT, "models/item", name + ".json"), JSON.stringify(base, null, 1) + "\n");
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
