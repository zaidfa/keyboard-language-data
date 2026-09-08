/*
 * build_layers.js — Layer-aware package generator / converter.
 *
 * Converts flat `layout.json` packages ({rows:[...]}) into the layered schema
 * ({layers:[{id,label,rows:[...]}]}) using the SAME algorithm as the client
 * (com.Kaptaan.khmer.LayerBuilder). This lets the GitHub repo optionally ship
 * pre-split packages. It is OPTIONAL: the app auto-splits flat packages at load,
 * so existing packages keep working unchanged (backward compatible).
 *
 *   node build_layers.js            # dry-run: report what would change
 *   node build_layers.js --write    # rewrite layout.json for tall packages
 *
 * Keep constants in sync with LayerBuilder.java.
 */
const fs = require("fs");
const path = require("path");

const MAX_CONTENT_ROWS = 4;
const MIN_COLS = 10, MAX_COLS = 12;
const CASE_SCRIPTS = new Set(["Latin", "Cyrillic", "Greek", "Armenian"]);
const FUNCTION_TYPES = new Set([
  "shift", "delete", "enter", "space", "symbols", "mode",
  "language", "emoji", "settings", "theme", "layer",
]);
const clamp = (v, lo, hi) => (v < lo ? lo : v > hi ? hi : v);
const write = process.argv.includes("--write");
const langDir = path.join(__dirname, "..", "languages");

function fn(type) { return { type }; }
function funcRow(caseBased, numLayers) {
  const r = [];
  if (caseBased) r.push(fn("shift"));
  if (numLayers > 1) r.push(fn("layer"));
  r.push(fn("symbols"), fn("language"), fn("emoji"), fn("space"),
    { label: ".", code: 46 }, fn("delete"), fn("enter"));
  return { keys: r };
}

/** Returns {layers:[{id,label,rows}]} or null if the board already fits (leave flat). */
function toLayers(rows, script) {
  const caseBased = CASE_SCRIPTS.has(script);
  const fnIdx = rows.findIndex((r) => (r.keys || []).some((k) => k.type === "space"));
  const contentRows = rows.filter((_, i) => i !== fnIdx);
  if (contentRows.length <= MAX_CONTENT_ROWS) return null; // fits -> keep flat

  const chars = [];
  let maxCols = 0;
  rows.forEach((r, i) => {
    if (i === fnIdx) return;
    let c = 0;
    for (const k of r.keys || []) if (!(k.type && FUNCTION_TYPES.has(k.type))) { chars.push(k); c++; }
    maxCols = Math.max(maxCols, c);
  });
  if (!chars.length) return null;

  const cols = clamp(maxCols, MIN_COLS, MAX_COLS);
  const charRows = [];
  for (let i = 0; i < chars.length; i += cols) charRows.push(chars.slice(i, i + cols));

  const numLayers = Math.max(1, Math.ceil(charRows.length / MAX_CONTENT_ROWS));
  const rowsPerLayer = Math.ceil(charRows.length / numLayers);
  const layers = [];
  let idx = 0;
  for (let s = 0; s < charRows.length; s += rowsPerLayer) {
    const board = charRows.slice(s, s + rowsPerLayer).map((r) => ({ keys: r }));
    board.push(funcRow(caseBased, numLayers));
    const label = caseBased
      ? (idx === 0 ? "abc" : "layer " + (idx + 1))
      : (idx === 0 ? "primary" : idx === 1 ? "secondary" : "layer " + (idx + 1));
    layers.push({ id: idx === 0 ? "primary" : "layer" + (idx + 1), label, rows: board });
    idx++;
  }
  return { layers };
}

const dirs = fs.readdirSync(langDir).filter((d) =>
  fs.existsSync(path.join(langDir, d, "layout.json"))
);
let converted = 0, kept = 0;
for (const id of dirs.sort()) {
  const p = path.join(langDir, id, "layout.json");
  let layout, meta = {};
  try { layout = JSON.parse(fs.readFileSync(p, "utf8")); } catch (e) { console.log(`${id}: ERR`); continue; }
  if (layout.layers) { kept++; continue; } // already layered
  try { meta = JSON.parse(fs.readFileSync(path.join(langDir, id, "metadata.json"), "utf8")); } catch (e) {}
  const res = toLayers(layout.rows || [], meta.script || layout.script);
  if (!res) { kept++; continue; }
  converted++;
  console.log(`${id} (${meta.script || "?"}): -> ${res.layers.length} layers`);
  if (write) {
    const out = { id: layout.id || id, type: layout.type || "main", layers: res.layers };
    fs.writeFileSync(p, JSON.stringify(out, null, 2) + "\n", "utf8");
  }
}
console.log(`\n${write ? "Wrote" : "Would convert"}: ${converted}   left flat: ${kept}`);
if (!write) console.log("Dry-run. Re-run with --write to apply. (Client auto-splits flat packages regardless.)");
