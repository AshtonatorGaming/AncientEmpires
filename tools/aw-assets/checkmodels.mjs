import fs from "fs"; import path from "path";
const A = "src/main/resources/assets/ancientempires";
const bad = [];
function exists(id, kind) {
  const [ns, p] = id.includes(":") ? id.split(":") : ["minecraft", id];
  if (ns !== "ancientempires") return true;
  return fs.existsSync(path.join(A, kind, p + (kind === "textures" ? ".png" : ".json")));
}
function walk(d, f) { for (const x of fs.readdirSync(d)) { const p = path.join(d, x); fs.statSync(p).isDirectory() ? walk(p, f) : f(p); } }
const decor = Object.keys(JSON.parse(fs.readFileSync(path.join(A, "aw_decor/blocks.json"))));
for (const n of decor) {
  const bs = JSON.parse(fs.readFileSync(path.join(A, "blockstates", n + ".json")));
  for (const part of bs.multipart) if (!exists(part.apply.model, "models")) bad.push(n + " -> " + part.apply.model);
}
walk(path.join(A, "models/block/aw"), file => {
  const m = JSON.parse(fs.readFileSync(file));
  if (m.parent && !exists(m.parent, "models")) bad.push(file + " parent " + m.parent);
  for (const t of Object.values(m.textures || {})) if (!t.startsWith("#") && !exists(t, "textures")) bad.push(file + " texture " + t);
  for (const el of m.elements || []) { const r = el.rotation; if (r && ![0, 22.5, -22.5, 45, -45].includes(r.angle)) bad.push(file + " angle " + r.angle); }
});
console.log(bad.length ? bad.join("\n") : "all references resolve");
