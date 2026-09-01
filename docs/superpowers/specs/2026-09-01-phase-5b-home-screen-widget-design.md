# Phase 5B — Home-Screen Widgets — Design

Date: 2026-09-01
Status: approved for planning

## Context

Phase 5 of `plan.md` is "Notifications and Widget". Sub-projects:

- **5A** (done, merged PR #4) — per-routine reminder notifications.
- **5B (this spec)** — home-screen widgets.
- **5C** — prayer-linked reminders (deferred).

The app is native Android (Kotlin, Compose, Room, DataStore). No DI
framework: repositories are constructed by hand — in the `DhikrApp`
composable for the UI, and directly from `DhikrApplication.database` in
`BroadcastReceiver`s (see `ReminderReceiver`). `DhikrApplication` owns
the Room database (`by lazy`, `fallbackToDestructiveMigration`).

Relevant existing pieces:

- `core/counter/TasbihCounter` — pure-Kotlin counter engine, no Android
  imports. `restore(count, lap, previous)`, `increment()`, `totalCount()`,
  `snapshot()`. Already designed (plan.md §10) for reuse outside the app UI.
- `CounterSessionState` (`core/model`) + `SessionRepository`
  (`core/datastore`, store name `"session"`) — the single persisted
  "active session": `activeDhikrId`, `count`, `lap`, `previousCount/Lap`,
  `running`, `routineId`, `routineStep`, `loggedTotal`, `elapsedSeconds`,
  `locked`.
- `HistoryRepository.logSession(tasbihId, routineId, count, startedAt, endedAt)`
  → inserts a `SessionEntity` row (ignored if `count <= 0`).
- `TasbihRepository.saveSessionProgress(tasbihId, count, lap, loggedInSession)`
  → per-day `tasbih_progress` row that drives the green card-fill; drops
  stale (previous-day) rows in the same call. `getSessionProgress(id)`
  reads today's row.
- `SessionDao.totalSince(sinceMillis): Flow<Int>` — count total since a
  cutoff.
- `AppPreferencesRepository.dailyGoalTarget: Flow<Int>` (store `"preferences"`,
  default 100).
- `MainActivity` reads `intent.getStringExtra(EXTRA_ROUTINE_ID)` in
  `onCreate`/`onNewIntent` and hands a `pendingRoutineId` to `DhikrApp`,
  which `navController.navigate("counter?routineId=$id")` in a
  `LaunchedEffect`. `launchMode="singleTop"`.
- `CounterScreen` has an `ON_STOP` `LifecycleEventObserver` (calls
  `viewModel.flushSession()`).
- `res/values/colors.xml` + `res/values-night/colors.xml` already exist
  (auto theme-switching for resource-referenced colors).

The `loggedTotal` watermark: `CounterViewModel` logs to History only on
session end (goal reached, routine step advance, screen leaves
composition), never per-tap. Each log writes `totalCount() - loggedTotal`,
then bumps `loggedTotal = totalCount()`. On re-entry it adopts the
persisted watermark so a left-and-reentered session with no new taps
logs nothing (the "achievement climbs on its own" bug fix).

## Goal

Ship two classic-RemoteViews home-screen widgets:

1. **Counter widget** — shows the current active session (dhikr name,
   `count / lapTarget`, progress). A large `[+]` button increments the
   persisted session **without opening the app**. Tapping the body opens
   the counter.
2. **Insights widget** — shows today's count with progress toward the
   daily goal, this week's total, and the all-time total. Tapping it
   opens the Insights tab.

Everything is local. No network, no account, **no new shipped dependency**
(classic `AppWidgetProvider` + `RemoteViews`, not Glance; the only new
Gradle entries are `testImplementation` — see Testing). No Room schema
change. No DataStore key change.

## Non-goals

- Jetpack Glance.
- Counting a **routine** session from the widget (guided multi-step flow;
  the widget shows it but `[+]` opens the app instead).
- Multiple widget sizes / a resizable responsive layout beyond a single
  sensible default cell size each. No "Continue" button distinct from the
  body tap.
- A favorite-Tasbih fallback when there is no active session (v1 shows an
  "open the app to start" state).
- Real-time widget updates via a long-lived Room `InvalidationTracker`
  observer or a foreground service. Refresh is: explicit after the
  widget's own `[+]`, on app `ON_STOP`, plus a 30-minute
  `updatePeriodMillis` baseline.
- 7-day sparkline / calendar on the insights widget.
- Localization beyond the existing English `strings.xml`.
- Keeping the widget's write and a foregrounded `CounterViewModel` in
  sync. Last-writer-wins; see Concurrency.
- Widget preview images beyond a static `previewImage`/`previewLayout`.

## Architecture

New package `core/widget/`:

| File | Role |
|---|---|
| `DhikrWidgets.kt` | `refreshAll(context)` — sends an `APPWIDGET_UPDATE` broadcast (with current widget ids) to both providers. Single entry point everything else calls. |
| `CounterWidgetProvider.kt` | `AppWidgetProvider`. `onUpdate` builds and pushes the counter `RemoteViews`. |
| `CounterWidgetReceiver.kt` | `BroadcastReceiver` (`exported=false`). Handles the `[+]` action: `goAsync` → `WidgetCounter.applyIncrement` → `DhikrWidgets.refreshAll`. |
| `InsightsWidgetProvider.kt` | `AppWidgetProvider`. `onUpdate` builds and pushes the insights `RemoteViews`. |
| `WidgetRenders.kt` | Pure-ish helpers: build each `RemoteViews`, format `"$count / $target"`, clamp progress to `0..100`. Reads repositories passed in. |

New file `core/counter/WidgetCounter.kt`:

```
object WidgetCounter {
    sealed interface Result { object Applied : Result; object NoOp : Result }
    suspend fun applyIncrement(context: Context): Result
}
```

Builds `SessionRepository`, `TasbihRepository`, `HistoryRepository` from
`(context.applicationContext as DhikrApplication).database` — same
hand-wiring `ReminderReceiver` uses. Steps:

1. `state = sessionRepository.sessionFlow.first()`.
   `state == null || state.routineId != null` → return `NoOp`.
2. `tasbih = tasbihRepository.getById(state.activeDhikrId)` → `null` →
   `NoOp`.
3. `engine = TasbihCounter(tasbih.lapTarget, tasbih.lapCount)` then
   `engine.restore(state.count, state.lap, prev)` where `prev` is
   `(state.previousCount, state.previousLap)` if both non-null.
4. `engine.increment()`.
5. `historyRepository.logSession(tasbih.id, routineId = null, count = 1,
   startedAt = now, endedAt = now)`.
6. `sessionRepository.save(state.copy(count = snap.count, lap = snap.lap,
   previousCount = snap.previousCount, previousLap = snap.previousLap,
   loggedTotal = engine.totalCount()))`.
7. `tasbihRepository.saveSessionProgress(tasbih.id, snap.count, snap.lap,
   loggedInSession = engine.totalCount())`.
8. Return `Applied`.

Invariant after every widget tap: `loggedTotal == engine.totalCount()`.
So when the app next opens this session, `CounterViewModel`'s
`logCurrentSessionIfNonZero()` computes `unlogged = 0` and logs nothing —
no double count. History accrues one `count = 1` row per widget tap,
which is consistent with the card-fill fraction (it reads History via
`SessionDao.totalsByTasbihSince`).

`elapsedSeconds` is left unchanged by the widget (the session timer is a
UI concern; a widget tap does not advance it).

`WidgetCounter` performs no haptics and no sound (no `Activity`, and a
widget tap giving a system-wide buzz is undesirable).

## Counter widget

### Layout (`widget_counter.xml`)

Vertical `LinearLayout` on a rounded `widget_background` drawable:

- Dhikr name — `TextView`, single line, ellipsize end.
- `count / lapTarget` — `TextView`, larger.
- Horizontal determinate `ProgressBar` — `progress = count`,
  `max = lapTarget` (RemoteViews cannot render an arc; a bar is the
  supported primitive). Uses `widget_progress` drawable tinted from a
  theme color resource.
- `[+]` `Button` (or an `ImageButton` with `widget_plus`), large tap
  target (≥ 48dp), full width.

Default size: ~4×2 cells (`widget_counter_info.xml`
`minWidth`/`minHeight` ≈ 250dp × 110dp; `resizeMode="horizontal|vertical"`
allowed but a single layout).

### States

| Active session | `[+]` behavior | Body tap |
|---|---|---|
| non-routine | `WidgetCounter.applyIncrement` broadcast | open `counter` (bare = resume) |
| routine (`routineId != null`) | open the app (`counter?routineId=…`) | open the app (same) |
| none (`sessionFlow` null) | open the app | open the app |

For the routine and none states the widget still renders: routine → step
tasbih name + `count / target` with a muted "Open to continue" line;
none → app name + "Tap to start counting".

### Intents

- `[+]` (non-routine): `PendingIntent.getBroadcast(context, reqCode,
  Intent(context, CounterWidgetReceiver::class).setAction(ACTION_INCREMENT),
  FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)`.
- Body: `PendingIntent.getActivity` → `MainActivity` with
  `putExtra(EXTRA_OPEN, "counter")` (and, for a routine, the existing
  `EXTRA_ROUTINE_ID`).

## Insights widget

### Layout (`widget_insights.xml`)

Vertical `LinearLayout` on `widget_background`:

- "Today" row: `"$today / $goal"` + a horizontal determinate `ProgressBar`
  (`progress = today`, `max = goal`).
- "This week" row: label + value.
- "All time" row: label + value.

Values formatted with grouping separators
(`NumberFormat.getIntegerInstance()`).

Default size ~4×2 cells.

### Data

Provider `onUpdate` (in a `goAsync` coroutine):

- `today = sessionDao.totalSince(startOfTodayMillis).first()`
- `week  = sessionDao.totalSince(startOfTodayMillis - 6*DAY).first()`
- `allTime = sessionDao.totalSince(0L).first()`
- `goal = appPreferencesRepository.dailyGoalTarget.first()`

(`startOfToday` via `Calendar` set to 00:00, mirroring
`HistoryRepository` — extract a shared `DayBounds` helper in
`core/utilities` OR duplicate the ~5 lines; planner picks. Prefer a small
shared helper since three files now compute it.)

### Intent

Body → `MainActivity` with `putExtra(EXTRA_OPEN, "insights")`.

## MainActivity / DhikrApp deep-link changes

- New constant `MainActivity.EXTRA_OPEN` (`"com.dhikr.app.extra.OPEN"`),
  values `"counter"` / `"insights"`.
- `MainActivity` reads it alongside `EXTRA_ROUTINE_ID`, exposes a
  `pendingOpen: String?` to `DhikrApp` mirroring the existing
  `pendingRoutineId` pattern (state var, consumed once, `onNewIntent`
  updates it).
- `DhikrApp`: extend the existing `LaunchedEffect(pendingRoutineId)` (or
  add a sibling `LaunchedEffect(pendingOpen)`) —
  `"counter"` → `navigate("counter")`, `"insights"` →
  `navigate(ROUTE_INSIGHTS)`, then call `onPendingOpenConsumed()`.
  `routineId` still takes precedence when both are present.

## Refresh strategy

No polling, no long-lived observers.

1. `updatePeriodMillis = 1800000` (30 min) in both `*_info.xml` —
   system-batched, coalesced with other wakeups.
2. `DhikrWidgets.refreshAll(context)` immediately after any widget `[+]`
   (in `CounterWidgetReceiver`, after `applyIncrement`).
3. `CounterScreen`'s existing `ON_STOP` `LifecycleEventObserver` also
   calls `DhikrWidgets.refreshAll(context)` — leaving/backgrounding the
   counter pushes the latest count to both widgets. Cheap: a `RemoteViews`
   rebuild + `AppWidgetManager.updateAppWidget`.

`refreshAll` early-returns if `AppWidgetManager.getAppWidgetIds` is empty
for a provider (no widget placed → no work).

## Manifest

```xml
<receiver android:name=".core.widget.CounterWidgetProvider" android:exported="true">
    <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter>
    <meta-data android:name="android.appwidget.provider" android:resource="@xml/widget_counter_info" />
</receiver>
<receiver android:name=".core.widget.InsightsWidgetProvider" android:exported="true">
    <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter>
    <meta-data android:name="android.appwidget.provider" android:resource="@xml/widget_insights_info" />
</receiver>
<receiver android:name=".core.widget.CounterWidgetReceiver" android:exported="false">
    <intent-filter><action android:name="com.dhikr.app.action.WIDGET_INCREMENT" /></intent-filter>
</receiver>
```

No new `<uses-permission>`.

## Concurrency & edge cases

- **Widget `[+]` while the counter screen is foregrounded.** The app's
  in-memory `engine` is authoritative for the UI and rewrites the store
  on its next tap / debounced save / `flushSession`, so the widget's
  write can be lost. Accepted (product decision). Not synced back. The
  reverse — app writes, then `ON_STOP` refresh — is handled.
- **Day rollover.** `saveSessionProgress` drops previous-day rows;
  History "today" totals are timestamp-ranged; a widget showing stale
  numbers self-corrects on the next refresh tick or app `ON_STOP`.
- **Session points at a deleted custom Tasbih.** `getById` → `null` →
  `NoOp`; widget renders the "open to start" state.
- **Rapid `[+]` taps.** Each broadcast is an independent
  read-modify-write on `Dispatchers` via `goAsync`; DataStore serializes
  writes. Under a burst some reads may see a pre-write state and a tap be
  effectively coalesced — acceptable for a widget; the primary counter is
  the in-app screen.
- **No widget placed.** `refreshAll` no-ops.
- **`WidgetCounter` at cold start before Room seed completes.** `getById`
  → `null` → `NoOp`. Same guard already relied on elsewhere.
- **Process death during `goAsync`.** `goAsync` holds a wake lock up to
  ~10s; the write is a single fast DataStore/Room op. Loss of one count
  on a kill is acceptable and no worse than the in-app debounce window.

## Testing

No test infrastructure exists (`plan.md` §58 unmet project-wide; tracked
separately). This spec adds a minimal JVM harness and covers the pure
logic only:

- New `app/src/test/` source set, `testImplementation` JUnit4 +
  `kotlinx-coroutines-test` (added to `libs.versions.toml`).
- `WidgetCounterTest` — drive `applyIncrement` against fake repositories:
  - non-routine session: count advances by 1, `loggedTotal == totalCount`,
    exactly one `logSession(count=1)` call, progress row written.
  - `routineId != null` → `NoOp`, no writes.
  - `sessionFlow` null → `NoOp`.
  - unknown `activeDhikrId` → `NoOp`.
  - lap-boundary tap: `restore` + `increment` rolls lap, state saved with
    `count = 0`, next lap.
- `WidgetRendersTest` — `"$count / $target"` formatting; progress clamp
  for `target = 0`, `count > target`, negative; integer grouping in the
  insights values.
- Providers, `RemoteViews`, manifest wiring, deep links: manual —
  `gradlew :app:assembleDebug` then place both widgets on a device/emulator
  and exercise `[+]`, body tap, no-session state, dark mode.

## Files touched (summary)

New:
- `core/widget/{DhikrWidgets,CounterWidgetProvider,CounterWidgetReceiver,InsightsWidgetProvider,WidgetRenders}.kt`
- `core/counter/WidgetCounter.kt`
- `core/utilities/DayBounds.kt` (small shared start-of-day helper)
- `res/layout/{widget_counter,widget_insights}.xml`
- `res/xml/{widget_counter_info,widget_insights_info}.xml`
- `res/drawable/{widget_background,widget_progress,widget_plus}.xml`
- `app/src/test/.../WidgetCounterTest.kt`, `WidgetRendersTest.kt`

Modified:
- `AndroidManifest.xml` — three `<receiver>` entries.
- `MainActivity.kt` — `EXTRA_OPEN`, `pendingOpen`.
- `DhikrApp.kt` — consume `pendingOpen`, navigate.
- `feature/counter/CounterScreen.kt` — `ON_STOP` also calls
  `DhikrWidgets.refreshAll`.
- `res/values/strings.xml` — widget labels/descriptions.
- `res/values/colors.xml` + `res/values-night/colors.xml` — widget
  surface / progress / text colors.
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — `test` deps only.
- `HistoryRepository` / `TasbihRepository` — extract start-of-day to
  `DayBounds` (optional, planner's call).

## Open decisions for the plan

- Shared start-of-day helper vs local duplication (spec leans: shared).
- `[+]` as `Button` with "+" text vs `ImageButton` + vector (either;
  ensure ≥ 48dp and a `contentDescription`).
- Whether the insights widget's "today" progress bar is hidden when
  `goal <= 0` (spec: clamp `max` to at least 1, show empty).
