import fs from "fs"; import p from "path";
process.chdir(process.argv[2]);
const out = fs.existsSync("sounds.json") ? JSON.parse(fs.readFileSync("sounds.json", "utf8")) : {}; let n = 0;
(function walk(d) {
  for (const f of fs.readdirSync(d)) {
    const q = p.join(d, f);
    if (fs.statSync(q).isDirectory()) walk(q);
    else if (f.endsWith(".ogg")) {
      const k = p.relative("sounds", q).split(p.sep).join("/").replace(/\.ogg$/, "");
      if (!out[k]) { out[k] = { category: "ambient", sounds: [{ name: "ancientempires:" + k, stream: fs.statSync(q).size > 200000 }] }; n++; }
    }
  }
})("sounds");
fs.writeFileSync("sounds.json", JSON.stringify(out, null, 1) + "\n");
console.log("added", n, "total", Object.keys(out).length);
