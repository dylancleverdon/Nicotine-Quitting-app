# Firewatch data format

One format everywhere: the phone's database rows, backup/export files, and (later) the web app.

## Records
Every piece of data is a record:

| field | meaning |
| --- | --- |
| `id` | unique id (time-prefixed random string) |
| `type` | `product`, `dose`, `craving`, `sleep`, `settings`, `rung` (target changes), `checkin`, `mode` (Relapse prevention mode switched on/off: `at`, `on`, `mode: "relapse"`), `timercheck` (a tap on the hidden next-piece timer: `at`, `charging`), `refchange` (the reference piece changed: `at`, `from`, `productId`, `previousProductId`; doses before `from` keep the old piece size) (more may be added) |
| `ts` | main timestamp, epoch milliseconds (null for settings) |
| `updatedAt` | epoch ms of the last change; newest wins when merging |
| `deleted` | tombstone; deleted records are kept so undo and merging work |
| `data` / `json` | the record's JSON object (models in `core/.../model/Models.kt`) |

On the phone they live in SQLite table `records(id, type, ts, updated_at, deleted, json)`.
That schema is frozen at version 1.

Dose field `estimated: true` marks back-dated rough entries (no real time): counted in totals and the tier, left out of timing stats.

Added in 0.16.0: settings `showWatch` (false), `lastZone` (""), `zoneHistory` (list of "<epoch ms>|<zone id>": which zone the usual wake/sleep times are in from the next day on).

Added in 0.14.0: settings `theme` ("firewatch"), `themeMode` ("system"), `trueBlack` (false), `colourBlindCharts` (false), `calmColours` (true), `installCardSeen` (false), `lastBackupAt` (0), `clearAirOfferSnoozedAt` (0). The old `remindCheckIn` / `remindBackup` settings are no longer read (those reminders were removed).

Added in 0.13.0: record types `daymark` (an empty day marked by D: `id` "daymark-<date>", `date` ISO, `state` "clear" or "ghost", `at`; un-marking is a tombstone; "?" is never stored, it's worked out from the logs) and `practice` (practice pace: `id`, `start`, `pieces`, `end` null while on, `untilBedtime`); field `detail` on `rung` (the reason, e.g. the step-up reason, default ""; `reason` "measured" = working from the measured level); settings `practiceUntilBedtime` (true), `lighterOffers` (true), `lighterSnoozedAt`, `practiceAnswered`, `workFromSnoozedAt` (0/""). The old `practiceDate`/`practicePieces` settings are no longer read.

Speed profile `CHEW` (chew and park, gum) was added in 0.11.0; older versions read it as the default `BUILD`. Gum doses stored as `BUILD` are drawn as `CHEW`. Settings fields added in 0.11.0: `coachingTips` (false), `tipDismissedAt` (tip id → ms), `netExplained` (false), `showVolatility` (false).

Settings fields `morningDelayClock` (minutes after midnight, -1 = off) and `timeFormat` (system/12h/24h) were added in 0.4.0.

## Compatibility rules (so updates and rollbacks never lose data)
1. Every JSON field has a default. Never rename, remove or change the meaning of a field.
2. Readers ignore unknown fields and unknown enum values (they fall back to defaults).
3. Writers merge into the stored JSON, so fields written by a newer version survive an older one.
4. New kinds of data get a new `type`; older versions keep them untouched.

## Backup file
```json
{
  "app": "Firewatch by Baastik Labs",
  "format": 1,
  "exportedAt": 1790000000000,
  "appVersion": "0.1.0",
  "reason": "export",
  "records": [ { "id": "...", "type": "dose", "ts": 0, "updatedAt": 0, "deleted": false, "data": { } } ]
}
```
Import either merges (per id, newest `updatedAt` wins) or replaces (current records are
tombstoned). A safety backup is always written first.
