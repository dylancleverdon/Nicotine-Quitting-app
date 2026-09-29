# Firewatch by Baastik Labs: notes for Claude Code

Nicotine tracker and coach (Android + web) for anyone who uses nicotine. It counts use in "pieces"
and, in this order: finds where the user's use really is, helps them control it, and helps them
taper if they want. Read `docs/vision.md` (vision and principles) before changing behaviour; the
living spec is `docs/design-spec.md` (1.0 original in `docs/history/`). The owner ("D") is
non-technical: Claude Code builds, releases and maintains everything.

## Rule zero: the self-updater outranks everything
- New versions must reach users' phones with zero taps. Never ship a change that could break
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
- Before every release, read any new or changed app wording through for spin, judgement or negative
  comparisons (the test only catches known phrases).
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

## Product principles (from docs/vision.md; every change must follow them)
- Priority order when features compete: **find** the real level > **control** it > **taper** (optional).
  New users start at an early estimate of 8 pieces a day (`Control.EARLY`); step-ups are offered the
  same day a day runs heavy. Never add a "just for today" step-up.
- Holding steady is a win and gets celebrated. Tapering is offered, never pushed: step-down offers
  always include "Stay here". Clear Air stays visible and within reach, never downplayed.
- Never judge. Tone: factual by default, calm coach for wins, neutral for misses. Never condescending,
  never spin a miss positively, never show a negative comparison ("last week you were lighter").
  Motivating figures (Log tab wins) only ever show positive facts.
- The app must never become a trigger: logging takes ~2 seconds; **no buttons in the craving flow**
  (how a craving ended is inferred from the logs); the next-piece timer can be hidden.
- A tool, not a nag: the **only** notifications are in the opt-in Relapse prevention mode
  (`core/.../engine/Relapse.kt`, `app/.../reminders/RelapseReminders.kt`). Keep that exception, add no others.
- A relapse means cigarettes or vapes (including a friend's), never gum or pouches. Any product can be
  tracked; delivery method shows in the quality score with no extra coaching, except in opt-in
  Coaching tips (off by default; `core/.../engine/Coaching.kt`): opting in is the licence to coach a bit.
- Never spin a miss: `WordingTest` bans known spin phrases in all app text; also read new wording
  through before every release.
- Private: nothing shareable. Easy to come back: after a gap, "Welcome back" offers back-dating.
- Back-dating defaults to no times (rough counts spread across the waking day); exact time optional.
- Tiny learning curve: advanced features are opt-in and live in Settings. No red screens, no streaks
  that reset. Every figure is labelled as an estimate.
- Help, the welcome tour and "Why Firewatch works this way" are written once in `core/.../Help.kt` and shown by
  both apps. Update them (and `docs/design-spec.md`) when a feature changes.
