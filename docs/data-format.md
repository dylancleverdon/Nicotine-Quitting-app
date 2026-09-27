# Firewatch data format

One format everywhere: the phone's database rows, backup/export files, and (later) the web app.

## Records
Every piece of data is a record:

| field | meaning |
| --- | --- |
| `id` | unique id (time-prefixed random string) |
| `type` | `product`, `dose`, `craving`, `sleep`, `settings`, `rung` (target changes), `checkin` (more may be added) |
| `ts` | main timestamp, epoch milliseconds (null for settings) |
| `updatedAt` | epoch ms of the last change; newest wins when merging |
| `deleted` | tombstone; deleted records are kept so undo and merging work |
| `data` / `json` | the record's JSON object (models in `core/.../model/Models.kt`) |

On the phone they live in SQLite table `records(id, type, ts, updated_at, deleted, json)`.
That schema is frozen at version 1.

Dose field `estimated: true` marks back-dated rough entries (no real time): counted in totals and the tier, left out of timing stats.

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
