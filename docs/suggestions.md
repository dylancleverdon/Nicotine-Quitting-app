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
| 2026-09-28 | Idea | Scrub along graphs to see the value at any point | Planned | `proposals/steady-days-and-dose-preview.md` §6 |
| 2026-09-28 | Idea | Choose which dates a graph shows, e.g. yesterday's wave against the dotted line | Planned | §7: "Show yesterday" on day charts; full date control only in opt-in "Detailed charts". |
| 2026-09-28 | Idea | Taper speed and arrival dates should show a default from the step-down settings right away, then adjust to real data | Planned | §8 |
| 2026-09-28 | Idea | Show how a dose affects net (e.g. a Zyn needs 38m of extra stretch); careful not to encourage stronger products | Planned | §2: neutral "Would add ≈ 38m pull / ≈ 1h 36m stretch" under each product. |

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
| 2026-09-28 | Product swap suggestions in step-down offers | Declined | |
| 2026-09-28 | Practice day before a step down | Done | 0.10.0 |
| 2026-09-28 | App lock (PIN / fingerprint) | Declined | |
| 2026-09-28 | Battery as a plain timer; dose size in stretch and pull; dose preview (replaces "fits now") | Done | 0.10.0 |
| 2026-09-28 | Scrub any chart; view earlier days on day charts (no overlay); opt-in Detailed charts | Done | 0.10.0 |
| 2026-09-28 | Taper forecast from day one | Done | 0.10.0 |
