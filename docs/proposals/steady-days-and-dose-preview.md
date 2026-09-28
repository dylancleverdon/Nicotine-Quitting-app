# Proposed update: dose-honest battery, steady days, chart touches and continuity

Status: **proposed, not built.** Written against Android 0.9.0. The maths lives in the shared
`core`, so the web app gets it too, but web-only work isn't a priority. Decisions on each idea are
tracked in `docs/suggestions.md`.

## Summary
1. **Pieces today follow the waking day,** and one shared "which day" rule is used everywhere.
2. **The battery becomes a plain dosing timer; dose size lives in stretch and pull.** Each product
   shows how it would affect net before you log it.
3. The "fits now" line is removed (replaced by 2).
4. **Steady days:** a total that only ever goes up, shown on the home card, with milestones.
5. **Practice day** before a step down.
6. **Scrub any chart** to see the value at that point.
7. **"Show yesterday"** on day charts, plus an opt-in "Detailed charts" setting.
8. **Taper forecasts from day one.**
9. Remove the mouth-time statistic.
10. **Changing the reference piece:** from today, or back-dated with a warning.
11. Faster battery maths and continuity tests.

---

## 1. One day, everywhere
**Problem.** "Pieces today" and the calendar reset at midnight, while the tier, pace and battery
use the waking day. A 1am piece counts towards last night in one place and tomorrow in another.

**Rule.** A *waking day* runs from wake-up to the next wake-up. That's still a full 24 hours, just
starting at wake time instead of midnight.
- **Daily counts use the waking day:** pieces today, calendar totals, daily bars in Insights,
  tiers and averages, stretch, pull and net, steady days.
- **Things that happen continuously keep real clock time:** the blood-level wave, receptor healing
  (runs all 24 hours), the hour-of-day heatmap and the craving forecast. These are unchanged.

One shared function in `core` decides which day a dose belongs to. Every screen uses it.

## 2. The battery is a timer; dose size lives in stretch and pull
**Why.** Draining the battery halfway for a gum 2 mg and fully for a Zyn 6 mg made the timer look
more precise than it is, and split dose size between two places. Now the battery does one simple
job, and net carries dose size honestly.

**The battery.** Every dose empties it, whatever its size. It refills over one gap (16 waking
hours ÷ your rung). "Next piece around …" is always one gap after your last piece. Fresh start
every morning, full at wake-up and the late-night catch-up are unchanged.

**Stretch and pull.** Each dose of *p* pieces (1 piece = one gum 4 mg):
- **Timing:** taken before the battery is full → pull = time it still needed.
  Holding off with a full battery → stretch (as now).
- **Size:** bigger than one piece → extra pull = (p − 1) × gap.
  Smaller than one piece → extra stretch = (1 − p) × gap.
- **Net = stretch − pull,** as now.

At Bonfire (gap 3h 12m):

| Dose | Size effect |
|---|---|
| Gum 2 mg (0.5) | +1h 36m stretch |
| Zyn 3 mg (0.6) | +1h 17m stretch |
| Gum 4 mg (1.0) | none |
| Zyn 6 mg (1.2) | +38m pull |
| 2 × gum 4 mg (2.0) | +3h 12m pull |

**Checks that it's fair:**
- A Zyn 6 mg every 3h 50m is exactly 5 pieces a day, so net = 0 (38m stretch waiting past full,
  38m pull for size).
- A gum 2 mg every 1h 36m is also 5 pieces a day, so net = 0.
- Two gum 2 mg taken together count exactly like one gum 4 mg.
- Net therefore reads as "time ahead of or behind your rung's pace", whatever you use.

**Before you log: the dose preview.** Under each product button on the Log tab, one quiet line
shows what logging it *now* would do to net, combining timing and size:
- "Would add ≈ 38m pull" (a Zyn 6 mg with a full battery)
- "Would add ≈ 1h 36m stretch" (a gum 2 mg with a full battery)
- "Would add ≈ 2h 14m pull" (a Zyn 6 mg with the battery half full)

The wording stays neutral: never "wait X to earn it". The quality score still shows the delivery
method separately, so waiting out a stronger product is honest pacing, but nothing in the app
promotes it. Hidden when "Hide next piece timer" is on (the preview reveals the timer).

**Unchanged:** stretch and pull stay paused in Relapse prevention mode; negative net is grey, never
red; the full-battery cheer.

## 3. Remove "fits now"
The "A gum 2 mg fits now" line (0.4.1) goes, along with draining the battery by dose size. The dose
preview in §2 covers the same need for every product.

## 4. Steady days
- **A steady day:** net ≥ 0 and no cigarette or vape. Back-dated days, which have no times, use
  pieces at or under the rung instead. In Relapse prevention mode: no cigarette or vape.
- **The count only ever goes up.** A rough day or a relapse just doesn't add one. It never resets
  and never goes to zero. Days don't need to be in a row.
- **Home card:** "✓ 23 steady days", on by default, with an off switch in Settings → Plan.
- **Milestones:** a one-time win on the Log tab at 7, 30, 60, 90, 180 and 365 steady days,
  alongside the existing "held Bonfire for X days" badges. Also listed in Insights → Milestones.
- The step-up and step-down offers are unchanged. Steady days are a celebration, not a gate.

## 5. Practice day before a step down
- The step-down offer gains **"Try it for a day"** next to "Step down" and "Stay here".
- For that waking day, the battery, the gap and the dose preview use the next rung. Your rung and
  tier don't change, and nothing counts against you.
- A small note on the home card: "Practice day: Campfire pace".
- The next morning: "How was Campfire pace? **Step down** / **Stay here**" (it's already an offer,
  so "Stay here" keeps things as they are).
- It isn't a "just for today" step-up. It only works downward and only from a step-down offer.

## 6. Scrub charts
Touch and drag on any chart to show a thin line with the value and time or date at that point.
Let go and it disappears. Built once and shared by every chart, with no extra buttons.

## 7. "Show yesterday", and opt-in detailed charts
- **For everyone:** day charts (the blood-level wave, the dose strip) get a small
  **Today / Yesterday** switch. The wave also gets **"Overlay yesterday"**, a dotted line.
- The overlay is neutral: no "lighter/heavier" text and no better/worse colours. The user chooses
  the day and the app doesn't comment (vision: never show a negative comparison).
- **Settings → "Detailed charts" (off by default):** for people who want to dig in. Adds a date
  picker and range choices (7 / 30 / 90 days / all) to every chart, at the cost of a busier
  Insights screen.

## 8. Taper forecasts from day one
- Today, Forecasts are blank until there's a week of data, and blank if you aren't tapering.
- **New:** before real data, show a plan: "If you step down each time it's offered (every 7 days):
  Campfire around 12 Oct, Clear Air around March." Based on the hold period in Settings.
- As weeks pass, blend from the plan towards your real pace, and say which one it is: "Based on
  your plan" → "Based on your plan and your last 4 weeks" → "Based on your pace".
- Always worded as "if you take each step", so tapering stays optional.

## 9. Remove mouth-time
Remove the mouth-time statistic and any charts that use it. It isn't accurate enough to be useful.

## 10. Changing the reference piece
When the product that counts as "one piece" is changed in Settings, choose:
- **From today on (default):** past days keep the old piece size. Nothing to warn about.
- **Back-date the change:** pick a date. Totals, tiers and forecasts from that date are
  recalculated. Warning first: "This changes your past totals and may change your tier."

Stored as a new record type (reference changes with a date), so older versions ignore it safely.

## 11. Under the hood
- **Faster battery maths:** jump from dose to dose instead of simulating every minute. It gives
  the same answers with far less work (a few steps a day instead of about 1,440), which matters
  for Insights recalculating 90 days.
- **Continuity tests:** the same data must give the same pieces, net, steady days and tier on the
  home card, calendar, Insights and widget, plus a test that the fast and minute-by-minute battery
  maths agree.

---

## Data
- New settings, each with a default: show steady days (on), detailed charts (off), practice day
  (date and rung).
- New record type: reference-piece changes (for back-dating, §10).
- No schema change. Follows `docs/data-format.md`.
- Update `docs/design-spec.md` §6, §7, §8, §11 and §13, and `core/.../Help.kt` (battery, stretch
  and pull, steady days, practice day, detailed charts).

## Tests to add
- Battery: every dose empties it; the next piece is one gap later, whatever the dose size.
- Size effects: gum 2 / Zyn 3 / gum 4 / Zyn 6 / 2 × gum 4 give +96m / +77m / 0 / −38m / −192m.
- Net is 0 for a Zyn 6 mg every 3h 50m, for a gum 2 mg every 1h 36m, and for a gum 4 mg every gap.
- Two gum 2 mg together = one gum 4 mg.
- Steady days: never decrease; a relapse or negative net day doesn't add; back-dated days use
  pieces.
- A 1am piece belongs to the previous waking day in every screen; the wave and receptors are
  unchanged.
- Practice day uses the lower rung's gap for one day only and changes nothing else.
- Forecast shows the plan with no data, then blends.

## Release notes (plain language, draft)
- The next-piece timer is now simply "one gap after your last piece". Dose size now shows up where
  it belongs, in stretch and pull: smaller doses add stretch, bigger ones add pull.
- Each product now shows what it would do to your net before you log it.
- New: your total **steady days**, which only ever go up, with milestones along the way. You can
  hide it in Settings.
- New: try the next rung for a day before stepping down.
- Charts: touch and drag to read any point. Day charts can show yesterday.
- Forecasts show a plan from day one and adjust as you go.
- Pieces after midnight now count towards the night before everywhere, like the rest of the app.
