#!/usr/bin/env python3
"""
generate_catalog.py - Regenerate catalog/languages.json from:
  * tools/languages_seed.json   (the master list of every language + metadata)
  * languages/<id>/manifest.json (real packages that actually exist)

A language becomes "available" only when a real languages/<id>/manifest.json
exists (with a real sha256). Everything else in the seed is "coming_soon".
Sizes and checksums are read from the manifests - never hardcoded.

Usage:
    python tools/generate_catalog.py
"""
import json
import os

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCHEMA_VERSION = 1
CATALOG_VERSION = 1


def load_seed():
    with open(os.path.join(REPO, "tools", "languages_seed.json"), encoding="utf-8") as f:
        return json.load(f)["languages"]


def load_manifest(lang_id):
    path = os.path.join(REPO, "languages", lang_id, "manifest.json")
    if not os.path.isfile(path):
        return None
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def main():
    seed = load_seed()
    languages = []
    available = 0
    for lang in seed:
        manifest = load_manifest(lang["id"])
        if manifest and manifest.get("sha256"):
            available += 1
            languages.append({
                "id": lang["id"],
                "name": lang["name"],
                "nativeName": lang["nativeName"],
                "locale": lang["locale"],
                "script": lang.get("script"),
                "version": int(manifest.get("version", 1)),
                "status": "available",
                "package": "languages/{}/language.zip".format(lang["id"]),
                "sizeBytes": int(manifest.get("sizeBytes", 0)),
                "sha256": manifest.get("sha256", ""),
            })
        else:
            languages.append({
                "id": lang["id"],
                "name": lang["name"],
                "nativeName": lang["nativeName"],
                "locale": lang["locale"],
                "script": lang.get("script"),
                "version": int(lang.get("version", 1)),
                "status": "coming_soon",
                "package": "",
                "sizeBytes": 0,
                "sha256": "",
            })

    catalog = {
        "schemaVersion": SCHEMA_VERSION,
        "catalogVersion": CATALOG_VERSION,
        "languages": languages,
    }
    out = os.path.join(REPO, "catalog", "languages.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print("catalog: {} languages, {} available, {} coming_soon".format(
        len(languages), available, len(languages) - available))


if __name__ == "__main__":
    main()
