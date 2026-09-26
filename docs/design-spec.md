# Firewatch by Baastik Labs: Design Spec

Sep 26, 2026 · @D

## Overview

Firewatch is a personal Android app by Baastik Labs that tracks nicotine use and helps D taper down and quit. It's for one person on one phone, installed directly rather than through the Play Store.

The app has two halves: a quick logger on the home screen, and an insights side full of graphs and stats. Watching the numbers trend down is the main motivator, so the insights side should be generous.

Everything runs on one unit, the piece: what a 4mg nicotine gum actually delivers into the blood. Every dose converts into pieces, so D can switch between pouches, gum and vapes while tiers, recommendations and graphs stay consistent.

This spec covers what the app does and how it should feel. How it's built is entirely up to Claude Code.

once a basic logger version with updater and calendar is up and running push it and provide a download link so i can start using it while other features are being built out.

## Top priority: automatic updates

The updater is the most important feature in this spec and outranks every other requirement in it. If anything here gets in the way of automatic updates, including branding, design or other features, the updater wins.

- **No intervention.** New versions find, download and install themselves. D never taps, approves, checks or decides anything for an update to happen.
- **Built entirely by Claude Code.** D can't do anything technical. Claude Code creates, sets up, publishes and maintains everything the updater depends on.
- **Any compromise is acceptable.** Claude Code can make whatever compromise it takes to get updates working.
- **Privacy is not a concern.** The code, the update files and wherever they're hosted can all be completely public.
- **One-time setup is the only exception.** A few steps may unavoidably need a real person, like the very first install, a one-time "allow" tap, or signing up to a website. Claude Code keeps these to the bare minimum, walks D through each in plain step-by-step language, and makes sure none ever repeats.
- **Updates never touch D's data.** Logs, products and settings survive every update. The app backs everything up automatically before installing one.
- **Every version installs over the last, forever.** D never has to uninstall, reinstall or start over.
- **Visible but quiet.** Settings shows the current version and when the app last checked. After an update, a short "What's new" screen appears.
- also an ability to roll back to the last working update encase features get buggy or we want to try a different way of implementing a feature.

## Branding

Baastik Labs is the maker's name everywhere, and D's real name shouldn't appear anywhere in or around the app. The app is called Firewatch, shown as "Firewatch by Baastik Labs."

- **Visible places:** the splash screen ("by Baastik Labs"), the about page, a small footer in settings, the copyright line, update notes and exported files.
- **Behind-the-scenes places:** anywhere someone could look up who made it, including the app's own identity details, where updates are hosted, and the code's author history.
- **Theme:** the fire theme runs through the tier names and stays exactly as written in this spec.

## The absorption engine

The app tracks estimated nicotine absorbed into the blood, not the number on the packaging. A 6mg pouch and a 4mg gum deliver different amounts, and the app accounts for that.

Every product carries three things:

- **Label strength:** the mg printed on the packaging.
- **Absorption estimate:** how much of that actually reaches the blood. Defaults come from published research and are editable. Gum is commonly cited at around half its label amount; pouches vary by brand and by how long one stays in.
- **Speed profile:** how fast it hits. A vape spikes within minutes, gum and pouches build over about half an hour, and a patch is a slow, flat line.

Nicotine's half-life is about two hours. From those three things, the app estimates how each dose rises and fades. That curve powers the blood-level graph and the timing recommendations.

- **Preloaded products:** Zyn 3mg and 6mg, nicotine gum 2mg and 4mg. D can add anything else as a custom product, like lozenges, patches, other pouch brands or vapes.
- **The reference piece:** 4mg gum by default, changeable in settings. A 2mg gum logs as half a piece; a 6mg Zyn logs as whatever its absorbed amount works out to.
- **Log-time tweaks (optional):** how long it stayed in (full, half, quick), and a coffee/soda toggle for gum. Acidic drinks around chewing noticeably cut absorption, which is why gum boxes say not to eat or drink 15 minutes before or during.
- **Honest labels:** every figure is marked as estimated. Absorption varies from person to person, so the real value is comparing D's numbers over time.

## Home screen: the quick logger

The home screen logs a dose in about two seconds, one-handed.

- **Product buttons:** big buttons for D's regular products. One tap logs it right now, with an undo. Long-press to backdate it ("had this 20 minutes ago"), change the dose, mark it partial, or tag what was going on: coffee, after food, driving, work, stress, drinking, boredom.
- **Status card:** sits at the top. It shows the current tier name, with a bar for how close D is to the next tier down. It also shows the next-piece battery, today's total in absorbed mg and pieces, and a small version of today's blood-level curve.
- **Good morning / good night buttons:** D sets a default sleep schedule once. The buttons correct it on early mornings and late nights, so waking hours stay accurate.
- **Craving button:** logs an urge D rode out without using. A second tap when it passes records how long it lasted.
- **Friend's vape button:** logs doses of unknown size as a range (see Unknown doses).

## The tier ladder

Every tier answers one question: if all of D's nicotine came from gum, how often would D be chewing? The app works it out from a 7-day rolling average against waking hours, so one heavy day doesn't swing things around.

| Tier | Pace | Pieces a day (approx.) |
| --- | --- | --- |
| Wildfire | 1 every waking hour | 16 |
| Blaze | 1 every 2 hours | 8 |
| Bonfire | 1 every 3 hours | 5 |
| Campfire | 1 every 4 hours | 4 |
| Flicker | 1 every 6 hours | 3 |
| Embers | Twice a day | 2 |
| Cinders | Once a day | 1 |
| Ash | 1 every 2 days | ½ |
| Last Wisp | 1 every 3 days | ⅓ |

- **Waking hours:** pieces a day assume about 16 waking hours.
- **Off the ladder:** anything heavier than hourly still counts as Wildfire. Below every 3 days is Clear Air, which also covers stretches with no nicotine at all.
- **Plain description:** each tier shows its name plus a plain line, like "Moderate, about 4 pieces a day."
- **Baseline week:** for the first 7 days, the app only logs and graphs. Then it reveals D's starting tier.
- **Context:** the top rungs mirror the official schedule on nicotine gum boxes: a piece every 1–2 hours for weeks 1–6, every 2–4 hours for weeks 7–9, and every 4–8 hours for weeks 10–12, then stop. This ladder keeps going with smaller steps at the bottom.

## Next-piece recommendations

The app tells D when the next piece is recommended, using a battery that recharges at the target tier's pace. It tracks two tiers: the measured tier (where D actually is) and the target tier (what D is working at). The target starts at the measured tier.

### How the battery works

- While D is awake, it refills one piece's worth per tier interval.
- Every dose drains it by that dose's piece count, so a strong pouch pushes the next window further out than a 2mg gum.
- When it's full, the screen says D is clear for one if wanted. Until then, it shows when that will be.
- It tops out at one piece, so holding off for hours never banks a double.

### Features

- **Stepping down:** once D has held at or under the target for the chosen pace (1, 2 or 3 weeks per rung), the app offers the next rung down. D accepts it; the target never changes on its own. Reaching a new rung gets a small moment on screen.
- **Stepping back up:** allowed after a rough stretch, and treated as normal, not failure.
- **Stretch time:** every minute the battery sits full while D doesn't use counts up as a stat.
- **Morning delay (optional):** a goal to push the first piece later after waking. Time from waking to first use is one of the strongest standard markers of dependence.
- **Wind-down (optional):** no "clear" signal in the last hour or so before bed. Nicotine is a stimulant, and late doses can disrupt sleep.
- **Heads-up:** a gentle flag when D doses while the last one is still peaking, like a Zyn on top of a fresh gum. Also a flag when a day heads toward the 24-pieces-a-day max printed on gum boxes.

### Ground rules

1. The app answers when asked; it doesn't invite. No "time for your next piece!" notifications by default, because a ping saying nicotine is allowed is itself a craving trigger. Any notifications are opt-in and neutral.
2. It never recommends a pace faster than D is actually using.
3. Using early just moves the next window. No red screens, no scolding and no streaks that reset. Progress shows as counts and personal bests.

## Unknown doses (a friend's vape)

When D uses a friend's vape, the app logs a range instead of faking one number. Two things drive the estimate: the vape's strength (usually printed on the device or box, like "5%" or "50mg") and roughly how much D hit it.

- **Three taps:** strength (or "no idea"), then how much: a couple of hits, a proper session, or on and off all night. Each maps to a range of puffs, using typical per-puff figures from research.
- **Shown as a range:** it logs as something like "between 1 and 3 pieces," and appears in every graph as a hatched bar or shaded band. Guesswork always looks different from confident data.
- **Unknown strength:** the app assumes a common strength and widens the range.
- **Cautious timing:** stats use the middle of the range, but the battery uses the top end, so a mystery vape never says D is clear too soon.
- **Save it:** once D knows what a friend's vape is, it's saved as its own product under the friend's name. Next time it's one tap.
- **Puff counter:** a tap-per-puff counter on the widget, for when D's phone is already in hand.
- **Borrowed tag:** these doses count as borrowed, which feeds a stat on how much nicotine comes from other people's products.

## Graphs and stats

The insights side should be packed, because more stats means more motivation to quit. Consumption lines should trend down and wins should climb. Climbing stats matter most near the end, when the downward lines flatten out.

### Today

- **Blood-level wave:** estimated nicotine in D's system across the day. Each dose is a hill or spike, sleep is shaded, and D's typical day sits behind it as a faint line.
- **Dose strip:** a 24-hour timeline of dots, sized by amount and colored by product.

### Trends

- **Daily totals:** bars of absorbed nicotine per day, with the tier bands shaded behind them and a 7-day average line on top.
- **Tier staircase:** D's tier over weeks and months, which should look like stairs going down.
- **Gap trend:** average time between pieces, week by week. This one should climb.

### Patterns

- **Heatmap:** hour of day against day of week, so the heaviest times stand out.
- **Wake-to-first-piece:** how long after waking the first piece happens, over time.
- **Trigger breakdown:** which tags show up most around doses.
- **Comparisons:** this week vs last, weekdays vs weekends.

### Going up

- **Clear hours:** hours each day D's estimated level sat near zero while awake. Probably the single best line to watch rise.
- **Overnight gap:** time from the last dose at night to the first the next morning.
- **Nicotine avoided:** a running total of mg and pieces not taken, compared with the baseline week.
- **Money saved:** compared with baseline spending, plus tins and boxes not bought, and a progress bar toward a reward D picks.
- **Mouth-free hours:** time not spent with a pouch or gum in, since each one sits there half an hour or more.
- **Craving win rate:** the share of urges ridden out.
- **Craving length:** how long urges actually last, from the craving button's two taps.
- **Beaten triggers:** how often each trigger now happens without nicotine, like "coffee: 7 of the last 10 without."

### Going down

- **Average dose size:** mg per dose, which catches moves like 6mg to 3mg even when timing stays the same.
- **Spike share:** how much nicotine arrives as fast spikes (vapes) versus slow hills (gum, pouches). Fast-hitting nicotine is generally the most habit-forming kind, so this shrinking is real progress.
- **Heaviness score:** an automatic score from how soon after waking D uses and how much per day. Those are the two things standard dependence tests weight most.
- **Background level:** a slow, smooth line estimating longer-term exposure, modeled on cotinine, the substance nicotine breaks down into. It builds and clears over days, so it drifts down even through messy days.
- **Double-ups:** how often per week doses get stacked.
- **Cravings vs doses:** urges and uses side by side. Ideally urges fade as doses drop.
- **Daily check-in (optional):** three taps for craving strength, mood and sleep, to watch withdrawal fade.

### Mix

- **Product mix:** nicotine split by delivery method over time.
- **Label vs absorbed:** what the packaging says versus what D actually absorbed.
- **Borrowed share:** how much nicotine came from other people's products.

### Forecasts

- **Arrival dates:** at the current pace, when D reaches each tier and Clear Air. The dates get closer with progress.
- **Taper speed:** average percentage drop per week.
- **Journey meter:** how far D has come from baseline to Clear Air, as one big percentage.
- **Then vs now:** the average baseline day's blood-level curve laid over the average day now.

### Milestones and fun

- **Records:** longest gap, lightest day, total stretch time, days at the current tier.
- **Insight cards:** plain-English observations, like "your heaviest hour is 2–3pm" or "weekends run about a third higher than weekdays."
- **Calendar grid:** each day colored by how much D used.
- **Monthly recap:** a year-in-review style summary with the biggest drop, longest gap, most-beaten trigger and tiers reached.
- **Tier badges:** one for each rung reached, plus firsts like the first 12-hour gap or first nicotine-free morning. Badges are never taken away.
- **Day barcode:** each day as a thin strip, dark where nicotine was in D's system and light where D was clear. Stacked into months, the light visibly spreads.
- **Silly conversions:** pouches skipped laid end to end, hours of chewing avoided, cigarettes' worth of nicotine not consumed.

### Real-world check-ins

- **Watch overlay:** if D wears a fitness watch, resting heart rate or sleep plotted next to the nicotine line.
- **Clear Air countdown:** once D reaches Clear Air, a recovery timeline built from research. For example, studies of people quitting suggest the brain's nicotine receptors take roughly 6–12 weeks nicotine-free to return to normal.

## Extras

extras to round out the app.

- **Home-screen widget:** one-tap logging, the next-piece battery and the puff counter, so most logs never need the app opened. also have a widget to log cravings and intensity very quickly.
- **Data and backup:** everything lives on the phone, and the app needs no sign-in. Export and backup let D's history survive a lost or replaced phone, and both must be simple enough for someone non-technical.

additional things to consider/ changes added after design doc was made:

need option to export and import logs

eventual port to a web app for iphones and computers no claude dependencies and saves logs locally. with option to export and import logs

need much more micro rungs at the higher end so it's easier to bridge the gap between the higher amount of daily pieces to the lower amount. the step down steps down one piece at a time instead of a jarring 16 pieces a day to 8 a day.

keeping the name as wildfire is still good though. but i think adding more micro rungs to make stepping down an easier more common action to take also a tracker that if you're getting too many cravings at a certain lower rung it offers to step you up until you're ready to make that rung work because denying how addicted you are is a perfect way to end up cracking and using worse nicotine products instead of the gum which is the best.

make sure that it has a really quick way to log cravings and intensity and this will inform the addiction tracker to make sure that you are always aware exactly how addicted you are and are not delusional about it. or in denial thinking you're on flicker when you're actually on campfire.

it needs a good way of tracking what cravings you're actually capable of riding out thats personalized. so maybe i can ride out a craving level 4 but someone else might need to keep cravings below 3 to be success full. but the person that can handle 4 will go through the rungs faster and the person that can only handle craving level 3 will take a bit more time. so that step down suggestion is personalized based on what it predicts your craving intensity will be.

nicotine quality monitor that holds gum as the gold standard and rates other delivery mechanisms lower or higher.&#32;
