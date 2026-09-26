#!/usr/bin/env python3
"""Writes update.json, the manifest the Firewatch self-updater polls.

Usage:
  make_manifest.py --out update.json --latest APK CODE NAME URL [--rollback APK CODE NAME URL]
                   [--notes-file notes.md] [--moved-to URL]
"""
import argparse
import hashlib
import json


def asset(path, code, name, url, notes=""):
    with open(path, "rb") as f:
        data = f.read()
    return {
        "versionCode": int(code),
        "versionName": name,
        "url": url,
        "sha256": hashlib.sha256(data).hexdigest(),
        "size": len(data),
        "notes": notes,
    }


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--out", required=True)
    p.add_argument("--latest", nargs=4, metavar=("APK", "CODE", "NAME", "URL"), required=True)
    p.add_argument("--rollback", nargs=4, metavar=("APK", "CODE", "NAME", "URL"))
    p.add_argument("--notes-file")
    p.add_argument("--moved-to")
    a = p.parse_args()
    notes = ""
    if a.notes_file:
        with open(a.notes_file) as f:
            notes = f.read().strip()
    manifest = {
        "format": 1,
        "channel": "apk",
        "latest": asset(*a.latest, notes=notes),
        "rollback": asset(*a.rollback) if a.rollback else None,
        "movedTo": a.moved_to,
    }
    with open(a.out, "w") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
