/*
 * repackage.js — Rebuild language.zip + refresh manifest/catalog hashes for any
 * package whose layout.json changed (e.g. after build_layers.js --write).
 *
 * The app downloads language.zip and verifies sha256 + sizeBytes from the
 * manifest and catalog, so the zip and hashes must be regenerated when a
 * layout changes. Only packages that now use the layered schema are rebuilt;
 * flat packages keep their existing (still-valid) zip and hash.
 *
 *   node repackage.js
 *
 * Uses the JDK `jar` tool (zip-format writer) — no external zip binary needed.
 */
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const { execSync } = require("child_process");

const langDir = path.join(__dirname, "..", "languages");
const catalogPath = path.join(__dirname, "..", "catalog", "languages.json");
const ZIP_ENTRIES = ["metadata.json", "layout.json", "symbols.json"];

const sha256 = (buf) => crypto.createHash("sha256").update(buf).digest("hex");

const dirs = fs.readdirSync(langDir).filter((d) =>
  fs.existsSync(path.join(langDir, d, "layout.json"))
);

const updates = {}; // id -> {sha256, sizeBytes, version}
let rebuilt = 0;

for (const id of dirs.sort()) {
  const p = path.join(langDir, id);
  let layout;
  try { layout = JSON.parse(fs.readFileSync(path.join(p, "layout.json"), "utf8")); } catch (e) { continue; }
  if (!Array.isArray(layout.layers) || !layout.layers.length) continue; // only rebuilt layered packages

  const entries = ZIP_ENTRIES.filter((f) => fs.existsSync(path.join(p, f)));
  // Rebuild the zip deterministically (no jar manifest) from inside the dir.
  execSync(`jar cfM language.zip ${entries.join(" ")}`, { cwd: p });

  const zipBuf = fs.readFileSync(path.join(p, "language.zip"));
  const hash = sha256(zipBuf);
  const size = zipBuf.length;

  // Update manifest.json (bump version so existing installs refresh).
  const manPath = path.join(p, "manifest.json");
  let man = {};
  try { man = JSON.parse(fs.readFileSync(manPath, "utf8")); } catch (e) {}
  const version = (man.version || 1) + 1;
  man.package = "language.zip";
  man.sizeBytes = size;
  man.sha256 = hash;
  man.version = version;
  fs.writeFileSync(manPath, JSON.stringify(man, null, 2) + "\n", "utf8");

  updates[id] = { sha256: hash, sizeBytes: size, version };
  rebuilt++;
  console.log(`${id}: zip ${size}B  sha256 ${hash.slice(0, 12)}…  v${version}`);
}

// Patch the catalog entries in one pass.
let catalog = JSON.parse(fs.readFileSync(catalogPath, "utf8"));
const arr = Array.isArray(catalog) ? catalog : catalog.languages;
let patched = 0;
for (const entry of arr) {
  const u = updates[entry.id];
  if (!u) continue;
  entry.sizeBytes = u.sizeBytes;
  entry.sha256 = u.sha256;
  entry.version = u.version;
  patched++;
}
fs.writeFileSync(catalogPath, JSON.stringify(catalog, null, 2) + "\n", "utf8");

console.log(`\nRebuilt ${rebuilt} zips; patched ${patched} catalog entries.`);
