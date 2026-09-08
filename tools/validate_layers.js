/*
 * validate_layers.js — Automated language-layout validator / report generator.
 *
 * Mirrors the client-side com.Kaptaan.khmer.LayerBuilder algorithm and runs it
 * over EVERY package in ../languages so we can prove the max-height policy holds
 * without a device. Reports rows/layers/status and flags data problems
 * (invalid JSON, dropped characters, duplicate codes).
 *
 * Run:  node validate_layers.js
 * Keep MAX_CONTENT_ROWS / CASE_SCRIPTS in sync with LayerBuilder.java.
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

const langDir = path.join(__dirname, "..", "languages");
const clamp = (v, lo, hi) => (v < lo ? lo : v > hi ? hi : v);

function build(rows, script) {
  const caseBased = CASE_SCRIPTS.has(script);
  if (!rows || rows.length === 0) return { layers: [], caseBased };

  let fnIdx = rows.findIndex((r) => (r.keys || []).some((k) => k.type === "space"));
  let contentRowCount = rows.filter((_, i) => i !== fnIdx).length;

  // Fast path: already compact -> keep original rows verbatim.
  if (contentRowCount <= MAX_CONTENT_ROWS) {
    return { layers: [rows.map((r) => r.keys || [])], caseBased, passthrough: true };
  }

  // Split path.
  const chars = [];
  let maxCols = 0;
  rows.forEach((r, i) => {
    if (i === fnIdx) return;
    let c = 0;
    for (const k of r.keys || []) {
      if (!(k.type && FUNCTION_TYPES.has(k.type))) { chars.push(k); c++; }
    }
    maxCols = Math.max(maxCols, c);
  });
  if (chars.length === 0) return { layers: [rows.map((r) => r.keys || [])], caseBased, passthrough: true };

  const cols = clamp(maxCols, MIN_COLS, MAX_COLS);
  const charRows = [];
  for (let i = 0; i < chars.length; i += cols) charRows.push(chars.slice(i, i + cols));

  const numLayers = Math.max(1, Math.ceil(charRows.length / MAX_CONTENT_ROWS));
  const rowsPerLayer = Math.ceil(charRows.length / numLayers);
  const layers = [];
  for (let start = 0; start < charRows.length; start += rowsPerLayer) {
    const board = charRows.slice(start, start + rowsPerLayer).map((r) => r.slice());
    board.push([{ type: "func" }, { code: 46 }]); // stand-in function row (1 row) incl. period
    layers.push(board);
  }
  return { layers, caseBased, numLayers, cols };
}

function charCodes(rows) {
  const out = [];
  for (const r of rows) for (const k of r.keys || []) if (!k.type) out.push(k.code);
  return out;
}

const dirs = fs.readdirSync(langDir).filter((d) =>
  fs.existsSync(path.join(langDir, d, "layout.json"))
);

let pass = 0, fail = 0, warn = 0;
const rowsReport = [];
let maxRowsAcross = 0;

for (const id of dirs.sort()) {
  const p = path.join(langDir, id);
  let meta = {}, layout;
  try { meta = JSON.parse(fs.readFileSync(path.join(p, "metadata.json"), "utf8")); } catch (e) {}
  try { layout = JSON.parse(fs.readFileSync(path.join(p, "layout.json"), "utf8")); }
  catch (e) { rowsReport.push([id, meta.script || "?", "ERR", "-", "invalid JSON"]); fail++; continue; }

  const script = meta.script || layout.script || null;

  // Already-layered package: measure its authored layers verbatim.
  if (Array.isArray(layout.layers) && layout.layers.length) {
    const boards = layout.layers.map((l) => (l.rows || []).map((r) => r.keys || []));
    const maxVisible = Math.max(...boards.map((b) => b.length), 0);
    maxRowsAcross = Math.max(maxRowsAcross, maxVisible);
    const ok = maxVisible <= MAX_CONTENT_ROWS + 1;
    if (ok) pass++; else fail++;
    rowsReport.push([id, script || "?", `layered->${maxVisible}`,
      `${layout.layers.length}${CASE_SCRIPTS.has(script) ? " (case)" : ""}`,
      ok ? "PASS" : "FAIL too tall"]);
    continue;
  }

  const origRows = (layout.rows || []).length;
  const res = build(layout.rows || [], script);

  // Max visible rows over all layers (each layer board already includes its func row).
  const maxVisible = Math.max(...res.layers.map((l) => l.length), 0);
  maxRowsAcross = Math.max(maxRowsAcross, maxVisible);

  // Character preservation check: every input character must survive (subset).
  const inCodes = charCodes(layout.rows || []);
  const outSet = new Set();
  for (const l of res.layers) for (const r of l) for (const k of r) if (!k.type) outSet.add(k.code);
  const missing = res.passthrough ? [] : inCodes.filter((c) => !outSet.has(c));
  const preserved = missing.length === 0;

  const notes = [];
  if (!preserved) notes.push(`dropped ${missing.length} chars`);
  const seen = new Set(), dup = new Set();
  for (const c of inCodes) { if (seen.has(c)) dup.add(c); seen.add(c); }
  if (dup.size) { notes.push(`${dup.size} dup codes`); warn++; }

  let status = "PASS";
  if (!preserved || maxVisible > MAX_CONTENT_ROWS + 1) { status = "FAIL"; fail++; }
  else pass++;

  rowsReport.push([
    id, script || "?", `${origRows}->${maxVisible}`,
    `${res.layers.length}${res.caseBased ? " (case)" : ""}`,
    status + (notes.length ? " " + notes.join("; ") : ""),
  ]);
}

// ---- print report ----
const pad = (s, n) => String(s).padEnd(n);
console.log("LANGUAGE LAYOUT REPORT  (policy: max " + (MAX_CONTENT_ROWS + 1) + " visible rows)\n");
console.log(pad("id", 16) + pad("script", 20) + pad("rows(old->new)", 16) + pad("layers", 12) + "status");
console.log("-".repeat(90));
for (const r of rowsReport) {
  console.log(pad(r[0], 16) + pad(r[1], 20) + pad(r[2], 16) + pad(r[3], 12) + r[4]);
}
console.log("-".repeat(90));
console.log(`Total: ${dirs.length}   PASS: ${pass}   FAIL: ${fail}   (dup-code warnings: ${warn})`);
console.log(`Tallest keyboard after split: ${maxRowsAcross} rows (was up to 9).`);
process.exit(fail ? 1 : 0);
