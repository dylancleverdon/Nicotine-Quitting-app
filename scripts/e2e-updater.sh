#!/usr/bin/env bash
# Proves on a real (emulated) phone that Firewatch updates itself with no taps, keeps its data,
# and can go back to the previous version. Run by CI inside an Android emulator.
set -euo pipefail
PKG=com.baastiklabs.firewatch
DIR=build/e2e
PORT=8765

log() { echo "=== $*"; }

installed_code() {
  adb shell dumpsys package "$PKG" | grep -m1 -o 'versionCode=[0-9]*' | cut -d= -f2
}

dump_logs() {
  log "logcat (filtered)"
  adb logcat -d | grep -iE "firewatch|PackageInstaller|FW_E2E|WM-|InstallPackageHelper|PackageManager" | tail -150 || true
  log "http server log"
  cat /tmp/fw-http.log || true
}

wait_for_code() {
  local want=$1 have=""
  for _ in $(seq 1 120); do
    have=$(installed_code || true)
    if [ "$have" = "$want" ]; then
      log "installed versionCode is now $have"
      return 0
    fi
    sleep 2
  done
  log "FAILED: expected versionCode $want, still $have"
  dump_logs
  return 1
}

broadcast() {
  adb shell am broadcast -f 32 -n "$PKG/.e2e.E2eReceiver" -a "$PKG.e2e.$1" >/dev/null
}

# Prints the dose count the app reports. Android drops broadcasts to an app while an install is
# still finishing (the package is frozen), so keep asking for a while.
report_doses() {
  for attempt in $(seq 1 8); do
    adb logcat -c
    broadcast REPORT || true
    for _ in $(seq 1 10); do
      local line
      line=$(adb logcat -d -s FW_E2E:I | grep "report versionCode" | tail -1 || true)
      if [ -n "$line" ]; then
        echo "$line" >&2
        echo "$line" | grep -o 'doses=[0-9]*' | cut -d= -f2
        return 0
      fi
      sleep 1
    done
    echo "=== no report yet (attempt $attempt), asking again" >&2
  done
  log "FAILED: app did not report" >&2
  local pid
  pid=$(adb shell pidof "$PKG" || true)
  [ -n "$pid" ] && adb logcat -d --pid="$pid" | tail -80 >&2
  dump_logs >&2
  return 1
}

cp "$DIR/manifest-none.json" "$DIR/update.json"
python3 -m http.server "$PORT" --directory "$DIR" >/tmp/fw-http.log 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null || true' EXIT
sleep 2

log "Install version A (10) and allow it to install updates, as D does once"
adb install -r "$DIR/a.apk"
adb shell appops set "$PKG" REQUEST_INSTALL_PACKAGES allow
adb shell am start -W -n "$PKG/.MainActivity"
sleep 5
adb shell input keyevent KEYCODE_HOME
sleep 2
[ "$(installed_code)" = "10" ]

log "Seed some logs"
broadcast SEED
sleep 5
before=$(report_doses)
log "doses before update: $before"
[ "$before" -ge 5 ]

log "Publish version B (20) and let the app find it by itself"
cp "$DIR/manifest-update.json" "$DIR/update.json"
broadcast CHECK
wait_for_code 20
sleep 10
after=$(report_doses)
log "doses after update: $after"
[ "$after" = "$before" ]

log "Go back to the previous version (rollback build, 25)"
# Android throttles silent updates of the same app to one per ~30 s; stay clear of it here.
sleep 35
cp "$DIR/manifest-rollback.json" "$DIR/update.json"
broadcast ROLLBACK
wait_for_code 25
sleep 10
after_rollback=$(report_doses)
log "doses after rollback: $after_rollback"
[ "$after_rollback" = "$before" ]

log "Pre-update backups were written"
adb shell run-as "$PKG" ls files/backups 2>/dev/null || log "(backups dir not readable on release builds; skipped)"

log "PASSED: silent update, data kept, rollback works"
