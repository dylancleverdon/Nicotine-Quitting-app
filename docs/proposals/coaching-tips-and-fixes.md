# Proposed update: two fixes, clearer core tools, and opt-in Coaching tips

Status: **proposed, not built.** Written against Android 0.10.2. Covers the 11 suggestions
received 28–29 Sep (see `docs/suggestions.md`).

## Summary
**Fixes**
1. Changing a dose's time doesn't always save.
2. The taper speed card spins a heavier stretch ("that's OK, it happens").

**Clearer core tools**

3. Put spacing and net front and centre for new users.
4. "What counts as a steady day?" answered where the count is shown.
5. Morning stretch is already counted; say so. Late-night pieces stop getting free refills.

**Opt-in Coaching tips (off by default)**

6. A Settings switch that turns on practical guidance, so nobody gets tips they didn't ask for.
7. Yesterday in review (the facts for everyone; tips only with Coaching tips on).
8. "Bridge with gum" when mostly using pouches.
9. Guidance for moving from pouches to gum.

**Help articles**

10. Why pouches can be harder to cut down than gum.
11. How delivery speed relates to receptors (and why the healing estimate doesn't use it).

---

## 1. Fix: changing a dose's time doesn't always save
**What happens.** Editing a logged dose (tap it → Change time → Save) can lose the change.

**Likely cause.** The edit sheet closes itself *before* saving, and the save runs on the sheet's
own background task. When the sheet closes, that task is cancelled, so the write can be dropped
part-way (`app/.../ui/home/HomeScreen.kt`, `EditDoseSheet`: `onDone()` then `scope.launch`).
Logging from the hold sheet uses the home screen's task, which is why new logs are usually fine.

**Fix.**
- Run every save on a task that outlives the screen (the app's view model).
- Check every sheet and dialog for the same pattern. There are about 20.
- A test: edit a dose's time, close the sheet, and the new time is stored.

**Also.** Add "2 hours ago" and "3 hours ago" to the quick "When" chips, so "a gum a few hours ago"
doesn't need the clock picker.

## 2. Fix: taper speed wording
The Taper speed card says "X% heavier each week lately; that's OK, it happens". That's a positive
spin on a miss, which the vision rules out.
- **New wording, neutral like a tool:** "About 4% more each week (last 4 weeks)". When it's
  roughly flat: "About level (last 4 weeks)". The "lighter" case stays as is.
- **Guard against it coming back:** a test that scans all app text (Help, cards, messages) for
  spin phrases such as "that's OK", "it happens", "you'll get", "don't worry" and fails if any
  appear.

## 3. Spacing and net, front and centre
Your two core tools are **spacing** (the battery) and **net** (stretch − pull). New users should
learn that on day one.
- **Welcome tour:** one screen, "Your two tools". The battery shows when your next piece fits your
  pace. Net shows whether you're ahead of or behind that pace, counting both timing and dose size.
  Holding net at zero or better is how you stay in control.
- **First week:** a one-time card the first time net appears. "This is your net: +40m means you're
  ahead of your pace today. Tap to learn more."
- **Help:** "Stretch, pull and net" moves to the top of Help.

## 4. What counts as a steady day?
- Tapping the steady-days line on the home card shows one sentence: "A steady day is one with net
  at zero or better and no cigarettes or vapes. Days without times count if you stayed at or under
  your level."
- The same wording goes in Help.

## 5. Morning stretch and late nights
**Morning (already works).** The battery is full at wake-up, so every minute before your first
piece already counts as stretch. The change is to make that visible:
- The battery row shows "Morning stretch: 1h 20m" until the first piece.
- Help says it plainly.

**Late night (the balance).** After bedtime, the timer catches up so the guidance stays useful.
But that catch-up also acts as a free refill for stretch and pull: someone who stays up 4 hours
late gets more than a piece of room without any pull.
- **Change:** for stretch and pull only, the battery stops refilling at bedtime. A piece after
  bedtime counts pull for the room it didn't have when you went to bed.
- **Unchanged:** the "Next piece" guidance still catches up at night, and late pieces still belong
  to the night before.
- **Symmetry:** holding off in the morning earns stretch; staying up late doesn't earn refills.

## 6. Coaching tips (opt-in)
- **Where:** Settings → Your plan → **Coaching tips**, off by default. A one-line description:
  "Practical tips based on your own logs. Off means Firewatch just measures."
- **What it turns on:** the tips in §7, §8 and §9. Nothing else changes.
- **Rules:**
  - no notifications,
  - never in the craving flow,
  - at most one tip on the Log tab at a time,
  - every tip can be dismissed for 2 weeks,
  - tone: factual, never "you should".

## 7. Yesterday in review
A card at the top of Insights, for yesterday's waking day.
- **For everyone, facts only:**
  - pieces and net,
  - delivery mix (e.g. "70% pouches, 30% gum"),
  - longest gap,
  - doses stacked while the last one was still peaking,
  - morning stretch.
- **With Coaching tips on:** up to two tips drawn from those facts. For example:
  - "Your longest gaps started with gum."
  - "Two doses were stacked after 4pm; a gum 2 mg there would have been net-neutral."
- No comparisons with earlier days (vision: never show a negative comparison).

## 8. "Bridge with gum" (Coaching tips)
**The problem.** A pouch every gap can become a cycle: by the time the next one is allowed, the
craving is already strong.

**The tip.** When pouches are most of your use and a smaller gum is on your home screen, a quiet
line under the battery offers a small bridging dose before the next pouch:

> "A gum 2 mg now is net-neutral and can take the edge off before your next piece."

**Why it's honest.** Under the current maths, a gum 2 mg taken with the battery half full adds
1h 36m of pull for timing and 1h 36m of stretch for size, so net is 0. It really doesn't cost
anything against the pace.

**Rules.** Shown only when:
- the battery is at least half full,
- no craving is being logged,
- the tip hasn't been dismissed in the last 2 weeks.

## 9. Moving from pouches to gum (Coaching tips)
Short, optional guidance cards, one at a time, only while pouches are more than half of use:
- **Swap one:** replace one pouch a day with a gum 4 mg. Firewatch shows the size effect (e.g. at
  Bonfire, a Zyn 6 mg → gum 4 mg is 38m less pull).
- **Step the strength:** Zyn 6 mg → Zyn 3 mg, shown the same way.
- **The quality score** (already in Insights) is how progress shows up.

This revisits "product swap suggestions", which was declined when it would have appeared in every
step-down offer. Here it lives only inside opt-in Coaching tips.

## 10. Help: why pouches can be harder to cut down than gum
Plain facts, no lecture:
- **Stronger doses:** a Zyn 6 mg is about 1.2 pieces, so each pouch delivers more than a 4 mg gum.
- **No effort to use:** there's no chewing technique, so it's easy to keep one in for a long time
  or reach for another without noticing.
- **Discreet and flavoured:** usable anywhere, any time, which makes the habit fit more of the day.
- **Similar speed to gum:** both peak after about half an hour in Firewatch's estimates; the
  difference is mostly dose and habit, not speed.

## 11. Help: delivery speed and receptors
- The receptor healing estimate uses how much nicotine is in your body on average over 24 hours.
  More nicotine for longer means more load; less means healing.
- Faster delivery (vapes, cigarettes) mostly strengthens the *habit*. A quick hit feels rewarding
  sooner, so the brain learns the routine faster. The estimate doesn't try to model that, because
  there isn't a reliable way to put a number on it.
- The **spike share** chart (Insights → Going down) is where fast delivery shows up.
- The receptor model itself is **not** changed.

---

## Data
- New settings, each with a default:
  - Coaching tips (off)
  - tip dismissal times
  - "first net explained" flag
- No schema change. Follows `docs/data-format.md`.
- Update `docs/design-spec.md` (§6, §7, §11, §13, §14) and `core/.../Help.kt`.

## Tests to add
- Editing a dose's time and closing the sheet stores the new time.
- The spin-phrase scan passes over all app text.
- Morning stretch runs from wake-up to the first piece.
- A piece after bedtime counts pull for the room it didn't have at bedtime. The guidance timer is
  unchanged.
- The bridging tip appears only when its rules are met and says net-neutral only when that's true.
- Yesterday in review with Coaching tips off shows no tips.

## Release notes (plain language, draft)
- Fixed: changing the time of a dose you already logged now always saves. New quick choices:
  2 and 3 hours ago.
- Taper speed now reads plainly, without commentary.
- New: "Yesterday in review" at the top of Insights.
- New in Settings: **Coaching tips**, practical tips from your own logs, off unless you turn it on.
- Your morning stretch now shows until your first piece. Pieces after bedtime no longer get free
  room.
- Help: why pouches can be harder to cut down than gum, and how delivery speed relates to your
  receptors.
