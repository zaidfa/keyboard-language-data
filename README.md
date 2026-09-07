# Keyboard Language Data

Cloud distribution repository for the **Multilanguage Keyboard** app's downloadable
languages. The Android app ships with a core keyboard engine plus a couple of
built-in languages; every other language is downloaded from here on demand and
then works **fully offline**.

> **This repository is independent from the Android source code.** It contains
> only public, read-only language data. No credentials, tokens, or secrets.

## Directory structure

```
github-language-repository/
├── README.md
├── catalog/
│   └── languages.json          # master catalog (all languages)
├── languages/
│   ├── en-US/
│   │   ├── manifest.json
│   │   ├── language.zip         # the downloadable package
│   │   ├── metadata.json        # readable source of the package
│   │   ├── layout.json
│   │   └── symbols.json
│   ├── vi/
│   │   └── ... (same shape)
│   └── <id>/ ...
└── tools/
    ├── README.md
    ├── languages_seed.json      # master list of every language + metadata
    ├── package_language.py      # zip + checksum + manifest for one language
    ├── generate_catalog.py      # regenerate catalog/languages.json
    └── BuildRepo.java           # one-off bootstrap (exported en/vi from the app)
```

## The catalog: `catalog/languages.json`

```json
{
  "schemaVersion": 1,
  "catalogVersion": 1,
  "languages": [
    {
      "id": "en-US",
      "name": "English (United States)",
      "nativeName": "English (US)",
      "locale": "en-US",
      "script": "Latin",
      "version": 1,
      "status": "available",
      "package": "languages/en-US/language.zip",
      "sizeBytes": 1073,
      "sha256": "bc04...95be"
    }
  ]
}
```

### Status values

| Status | Meaning |
|--------|---------|
| `available` | A real package exists and can be downloaded. |
| `coming_soon` | Listed in the catalog but no real keyboard data yet. Shown greyed-out in the app. |
| `disabled` | Temporarily hidden/withdrawn. |

Only languages we have **real** data for are `available`. We never duplicate one
language's data under another language's name.

## Language package format (`language.zip`)

A flat zip of runtime files:

```
language.zip
├── metadata.json    { id, name, nativeName, locale, script, version, layouts[] }
├── layout.json      main letters   (rows of keys)
└── symbols.json     symbols page    (rows of keys)
```

See [`tools/README.md`](tools/README.md) for the exact key JSON format.

## Manifest format (`languages/<id>/manifest.json`)

```json
{
  "id": "vi",
  "name": "Vietnamese",
  "nativeName": "Tiếng Việt",
  "locale": "vi",
  "version": 1,
  "package": "language.zip",
  "sizeBytes": 1168,
  "sha256": "63ea...27ee"
}
```

The `sha256` is the **real** checksum of `language.zip`. The Android app verifies
it after download and rejects the package on mismatch.

## How Android downloads a language

1. App fetches `catalog/languages.json` (and caches it locally).
2. User picks a language → app resolves its `package` path (or a GitHub Release
   asset URL — see below).
3. App streams the `language.zip` to a temp file, verifies `sha256`, extracts it
   atomically into app-private storage, marks it installed.
4. From then on the language loads **from local storage** — no network needed.

The base repository URL is **configurable in the app** (owner/repo), so this data
can be hosted anywhere without changing app code.

## Hosting options

**A. Raw files (simplest).** Serve straight from the repo via
`https://raw.githubusercontent.com/<OWNER>/<REPO>/<BRANCH>/<path>`.
The app points at `<OWNER>/<REPO>/<BRANCH>` and appends `catalog/languages.json`
or a language's `package` path.

**B. GitHub Releases (recommended for large files).** Upload each `language.zip`
as a release asset and set the catalog `package` (or a per-language override) to
`https://github.com/<OWNER>/<REPO>/releases/download/<TAG>/<id>-language.zip`.
Public releases need no authentication.

See [`../GITHUB_LANGUAGE_SETUP.md`](../GITHUB_LANGUAGE_SETUP.md) in the Android
project for step-by-step upload instructions.

## Offline behavior

- Downloaded languages live in app-private storage and work with no internet.
- The catalog is cached; if GitHub is unreachable the app uses the cached copy.
- Built-in English/Vietnamese always work, online or offline.

## Adding / updating languages

See [`tools/README.md`](tools/README.md). In short:

```bash
# add or edit languages/<id>/{metadata,layout,symbols}.json, then:
python tools/package_language.py <id>
python tools/generate_catalog.py
```

## License

Language data is provided as-is for use by the Multilanguage Keyboard app.
