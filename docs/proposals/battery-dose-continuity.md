# Proposed update: battery ↔ dose-size continuity fix

Status: **proposed, not built.** Written against Android 0.4.0 / web 0.2.0
(`core/src/commonMain/kotlin/com/baastiklabs/firewatch/core/engine/BatteryEngine.kt`).

## Why
The next-piece battery, stretch and pull should treat nicotine the same way the rest of the app
does: by size. Right now a bigger dose can get the same wait as a normal one, and taking a small
piece early can look worse than taking a big one. Every figure should agree with "pieces today"
and with the tier.

## Reference numbers
Bonfire (5 a day), so one gap = 16 waking hours ÷ 5 = **3h 12m**. One piece = 2.0 mg absorbed
(a 4 mg gum at about 50%).

| Product | Absorbed (est.) | Pieces |
|---|---|---|
| Gum 2 mg | 1.0 mg | 0.5 |
| Zyn 3 mg | 1.2 mg | 0.6 |
| Gum 4 mg (the reference piece) | 2.0 mg | 1.0 |
| Zyn 6 mg | 2.4 mg | 1.2 |

## Issues found

### 1. Anything 1 piece or bigger gets the same wait
Each dose drains the battery by its size, but never below empty.

| Taken with a full battery | Wait shown today | Fair wait for its size |
|---|---|---|
| Gum 2 mg | 1h 36m | 1h 36m ✅ |
| Gum 4 mg | 3h 12m | 3h 12m ✅ |
| Zyn 6 mg | 3h 12m | 3h 50m ❌ |
| 2 × gum 4 mg | 3h 12m | 6h 24m ❌ |

Doses smaller than one piece are sized fairly. Bigger ones are cut off at one gap.

### 2. Pull ignores the size of the early piece
Pull measures how far the battery is from full *when* a piece is taken, not *how big* the piece is.
With the battery half full, a gum 2 mg and a Zyn 6 mg both count as 1h 36m of pull, although the
Zyn is more than twice the nicotine.

### 3. Splitting a dose is penalised, a big dose isn't
The same nicotine (1 piece), taken two ways:
- One gum 4 mg: pull 0, then a 3h 12m wait.
- Two gum 2 mg a few minutes apart: the second lands on a half-empty battery, so pull ≈ 1h 36m.

### 4. Net can say "on pace" while the tier says "over"
Five Zyn 6 mg a day, each taken when the battery is full, shows pull 0 and net ≈ 0. That's
6 pieces, and the 7-day average correctly measures it as over Bonfire. Two honest-looking
figures disagree.

### 5. Timer and screen use different estimates
The battery drains by the top of the range, while "pieces today" shows the middle estimate. For
known products these match. For a Friend's vape (logged as a range), the timer reacts to a bigger
dose than the screen shows.

### 6. "Full" always means one reference piece
The battery only answers "when could I have a gum 4 mg?". A gum 2 mg would fit in a half-full
battery much sooner, but the app only ever says "Next piece at …".

**Common root:** the battery only measures how empty it is. A dose's size is cut off at one gap
(issues 1 and 4) and ignored in pull (issues 2 and 3).

## Proposed fixes

### A. Size-honest wait (fixes 1 and 4)
Forgive earlier debt, never the dose just taken. When a dose lands, clear any leftover debt from
earlier doses first, then subtract this dose in full:

`charge = max(charge, 0) − dosePieces`

The wait after any dose is then that dose's pieces × one gap (Zyn 6 mg ≈ 3h 50m, a double ≈ 6h 24m).
Earlier pieces still never pile on, so the "never a slog" promise holds: the wait depends only on
the last dose.

### B. Size-honest pull (fixes 2 and 3)
Pull = the part of *this dose* the battery didn't have yet, in minutes:

`pull += max(0, dosePieces − max(charge, 0)) × gap`

- A gum 4 mg with a full battery: pull 0.
- Two gum 2 mg together: 0 + 0 = 0, the same as one gum 4 mg.
- A Zyn 6 mg with a full battery: 0.2 × gap ≈ 38m of pull, honest that it's over one piece.
- A Zyn 6 mg with a half-full battery: 0.7 × gap ≈ 2h 14m.

This also closes issue 4: five Zyn 6 mg a day now show a small, honest negative net.

### C. One estimate everywhere (fixes 5)
Battery, stretch and pull use the same middle estimate as "pieces today".

### D. "What fits now" (fixes 6, optional)
When the battery holds at least half a piece, add a quiet line such as "A gum 2 mg fits now". It
only lists products on the home screen whose size fits the current charge. There's no nagging:
this appears only on the battery row, never as a notification.

## What doesn't change
- Fresh start each morning, full at wake-up, no waiting overnight, and the late-night catch-up.
- Stretch (minutes held off with a full battery) is already size-honest: small doses refill sooner.
- The cheer after waiting for a full battery.
- Tiers, the 7-day average and "pieces today".
- No stored data changes. This is maths only, so past days are simply recalculated.

## Tests to add
- Wait after gum 2 / gum 4 / Zyn 6 / 2 × gum 4 from full = 0.5 / 1.0 / 1.2 / 2.0 gaps.
- Two gum 2 mg together give the same pull and wait as one gum 4 mg.
- Heavy earlier doses never make the wait longer than the last dose's size × gap.
- A range dose uses the middle estimate for the battery.
- Five Zyn 6 mg a day at full battery give a negative net.

## Release notes (plain language, draft)
- The next-piece timer now matches your dose size: a Zyn 6 mg waits a bit longer than a 4 mg gum,
  and a 2 mg gum a bit less.
- Pull now counts how much nicotine came early, so two small pieces count the same as one big one.
- The battery and "pieces today" now use the same estimate.
