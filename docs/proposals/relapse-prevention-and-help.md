# Proposed update: Relapse prevention mode, first-run guide and Help

Status: **built in Android 0.5.0 / web 0.3.0.** The reminder gap follows the tier (2 hours before there's a tier); both optional extras are included. Covers the Android app and the web app.

## Summary
1. **Relapse prevention mode**: an optional mode that reminds you to chew gum at a steady gap,
   so you stay ahead of cravings and don't go back to smoking or vaping.
2. **New onboarding option**: "I'm just starting gum and want help sticking to it".
3. **Welcome screens**: a few short screens on first launch explaining how the app works.
4. **"Why Firewatch works this way"**: a page explaining the thinking behind the app.
5. **Help**: a place to learn every feature and how to do things.

Look and feel stay the same as the rest of the app. The only new visual elements are the
"Relapse prevention mode" button and a small indicator while the mode is on.

---

## 1. Relapse prevention mode

### Why
Early on, the best way to stay on gum (and off cigarettes or vapes) is to chew *before* cravings
start. That means regular reminders, which is the opposite of the app's usual "no nagging" rule.
So it's a separate mode that you choose to turn on and can turn off at any time.

### Turning it on
- A **"Relapse prevention mode"** button at the bottom of the home screen.
- Tapping it opens a short explanation:
  - **What it is:** a reminder to chew a piece at a steady gap.
  - **Why:** staying ahead of cravings early on makes going back to smoking or vaping much less
    likely. It sounds backwards for an app about cutting down, and that's intentional for now.
  - **How to turn it off:** the same button, any time.
  - Buttons: **Turn on** / **Not now**.
- **Android:** a one-time "allow notifications" tap the first time it's turned on.

### Settings
- **Gap between pieces:** every 2 hours by default, and adjustable.
- The product the reminder is for (nicotine gum by default).

### Reminders
- One reminder per scheduled piece.
- Silent during sleeping hours.
- Skipped if a piece was logged recently, and the timer restarts from that piece.
- A **"Log it"** button on the notification logs the piece in one tap.
- Supportive wording, never scolding. For example: "Piece time: staying ahead of cravings." Never
  "you missed one".

### While it's on
- **A small indicator** on the home screen shows "Relapse prevention mode is on".
- The battery row becomes **"Next scheduled piece at …"**.
- **Stretch and pull are paused,** because waiting longer isn't the goal in this mode.
- **Tiers, the 7-day average and pieces today stay exactly as honest as before.**
- Days in the mode are marked in Insights (and the calendar), so heavier weeks make sense later.

### Recommendation card
A one-time card on the home screen suggests the mode when any of these apply:
- a cigarette or vape (including Friend's vape) was logged recently, or
- the person is in their first weeks of using gum at 6 or more pieces a day, or
- (optional) frequent strong cravings.

Once dismissed, it's **not shown again for 2 weeks**. It never appears while the mode is on.

### Moving on (optional)
After a few steady weeks in the mode, a one-time card could offer: "You've been steady for 4 weeks.
Ready to switch to tapering?" It's an offer, never automatic. If dismissed, it's not shown again
for 2 weeks.

### Web app
The web app shows the mode and its indicator. It honestly notes that **reminders are Android-only
for now**: dependable reminders on iPhone would need a server, and the web app deliberately has
none. The "Next scheduled piece" row still works.

### Project rule change
`CLAUDE.md` and the design spec say: no "time for your next piece" notifications. This proposal
rewords that to: **no next-piece notifications, except in the opt-in Relapse prevention mode.**
That way future work keeps the exception instead of removing it by accident.

---

## 2. New onboarding option
Next to the existing baseline options:

> **I'm just starting gum and want help sticking to it**

Picking it:
- sets nicotine gum as the main product,
- turns on Relapse prevention mode with a 2-hour gap (and asks for notification permission on
  Android),
- still measures use in the background, so tiers and tapering are ready when the person is.

---

## 3. Welcome screens (first launch)
A few short swipeable screens, skippable, and reachable again from Help:
1. **What Firewatch does:** shows where you really are with nicotine, and helps you taper at your
   own pace.
2. **Pieces:** everything counts in pieces, where one piece is what a 4 mg gum delivers. Every
   number is an estimate.
3. **The battery:** a suggestion for spacing, not an order. It starts fresh every morning.
4. **Tiers:** you find your level, and a step down is *offered* once you've held it.
5. **No nagging:** no red screens, and no streaks that reset. Relapse prevention mode is the one
   opt-in exception.

---

## 4. "Why Firewatch works this way"
A longer, plain-language page, reachable from the welcome screens and from Help:
- **Measure, don't judge:** honest numbers are more useful than guilt.
- **Pieces:** why every product is converted into one common unit, and why it's an estimate.
- **One piece at a time:** why step-downs are small.
- **The battery:** why it's a suggestion, why it forgives every morning, and why the wait is never
  more than one gap.
- **Stretch, pull and net:** what they measure and why net is the honest summary.
- **Why there's no step-down button:** a step down is offered when you've shown you can hold your
  current level, so each step is one you're ready for.
- **Why stepping up is always allowed:** a tough stretch is normal, not a failure.
- **Relapse prevention mode:** why it's the one exception to the "no reminders" rule.

---

## 5. Help
- A **Help** entry in Settings, plus a small **?** on the home screen.
- A searchable list of short articles.

**How to…**
- Log a dose; change its time or amount; undo or edit it
- Log a friend's vape
- Use Good morning / Good night
- Step up your taper
- Export and import a backup (and move between phone and web)
- Add a widget
- Turn Relapse prevention mode on or off

**What it means**
- The battery, "Next piece" and "Clear for one"
- Stretch, pull and net
- Tiers and the 7-day average
- Nicotine quality
- The Insights charts

**Questions**
- Why is there no step-down button?
- Why does the battery reset every morning?
- Why does it say "estimate" everywhere?

The Help text is written once and used by both the Android and web apps, so they never drift apart.

---

## Data
- New settings fields only, each with a default:
  - relapse prevention on/off
  - gap
  - reminder product
  - when the recommendation card was last dismissed
  - whether the welcome screens have been seen
- No schema change. It follows `docs/data-format.md`, so older versions keep the new fields.

## Tests to add
- The recommendation shows for a recent cigarette or vape, or for early heavy gum use; stays hidden
  for 2 weeks after dismissal; never shows while the mode is on.
- Reminder timing: every gap while awake, none while asleep, restarts after a logged piece.
- The "just starting gum" onboarding sets gum as the main product, a 2-hour gap and the mode on.
- Stretch and pull are paused on days in the mode; tiers are unchanged.

## Release notes (plain language, draft)
- New: **Relapse prevention mode.** If you're new to gum, Firewatch can remind you to chew at a
  steady gap so cravings don't catch you off guard. It's off unless you turn it on.
- New: "I'm just starting gum" option when setting up.
- New: a short welcome tour, a "Why Firewatch works this way" page, and Help for every feature.
