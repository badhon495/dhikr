# Handoff: Dhikr — Android Tasbih / Dhikr counter

## Overview

A native Android app for counting Dhikr: offline-first, no account, no ads, no tracking. The
bundle covers six destinations — Counter, Home, Tasbih library, Custom Tasbih editor, Routines,
Insights (statistics + history), Settings — plus the resume-session, goal-reached, reset-confirm
and counter-lock states.

The design is calm, warm and numbers-forward: the count is the largest thing on screen, and the
only required interaction is "tap anywhere to count".

## About the design files

The files in this bundle are **design references created in HTML**. They are prototypes showing
intended look and behaviour — not production code to copy.

The task is to **recreate these designs natively in Kotlin + Jetpack Compose**, using the target
project's established patterns (Material 3, ViewModel, Room, DataStore). Nothing in the HTML
should ship. No WebView, no cross-platform framework.

If you also have the product/engineering spec ("Build a High-Performance Android Tasbih / Dhikr
Application"), treat that document as the authority on architecture, performance budgets,
persistence and testing; treat this README as the authority on visual design and interaction.

## Fidelity

**High-fidelity.** Colors, type, spacing, radii, elevation and copy are final and exact. Recreate
the UI faithfully; map the values below onto a Material 3 `ColorScheme` and `Typography` rather
than hard-coding them per composable.

## Design tokens

Taken from the "Organic" system: warm cream ground, terracotta primary accent, sage secondary.

### Light theme

| Role | Hex | Use |
| --- | --- | --- |
| `bg` | `#f5ead8` | screen background |
| `surface` | `#ebddc5` | bottom nav, search field, stat tiles |
| `card` | `#f9f4ed` | cards, ring centre, dialogs, switch knob |
| `text` | `#201e1d` | primary text |
| `dim` | `#645c50` | secondary text, section labels |
| `faint` | `#a19786` | tertiary text, inactive nav |
| `line` | `rgba(32,30,29,.13)` | dividers, 1px borders |
| `sage` (secondary) | `#7a8a5e` | primary buttons, completed laps, bars, calendar peak |
| `sage-soft` | `#e1eecc` | tinted panels, active nav pill |
| `terra` (primary) | `#c67139` | progress ring, destructive confirm, links/accents |
| `terra-soft` | `#ffe1d0` | lock banner background |
| `track` | `rgba(32,30,29,.10)` | ring track, progress bar track, off switch |
| on-sage | `#f9f4ed` | text/icons on sage fills |

### Dark theme

| Role | Hex |
| --- | --- |
| `bg` | `#1c1a17` |
| `surface` | `#2a261f` |
| `card` | `#332e26` |
| `text` | `#f6efe2` |
| `dim` | `#c0b6a5` |
| `faint` | `#82796a` |
| `line` | `rgba(246,239,226,.12)` |
| `sage` | `#aebf92` |
| `sage-soft` | `#3d472b` |
| `terra` | `#f6a06b` |
| `terra-soft` | `#4d2f18` |
| `track` | `rgba(246,239,226,.10)` |
| on-sage | `#272e1b` |

Calendar intensity ramp (4 steps, light): `track` → `#e1eecc` → `#aebf92` → `#7a8a5e`.
Dark: `track` → `#3d472b` → `#728157` → `#aebf92`.

### Typography

| Role | Family | Size / weight |
| --- | --- | --- |
| Display (screen titles, count, big numerals) | **Caprasimo** 400 | screen title 23sp; count 84sp (56sp for long-text Dhikr); large-counter mode 116sp / 76sp; stat value 24sp; reminder time 17sp |
| Body / UI | **Figtree** 400–700 | body 13.5–14.5sp; list title 14.5sp/600; section label 11.5sp/700, `letter-spacing .07em`, uppercase; meta 11–12.5sp |
| Arabic | **Noto Naskh Arabic** | counter 30sp, line-height 1.7, `dir=rtl`; list 18sp |
| Bengali transliteration | **Noto Sans Bengali** | counter 14.5sp / long text 13.5sp with line-height 2.0 and justified; list 12sp clamped to 2 lines |

Numerals that change (count, totals, times, per-minute) use tabular figures.

Display sizes are given in sp for the port — the prototype is at 412 × 892 dp, 1× density.

### Spacing, radius, elevation

- Spacing steps used: 4 / 6 / 8 / 9 / 10 / 12 / 14 / 16 / 20 / 22 dp. Screen gutter 16dp.
- Radius: pill `999dp` for every button, input, chip and switch; `26–30dp` for cards, dialogs and
  panels; `22dp` for list rows and stat tiles; `9–10dp` for calendar cells and chart bars.
- Elevation: cards use a soft ambient shadow (`0 1px 2px rgba(46,43,37,.14)`); dialogs use
  `0 12px 32px rgba(46,43,37,.22)`. In Compose prefer tonal surfaces over heavy shadows.
- Minimum touch target 44dp everywhere; nav items 52dp tall.
- Icons: Lucide-style, stroke width **2.75**, round caps and joins, 20–21dp in nav and app bars.

## Screens

### 1. Counter — the primary screen

Purpose: count Dhikr. Entry point of the app.

Layout, top to bottom inside the content area:

1. **Top row** (48dp): back chevron (returns Home) · name + session line (`12:43 · 39/min`,
   tabular) · lock toggle on the right. Lock icon turns terracotta and closes its shackle when on.
2. **Routine chips** (only while a routine is running): horizontal row of pills, one per step,
   label `"<first word> <count>"`. Current step is a sage fill with on-sage text; completed steps
   are `surface` + `faint`; upcoming are `surface` + `dim`.
3. **Tap area** — everything from the Arabic line down to the hint text, `flex: 1`, vertically
   centred, scrollable if content exceeds the space:
   - Arabic phrase, 30sp Naskh, centred, RTL. Hidden when the Dhikr has no Arabic text.
   - Bengali transliteration, 14.5sp, centred. **Long-text mode** (transliteration over 90
     characters, e.g. Ayatul Kursi): 13.5sp, line-height 2.0, justified, and the ring shrinks from
     252dp to 178dp and the count from 84sp to 56sp so the full text is always shown, never
     truncated. This threshold behaviour is required — long Dhikr must render in full.
   - **Progress ring**, 252dp (178dp long-text): 12dp track in `track`, 12dp progress arc in
     `terra` with round caps, starting at 12 o'clock, `stroke-dashoffset` animated 160ms
     `cubic-bezier(.2,.7,.3,1)`. Inner disc in `card` inset 9%. Centre: count in Caprasimo, and
     `of <lapTarget>` at 13sp/600 `faint` beneath.
   - **Lap pips**: one pill per lap — completed `sage`, current `terra` and stretched to 26dp
     wide, upcoming 8dp in `track`. Under it: `Lap 2 of 3 · 57 of 99` (or `Step 2 of 3` in a
     routine).
   - Hint: `Tap anywhere to count` / `Locked — counting still works`.
4. **Control row**: `Undo` pill (surface), `Pause`/`Resume` pill (sage fill), and a 46dp circular
   reset button (surface). Reset opens a confirmation; it is never a one-tap action.

Tap feedback: count scales to 1.07 and the inner disc to 1.02 for 110ms, ease-out — this stands in
for the haptic tick. In the real app the visual response must be immediate and the haptic fired on
the same frame; persistence happens off the tap path.

Lap completion: at the lap target the ring empties and redraws for the next lap, the pip advances,
the count returns to 0. **No interruption, no full-screen flash.**

### 2. Home

Gutter 16dp, 16dp vertical gaps, scrollable.

- Greeting block: `Assalamu alaikum` (Caprasimo 24sp) + `Monday, 25 August` (12.5sp dim), with a
  74dp day-goal ring on the right (7dp stroke, terracotta arc, percentage centred, 15sp/700).
- **Continue session** card: `sage-soft` fill, 28dp radius, 1px `line` border; 42dp sage circle
  with a play glyph, uppercase kicker `CONTINUE SESSION`, Dhikr name, and `count/target` at the
  right. Tapping resumes the counter.
- **Favourites**: section label + `All` link (terracotta) → library. Rows are `card`-filled 22dp
  pills: name (14.5sp/600) over Bengali (12sp faint, ellipsised), Arabic right-aligned in `dim`.
- **Routines**: section label + `Manage` link → Routines. Three equal-width `surface` tiles with
  a 1px border: routine name (13sp/700) and `3 steps · 100 counts`. Tapping starts the routine.

### 3. Tasbih library

- Header: title `Tasbih` + a sage `+ New` pill → editor.
- Search field: 46dp pill, `surface`, 1px border, magnifier in `faint`, placeholder
  `Search name, Arabic, transliteration`. Filters live across name, Arabic, transliteration and
  translation, offline, case-insensitive substring.
- Result count line: `6 built-in · 0 custom`, or `2 of 6 match "sub"` while searching.
- Rows (`card`, 22dp): name, Bengali clamped to 2 lines, meta (`33 × 3 laps` / `100 per lap`);
  right column holds the Arabic (18sp, max 96dp wide) and a heart — filled terracotta when
  favourited, outlined `faint` when not. The heart must not open the Dhikr (stop propagation).
- Empty result: centred `Nothing matches that. Create it as a custom Tasbih.`

### 4. Custom Tasbih editor

Back chevron + `New Tasbih` title. Fields, 15dp apart, each an uppercase label over a 48dp pill
input (`card`, 1px `line`):

- Name (`e.g. Evening Tasbih`)
- Arabic text — RTL, Naskh, placeholder `اكتب الذكر`
- Translation (`What it means`)
- Personal note — 3-row textarea, 22dp radius, placeholder `Why you are reciting it`
- Lap target — stepper: value at 16sp/700 left, `−` (surface) and `+` (sage) 36dp circles right,
  clamped at a minimum of 1
- Daily goal — three pill options 33 / 100 / 500; selected is a sage fill

Footer: full-width 52dp terracotta `Save Tasbih`, then
`Stored on this device only. Nothing is uploaded.` in 11.5sp `faint`.

### 5. Routines

Title `Routines`, then one `card` per routine (26dp radius):

- Header row: name (16sp/700) + `3 steps · 100 counts` meta, and a sage `Start` pill.
- Steps: index (`faint`, tabular), name, count in terracotta 700, and a drag handle in `faint`.
  Each step row is separated by a 1px `line` top border.

Footer: a 50dp dashed-border pill `+ New routine`.

Running a routine uses the Counter screen with the step chips; on step completion it auto-advances
to the next step with the count reset. Completing the last step shows the completion overlay.

### 6. Insights (statistics + history, one scroll)

- Header: `Insights` + `August`.
- **Totals**: 2×2 grid of `surface` tiles, uppercase label over a Caprasimo 24sp figure —
  Today / This week / This month / All time.
- **Last 7 days**: `card`, 112dp tall bar row; bars are 10dp-radius, sage, with today in
  terracotta; value above and weekday below each bar in 10.5sp.
- **Consistency**: `card` with a 7-column calendar grid of square 9dp-radius cells using the
  4-step intensity ramp; day number centred, 10sp. Legend row `less ▢▢▢▢ more`. Header meta
  `23 days this month`. Language stays positive — never "streak lost".
- **History**, grouped by Dhikr: section label + `grouped by Dhikr`, then one `card` per Dhikr —
  name and lifetime total (terracotta) in the header, then day rows: label (`Today`, `Yesterday`,
  `Sat 23 Aug`), a 7dp sage progress bar scaled against 200 counts, and the count right-aligned.
- **Empty state** (fresh install): centred 66dp `surface` circle with a clock glyph,
  `No sessions yet`, `Counts appear here as soon as you finish your first session.`, and a sage
  `Start counting` button.

### 7. Settings

Title `Settings`, 16dp gutter, sections 16dp apart.

- **Theme**: segmented control in a `surface` pill — System / Light / Dark; selected option is a
  sage fill with on-sage text.
- Grouped toggle lists in `card` containers (rows separated by 1px `line`), each row a label
  (14sp/600) over a note (11.5sp `faint`) with a 46×26dp switch on the right — sage track when on,
  `track` when off, `card` knob 22dp, 140ms `cubic-bezier(.2,.7,.3,1)` slide. The whole row is the
  tap target.
  - *Counting*: Vibration on each count · Sound on each count · Keep screen awake while counting ·
    Counter lock on start · Advance routines automatically
  - *Display*: Arabic above transliteration
  - *Reminders*: Daily reminders
- **Reminder times**: `card` list — 8dp status dot (sage when on, `track` when off), time in
  Caprasimo 17sp, label + days, chevron. Disabled rows drop to `faint`. Footer row
  `+ Add a reminder` in terracotta.
- **Your data**: `sage-soft` panel — shield glyph +
  `Everything stays on this phone. No account, no ads, no analytics, and nothing is sent to a
  server.`, then two `card` pills `Export` / `Import`, then
  `Last backup — MyTasbihBackup.json · 23 Aug, 21:14`.
- Footer: `Dhikr 1.0 · Offline · Works without an account` and
  `Language — English, বাংলা, العربية`.

### Bottom navigation

Persistent across all destinations, including while counting. `surface` background, 1px `line` top
border. Five items: **Home · Tasbih · Count · Insights · Settings**. Active item gets a 52×28dp
`sage-soft` pill behind the icon and a `#56633f` (dark: `#aebf92`) icon and label; inactive is
`faint`. Label 10.5sp/600.

While the counter lock is on, navigation away from the counter is blocked and a
`terra-soft` toast reads `Counter locked — reset and nav are blocked`.

## Overlays

| Overlay | Trigger | Content |
| --- | --- | --- |
| Resume session | Cold start with a saved session | Bottom sheet, `card`, 30dp radius, rises 220ms: `Continue where you left off?`, `<name> — 3 of 7, paused 12:43 ago.`, buttons `Start new` (surface) / `Continue` (terracotta) |
| Goal reached | Final lap of the target completed | Centred dialog, 34dp radius: 76dp `sage-soft` circle with a sage check, `Goal reached`, `99 of SubhanAllah in 12:43.`, sage `Done`. Dismissing resets count, lap and timer |
| Routine complete | Last routine step completed | Same dialog, `Routine complete`, `Morning Dhikr finished — 100 counts in 08:12.` |
| Reset confirm | Reset button | Centred dialog: `Reset this session?`, `57 counts will be cleared. This cannot be undone.`, `Keep counting` / `Reset` (terracotta) |

Scrims are `rgba(20,18,16,.45)` (goal dialog `.5`). Entry animation: 8dp rise + fade,
200–240ms ease-out.

## State

Counter session state (survives process death — persist to DataStore/Room, restore on launch):
`activeDhikrId`, `count` (within lap), `lap`, `previousCount` (single-step undo), `running`,
`elapsedSeconds`, `locked`, `routineId`, `routineStep`.

Derived, never stored: lap progress fraction, total (`(lap-1) × lapTarget + count`), counts per
minute (`total / elapsed × 60`, hidden under 5 seconds), lap pip states, ring dash offset.

Preferences (DataStore): theme mode, haptics, sound, keep-awake, lock-on-start, auto-advance
routines, Arabic-first, reminders enabled, reminder schedule, large-counter mode.

Library/insights data (Room): Dhikr entities (built-in + custom), favourites, routines with
ordered steps, sessions and per-day aggregates. The insights screen reads pre-aggregated daily
totals — do not observe the whole session table.

Interaction rules to preserve:

- The tap increments and repaints immediately; persistence is debounced/batched off the tap path.
- Undo restores exactly one step, including across a lap boundary.
- Reset always requires confirmation; when locked, reset and navigation are refused.
- Timer ticks only while the session is running and the counter is visible.

## Content

Built-in library, in order — Arabic, Bengali transliteration, English translation, common count:

1. **Ayatul Kursi** — no Arabic in the prototype; Bengali transliteration supplied by the client
   verbatim (see `Dhikr Android App.dc.html`, `DHIKR[0].bn`); 7 per lap
2. **SubhanAllah** — `سُبْحَانَ اللّٰه` — সুবহানাল্লাহ — Glory be to Allah — 33 × 3
3. **Alhamdulillah** — `الْحَمْدُ لِلّٰه` — আলহামদুলিল্লাহ — All praise is due to Allah — 33 × 3
4. **Allahu Akbar** — `اللّٰهُ أَكْبَر` — আল্লাহু আকবার — Allah is the greatest — 34 × 3
5. **Astaghfirullah** — `أَسْتَغْفِرُ اللّٰه` — আস্তাগফিরুল্লাহ — I seek forgiveness from Allah — 100
6. **Subhanallahi wa bihamdihi** — `سُبْحَانَ اللّٰهِ وَبِحَمْدِهِ` — সুবহানাল্লাহি ওয়া বিহামদিহি —
   Glory be to Allah and praise be to Him — 100
7. **La hawla wa la quwwata illa billah** — `لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللّٰه` —
   লা হাওলা ওয়ালা কুওয়াতা ইল্লা বিল্লাহ — There is no power nor strength except with Allah — 33

**Religious content rule, carried into implementation:** the design deliberately shows no hadith
references, no claimed rewards or benefits, and no assertion that a count is religiously required.
Counts are presented as user-configurable targets. Any sourced material added later must carry its
reference and be visually separated from app copy; AI-generated explanations must be labelled as
such and never presented as religious authority.

## Localization & RTL

All strings are placeholders for resources — nothing hard-coded. Languages: English, Bengali,
Arabic. Arabic must flip the whole layout (`layoutDirection = Rtl`); the Arabic Dhikr line is
always RTL regardless of UI language. Bengali and Arabic must not clip at large font scales —
the counter text block scrolls rather than truncating, and the long-text mode above is the
required behaviour for verse-length transliterations.

## Accessibility

- Every control ≥ 44dp; the counter tap area is the whole content region.
- Content descriptions on all icon-only buttons (back, lock, reset, favourite, settings).
- The count announces politely on change; lap completion announces once.
- Large-counter mode raises the count to 116sp / 76sp; support system font scaling throughout.
- Reduce-motion: drop the tap scale and the ring transition, keep the value changes.
- Haptics and sound are independently switchable and default to haptics on, sound off.

## Assets

No bitmap or vector assets. All icons are inline stroke paths in the Lucide style at stroke width
2.75 — replace with the Lucide/Material equivalents in the app. Fonts to bundle: Caprasimo,
Figtree, Noto Naskh Arabic, Noto Sans Bengali.

## Files in this bundle

| File | What it is |
| --- | --- |
| `Dhikr Android App.dc.html` | The prototype — all seven screens, overlays, both themes. Open in a browser. Data arrays (`DHIKR`, `ROUTINES`, `HISTORY`, `WEEK`, `CAL`, `NAV`) and the exact interaction logic live in its logic class |
| `android-frame.jsx` | The device bezel used to display the prototype — presentation only, ignore |
| `support.js` | Runtime for the prototype — ignore |
| `_ds/` | The Organic design system stylesheet the tokens come from |

The prototype opens on the Counter screen with a saved session and the resume prompt showing. Dark
mode, large-counter mode, tap feedback and the empty-history state are toggles on the prototype's
tweak panel; in the app they are settings or data states.
