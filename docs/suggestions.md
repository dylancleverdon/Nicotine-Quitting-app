# Suggestions

Ideas and bug reports sent from the app ("Suggest something / report a bug" at the bottom of the
Log tab) land in the maker's private Google Sheet ("Firewatch suggestions", fed by a Google Form;
the form's field codes are in `core/.../Feedback.kt`). Rows start as **Undecided** (blank Status).

This file mirrors the decisions, without anyone's name. Before proposing anything, check both
tables so declined or finished ideas aren't raised again.

Statuses: **Undecided** · **Planned** (in a proposal, not built) · **Done** (released) ·
**Declined** · **Later** (good, not a priority now).

## From the app

| Received | Type | Suggestion | Status | Notes |
|---|---|---|---|---|
| 2026-09-27 | Idea | "hey testing" | Declined | Test entry. |
| 2026-09-27 | Idea | Report symptoms you think are nicotine related (headaches, restless legs, mood) to inform receptor healing or other insights | Declined | Would make an estimate look more precise than it is. |
| 2026-09-28 | Idea | Scrub along graphs to see the value at any point | Done | 0.10.0 |
| 2026-09-28 | Idea | Choose which dates a graph shows, e.g. yesterday's wave against the dotted line | Done | 0.10.0: step back to earlier days; ranges in opt-in "Detailed charts". |
| 2026-09-28 | Idea | Taper speed and arrival dates should show a default from the step-down settings right away, then adjust to real data | Done | 0.10.0 |
| 2026-09-28 | Idea | Show how a dose affects net (e.g. a Zyn needs 38m of extra stretch); careful not to encourage stronger products | Done | 0.10.0; 0.10.2 adds a switch to hide it. |
| 2026-09-28 | Idea | Highlight and explain stretch, pull and net for new users; spacing and net are the two core tools | Done (0.11.0) | `proposals/coaching-tips-and-fixes.md` §3 |
| 2026-09-28 | Idea | Why are Zyns more addictive than gum? | Done (0.11.0) | §11 (Help article) |
| 2026-09-28 | Idea | Stretch for delaying the first piece in the morning; balance pieces taken after bedtime | Done (0.11.0) | §5: morning already counts, now shown. Late nights unchanged (tiers already count per waking hour). |
| 2026-09-29 | Idea | What counts as steady? | Done (0.11.0) | §4 |
| 2026-09-29 | Idea | Yesterday in review in Insights, with delivery and timing analysis and tips | Done (0.11.0) | §8: facts for everyone; tips only with Coaching tips on. |
| 2026-09-29 | Idea | Suggest a gum before the timer when mostly using pouches, and explain why | Done (0.11.0) | §10 (Coaching tips) |
| 2026-09-29 | Idea | More guidance on dropping pouches without being preachy | Declined | Dropped along with pouch → gum swaps. |
| 2026-09-29 | Idea | How do pouches and faster absorption affect receptors and healing? | Done (0.11.0) | §12 (Help) and §7 (nicotine volatility); receptor model unchanged. |
| 2026-09-29 | Idea | A mode that gives quitting guidance, so nobody gets unwanted guidance | Done (0.11.0) | §9: Coaching tips, off by default. |
| 2026-09-29 | Bug | Taper speed said "that's OK, it happens": a positive spin on a miss | Done (0.11.0) | §2: plus one Settings line; a test bans spin phrases. |
| 2026-09-29 | Bug | Time changes don't save when entering a gum from a few hours ago | Done (0.11.0) | §1: the edit sheet cancels its own save; plus 2h/3h quick chips. |

## From design reviews

Ideas raised while reviewing the app, not sent through the form.

| Date | Idea | Status | Notes |
|---|---|---|---|
| 2026-09-27 | Battery: no debt, fresh start every morning, late-night catch-up | Done | 0.4.0 |
| 2026-09-27 | Stretch, pull and net on home and in Insights | Done | 0.4.0 |
| 2026-09-27 | Cheer after waiting for a full battery | Done | 0.4.0 |
| 2026-09-28 | Size-honest pull, one estimate everywhere, "fits now" line | Done | 0.4.1; "fits now" to be replaced by §2 of the new proposal |
| 2026-09-28 | Relapse prevention mode, welcome tour, Help, "Why Firewatch works this way", "just starting gum" onboarding | Done | 0.5.0 |
| 2026-09-28 | Steady-days step-down gate (earn step-downs with steady days) | Declined | Replaced by counting steady days as a win (§4). |
| 2026-09-28 | Pieces today by waking day | Done | 0.10.0 |
| 2026-09-28 | Watch overlay uses the shared dose maths | Later | Watch not a priority. |
| 2026-09-28 | Mouth-time statistic | Done (removed) | 0.10.0 |
| 2026-09-28 | Changing the reference piece: "from today" or back-dated with a warning | Done | 0.10.0 |
| 2026-09-28 | Web app: check storage protection, "last backup" line, local auto-backup | Later | Web app not a priority. |
| 2026-09-28 | Faster battery maths (dose to dose, not minute by minute) | Done | 0.10.0 |
| 2026-09-28 | One shared "which day" rule | Done | 0.10.0 |
| 2026-09-28 | Continuity tests | Done | 0.10.0 |
| 2026-09-28 | Confidence label on the level ("6 of 7 days logged") | Declined | Too much information. |
| 2026-09-28 | Absorption check-in per product | Declined | Estimates stay fixed per product. |
| 2026-09-28 | Steady days total with milestones | Done | 0.10.0 |
| 2026-09-28 | Trigger plans | Declined | |
| 2026-09-28 | Product swap suggestions in step-down offers | Declined | Also declined as an opt-in tip (2026-09-29). |
| 2026-09-28 | Practice day before a step down | Done | 0.10.0 |
| 2026-09-28 | App lock (PIN / fingerprint) | Declined | |
| 2026-09-28 | Battery as a plain timer; dose size in stretch and pull; dose preview (replaces "fits now") | Done | 0.10.0 |
| 2026-09-28 | Scrub any chart; view earlier days on day charts (no overlay); opt-in Detailed charts | Done | 0.10.0 |
| 2026-09-28 | Taper forecast from day one | Done | 0.10.0 |
| 2026-09-29 | Gum delivered as chew-and-park (slower, stepped release) | Done (0.11.0) | `proposals/coaching-tips-and-fixes.md` §6 |
| 2026-09-29 | "How each product hits" chart; wave coloured by delivery speed | Declined | Replaced by nicotine volatility. |
| 2026-09-29 | Nicotine volatility: one-hour swing overlay on the wave, daily volatility chart | Done (0.11.0) | `proposals/coaching-tips-and-fixes.md` §7 |
| 2026-09-29 | Rethink stretch and pull (e.g. live net, net in pieces) | Declined | Kept as they are for now. |
| 2026-09-29 | Late nights: stop refills after bedtime for stretch and pull | Declined | Tiers already count per waking hour. |
| 2026-09-29 | Step-down progress ("1 of 3 days held") in Insights → Forecasts | Done | 0.12.0 |
