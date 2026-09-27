# Firewatch by Baastik Labs: notes for Claude Code

Personal Android app that tracks nicotine use in "pieces" and helps D taper down.
The full product spec is `docs/design-spec.md`. The owner ("D") is non-technical: Claude Code
builds, releases and maintains everything.

## Rule zero: the self-updater outranks everything
- New versions must reach D's phone with zero taps. Never ship a change that could break
  `app/src/main/java/com/baastiklabs/firewatch/update/` or the release pipeline.
- The `Updater E2E` workflow (emulator) must pass before anything reaches the `apk` branch.
- Keep `targetSdk` current (silent self-updates need a recent one). Bump it yearly.
- Never replace `app/signing/firewatch.jks` or its passwords. Every version must be signed with
  it, forever, or it can't install over the previous one. It is committed on purpose.
- The `-PversionCodeOverride` and `-Prollback` Gradle properties must keep working: the release
  workflow uses them to rebuild the previous release as a rollback APK.
- See `docs/updater.md`.

## Releasing
- Work on a `claude/*` branch. CI (`ci.yml`) builds, tests and runs the updater E2E test.
- To release: bump `versionName` and `releaseNumber` in `version.properties`, add a
  `## <versionName> · <date>` section at the top of `app/src/main/assets/CHANGELOG.md`
  (plain language; D reads it in "What's new"), then push/merge to the `apk` branch.
  `release-apk.yml` publishes `apk-v<version>` and refreshes the rolling `apk-latest` release
  (`firewatch.apk` = first-install link, `update.json` = what the app polls).
- A push to `apk` without a version bump publishes nothing.
- The web app (`web/`, Vite + Preact PWA) releases from the `webapp` branch via `release-web.yml`
  to GitHub Pages (https://dylancleverdon.github.io/Nicotine-Quitting-app/). Bump `web/package.json`
  version and `web/CHANGELOG.md` per web release. It uses the same record/backup format as Android.
  Local check: `cd web && npm ci && npm run build && npx playwright test`.

## Data must survive updates and rollbacks
- SQLite schema is frozen: one `records` table (id, type, ts, updated_at, deleted, json),
  DB version 1 forever. Never add columns or bump the version.
- New data = new JSON fields (with defaults) or new record types. Never rename/repurpose a field.
- Back-dated doses carry `estimated: true`: counted in totals and tiers, excluded from timing stats.
- Decoders ignore unknown keys/enum values; writers merge into the stored JSON so older versions
  keep newer fields. Deletes are tombstones. See `docs/data-format.md`.

## Branding
- Maker name is "Baastik Labs" everywhere ("Firewatch by Baastik Labs"). D's real name or email
  must never appear in the app, code, commits, releases or docs.
- The fire-themed tier names are fixed by the spec.

## Layout
- `core/`: pure Kotlin (no Android). Models, piece math, stats, backup format, update policy.
  Kotlin Multiplatform: JVM target for Android, JS target for the web app (`jsMain/.../web/WebApi.kt`
  is the web's JSON facade). Tested with `./gradlew :core:jvmTest` (works without an Android SDK).
- `app/`: Android app (Compose). `data/` storage, `update/` self-updater, `ui/` screens.
  `app/src/e2e/` holds test-only hooks for the updater E2E test.
- Android builds need the SDK; if this container can't reach dl.google.com, rely on CI.

## Product principles (from the spec)
- Logging takes ~2 seconds, one-handed. Every figure is labelled as an estimate.
- No nagging: no "time for your next piece" notifications, except in the opt-in Relapse prevention mode
  (`core/.../engine/Relapse.kt`, `app/.../reminders/RelapseReminders.kt`). Keep that exception. No red screens,
  no streaks that reset.
- Help, the welcome tour and "Why Firewatch works this way" are written once in `core/.../Help.kt` and shown by
  both apps. Update them when a feature changes.
