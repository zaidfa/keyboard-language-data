# Compact multi-layer keyboard format

Scripts with many characters (Devanagari, Arabic, Cyrillic kitchen-sinks, Thai,
Bengali, …) used to render every letter in one very tall board (up to 9 rows).
The keyboard now splits those characters across **layers** the user switches
between, so the physical keyboard is never taller than **5 rows** and keys stay
comfortably sized. **No character is removed** — extra characters move to a
secondary layer, not off the keyboard.

## Two package formats (both supported)

### Flat (original) — auto-split by the app
```json
{ "id": "hi", "type": "main", "rows": [ { "keys": [ ... ] }, ... ] }
```
The client (`LayerBuilder`) splits this into compact layers at load time.
Existing downloaded packages keep working with **no re-download**.

### Layered (optional, forward-compatible)
```json
{
  "id": "hi",
  "type": "main",
  "layers": [
    { "id": "primary",   "label": "primary",   "rows": [ { "keys": [ ... ] } ] },
    { "id": "layer2",    "label": "secondary", "rows": [ { "keys": [ ... ] } ] }
  ]
}
```
Each layer's `rows` are used verbatim. Generate these with
`tools/build_layers.js --write`.

## Layer switching

* **Case scripts** (Latin, Cyrillic, Greek, Armenian): a **shift** key toggles
  lower/upper case within each layer. Tall case scripts also get a layer key.
* **Caseless scripts** (Arabic, Indic, Thai, Hangul, Japanese kana, …): a
  **layer** key (shown as `1/2`) cycles primary → secondary. No fake
  uppercase/lowercase is forced on them.
* Delete, space and enter live on every layer's function row, so editing works
  no matter which layer is active. Layer switching is instant and fully offline.

## Height policy

`MAX_CONTENT_ROWS = 4` character rows + 1 function row = **5 rows max**. Keep
this value in sync between `app/.../LayerBuilder.java`, `tools/build_layers.js`
and `tools/validate_layers.js`.

## Tools

| Tool | Purpose |
|------|---------|
| `tools/validate_layers.js` | Runs the split over every package; reports rows/layers/status; fails if any board exceeds the height policy or drops a character. |
| `tools/build_layers.js`    | Converts flat packages to the layered schema (`--write`). Optional — the client auto-splits regardless. |
