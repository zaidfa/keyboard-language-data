#!/usr/bin/env python3
"""
package_language.py - Package one language's runtime files into a distributable
language.zip, compute its real SHA-256 + size, and (re)write its manifest.json.

It does NOT invent data. You provide a source folder that already contains the
runtime files for the language (metadata.json + layout.json [+ symbols.json ...]),
and this tool zips them and records the real checksum.

Usage:
    python tools/package_language.py <language-id> [--src <source-folder>]

Example:
    python tools/package_language.py fr --src work/fr

If --src is omitted it defaults to languages/<id> (the readable source files
that live next to the zip).

Result:
    languages/<id>/language.zip      (flat zip of the runtime files)
    languages/<id>/manifest.json     (real sizeBytes + sha256)
"""
import argparse
import hashlib
import json
import os
import sys
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Files that make up a language package, in order. metadata.json is required.
PACKAGE_FILES = ["metadata.json", "layout.json", "symbols.json"]


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("language_id")
    ap.add_argument("--src", default=None)
    args = ap.parse_args()

    lang_id = args.language_id
    out_dir = os.path.join(REPO, "languages", lang_id)
    src_dir = args.src or out_dir
    os.makedirs(out_dir, exist_ok=True)

    meta_path = os.path.join(src_dir, "metadata.json")
    if not os.path.isfile(meta_path):
        sys.exit("ERROR: missing metadata.json in " + src_dir)
    with open(meta_path, encoding="utf-8") as f:
        meta = json.load(f)

    present = [name for name in PACKAGE_FILES
               if os.path.isfile(os.path.join(src_dir, name))]

    zip_path = os.path.join(out_dir, "language.zip")
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in present:
            # write with a fixed timestamp for reproducible zips
            info = zipfile.ZipInfo(name, date_time=(2000, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            with open(os.path.join(src_dir, name), "rb") as f:
                z.writestr(info, f.read())

    size = os.path.getsize(zip_path)
    digest = sha256_of(zip_path)

    manifest = {
        "id": meta["id"],
        "name": meta.get("name", meta["id"]),
        "nativeName": meta.get("nativeName", meta["id"]),
        "locale": meta.get("locale", meta["id"]),
        "version": int(meta.get("version", 1)),
        "package": "language.zip",
        "sizeBytes": size,
        "sha256": digest,
    }
    with open(os.path.join(out_dir, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print("packaged {}: {} files, {} bytes, sha256={}".format(
        lang_id, len(present), size, digest))
    print("now run: python tools/generate_catalog.py")


if __name__ == "__main__":
    main()
