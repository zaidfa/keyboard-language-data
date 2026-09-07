# Packaging tools

These tools build language packages and the master catalog. They never invent
keyboard data — you supply real runtime files, they zip + checksum + index them.

## Files

| File | Purpose |
|------|---------|
| `languages_seed.json` | Master list of every catalog language (id, English name, native name, locale, script). Add a row here to make a language appear in the catalog as `coming_soon`. |
| `package_language.py` | Zip one language's runtime files into `language.zip`, compute real SHA-256 + size, write `manifest.json`. |
| `generate_catalog.py` | Regenerate `catalog/languages.json` from the seed + the real manifests. |
| `BuildRepo.java` | One-off bootstrap used to export the app's built-in English/Vietnamese layouts from Android XML into the JSON package format. You normally won't need it again. |

## Requirements

Python 3.8+ (standard library only — no pip installs). `BuildRepo.java` needs JDK 17+.

## Add a new language

1. Create the runtime files under `languages/<id>/`:
   - `metadata.json` — `{ "id", "name", "nativeName", "locale", "script", "version", "layouts": ["layout.json","symbols.json"] }`
   - `layout.json` — main letters (see format below)
   - `symbols.json` — symbols page
2. Make sure `<id>` also exists in `languages_seed.json` (add a row if not).
3. Package it and regenerate the catalog:
   ```bash
   python tools/package_language.py <id>
   python tools/generate_catalog.py
   ```
4. Commit the new `languages/<id>/` folder and the updated `catalog/languages.json`.

## Update a language

1. Edit the runtime files under `languages/<id>/`.
2. Bump `"version"` in `metadata.json` (e.g. 1 → 2).
3. Re-run the two commands above. The new size/sha256 are written automatically.
   Installed apps compare versions and offer an update only to users who have it.

## Layout JSON format

```json
{
  "id": "en-US",
  "type": "main",
  "rows": [
    { "keys": [ {"label": "q", "code": 113}, {"label": "w", "code": 119} ] },
    { "keys": [ {"type": "shift"}, {"label": "z", "code": 122}, {"type": "delete"} ] },
    { "keys": [ {"type": "symbols"}, {"type": "language"}, {"type": "space"}, {"type": "enter"} ] }
  ]
}
```

- A **character key** is `{"label": "<char>", "code": <unicodeOrKeycode>}`.
- A **function key** is `{"type": "<name>"}` where name is one of:
  `shift`, `delete`, `space`, `enter`, `symbols`, `mode`, `language`, `emoji`,
  `settings`, `theme`, `function`.

## Calculating SHA-256 manually (sanity check)

```bash
# Linux / macOS / Git-Bash
sha256sum languages/en-US/language.zip
# Windows PowerShell
Get-FileHash languages/en-US/language.zip -Algorithm SHA256
```

The value must equal `sha256` in that language's `manifest.json` and in
`catalog/languages.json`.
