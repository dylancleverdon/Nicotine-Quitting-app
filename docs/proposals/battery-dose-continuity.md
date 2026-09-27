# Proposed update: battery ↔ dose-size continuity fix

Status: **built in Android 0.4.1 / web 0.2.1** (fixes A, B and C). Written against Android 0.4.0 / web 0.2.0
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

### A. Size-honest pull (fixes 1, 2, 3 and 4)
Keep the wait as it is (never more than one gap, the "never a slog" promise). Instead, record the
part of each dose that didn't fit in the battery as pull:

`pull += max(0, dosePieces − charge) × gap`, then `charge = max(0, charge − dosePieces)`

The overage from a big or early dose is counted honestly as pull, then forgiven, so it never
stretches the wait.

| At Bonfire (gap 3h 12m) | Pull |
|---|---|
| Gum 4 mg with a full battery | 0 |
| Gum 2 mg with a half-full battery | 0 (it fits) |
| Two gum 2 mg together | 0 + 0 = 0, same as one gum 4 mg |
| Zyn 6 mg with a full battery | 0.2 × gap ≈ 38m |
| Zyn 6 mg with a half-full battery | 0.7 × gap ≈ 2h 14m |
| 2 × gum 4 mg with a full battery | 1 × gap = 3h 12m |

Issue 4 example: five Zyn 6 mg a day, each at a full battery, now show about 3h 12m of pull
(5 × 38m), roughly one piece's worth, which matches being 1 piece over Bonfire. Net goes negative
instead of reading "on pace" or positive.

**Rejected alternative:** making the wait longer for bigger doses (wait = dose pieces × gap).
Combined with size-honest pull, it counts the same overage twice. A Zyn 6 mg every 3h 50m is
exactly 5 pieces a day, yet it would show pull on every dose. It also breaks "never more than one
gap". So pull carries dose size and the wait stays capped.

### B. One estimate everywhere (fixes 5)
Battery, stretch and pull use the same middle estimate as "pieces today".

### C. "What fits now" (fixes 6, optional)
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
- Pull from full for gum 2 / gum 4 / Zyn 6 / 2 × gum 4 = 0 / 0 / 0.2 / 1.0 gaps; the wait never exceeds one gap.
- Two gum 2 mg together give the same pull and wait as one gum 4 mg.
- A Zyn 6 mg taken each time the battery is full gives negative net, never positive.
- A range dose uses the middle estimate for the battery.
- Five Zyn 6 mg a day at full battery give a negative net.

## Release notes (plain language, draft)
- Pull now counts dose size: anything bigger than your battery had room for (like a Zyn 6 mg, or a
  double) shows up as pull, so your net stays honest. The wait is still never more than one gap.
- Two small pieces now count the same as one big one.
- The battery and "pieces today" now use the same estimate.
