# Proposed update: Life at Clear Air, themes, accessibility and new charts

Status: **built in Android 0.14.0 / web 0.11.0.** At Clear Air an empty day counts as nicotine-free without marking (D logs only when they had some). Dated 2 Oct 2026. Written against Android 0.13.0 / web 0.10.0.
Decisions below are D's (2 Oct). Both apps, one release.

## Summary
1. **Life at Clear Air:** a calm Log tab for the finish line, with craving logging as the focus.
2. **Themes:** a hidden "More themes" menu with 18 colour themes and 4 colour switches.
3. **Accessibility pass:** text that scales, screen-reader labels, contrast checks.
4. **"Install on iPhone" card** for the web app.
5. **Remove the Android backup and check-in reminders**, which send notifications and so break the
   only-Relapse-prevention-notifies rule. "Last backup: 34 days ago" goes in Settings instead.
6. **Eleven new charts.**

Already done: "Undo: back to ?" on marked days (0.13.0). No 5-second undo.

---

## 1. Life at Clear Air
**When it begins**
- When you step down to Clear Air.
- Also offered (never automatic) when your logs measure zero for 7 known days, even if your
  working level is higher: "Your last 7 days were nicotine-free. Switch to Clear Air?"

**The Log tab at Clear Air**
- Top card: "Clear Air · 23 days nicotine-free", receptor healing (e.g. "Receptors ≈ 82% of
  typical"), and the next step on the recovery timeline.
- **Craving logging is the focus:** "Craving? Log it" is the big button. Cravings at Clear Air still
  work out how they ended from the logs (no buttons in the craving flow).
- The product buttons are hidden behind a small "I had some" link, which opens the usual product
  list. A dose there is counted plainly, as at any level: nothing resets; that day just isn't
  nicotine-free. If it keeps happening, the usual step-up offer appears, with its reason.
- **Days nicotine-free** is a total that only goes up (like steady days), never a streak.
- No battery, stretch or pull at Clear Air (there's no gap).

## 2. Themes
**Where:** Settings → Appearance (one line: "Theme: Firewatch") → **More themes**, a separate screen
so the main Settings stay uncluttered.

**Mode:** Follow phone light/dark (default), Always light, Always dark. Every theme has a light and
a dark version.

**Themes (18)**

| Theme | Feel |
|---|---|
| Firewatch (default) | Today's warm orange |
| Ember Night | Near-black with glowing coal-orange accents |
| Forest | Deep greens with a moss accent |
| Moss & Stone | Grey stone with lichen green |
| Ink | Black and white only; charts use patterns and shades |
| Graphite | Mid-greys with one cool blue accent |
| Paper | Off-white with ink-blue text, like a notebook |
| Ocean | Slate and teal |
| Glacier | Icy white, pale blue and steel grey |
| Clear Sky | Bright sky blue and white |
| Aurora | Midnight blue with green and violet highlights |
| Dusk | Muted plum and lavender |
| Lavender Fields | Lilac and sage |
| Sakura | Soft blush pink with charcoal text |
| Sand | Soft beige and clay |
| Desert | Terracotta, sage and cream |
| Sunrise | Peach-to-gold accents on warm light |
| Retro Terminal | Black with amber text and mono-style numbers |

**Colour switches**
- **True black** in dark mode (saves battery on OLED screens).
- **Colour-blind-safe charts:** a palette that works for the common types, plus shapes as well as
  colour.
- **Calmer colours, no red:** lower saturation everywhere and no red at all (vapes and cigarettes
  get a muted colour). **On by default** (D: "that's fine").
- The theme also applies to the Android widgets.

**Rules:** every theme passes contrast checks (§3). Tier names and the fire branding stay; only
colours change. Negative net stays grey, never red, in every theme.

## 3. Accessibility pass
- Text scales with the phone's text-size setting (Android) and the browser's (web), with layouts
  that don't clip at the largest sizes.
- Screen-reader labels on the battery, the charts (a one-line summary, e.g. "Pieces a day, last 42
  days, 7-day average 4.2"), product buttons and the calendar.
- Every theme's text and chart colours checked against WCAG AA contrast.
- Touch targets at least 48 dp.

## 4. "Install on iPhone" (web)
- One card, the first time the web app opens in Safari on an iPhone without being installed:
  "Add Firewatch to your Home Screen for the full app: tap Share, then Add to Home Screen."
- Dismissed once, gone for good. A "How to install" article stays in Help.

## 5. Remove the Android reminders
- Remove Settings → "Reminders (optional)" (backup reminder and check-in reminder) and cancel
  anything already scheduled. Relapse prevention mode stays the only thing that notifies.
- Settings → Your data shows "Last backup: 34 days ago" (or "Never"). A fact, not a nudge.

## 6. New charts (Insights)
| Chart | Where | What it shows |
|---|---|---|
| Days nicotine-free | Forecasts / Clear Air | Total that only goes up, beside receptor healing |
| Gap sizes | Patterns | How often gaps fall under 1h, 1–2h, 2–4h, 4h+ |
| Pace vs your plan | Forecasts | Daily pieces against "If you take each step" (only after a first step down) |
| Practice pace runs | Ladder | Each run: practice net and hours toward "Work from" |
| Week shape | Patterns | Average pieces by weekday |
| First piece: weekdays vs weekends | Patterns | Two lines of time-to-first-piece |
| Longest gap each day | Going up | Daily bar of the day's biggest stretch |
| Craving strength over time | Going up | Average logged craving strength by week |
| Where your net comes from | Stretch & pull | Daily net split into timing and dose size |
| Doses by product over the day | Mix | When gum vs pouches are used, by hour |
| Steady days by month | Milestones | Steady days per month (a total, never a streak) |

All neutral and labelled as estimates; none compares you with a past version of yourself.

## Data
- New settings with defaults: `theme` ("firewatch"), `themeMode` ("system"), `trueBlack` (false),
  `colourBlindCharts` (false), `calmColours` (true), `installCardSeen` (false), `lastBackupAt` (0).
- Clear Air needs no new records (level 0 is already a rung change).
- No schema change. Follows `docs/data-format.md`.

## Tests to add
- Clear Air: begins on a step to Clear Air; the "nicotine-free week" offer needs 7 known zero days;
  days nicotine-free never goes down; a dose at Clear Air is counted and nothing resets.
- Every theme × light/dark passes a contrast check on text and chart colours.
- No reminder notifications are scheduled after the update (only Relapse prevention mode).
- Each new chart's figures on a fixed data set; "?" and ghost days left out.
- `WordingTest` passes, and a read-through of all new wording.

## Release notes (plain language, draft)
- New: **Life at Clear Air.** Once you reach Clear Air, the Log tab shows your days nicotine-free
  and receptor healing, with craving logging front and centre.
- New: 18 colour themes and colour options in Settings → Appearance → More themes, including
  black-and-white, colour-blind-safe charts and true black. Calmer colours, with no red, are now
  the default.
- Easier to read: text follows your phone's size setting, and screen readers describe the charts.
- The optional backup and check-in reminders are gone: Firewatch only notifies in Relapse
  prevention mode. Settings now shows when you last backed up.
- Eleven new Insights charts.
- Web: a tip on adding Firewatch to your iPhone's Home Screen.
