# Proposed update: known days, practice pace, honest volatility and level history

Status: **building for Android 0.13.0 / web 0.10.0** (decisions of 2 Oct below; web caught up with Android in the same release). Dated 2 Oct 2026. Written against Android 0.12.0. Covers the
suggestions received 29 Sep – 1 Oct and the open items from the 30 Sep review (see
`docs/suggestions.md`).

## Decisions (2 Oct, from D)
- Spelling: **practice** everywhere, noun and verb.
- "Try it for a day" on the step-down offer becomes practice pace, then the same morning question.
- Steady days: the old "empty day counts if a craving or Good morning/night was logged" rule goes;
  only clear 🌿 days count as empty steady days. **Steady days are recalculated once** under the new
  rules (the total can drop once; after that it only goes up).
- Practice pace has a **duration choice: "Turn off at bedtime" or "Leave it on until I turn it off"**.
  It's in Settings → Your plan → Practice pace (changeable any time, even while running), and on a
  second screen after accepting an offer (so the offer card isn't crowded).
- **"Work from your measured level"** (§8) is offered once practice pace at the measured pace has
  covered **at least 75% of the hold period's waking hours** (e.g. a 3-day hold: 75% of 3 × 16 h =
  36 h), counted over the run since it was turned on, with practice net ≥ 0. Choices: Work from X /
  Keep practicing / Back to Y pace.
- A multi-day run shows "Practice net +1h 10m · day 3"; level, steady days and the step-down count
  stay against the real level.
- Volatility is recalculated in mg/h, Yesterday in review included.
- **Web app** gets all of this and catches up with every Android feature a browser can do
  (not notifications, widgets or the watch).
- Offer wording (D approved):
  > **Your logs measure Campfire · 4 a day**
  > You're working at Blaze · 7. Practicing Campfire pace for a while is a way to check it. If it
  > suits you, you can then choose to work from Campfire: a more accurate level.
  > Practicing is just practice. You can stop any time and go back to your usual pace, with no step
  > down.
  > [Try Campfire pace] [Stay here]  ☐ Don't ask me again

## Summary
**Honest days**
1. Zero days become "?" days until you say what happened: clear, add what you had, or ghost.
2. One rule for which days count, applied everywhere.
3. A confidence chart in Insights: how many recent days are known.

**Your level and stepping down**

4. One step-down count everywhere (fixes "0 of 3" vs "around 30 Sep").
5. Level history in Insights, and ▲ / ▼ marks on the calendar.
6. **Practice pace:** try a lighter pace for the day, from Settings or an offer.
7. Lighter-level practice offer, with "Don't ask me again".
8. "Work from your measured level" after a successful practice at that pace.
9. A short note on short hold periods in Settings.

**Insights**

10. Volatility measures how fast *and* how much your nicotine level changes.

**Tidy-up**

11. The pouch → gum swap tip moves behind Coaching tips.

---

## 1. Zero days: clear, "?", or ghost
**The problem.** A day with nothing logged counts as 0 pieces today. A forgotten day pulls your
measured level down, which flatters you (`core/.../engine/Progress.kt` `rollingAverage`).

**Each full waking day gets one state:**

| State | What it means | How it gets there |
|---|---|---|
| **Logged** | At least one dose | Logging, back-dating |
| **Clear** 🌿 | A real 0-nicotine day | You mark it "I had none" |
| **?** | No doses, and the app can't tell why | Automatic, after the day ends |
| **Ghost** 👻 | You chose not to log it | You mark it "Don't log this day" |

- A day becomes "?" only once its waking day is over (at your next wake-up), so today never shows
  "?". Days before you finished setting up are never "?".
- Opening the app, logging cravings or Good morning / night do **not** make a zero day clear. Only
  "I had none" does.
- **No prompt anywhere.** "?" days are fixed from the calendar only. Tap the day:
  - **"I had none"** → Clear 🌿
  - **"Add what you had"** → the usual back-dating sheet (rough counts, no times needed)
  - **"Don't log this day"** → Ghost 👻
- **Calendar:** "?" days show a faint "?" instead of a colour; ghost days show faded with 👻; clear
  days show 🌿. When any "?" days are visible, the calendar shows a short note:
  "? = nothing logged that day. Tap one to mark it clear, add what you had, or leave it out. Until
  then it's left out of your figures."
- Existing zero days become "?" days too. There's no bulk question; fix them from the calendar any
  time.

## 2. One rule for which days count
**Clear days count fully:** 0 pieces in your level, a steady day, a held day for the step-down
count, and a 🌿 on the calendar as a win.

**"?" and ghost days are left out**, the same way, everywhere:
- your 7-day measured level and tier
- all Insights charts and figures, including:
  - daily totals, averages, records (e.g. "lightest day")
  - taper speed, forecasts, the "If you take each step" plan
  - stretch & pull, Yesterday in review
  - volatility, clear hours, money saved
- the calendar's "This month" figures (clear days counts only 🌿 days)
- steady days (not added, total never goes down)
- the step-down count (**skipped, not reset**: people have off days)
- Log tab wins

**Guard.** A continuity test builds the same data with logged, clear, "?" and ghost days and checks
that every screen's figures agree.

## 3. Confidence chart (Insights only)
- In Insights → Trends, next to your level: a strip of the last 30 days, one dot per day (logged,
  clear, ghost, "?"), titled "27 of 30 days known".
- In Insights only, the measured level line gets "(6 of 7 days known)" when any of the last 7 days
  aren't known. The Log tab stays uncluttered.

## 4. One step-down count everywhere
- **"Next step down"** (Insights → Forecasts) is the source of truth: full days in a row at or
  under your level since the last level change. "?" and ghost days are skipped.
- **"If you take each step"** starts from that count. At "0 of 3" its first date is 3 known days
  away, never sooner.
- **"Held Blaze for N days"** (Log tab, Milestones) stays a running total that never resets, worded
  so it can't be mistaken for the step-down count.
- **Test:** the card, the offer and the plan's first date must always agree.

## 5. Level history, and calendar marks
- **Insights → Ladder → "Level history"**, newest first:
  > 1 Oct · Blaze 7 → 6 · stepped down
  > 28 Sep · Bonfire 5 → Blaze 7 · stepped up: a craving ended with a vape
  > 27 Sep · Practised Campfire pace 2 pm – 9 pm: practice net +40m
- The reason for each step up, and each practice session, is stored from now on. Older entries show
  just the direction.
- **Calendar:** ▼ or ▲ on days your level changed. The day view says "Level: Blaze 7 → 6".

## 6. Practice pace
**What it is.** Try a lighter pace for the rest of the day without changing your level.

**Turning it on**
- **Settings → Your plan → Practice pace:** choose a rung, then "Start". "Back to my pace" turns it
  off, any time, including halfway through the day.
- From the step-down offer's "Try it for a day", or the lighter-level offer (§7).
- When it's accepted from an offer, a line says: "You can stop any time in Settings → Your plan →
  Practice pace."
- **Ends automatically at bedtime.** Each day starts at your real pace.
- **Unavailable in Relapse prevention mode.** Trying to turn it on shows: "Practice pace is off
  while Relapse prevention mode is on, because its reminders follow your level's gap."

**Which rungs you can practise**
- Down to your **measured level** (however many rungs below your working level that is, because
  your logs show you already live there), **or** one rung below your working level, whichever is
  lighter.
- Never further. Someone at 7 measuring 4 can practise 6, 5 or 4. Someone at 4 measuring 4 can
  practise 3.

**While it's on**
- **Battery row:** labelled "Practice pace · Campfire 4" with a **?** button. Tapping it explains in
  two lines what practice pace is and how to stop it in Settings.
- **Battery and dose preview** use the practice gap. Switching mid-day keeps the battery's charge
  and only changes the refill speed from then on.
- **Practice net** shows on the home card ("Practice net +40m"), with no stretch or pull figures.
  It's calculated only over the hours practice pace was on (added together if switched on and off),
  against the practice gap.
- **Unchanged:** your level and tier, measured level, normal net / stretch / pull (still against
  your real level), steady days, the step-down count, the taper plan and forecasts.
- Every session is recorded in Level history (§5).

## 7. Lighter-level practice offer
**The card:**
> "Your last 7 days measure Campfire · 4 a day. Try Campfire pace for a day?"
> [Try it for a day] [Stay here]
> ☐ Don't ask me again

**Shown only when all of these are true:**
1. At least your hold period has passed since your **last level change of any kind** (start, step
   up, step down). A step up within the hold period means no offer.
2. Your measured level is **2 or more rungs** lighter than your working level. One rung is already
   covered by the step-down count.
3. At least 6 of the last 7 days are known (logged or clear).
4. Not in your first week, not in Relapse prevention mode, and practice pace isn't already on.

**Behaviour**
- "Stay here" hides it for a full hold period. It then has to meet the rules again.
- Ticking "Don't ask me again" turns off **Settings → Your plan → Lighter-level practice offers**,
  which can be turned back on there.
- It only ever offers to *practise*. It never changes your level by itself.

## 8. "Work from your measured level"
After a practice day **at your measured pace**, if practice pace was on for at least **75% of your
waking hours** that day with **practice net ≥ 0**, the next morning offers:

> "Your logs show Campfire · 4 a day. Work from there?" [Work from Campfire] [Stay here]

- It's a correction to your real level, not a taper step. It still needs everything in §7's rules
  1–4.
- It **counts as a level change**: the step-down count starts over, so you hold the new level for a
  full hold period before the next step down.
- Real tapering stays **one rung at a time, at most once per hold period**. There's no two-rung step
  down.

## 9. Note on short hold periods
In Settings, next to the hold period, shown only when it's set below 7 days:
> "Shorter holds mean faster steps. Each step is easier to keep with more days at it."

## 10. Volatility: how fast and how much
**The measure.** How steep your nicotine wave is on average, with steep stretches counting extra:
at each minute, take how fast the level is changing (mg per hour), square it, average over the
period, then take the square root (a root-mean-square of the rate of change).
- Bigger changes score higher, and fast changes score much higher.
- Steady or clear time is near zero.

One dose each over 3 hours, with Firewatch's estimates:

| Dose | Volatility | Steepest climb |
|---|---|---|
| Vape, 1 piece's worth | 4.4 mg/h | 47 mg/h |
| Zyn 6 mg | 2.1 mg/h | 11 mg/h |
| Gum 4 mg (chew and park) | 1.1 mg/h | 4 mg/h |

- **Running overlay on the wave:** the same calculation over the last 30 minutes, as a line with
  its own "Volatility (mg/h)" scale. The "Show volatility" toggle stays.
- **Daily chart:** the same calculation over the whole waking day, one bar per day with a 7-day
  average line. "?" and ghost days are left out.
- The name stays **Volatility**. Help gets an updated "What is volatility?".

## 11. Swap tip behind Coaching tips
"Swapping pouches for gum would move today from 🍎 to 🥦" (Log tab and Insights → Going down)
shows only with Coaching tips on.

---

## Decided against (recorded in `docs/suggestions.md`)
- Asking about zero days in Insights, or in a bulk prompt: fixed from the calendar instead.
- Counting "opened the app" as a clear day.
- A two-rung step down: replaced by practice limits and "Work from your measured level".
- Naming it "Challenge mode": it framed tapering as a test you pass or fail.

## Data
- **New record type `daymark`** (date, state: `clear` or `ghost`). Back-dated doses still use
  `estimated: true`. "?" is never stored; it's worked out from the logs.
- **New record type `practice`** (start, end, rung) for practice sessions.
- **New JSON field on rung changes:** `detail` (default empty), the reason for a step up or "from
  measured level".
- **New settings, each with a default:**
  - lighter-level practice offers (on)
  - lighter-level offer snooze time
  - the current practice session (none)
- No schema change. Follows `docs/data-format.md`.
- Update `docs/design-spec.md` (§5–§8, §11–§13) and `core/.../Help.kt`:
  - "?" and ghost days
  - practice pace
  - the lighter-level offer
  - volatility
  - level history

## Tests to add
- A zero day becomes "?" only after its waking day ends; "I had none" makes it clear; ghost and "?"
  are left out of every figure; clear days count as 0.
- Step-down count skips "?" and ghost days without resetting, and matches the plan's first date.
- Practice pace:
  - its rung limits,
  - it ends at bedtime,
  - it's blocked in Relapse prevention mode,
  - switching mid-day keeps the battery charge,
  - practice net covers only practice hours,
  - it never changes level, net, steady days or the step-down count.
- Lighter-level offer: hidden within a hold period of any level change, below 2 rungs lighter,
  under 6 of 7 known days, or after "Don't ask me again".
- "Work from your measured level": needs 75% of waking hours at practice pace with practice
  net ≥ 0; restarts the step-down count.
- Volatility: a vape scores higher than gum of the same amount; near zero when clear.
- The swap tip is hidden with Coaching tips off.
- `WordingTest` passes, and a read-through of all new wording before release.

## Release notes (plain language, draft)
- Days with nothing logged now show a "?" on the calendar and are left out of your figures until you
  tap them: mark a clear day 🌿, add what you had, or leave the day out 👻. Clear days count as
  wins.
- New in Insights: how many recent days are known, your level history, and step ups and downs
  marked on the calendar.
- New: **Practice pace.** Try a lighter pace for the rest of the day from Settings → Your plan. It
  never changes your level and ends at bedtime.
- If your logs show you're well below your level, Firewatch may offer a practice day at that pace,
  and then to work from there.
- Volatility now measures how fast your nicotine level changes, as well as how much.
- "Next step down" and "If you take each step" now always agree.
- The pouch → gum swap tip now only shows with Coaching tips on.
