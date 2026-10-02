# Firewatch by Baastik Labs: design spec

Baastik Labs · living spec, updated 28 Sep 2026 (Android 0.13 / web 0.10)

This describes what Firewatch does today and how it should feel. Read `docs/vision.md` first: the
vision and principles there decide every trade-off here. The original 1.0 spec is kept in
`docs/history/design-spec-v1.md`. How things are built is up to Claude Code (see `CLAUDE.md`).

## 1. What Firewatch is

A private nicotine tracker and coach for anyone who uses nicotine. In priority order it helps you
**find** where your use really is, **control** it (keep it from creeping up, celebrate holding
steady) and, if you want, **taper** down, all the way to Clear Air.

- **Android app** (primary): installed from the `apk-latest` release link; updates itself silently.
- **Web app** (iPhone and any browser): https://dylancleverdon.github.io/Nicotine-Quitting-app/.
  Same features and maths (the shared `core`), same backup format. Relapse reminders are
  Android-only.
- Everything stays on the device. Nothing is shareable.

## 2. Top priority: automatic updates

Unchanged from 1.0 and still the most important requirement: new versions find, download and
install themselves with zero taps, never touch the user's data, always install over the last
version, and can roll back to the previous release. Settings shows the version and last check;
"What's new" appears after an update. Details: `docs/updater.md`. If anything conflicts with this,
the updater wins.

## 3. Branding and tone

- "Firewatch by Baastik Labs" everywhere; the owner's real name never appears in the app, code,
  commits, releases or docs. The fire-themed tier names are fixed.
- Tone: plain and factual by default; a calm, encouraging coach for wins; neutral like a tool for
  misses. Never condescending, never spin a miss, never show a negative comparison.

## 4. Pieces and the absorption engine

Everything is counted in **pieces**: what one 4 mg nicotine gum delivers (≈ 2 mg absorbed). Each
product has a label strength, an absorption estimate (editable) and a speed profile (spike, build,
flat). Gum is modelled as chew and park (released in bursts over the chewing time, absorbed slowly through
the cheek: later and lower than a pouch, same total). Nicotine's ~2-hour half-life gives each dose a rise-and-fade curve, which powers the
blood-level graph and "≈ X mg in your system now". Unknown doses (a friend's vape) are logged as a
range. Log-time tweaks: amount, how long it stayed in, acidic drink (gum). Every figure is labelled
as an estimate.

## 5. First run

- **Welcome tour** (5 short screens, skippable, reopenable from Help).
- **Setup:** usual wake and sleep times, products for the home screen, then how to start:
  - **Establish a baseline:** start at an early estimate of 8 pieces a day that firms up each day
    toward the measured level (blend of 8 and the days logged so far). Guidance works from day one.
  - **Estimate my last week:** back-date 7 days with rough counts; the real level shows at once.
  - **I'm just starting gum:** turns on Relapse prevention mode (2-hour gap), gum first.
- After the first week: "Your starting point: Campfire · Start here?" sets the working rung.

## 6. The Log tab (home)

- **Status card:** tier and pieces a day; the battery (see 7); pieces and mg today; doses, time
  since last, cravings ridden out, quality; today's nicotine curve with "≈ X mg in your system now".
- **Morning stretch** ("Morning stretch: 1h 20m") until the first piece; a one-time "This is your
  net" card in the first days.
- **Practice pace** (when on): "Practice pace · Campfire 4" with a "?", and "Practice net +40m · day 3".
- **Wins, only ever positive:** "✓ 23 steady days" (hideable; tap for what counts), "✓ 12 days held at Bonfire in total", "✓ About 20% lighter than when you
  started" (only when true), "✓ 30 days off cigarettes and vapes", "Journey to Clear Air: 40%".
- **Offers:** starting point; step down ("…or stay here, that's fine too", with **Try it for a day**
  and **Stay here**); "How was Campfire pace?" after a practice day; the lighter-level practice offer
  ("Your logs measure Campfire · 4 a day", Try Campfire pace / Stay here / Don't ask me again, then a
  second screen: Turn off at bedtime / Leave it on); "Campfire pace held. Work from Campfire?" (Work
  from / Keep practicing / Back to Blaze pace); steady-days milestones;
  step up (with the reason); Relapse prevention mode suggestion; "Welcome back". With Coaching
  tips on, at most one tip ("Bridge with gum" when pouches are most of your use and the battery is
  at least half full), dismissable for 2 weeks.
- **Quick logging:** one tap per product (hold for time, amount and more), each with a quiet
  dose preview ("+38m pull", "+1h 36m stretch": what logging it now does to net); "Craving? Log it";
  "Friend's vape"; Good morning / Good night; today's list; Relapse prevention mode button;
  "Suggest something / report a bug"; "?" for Help.

## 7. The battery (next-piece guidance)

- **A plain timer:** every dose empties it, whatever its size; it refills over one gap (16 waking
  hours ÷ the rung's pieces a day). "Next piece around …" is always one gap after the last piece.
  Full at wake-up, fresh every morning, no waiting overnight; sleeping hours (late-night activity
  catches up). **Wind-down** is a note only. A practice day uses the next rung's gap.
- **Stretch and pull carry timing and size.** Timing: a piece before the battery is full adds pull
  (the time it still needed); holding off with a full battery adds stretch. Size: p pieces above one
  add (p − 1) × gap of pull; below one add (1 − p) × gap of stretch. **Net** = stretch − pull = time
  ahead of or behind the rung's pace whatever the product (a Zyn 6 mg every 3h 50m nets 0 at
  Bonfire; two gum 2 mg = one gum 4 mg). Negative net is grey, never red. Paused in Relapse
  prevention mode. The full-battery cheer stays. Stretch, pull and net always stay against the real
  level, even with practice pace on.
- **Dose preview** under each product between the name and the pieces (shown even with "Hide next piece timer"; its own toggle "Hide stretch and pull on doses"). Neutral wording only.
- **Hide next piece timer** (opt-in): "Tap to see your next piece time"; each tap is a counted
  check, a "wanting it" signal used in Insights and the offers.
- The maths jumps from dose to dose (fast); tests check it matches a minute-by-minute version.

## 8. The ladder: find, control, taper

- Rungs one piece apart from 24 down to ½ and ⅓, then Clear Air; tiers Wildfire → Clear Air.
- **Step up (find):** offered, with the reason, the same day any one of these happens: pull
  reaches a full gap; 3 strong cravings (or 2 within 2 hours); more than a piece over today's
  target; a spike in timer checks; a craving that ends in a cigarette or vape. Also when the last
  7 days measure a heavier rung (creeping up), or when two multi-day signals line up. "I'm OK"
  hides it until tomorrow. No "just for today" option: the point is finding the real level.
- **Hold (control):** holding a rung is a win: held-days counts, a Holding steady chart, badges at
  7/30/90/180 days held. **Steady days:** net ≥ 0 and no cigarette or vape (back-dated days: pieces
  at or under the rung; Relapse prevention mode: no cigarette or vape; empty days count only at
  Clear Air or when the app was in use). The total only ever goes up; milestones at 7/30/60/90/180/365.
  A celebration, never a gate. Empty days count only when marked clear 🌿 (recalculated once in 0.13).
- **Step down (taper, optional):** offered after the hold period (Settings): that many full days in
  a row at or under the rung since the last change ("Next step down" in Insights → Forecasts shows
  "1 of 3 days held");
  never a button; always with "Stay here". **Try it for a day:** that waking day runs at the next
  rung's pace (battery, gap, preview); nothing else changes; next morning "Step down / Stay here".

## 9. Cravings

- One tap to log strength (1–10). Then nothing to press: a single quiet line for 45 minutes.
- How it ended is worked out from the next 45 minutes: rode it out ✓, waited for the right time ✓,
  early (gum, pouch or other product before the battery was full), relapse (cigarette or vape).
- **Craving forecast** (Insights): chance and likely strength over the next 24 hours from the
  user's own pattern, their nicotine level against normal, recent step-downs and timer checks;
  "Next craving likely around …"; an honest back-test.

## 10. Relapse prevention mode (the one notification exception)

Opt-in reminders to chew at the rung's gap (2 hours before there's a tier), never while asleep, a
"Log it" action, supportive wording. Suggested (not pushed) after a cigarette or vape, early heavy
gum use or strong cravings; after 4 steady weeks, offers the switch to tapering. Stretch and pull
pause on mode days.

## 11. Insights

Yesterday in review (facts only: pieces, net, volatility, mix, longest gap, stacked doses, morning
stretch; up to two tips with Coaching tips on), Today (wave with optional volatility line, off by
default; daily nicotine volatility in mg/h = root-mean-square of the rate of change over the waking
day, the line uses the last 30 minutes; dose strip), Trends (with "27 of 30 days known" and "(6 of 7
days known)" on the measured level), Ladder (with Level history), Cravings ahead, Receptors (estimated healing, following the plan vs
staying), Stretch & pull, Trends, Patterns (heatmap, how cravings ended, checking, triggers),
Going up (holding steady, clear hours, wins, money), Going down (quality, dose size, spike share,
background level), Mix, Forecasts (Journey to Clear Air, arrival dates), Milestones (records,
badges, recaps, day barcode), Ladder. Every chart has labelled axes; touch and drag reads any
point. Day charts (wave, dose strip) have ‹ Today › to view any earlier day (no overlay, no
comparison). Opt-in "Detailed charts" adds 7/30/90/all ranges and earlier/later on multi-day
charts. Forecasts show "If you take each step" from day one (plan → plan + recent weeks → pace).
Every figure is an estimate.

## 12. Calendar and back-dating

**Days that count:** a full day with nothing logged is "?" (once its waking day is over) until D marks
it on the calendar: "I had none" (clear 🌿: counts as 0, a win), "Add what you had", or "Don't log
this day" (ghost 👻). "?" and ghost days are left out of every figure (level, tier, Insights, steady
days, step-down count, wins, the calendar's month figures). Calendar shows ▲ / ▼ on level changes.

**One day everywhere:** daily counts (pieces today, calendar, daily Insights bars, tiers, stretch and
pull, steady days) use the waking day (wake-up to the next wake-up), so a 1 AM piece counts toward
the night before. Continuous things (the wave, receptors, the hour heatmap, the craving forecast)
keep clock time.

Month heatmap; day view with doses, cravings (with how they ended) and sleep. **Back-dating
without times is the default:** "Add what you had" takes rough counts for a day and spreads them
across the waking day (marked estimated: counted in totals and levels, not timing stats); "Add one
at an exact time" is optional. After a gap of 2+ days, "Welcome back" offers the same for each
missed day.

## 13. Settings

Plan (step back up a rung, hold period, first-piece goal, time format, Hide next piece timer, wind
down note, show steady days, Coaching tips (off; the pouch → gum swap tip is part of it), practice pace,
lighter-level practice offers, hide stretch and pull on doses, detailed charts, daily check-in), Relapse prevention mode, optional reminders, money, data (export,
import, automatic backups off the phone), products and the reference piece (change "from today on", or back-dated with a warning), sleep schedule,
unsent suggestions, Help, About, updates.

## 14. Help and feedback

Searchable Help, the tour and "Why Firewatch works this way", written once in `core/.../Help.kt`
for both apps. "Suggest something / report a bug" posts to the maker's private Google Form; failed
sends wait in Settings with Send now (never retried automatically).

## 15. Data

One frozen `records` table; new data is new JSON fields or record types; older versions keep newer
fields; deletes are tombstones. Same backup file on Android and web. See `docs/data-format.md`.
