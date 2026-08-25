# Phase 1+2 — Project Scaffold, Counter Engine, Counter Screen

Date: 2026-08-25
Status: Approved for planning

## Context

This is the first of a multi-phase build of **Dhikr**, a native Android Tasbih/Dhikr
counter app. The full product/engineering requirements live in [`plan.md`](../../../plan.md)
(70 numbered sections covering architecture, performance, privacy, features, and an
8-phase build order in §65). The full visual design lives in
[`design/README.md`](../../../design/README.md) plus the interactive prototype
`design/Dhikr Android App.dc.html` (7 screens, both themes, exact interaction logic in
its `Component` class).

The project is too large for a single spec/plan/implementation cycle. Per plan.md §65,
work proceeds in phases; each phase gets its own spec → plan → implementation cycle.
This document covers **Phase 1 (Architecture)** and **Phase 2 (Core Counter)** combined,
since the counter engine cannot be meaningfully verified without a minimal project shell
and one screen to host it.

**Explicitly out of scope for this phase** (deferred to later phases per plan.md §65):
Room database, History/Statistics/Routines/Tasbih-library/Settings screens, bottom
navigation shell, home-screen widget, notifications, backup/import-export, AI features,
audio, Baseline Profiles, Macrobenchmark. These require more app surface to be
meaningful and are listed starting from Phase 3 onward in plan.md.

## Goals

1. A buildable, runnable Android project with Kotlin + Jetpack Compose + Material 3
   configured, following the package structure in plan.md §8.
2. A pure-Kotlin `TasbihCounter` domain engine, independent of UI/Android framework,
   implementing increment/undo/reset/pause/resume/lap-completion/progress exactly as
   specified in plan.md §10 and matching the prototype's proven logic
   (`Dhikr Android App.dc.html` lines 594–621).
3. A Counter screen in Compose, visually faithful to `design/README.md` §"1. Counter",
   backed by the engine through a ViewModel, with session state surviving process death.
4. Unit tests for the engine's edge cases and a Compose UI test for the primary
   tap/undo/lap/reset flow.

## Non-goals

- No Room database (no relational data exists yet in this phase).
- No bottom navigation shell — a minimal Home stub exists only to navigate into the
  Counter screen.
- No routines, library, history, statistics, settings, widget, notifications, backup,
  AI, or audio.
- No Baseline Profiles / Macrobenchmark yet (tracked for a later performance-focused
  phase per plan.md §46–47).

## Architecture

### Project structure

Single `:app` Gradle module (multi-module is unwarranted until a later phase actually
needs build-graph isolation — see plan.md §70 "prefer fewer dependencies, simpler
maintenance"). Kotlin packages mirror the plan.md §8 tree:

```
app/src/main/java/com/dhikr/app/
├── core/
│   ├── model/          Dhikr, CounterSessionState domain data classes
│   ├── counter/        TasbihCounter engine (pure Kotlin)
│   └── datastore/       SessionRepository (DataStore Preferences-backed)
├── feature/
│   ├── counter/         CounterViewModel, CounterScreen + child composables
│   └── home/            Minimal HomeScreen stub (navigation entry point only)
├── ui/theme/            Color.kt, Type.kt, Shape.kt, Theme.kt
├── DhikrApp.kt           NavHost, top-level composable
└── MainActivity.kt
```

- `applicationId` / package: `com.dhikr.app`
- `minSdk 24` (Android 7.0), `targetSdk`/`compileSdk` = latest stable (35)
- Kotlin, Jetpack Compose (BOM, latest stable), Material 3, Kotlin Coroutines,
  AndroidX Lifecycle/ViewModel, AndroidX DataStore (Preferences), Navigation Compose.
- No Room, no WorkManager, no notification/widget dependencies added this phase —
  each is introduced in the phase that first needs it, per plan.md §70 ("do not add a
  library merely because it makes implementation slightly easier").

### Domain model

```kotlin
data class Dhikr(
    val id: String,
    val name: String,
    val arabic: String,          // "" if none (e.g. Ayatul Kursi in the prototype)
    val transliteration: String, // Bengali, per current content set
    val translation: String,
    val lapTarget: Int,
    val lapCount: Int,
    val isFavorite: Boolean = false,
)
```

Built-in library is an in-memory constant list (`BuiltInDhikr.all`), a direct port of
the prototype's `DHIKR` array (7 entries — Ayatul Kursi, SubhanAllah, Alhamdulillah,
Allahu Akbar, Astaghfirullah, Subhanallahi wa bihamdihi, La hawla wa la quwwata illa
billah — with their exact Arabic/Bengali/translation/lapTarget/lapCount values from
`design/README.md` §Content). No Room table for this yet — the list is small, static,
and read-only until custom Tasbih creation arrives in a later phase, so a database
would add write/query machinery with no present benefit.

```kotlin
data class CounterSessionState(
    val activeDhikrId: String,
    val count: Int,             // within current lap
    val lap: Int,                // 1-indexed
    val previousState: UndoState?, // single-step undo; null = nothing to undo
    val running: Boolean,
    val elapsedSeconds: Int,
    val locked: Boolean,
    val routineId: String?,      // always null this phase; field reserved for Phase 3+
    val routineStep: Int,        // always 0 this phase
)

data class UndoState(val count: Int, val lap: Int)
```

This matches the "State" section of `design/README.md` exactly, including the
routine fields — they're part of the persisted schema now so the DataStore format
doesn't need a migration when Routines land later, but they're inert (always
null/0) until then.

### TasbihCounter engine

Pure Kotlin class, package `core.counter`, zero Android/Compose imports so it is
directly unit-testable and reusable later by the widget/notification
controls/Wear OS per plan.md §10.

```kotlin
class TasbihCounter(private val lapTarget: Int, private val totalLaps: Int) {
    // internal mutable state; exposes an immutable snapshot
    fun increment(): CounterSnapshot
    fun undo(): CounterSnapshot
    fun reset(): CounterSnapshot
    fun pause()
    fun resume()
    fun getCurrentCount(): Int
    fun getCurrentLap(): Int
    fun getProgress(): Float   // count / lapTarget, derived
    fun isComplete(): Boolean  // true once final lap's target is reached
}
```

Logic ported directly from the prototype's proven `tap()`/`undo()` (verified against
`Dhikr Android App.dc.html:594-621`):

- **increment()**: before mutating, snapshot `(count, lap)` into `previousState` for
  undo. If `count + 1 < lapTarget`: `count += 1`. Else if `lap < totalLaps`: lap
  completes — `lap += 1`, `count = 0` (lap-complete signal returned in the snapshot so
  the UI can show subtle feedback per plan.md §14, no interruption). Else: final lap's
  final count — `count = lapTarget`, `isComplete = true`, counter auto-pauses.
  Calling `increment()` again after completion is a no-op (does not overflow past
  target) — covers plan.md §59 "rapid tapping" / double-tap-after-goal.
- **undo()**: if `previousState == null`, no-op. Else restore `(count, lap)` from it
  and clear `previousState` — exactly one step of undo, consistent with
  `design/README.md`'s "Undo restores exactly one step, including across a lap
  boundary."
- **reset()**: `count = 0`, `lap = 1`, `previousState = null`. The engine itself does
  not gate this on confirmation — that's a UI-layer concern (see Counter screen below);
  the engine stays a pure state machine.
- **pause()/resume()**: toggle `running`; the engine does not run its own timer —
  elapsed-time tracking is a ViewModel/UI concern so the engine has no background
  work and cannot leak (plan.md §51/§52).
- Edge cases explicitly handled and unit-tested: `lapTarget = 1` (every tap completes
  a lap), `totalLaps = 1` (no lap rollover, behaves like a flat counter),
  target already reached + extra increment (no-op, no overflow), undo with no prior
  state (no-op, does not throw), undo immediately after a lap-boundary increment
  (restores prior lap's count correctly).

Progress/derived values (never stored, computed on read, per `design/README.md`
"State" section): `totalCount = (lap - 1) * lapTarget + count`, `progressFraction =
count / lapTarget`.

### Persistence strategy

Per plan.md §12 ("do not write large database transactions on every tap") and §49:

- `CounterViewModel` holds a `StateFlow<CounterUiState>` updated synchronously and
  immediately on every `increment()`/`undo()` call — the UI recomposes from this
  in-memory state with zero I/O on the tap path.
- A `SessionRepository` (DataStore Preferences, package `core.datastore`) persists
  `CounterSessionState` **debounced** ~500ms after the last change (coroutine
  `debounce` on the state flow), plus unconditionally on `ON_STOP` (via
  `Lifecycle.Event.ON_STOP` observer) to guarantee crash/process-death safety without
  writing on every single tap.
- On cold start, `MainActivity`/`CounterViewModel` reads any persisted session. Per
  plan.md §26, if a session exists this phase restores it directly into the Counter
  screen (no resume-prompt bottom sheet yet — that overlay is deferred; simply not
  losing the session satisfies this phase's scope, since Home/other screens don't
  exist yet for the prompt to make sense against).

### Counter screen (Compose)

Visually faithful to `design/README.md` §"1. Counter — the primary screen", built
against Organic design tokens mapped into a Material 3 `ColorScheme` +
custom `Typography` (Caprasimo for display/count, Figtree for UI, Noto Naskh Arabic
for the Arabic line, Noto Sans Bengali for transliteration — all sourced from Google
Fonts, OFL-licensed, bundled as XML font resources under `res/font/`).

Layout, top to bottom, exactly per the README:
1. Top row (48dp): back chevron → Home stub, name + session line (`mm:ss · rate/min`,
   tabular figures, rate hidden under 5s elapsed), lock toggle.
2. Routine chips row: present in the composable tree but not rendered
   (`routineId` is always null this phase) — kept as a no-op branch so Phase 3+
   (Routines) doesn't need to restructure this screen.
3. Tap area (fills remaining space, scrollable if content overflows):
   - Arabic line (Naskh 30sp, RTL, centered) — hidden when `arabic.isEmpty()`.
   - Transliteration (Figtree 14.5sp centered; long-text mode at >90 chars switches to
     13.5sp/line-height 2.0/justified, matching the prototype's `longBn` threshold
     exactly).
   - Progress ring: 252dp (178dp in long-text mode), 12dp track + 12dp terracotta
     progress arc with round caps starting at 12 o'clock, animated via
     `Animatable`/`animateFloatAsState` over 160ms `cubic-bezier(.2,.7,.3,1)` easing.
     Count in Caprasimo at the center, `of <lapTarget>` beneath.
   - Lap pips: one pill per lap (completed=sage, current=terracotta+stretched,
     upcoming=track-colored), with the `Lap X of Y · total of target` label.
   - Hint text switching between "Tap anywhere to count" / "Locked — counting still
     works".
4. Control row: Undo pill (disabled/no-op when nothing to undo), Pause/Resume pill,
   46dp circular Reset button that opens a confirmation dialog (never a one-tap
   destructive action, per plan.md §13/§35) — reset and back-navigation are both
   refused while locked, per the README's lock behavior.

Tap feedback: on tap, count and inner disc scale up (1.07 / 1.02) for 110ms ease-out
via Compose animation, on the same frame as an optional haptic tick
(`HapticFeedback.performHapticFeedback`, gated by a settings flag that defaults to
on — the Settings screen itself doesn't exist yet this phase, so this reads a
DataStore-backed default rather than a user-editable toggle). The visual update and
haptic fire immediately in the tap handler; the debounced DataStore write happens
asynchronously off that path, satisfying plan.md §12's ordering requirement.

Reduced-motion / accessibility (from `design/README.md` Accessibility section, applied
where they affect this one screen): content descriptions on all icon-only buttons
(back, lock, reset), count region as a live region that announces politely on change,
lap completion announces once, minimum 44dp touch targets, tap area covers the whole
content region (not a tiny +/− button) per plan.md §11.

Home stub: a minimal screen with just enough content (app title + a button/list entry
into the one seeded Dhikr, e.g. SubhanAllah) to navigate into the Counter screen via
Navigation Compose. Full Home screen design (`design/README.md` §"2. Home") is
deferred to a later phase.

### Error handling

Per plan.md §57, this phase's failure modes and fallbacks:
- Corrupted/unreadable DataStore session on cold start → fall back to a fresh session
  on the first built-in Dhikr rather than crashing.
- `lapTarget <= 0` (shouldn't occur from built-in content, but defensively) → engine
  treats it as `lapTarget = 1` rather than dividing by zero in progress calculation.

## Testing

**Unit tests** (`TasbihCounter`, JVM, no Android dependency):
- Increment below lap target, at lap target (lap rollover), at final target
  (completion + no-op on further increments).
- `lapTarget = 1`, `totalLaps = 1`.
- Undo after a plain increment; undo across a lap boundary; undo with nothing to
  undo (no-op, no throw).
- Progress fraction and total-count derivation at various count/lap combinations.
- Reset clears count, lap, and pending undo state.

**Compose UI test** (primary flow, per plan.md §58):
- Tap increments the displayed count immediately.
- Undo restores the previous count.
- Reaching lap target advances the lap pip and resets the in-lap count.
- Reset button opens a confirmation dialog; confirming clears the session, dismissing
  leaves it untouched.
- Lock toggle blocks the back button and hides/disables reset without blocking
  counting.

**Manual verification**: run on emulator/device — cold start with no prior session,
cold start with a persisted session (kill app mid-session, relaunch), rotate device
mid-session (state survives), long-text Dhikr (Ayatul Kursi) renders in full without
clipping and switches to the compact ring/count sizing.

## Open questions / assumptions carried forward

- Ayatul Kursi's Arabic field is empty in both the prototype and this phase (per
  `design/README.md` §Content note); Arabic text for it is not fabricated here and
  should be sourced properly in a later content-accuracy pass, per plan.md §67.
- Haptic/sound toggles are read from DataStore with hard defaults (haptics on, sound
  off, per `design/README.md` Accessibility section) since the Settings UI to change
  them doesn't exist until a later phase.
