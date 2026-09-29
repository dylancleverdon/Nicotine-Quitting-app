# Proposed update: fixes, nicotine volatility, Yesterday in review and opt-in Coaching tips

Status: **built in Android 0.11.0 / web 0.9.0** (volatility line off by default; Bridge with gum kept, opt-in). Dated 29 Sep 2026. Written against Android 0.10.2. Covers the suggestions received
28–29 Sep and the decisions made on them (see `docs/suggestions.md`).

## Summary
**Fixes**
1. Changing a dose's time doesn't always save. Also adds "2 / 3 hours ago" quick choices.
2. Spin in the app's wording: Taper speed and one Settings line. Plus a guard so it can't come back.

**Clearer core tools**

3. Spacing and net, front and centre for new users.
4. "What counts as a steady day?" answered where the count is shown.
5. Morning stretch made visible (it already counts).

**Delivery and volatility**

6. Gum delivered the way it's really used: chew and park.
7. Nicotine volatility: a running overlay on the blood-level wave and a daily volatility chart.

**Insights**

8. Yesterday in review.

**Opt-in Coaching tips (off by default)**

9. The Coaching tips switch.
10. "Bridge with gum" when mostly using pouches.

**Help articles**

11. Why pouches can be harder to cut down than gum.
12. How delivery speed relates to receptors.

**Deliberately not changing:** stretch and pull maths, late nights, the receptor model.

---

## 1. Fix: an edited dose time shows up late
**What happens.** Editing a logged dose (tap it → Change time → Save) is saved, but the list and
figures don't update straight away. The new time appears later, e.g. after the next log or when the
app is reopened (confirmed by D on 29 Sep).

**Cause.** The edit sheet closes itself *before* saving, and the save runs on the sheet's own
background task (`app/.../ui/home/HomeScreen.kt`, `EditDoseSheet`: `onDone()` then `scope.launch`).
Closing the sheet cancels that task: the write to storage usually finishes, but the step that
refreshes the screen is cut off. In the worst case the write itself could be dropped. Delete has the
same pattern, so the "Dose deleted · Undo" message can be lost too.

**Fix.**
- Run every save on a task that outlives the screen (the app's view model).
- Check every sheet and dialog for the same pattern (about 20).
- Add "2 hours ago" and "3 hours ago" to the quick "When" choices, so "a gum a few hours ago"
  doesn't need the clock picker.

## 2. Fix: spin in the wording
A read-through of all app text (Android, web and the shared Help) found:
- **Taper speed** (Android Insights): "X% heavier each week lately; that's OK, it happens" is a
  positive spin on a miss.
  - Becomes neutral: "About 4% more each week (last 4 weeks)".
  - When flat: "About level (last 4 weeks)".
  - The "lighter" case is unchanged.
- **Settings → Plan:** "Stepping back up after a rough stretch is normal, not failure" is
  consolation. It becomes the vision's framing: "Stepping up makes your level more accurate.
  Step-downs are offered when you're ready."
- **Checked and fine:**
  - "This week is 12% lighter than last week" only shows as a win.
  - "Weekends run about 20% higher than weekdays" is a neutral pattern.
  - Help's "Stepping up isn't a failure; it's the app getting more accurate" is the vision's own
    framing.

**Guard.**
- A test scans all app text for spin phrases ("that's OK", "it happens", "don't worry", "you'll
  get", "better luck") and fails the build if any appear.
- A wording read-through becomes part of every release checklist, since a test only catches known
  phrases.

## 3. Spacing and net, front and centre
Firewatch's two core tools are **spacing** (the battery) and **net** (stretch − pull).
- **Welcome tour:** a "Your two tools" screen.
  - The battery shows when your next piece fits your pace.
  - Net shows whether you're ahead of or behind that pace, counting both timing and dose size.
  - Holding net at zero or better is how you stay in control.
- **First week:** a one-time card the first time net appears: "This is your net: +40m means you're
  ahead of your pace today." Tap to learn more.
- **Help:** "Stretch, pull and net" moves to the top of the list.

## 4. What counts as a steady day?
Tapping the steady-days line on the home card shows one sentence, which also goes in Help:

> "A steady day is one with net at zero or better and no cigarettes or vapes. Days added without
> times count if you stayed at or under your level."

## 5. Morning stretch
The battery is full at wake-up, so every minute before your first piece already counts as stretch.
Make that visible:
- The battery row shows "Morning stretch: 1h 20m" until your first piece.
- Help says it plainly.

Late nights stay as they are. Your tier counts pieces per waking hour, so a longer day fairly allows
a little more at the same pace, and time after bedtime never earns stretch.

## 6. Gum: chew and park
**Today.** Gum and pouches share one speed profile, a smooth rise peaking at about 35 minutes.

**Reality.** Gum is chewed until it tingles, then parked in the cheek, then chewed again, for about
30 minutes. Nicotine is released in small bursts across that time and absorbed through the cheek,
so it arrives more slowly and more gently than a pouch.

**Change.** A new **chew-and-park** delivery profile for gum:
- **Release:** spread in pulses over the chewing time. The time is already recorded per dose:
  full ≈ 30 min, about half ≈ 15 min, quick ≈ 5 min.
- **Absorption:** slower through the cheek, peaking later (around 45–60 minutes) and lower than a
  pouch of the same strength. The total absorbed is unchanged, so pieces, stretch, pull, net and
  tiers don't move.
- **What it changes:**
  - the blood-level wave
  - "≈ mg in your system now"
  - the double-up check (a gum stays "still peaking" for longer)
  - the craving forecast
  - nicotine volatility (§7)
- **Stored:**
  - Gum products get the chew-and-park profile.
  - Past gum doses are drawn with it too, unless the product's speed was changed by hand.
  - The profile is a new value on the existing speed field, so older app versions fall back safely
    (`docs/data-format.md`).
- **Editable:** as with every product estimate, the speed can still be changed in Settings →
  Products.

## 7. Nicotine volatility
**What it measures.** How high, how low and how fast you swing between nicotine highs and lows,
as a **one-hour swing**:

> At any moment: the highest minus the lowest estimated nicotine level over the past hour, in mg.

- **Fast spikes** (a vape) give a big swing.
- **Big doses** give a bigger swing than small ones.
- **Slow, steady delivery** (chew-and-park gum, evenly spaced small pieces) gives a small swing,
  even at the same overall level.
- **Clear hours** are near zero.
- Labelled as an estimate, like every figure.

**Both charts are in Insights → Today, with the blood-level wave:**

**1. Running overlay on the wave**
- A thin line in a second colour over the wave, showing the one-hour swing through the day.
- Its own scale on the right-hand side, labelled "Volatility (mg)".
- A "Show volatility" toggle on the chart, on by default.
- Scrub to read the level and the volatility at the same moment.
- Follows the day stepper, so yesterday and earlier days work too.

**2. Daily volatility, directly under the wave**
- One bar per day: that day's average one-hour swing across the waking day (e.g. "≈ 0.6 mg"),
  with a 7-day average line.
- The last 30 days; longer ranges with "Detailed charts" on.
- Scrubbable. Caption, facts only: "Lower means steadier nicotine through the day."

**Tone.** Neutral like the rest of Insights: no red, no flags on higher days, and no new Log tab
win.

## 8. Yesterday in review
A card at the top of Insights, for yesterday's waking day, shown to everyone:
- pieces and net
- volatility (e.g. "≈ 0.6 mg")
- delivery mix (e.g. "70% pouches, 30% gum")
- longest gap between pieces
- doses stacked while the last one was still peaking
- morning stretch

It shows facts only, with no comparison with earlier days (the vision rules out negative
comparisons). With Coaching tips on (§9), up to two tips appear under the facts, drawn from them.
For example:
- "Your longest gaps started with gum."
- "Two doses were stacked after 4pm."

## 9. Coaching tips (opt-in)
- **Where:** Settings → Your plan → **Coaching tips**, off by default. Description: "Practical tips
  based on your own logs. Off means Firewatch just measures."
- **What it turns on:** the tips in Yesterday in review (§8) and "Bridge with gum" (§10).
- **Rules:**
  - no notifications
  - never in the craving flow
  - at most one tip on the Log tab at a time
  - any tip can be dismissed for 2 weeks
  - factual tone, never "you should"

## 10. "Bridge with gum" (Coaching tips)
**The problem.** A pouch every gap can become a cycle: by the time the next one is allowed, the
craving is already strong.

**The tip.** When pouches are most of your use and a smaller gum is on your home screen, a quiet
line under the battery says:

> "A gum 2 mg now is net-neutral and can take the edge off before your next piece."

**Why it's honest.** Under the current maths, a gum 2 mg taken with the battery at least half full
costs no net. For example, at exactly half full it adds 1h 36m of pull for timing and 1h 36m of
stretch for size.

**Shown only when:**
- the battery is at least half full,
- no craving is being logged,
- the tip hasn't been dismissed in the last 2 weeks.

## 11. Help: why pouches can be harder to cut down than gum
Plain facts, no lecture:
- **Stronger doses:** a Zyn 6 mg is about 1.2 pieces, so each pouch delivers more than a 4 mg gum.
- **Steady and effortless:** a pouch releases on its own; gum needs chew-and-park. That makes it
  easy to keep a pouch in for a long time or reach for another without noticing.
- **Discreet and flavoured:** usable anywhere, any time, so the habit fits more of the day.
- **Speed:** a pouch arrives faster and more smoothly than gum's stepped release, but far slower
  than a vape. Nicotine volatility (§7) shows the difference day to day.

## 12. Help: delivery speed and receptors
- The receptor healing estimate uses how much nicotine is in your body on average over 24 hours.
  More for longer means more load; less means healing.
- Faster delivery mostly strengthens the **habit**: a quick hit feels rewarding sooner, so the
  brain learns the routine faster. The estimate doesn't try to put a number on that, because there
  isn't a reliable way to.
- Where speed shows up in Firewatch: nicotine volatility (§7) and spike share.
- The receptor model itself is **not** changed.

---

## Decided against (recorded in `docs/suggestions.md`)
- Changing stretch and pull maths: kept as they are for now.
- Late-night change (stopping refills after bedtime for stretch and pull): not needed.
- Guidance cards for moving from pouches to gum, and pouch → gum swap suggestions: dropped.
- Symptom reporting: declined earlier.
- "How each product hits" chart and colouring the wave by delivery speed: replaced by nicotine
  volatility.
- A Log tab win for low volatility.

## Data
- New settings, each with a default:
  - Coaching tips (off)
  - tip dismissal times
  - "first net explained" flag
- New speed-profile value for gum (chew-and-park); older versions fall back to the existing
  profile.
- No schema change. Follows `docs/data-format.md`.
- Update `docs/design-spec.md` (§4, §6, §7, §11, §13, §14) and `core/.../Help.kt` (including
  "What is volatility?").

## Tests to add
- Editing a dose's time and closing the sheet stores the new time.
- The spin-phrase scan passes over all app text.
- Morning stretch runs from wake-up to the first piece.
- Chew-and-park gum:
  - peaks later and lower than a pouch of the same strength,
  - absorbs the same total,
  - quick and half durations shorten the release.
- Pieces, net and tiers are unchanged by the gum profile.
- Volatility:
  - near zero when clear,
  - higher for a vape than for gum of the same absorbed amount,
  - daily figure = average one-hour swing over the waking day.
- The bridging tip appears only when its rules are met.
- Yesterday in review with Coaching tips off shows no tips.

## Release notes (plain language, draft)
- Fixed: changing the time of a dose you already logged now always saves. New quick choices:
  2 and 3 hours ago.
- Gum is now estimated the way it's really used, chew and park, so it arrives more slowly and
  gently in your nicotine wave.
- New in Insights: "Yesterday in review", and **nicotine volatility**: how much your nicotine level
  swings up and down, shown over the blood-level wave and as one number per day.
- Your morning stretch now shows until your first piece.
- Taper speed now reads plainly.
- New in Settings: **Coaching tips**, practical tips from your own logs, off unless you turn it on.
- Help: why pouches can be harder to cut down than gum, and how delivery speed relates to your
  receptors.
