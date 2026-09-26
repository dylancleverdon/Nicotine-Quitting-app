#!/usr/bin/env bash
# Builds the APKs for the updater end-to-end test (scripts/e2e-updater.sh):
#   a.apk  versionCode 10  the "installed" version
#   b.apk  versionCode 20  the update it should install silently
#   c.apk  versionCode 25  a rollback build it should install when asked
set -euo pipefail
OUT=build/e2e
URL=http://10.0.2.2:8765
rm -rf "$OUT"
mkdir -p "$OUT"

build() {
  local code=$1 name=$2
  shift 2
  ./gradlew :app:assembleE2e -PversionCodeOverride="$code" "$@" --stacktrace
  cp app/build/outputs/apk/e2e/app-e2e.apk "$OUT/$name.apk"
}

build 10 a
build 20 b
build 25 c -Prollback=true

python3 scripts/make_manifest.py --out "$OUT/manifest-none.json" --latest "$OUT/a.apk" 10 0.0.1 "$URL/a.apk"
python3 scripts/make_manifest.py --out "$OUT/manifest-update.json" --latest "$OUT/b.apk" 20 0.0.2 "$URL/b.apk"
python3 scripts/make_manifest.py --out "$OUT/manifest-rollback.json" \
  --latest "$OUT/b.apk" 20 0.0.2 "$URL/b.apk" \
  --rollback "$OUT/c.apk" 25 0.0.1 "$URL/c.apk"
ls -la "$OUT"
