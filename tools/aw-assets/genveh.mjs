// Turns AW's decompiled vehicle models (LegacyModelRenderer calls) into JSON part trees, and copies their textures.
import fs from "fs"; import path from "path";
const [SRC, AWTEX, REPO] = process.argv.slice(2);
const OUT = path.join(REPO, "src/main/resources/assets/ancientempires");
// vehType -> [model class, texture base, scale] (AW's VehicleRegistry, VehicleType textures, RenderTrebuchetLarge's 2.5).
const TYPES = {
  0: ["CatapultStandFixed", "catapult_stand_fixed", 1], 1: ["CatapultStandTurret", "catapult_stand_turret", 1],
  2: ["CatapultMobileFixed", "catapult_mobile_fixed", 1], 3: ["CatapultMobileTurret", "catapult_mobile_turret", 1],
  4: ["BallistaStand", "ballista_stand", 1], 5: ["BallistaStand", "ballista_stand", 1], 6: ["BallistaMobile", "ballista_mobile", 1],
  7: ["BallistaMobile", "ballista_mobile", 1], 8: ["BatteringRam", "battering_ram", 1], 9: ["CannonStandFixed", "cannon", 1],
  10: ["CannonStandTurret", "cannon", 1], 11: ["CannonMobileFixed", "cannon", 1], 12: ["Hwacha", "hwacha", 1],
  13: ["TrebuchetStandFixed", "trebuchet_mobile", 1], 14: ["TrebuchetStandTurret", "trebuchet_mobile", 1], 15: ["TrebuchetMobileFixed", "trebuchet_mobile", 1],
  16: ["TrebuchetStandFixed", "trebuchet", 2.5], 17: ["ChestCart", "chest_cart", 1], 18: ["BoatBallista", "boat_ballista", 1],
  19: ["BoatCatapult", "boat_catapult", 1], 20: ["BoatTransport", "boat_transport", 1] };
function num(expr, vars = {}) {
  const js = expr.replace(/\(float\)|\(double\)|\(int\)/g, "").replace(/(\d(?:\.\d*)?(?:E-?\d+)?)[FfDdL]\b/g, "$1")
    .replace(/Trig\.toRadians\(/g, "TR(").replace(/Trig\.toDegrees\(/g, "TD(").replace(/Mth\.m_14031_\(/g, "Math.sin(").replace(/Mth\.m_14089_\(/g, "Math.cos(");
  const f = new Function("TR", "TD", ...Object.keys(vars), "return (" + js + ");");
  return f(d => d * Math.PI / 180, r => r * 180 / Math.PI, ...Object.values(vars));
}
function args(s) { const out = []; let depth = 0, cur = ""; for (const c of s) { if (c === "(") depth++; if (c === ")") depth--; if (c === "," && depth === 0) { out.push(cur.trim()); cur = ""; } else cur += c; } if (cur.trim()) out.push(cur.trim()); return out; }
function body(src, signature) {
  const i = src.indexOf(signature); if (i < 0) return "";
  let j = src.indexOf("{", i), depth = 0, k = j;
  for (; k < src.length; k++) { if (src[k] === "{") depth++; if (src[k] === "}" && --depth === 0) break; }
  return src.slice(j + 1, k);
}
const models = {}; const problems = [];
for (const cls of new Set(Object.values(TYPES).map(t => t[0]))) {
  const src = fs.readFileSync(path.join(SRC, "Model" + cls + ".java"), "utf8");
  const parts = {}; let texW = 64, texH = 32;
  const part = name => parts[name] ??= { pivot: [0, 0, 0], rot: [0, 0, 0], cubes: [], children: [], u: 0, v: 0, mirror: false, child: false };
  for (const m of src.matchAll(/LegacyModelRenderer (\w+) = new LegacyModelRenderer\(this, "?(\w*)"?\)/g)) part(m[1]);
  const ctor = body(src, "public Model" + cls + "()");
  for (let st of ctor.split(";")) {
    st = st.trim().replace(/\s+/g, " "); let m;
    if ((m = st.match(/^(?:this\.)?(\w+) = new LegacyModelRenderer\(this(?:, (.*))?\)$/))) { const p = part(m[1]); if (m[2] && !m[2].startsWith('"')) { const a = args(m[2]); p.u = num(a[0]); p.v = num(a[1]); } }
    else if ((m = st.match(/^(?:this\.)?(\w+)\.setTextureOffset\((.*)\)$/))) { const a = args(m[2]); Object.assign(part(m[1]), { u: num(a[0]), v: num(a[1]) }); }
    else if ((m = st.match(/^(?:this\.)?(\w+)\.setTextureSize\((.*)\)$/))) { const a = args(m[2]); texW = num(a[0]); texH = num(a[1]); }
    else if ((m = st.match(/^(?:this\.)?(\w+)\.setRotationPoint\((.*)\)$/))) part(m[1]).pivot = args(m[2]).map(x => num(x));
    else if ((m = st.match(/^this\.(?:setPieceRotation|setRotation|setRotateAngle)\((?:this\.)?(\w+), (.*)\)$/))) part(m[1]).rot = args(m[2]).map(x => num(x));
    else if ((m = st.match(/^(?:this\.)?(\w+)\.addBox\((.*)\)$/))) { const a = args(m[2]).map(x => num(x)); const p = part(m[1]); p.cubes.push({ u: p.u, v: p.v, box: a.slice(0, 6), inflate: a[6] || 0, mirror: p.mirror }); }
    else if ((m = st.match(/^(?:this\.)?(\w+)\.addChild\((?:this\.)?(\w+)\)$/))) { part(m[1]).children.push(m[2]); part(m[2]).child = true; }
    else if ((m = st.match(/^(?:this\.)?(\w+)\.mirror = (true|false)$/))) part(m[1]).mirror = m[2] === "true";
    else if ((m = st.match(/^(?:this\.)?(\w+)\.rotateAngle([XYZ]) = (.*)$/))) part(m[1]).rot["XYZ".indexOf(m[2])] = num(m[3]);
    else if ((m = st.match(/^this\.texture(Width|Height) = (\d+)$/))) { if (m[1] === "Width") texW = +m[2]; else texH = +m[2]; }
    else if ((m = st.match(/^this\.(\w+)\((.*)\)$/)) && src.includes("public void " + m[1] + "(")) {
      // A pose setter the constructor calls (trebuchet arms, cart hitch): its rotations with the arguments given.
      const sig = src.match(new RegExp("public void " + m[1] + "\\(([^)]*)\\)"))[1];
      const names = sig.split(",").map(p => p.trim().split(/\s+/).pop()).filter(Boolean);
      const vars = Object.fromEntries(names.map((n, i) => [n, num(args(m[2])[i])]));
      for (let inner of body(src, "public void " + m[1] + "(").split(";")) {
        const r = inner.trim().replace(/\s+/g, " ").match(/^this\.(\w+)\.rotateAngle([XYZ]) = (.*)$/);
        if (r) part(r[1]).rot["XYZ".indexOf(r[2])] = num(r[3], vars);
      }
    }
    else if (st && !st.startsWith("super(")) problems.push(cls + ": " + st.slice(0, 90));
  }
  const render = body(src, "public void render(Entity");
  const roots = [...render.matchAll(/this\.(\w+)\.render\(/g)].map(m => m[1]).filter(n => parts[n]);
  // The turret: what setTurretRotation(yaw, pitch) sets, as constant + per-degree terms.
  const turret = [];
  for (let st of body(src, "public void setTurretRotation(").split(";")) {
    const m = st.trim().replace(/\s+/g, " ").match(/^this\.(\w+)\.rotateAngle([XYZ]) = (.*)$/); if (!m) continue;
    const c = num(m[3], { yaw: 0, pitch: 0 }); const dy = num(m[3], { yaw: 1, pitch: 0 }) - c; const dp = num(m[3], { yaw: 0, pitch: 1 }) - c;
    turret.push({ part: m[1], axis: "XYZ".indexOf(m[2]), constant: c, yaw: dy, pitch: dp });
  }
  for (const p of Object.values(parts)) { delete p.u; delete p.v; delete p.mirror; }
  models[cls] = { texW, texH, roots: roots.length ? roots : Object.keys(parts).filter(n => !parts[n].child), parts, turret };
}
// A rough footprint per model (rotations ignored), in blocks at scale 1: half width and height.
for (const m of Object.values(models)) {
  let r = 0, lo = 0, hi = 0;
  const walk = (name, ox, oy, oz) => { const p = m.parts[name]; const x = ox + p.pivot[0], y = oy + p.pivot[1], z = oz + p.pivot[2];
    for (const c of p.cubes) { const b = c.box; r = Math.max(r, Math.abs(x + b[0]), Math.abs(x + b[0] + b[3]), Math.abs(z + b[2]), Math.abs(z + b[2] + b[5])); lo = Math.min(lo, y + b[1]); hi = Math.max(hi, y + b[1] + b[4]); }
    for (const c of p.children) walk(c, x, y, z); };
  for (const root of m.roots) walk(root, 0, 0, 0);
  m.size = [Math.round(Math.min(r, 48) * 2 / 16 * 100) / 100, Math.round(Math.min(hi - lo, 96) / 16 * 100) / 100];
}
fs.mkdirSync(path.join(OUT, "aw_vehicles"), { recursive: true });
fs.writeFileSync(path.join(OUT, "aw_vehicles/models.json"), JSON.stringify({ types: TYPES, models }) + "\n");
const texOut = path.join(OUT, "textures/entity/aw/vehicle"); fs.mkdirSync(texOut, { recursive: true }); let copied = 0;
for (const base of new Set(Object.values(TYPES).map(t => t[1]))) for (let i = 1; i <= 5; i++) {
  const from = path.join(AWTEX, base + "_" + i + ".png"); if (fs.existsSync(from)) { fs.copyFileSync(from, path.join(texOut, base + "_" + i + ".png")); copied++; } else problems.push("no texture " + base + "_" + i);
}
console.log(Object.entries(models).map(([k, v]) => `${k}: ${Object.keys(v.parts).length} parts, roots ${v.roots.length}, tex ${v.texW}x${v.texH}, turret ${v.turret.length}`).join("\n"));
console.log("textures", copied); console.log([...new Set(problems)].slice(0, 30).join("\n"));
