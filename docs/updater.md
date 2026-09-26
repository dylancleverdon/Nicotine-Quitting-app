# How Firewatch updates itself

## Publishing (GitHub Actions)
- A push to branch `apk` runs `release-apk.yml`. It runs the emulator E2E test, then builds the
  release APK and a rollback APK, then publishes:
  - `apk-v<version>`: `firewatch-<version>.apk`, and `firewatch-rollback.apk` (the previous
    release rebuilt with version code `N*10+5`).
  - `apk-latest` (rolling): `firewatch.apk` (install link) and `update.json` (the manifest).
- Version codes: release N is `N*10`. Android refuses downgrades, so "going back" installs the
  previous code under a higher version code. The next release (`(N+1)*10`) installs over it.

## On the phone (`app/.../update/`)
- `UpdateScheduler`: WorkManager check every 3 h (with network), and on every app start.
- `UpdateEngine`: fetch `update.json` (following `movedTo` if hosting ever moves) → download →
  SHA-256 check → package name, version and signing key check → **backup** → install.
  An automatic update waits while the app is open and installs as soon as D leaves it.
- `SelfInstaller`: `PackageInstaller` session with `USER_ACTION_NOT_REQUIRED`. On Android 12+,
  with "install unknown apps" allowed once, there are no taps at all. Otherwise Android asks for
  one tap (notification, or a prompt if the app is open).
- `CrashGuard`: 3 crashes within 15 minutes of a fresh update → automatic rollback.
- `PackageReplacedReceiver`: tidies up after an update; What's new shows on next open.

## One-time steps a person must do
1. First install from the browser (Allow Chrome to install apps).
2. Allow Firewatch to install apps (onboarding step 2 / Settings card).
3. Later: move hosting to a Baastik Labs GitHub organisation (set `movedTo`, GitHub also
   redirects), and register the app with Google's developer verification (free hobbyist
   account) before the 2027 global rollout.
