# Phase 5B — Home-Screen Widgets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship two classic-RemoteViews home-screen widgets — a counter widget whose `[+]` increments the persisted session without opening the app, and an insights widget showing today/goal, this week, and all-time totals.

**Architecture:** New `core/widget/` package holds an `AppWidgetProvider` + a `BroadcastReceiver` per widget plus a `DhikrWidgets.refreshAll` entry point. The `[+]` path is a pure decision function (`WidgetCounter.evaluate`) wrapped by a repository-wiring `applyIncrement`, reusing the existing `TasbihCounter` engine and the same hand-wired-repository pattern `ReminderReceiver` uses. No Room schema change, no DataStore key change, no new shipped dependency (only `testImplementation` for a first JVM test harness).

**Tech Stack:** Kotlin, Android `AppWidgetProvider` + `RemoteViews`, Room, DataStore, coroutines (`goAsync` in receivers), JUnit4 + `kotlinx-coroutines-test` (new, test-only).

**Spec:** `docs/superpowers/specs/2026-09-01-phase-5b-home-screen-widget-design.md`

## Global Constraints

- **No new shipped dependency.** Only `testImplementation` entries may be added to `gradle/libs.versions.toml` / `app/build.gradle.kts`. Classic `AppWidgetProvider` + `RemoteViews`, never Jetpack Glance.
- **No Room schema change. No DataStore key change.** Widgets read/write only existing stores.
- **No new `<uses-permission>`.**
- No DI framework — construct repositories by hand from `(context.applicationContext as DhikrApplication).database`, exactly as `ReminderReceiver` does.
- `minSdk = 24`, `targetSdk = 37`, `compileSdk = 37`, Java/JVM 17.
- All `PendingIntent`s use `FLAG_IMMUTABLE` (or `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`).
- Localization: English `res/values/strings.xml` only.
- Widget colors go through `res/values/colors.xml` + `res/values-night/colors.xml` (auto theme switch); hex values mirror `DhikrColorTokens` in `ui/theme/Color.kt`.
- Every plan task ends by running `./gradlew :app:assembleDebug` (and `:app:testDebugUnitTest` once the test source set exists) and confirming BUILD SUCCESSFUL before committing. Do not launch an emulator unless the user asks (see memory `verify-via-build-not-emulator`).

---

## File Structure

New:

| File | Responsibility |
|---|---|
| `app/src/main/java/com/dhikr/app/core/utilities/DayBounds.kt` | `startOfTodayMillis()` / `startOfMonthMillis()` — shared local-midnight helper. |
| `app/src/main/java/com/dhikr/app/core/counter/WidgetCounter.kt` | `evaluate(state, tasbih)` pure decision + `applyIncrement(context)` repo wiring for the widget `[+]`. |
| `app/src/main/java/com/dhikr/app/core/widget/WidgetRenders.kt` | Pure formatting helpers (`formatCountOfTarget`, `clampProgress`, `formatGrouped`) + `RemoteViews` builders for both widgets. |
| `app/src/main/java/com/dhikr/app/core/widget/DhikrWidgets.kt` | `refreshAll(context)` — broadcasts `APPWIDGET_UPDATE` to both providers, no-op when no widget placed. |
| `app/src/main/java/com/dhikr/app/core/widget/CounterWidgetProvider.kt` | `AppWidgetProvider`; `onUpdate` builds + pushes the counter `RemoteViews`. |
| `app/src/main/java/com/dhikr/app/core/widget/CounterWidgetReceiver.kt` | `BroadcastReceiver` (`exported=false`); handles `[+]` → `WidgetCounter.applyIncrement` → `DhikrWidgets.refreshAll`. |
| `app/src/main/java/com/dhikr/app/core/widget/InsightsWidgetProvider.kt` | `AppWidgetProvider`; `onUpdate` builds + pushes the insights `RemoteViews`. |
| `app/src/main/res/layout/widget_counter.xml` | Counter widget layout. |
| `app/src/main/res/layout/widget_insights.xml` | Insights widget layout. |
| `app/src/main/res/xml/widget_counter_info.xml` | `appwidget-provider` metadata for the counter widget. |
| `app/src/main/res/xml/widget_insights_info.xml` | `appwidget-provider` metadata for the insights widget. |
| `app/src/main/res/drawable/widget_background.xml` | Rounded surface background. |
| `app/src/main/res/drawable/widget_progress.xml` | Determinate progress-bar drawable (track + sage fill). |
| `app/src/test/java/com/dhikr/app/core/counter/WidgetCounterTest.kt` | Unit tests for `WidgetCounter.evaluate`. |
| `app/src/test/java/com/dhikr/app/core/widget/WidgetRendersTest.kt` | Unit tests for the pure formatting helpers. |

Modified:

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | Add `junit` + `kotlinx-coroutines-test` versions/libraries (test-only). |
| `app/build.gradle.kts` | Add `testImplementation` entries + `testOptions`. |
| `app/src/main/AndroidManifest.xml` | Three `<receiver>` entries. |
| `app/src/main/java/com/dhikr/app/MainActivity.kt` | `EXTRA_OPEN` constant + `pendingOpen` state, mirrors `pendingRoutineId`. |
| `app/src/main/java/com/dhikr/app/DhikrApp.kt` | Consume `pendingOpen`, navigate to `counter` / `insights`. |
| `app/src/main/java/com/dhikr/app/feature/counter/CounterScreen.kt` | `ON_STOP` observer also calls `DhikrWidgets.refreshAll`. |
| `app/src/main/res/values/strings.xml` | Widget labels / descriptions / body copy. |
| `app/src/main/res/values/colors.xml` + `values-night/colors.xml` | `widget_surface`, `widget_progress_track`, `widget_progress_fill`, `widget_text`, `widget_text_dim`, `widget_accent_on`. |
| `app/src/main/java/com/dhikr/app/core/database/HistoryRepository.kt` | Delete private `startOfTodayMillis`/`startOfMonthMillis`, call `DayBounds`. |
| `app/src/main/java/com/dhikr/app/core/database/TasbihRepository.kt` | Delete private `startOfTodayMillis`, call `DayBounds`. |

---

## Task 1: Test harness + `DayBounds` helper

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/dhikr/app/core/utilities/DayBounds.kt`
- Test: `app/src/test/java/com/dhikr/app/core/utilities/DayBoundsTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `object DayBounds { fun startOfTodayMillis(now: Long = System.currentTimeMillis()): Long; fun startOfMonthMillis(now: Long = System.currentTimeMillis()): Long }`

- [ ] **Step 1: Add test dependencies to the version catalog**

In `gradle/libs.versions.toml`, under `[versions]` add:

```toml
junit = "4.13.2"
coroutinesTest = "1.9.0"
```

Under `[libraries]` add:

```toml
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }
```

- [ ] **Step 2: Wire the test source set in `app/build.gradle.kts`**

In the `android { }` block, add (after `buildFeatures { }`):

```kotlin
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
```

In `dependencies { }`, add at the end:

```kotlin
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 3: Write the failing test**

Create `app/src/test/java/com/dhikr/app/core/utilities/DayBoundsTest.kt`:

```kotlin
package com.dhikr.app.core.utilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DayBoundsTest {

    @Test
    fun startOfToday_isMidnightAtOrBeforeNow_andWithin24h() {
        val now = System.currentTimeMillis()
        val start = DayBounds.startOfTodayMillis(now)
        assertTrue(start <= now)
        assertTrue(now - start < 24L * 60 * 60 * 1000)
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun startOfMonth_isFirstDayMidnight() {
        val start = DayBounds.startOfMonthMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.utilities.DayBoundsTest"`
Expected: FAIL — `DayBounds` unresolved reference.

- [ ] **Step 5: Write `DayBounds`**

Create `app/src/main/java/com/dhikr/app/core/utilities/DayBounds.kt`:

```kotlin
package com.dhikr.app.core.utilities

import java.util.Calendar

/**
 * Local-time day/month boundaries. Extracted from the private helpers that
 * HistoryRepository and TasbihRepository each carried, now that widget code
 * needs the same "start of today" cutoff. Recomputed per call (never cached) so
 * a DST transition or device-timezone change mid-process stays correct — same
 * reasoning as HistoryRepository.localOffsetMillis().
 */
object DayBounds {

    fun startOfTodayMillis(now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun startOfMonthMillis(now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.utilities.DayBoundsTest"`
Expected: PASS (2 tests).

- [ ] **Step 7: Full build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/dhikr/app/core/utilities/DayBounds.kt app/src/test/java/com/dhikr/app/core/utilities/DayBoundsTest.kt
git commit -m "Add JVM test harness and shared DayBounds helper"
```

---

## Task 2: `WidgetCounter` — pure decision + increment wiring

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/counter/WidgetCounter.kt`
- Test: `app/src/test/java/com/dhikr/app/core/counter/WidgetCounterTest.kt`

**Interfaces:**
- Consumes:
  - `TasbihCounter(lapTarget: Int, totalLaps: Int)`, `.restore(count, lap, previous: Pair<Int,Int>?)`, `.increment(): CounterSnapshot`, `.totalCount(): Int`, `.snapshot(): CounterSnapshot`
  - `CounterSnapshot(count, lap, previousCount: Int?, previousLap: Int?, isComplete, justCompletedLap)`
  - `CounterSessionState(activeDhikrId, count, lap, previousCount: Int?, previousLap: Int?, running, elapsedSeconds, locked, routineId: String?, routineStep, loggedTotal)`
  - `TasbihEntity(id, name, ..., lapTarget: Int, lapCount: Int, ...)`
  - `SessionRepository(context)`, `.sessionFlow: Flow<CounterSessionState?>`, `.save(state)`
  - `TasbihRepository(tasbihDao, routineDao, tasbihProgressDao, sessionDao)`, `.getById(id): TasbihEntity?`, `.saveSessionProgress(tasbihId, count, lap, loggedInSession)`
  - `HistoryRepository(sessionDao, tasbihRepository)`, `.logSession(tasbihId, routineId: String?, count, startedAt, endedAt)`
  - `(context.applicationContext as DhikrApplication).database` — `.tasbihDao()`, `.routineDao()`, `.tasbihProgressDao()`, `.sessionDao()`
- Produces:
  - `sealed interface WidgetCounter.Outcome`
    - `object NoOp : Outcome`
    - `data class Apply(val newState: CounterSessionState, val engineTotal: Int) : Outcome`
  - `fun WidgetCounter.evaluate(state: CounterSessionState?, tasbih: TasbihEntity?): Outcome`
  - `sealed interface WidgetCounter.Result` — `object Applied`, `object NoOp` (note: `Result.NoOp` and `Outcome.NoOp` are distinct nested types)
  - `suspend fun WidgetCounter.applyIncrement(context: Context): Result`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/dhikr/app/core/counter/WidgetCounterTest.kt`:

```kotlin
package com.dhikr.app.core.counter

import com.dhikr.app.core.database.entity.TasbihEntity
import com.dhikr.app.core.model.CounterSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private fun tasbih(id: String, lapTarget: Int, lapCount: Int) = TasbihEntity(
    id = id, name = "x", arabic = "", pronunciation = "", translation = "",
    lapTarget = lapTarget, lapCount = lapCount, isBuiltIn = true,
    createdAt = 0L, updatedAt = 0L,
)

private fun session(
    dhikrId: String, count: Int, lap: Int, loggedTotal: Int,
    routineId: String? = null,
) = CounterSessionState(
    activeDhikrId = dhikrId, count = count, lap = lap,
    previousCount = null, previousLap = null, running = true,
    elapsedSeconds = 0, locked = false, routineId = routineId,
    routineStep = 0, loggedTotal = loggedTotal,
)

class WidgetCounterTest {

    @Test
    fun nonRoutineSession_advancesByOne_andSyncsLoggedTotal() {
        val out = WidgetCounter.evaluate(
            state = session("a", count = 5, lap = 1, loggedTotal = 5),
            tasbih = tasbih("a", lapTarget = 33, lapCount = 3),
        )
        out as WidgetCounter.Outcome.Apply
        assertEquals(6, out.newState.count)
        assertEquals(1, out.newState.lap)
        assertEquals(6, out.engineTotal)
        assertEquals(out.engineTotal, out.newState.loggedTotal)
    }

    @Test
    fun lapBoundaryTap_rollsLap_countResetsToZero() {
        val out = WidgetCounter.evaluate(
            state = session("a", count = 32, lap = 1, loggedTotal = 32),
            tasbih = tasbih("a", lapTarget = 33, lapCount = 3),
        )
        out as WidgetCounter.Outcome.Apply
        assertEquals(0, out.newState.count)
        assertEquals(2, out.newState.lap)
        assertEquals(33, out.engineTotal)
        assertEquals(33, out.newState.loggedTotal)
    }

    @Test
    fun routineSession_isNoOp() {
        val out = WidgetCounter.evaluate(
            state = session("a", count = 1, lap = 1, loggedTotal = 1, routineId = "r1"),
            tasbih = tasbih("a", lapTarget = 33, lapCount = 3),
        )
        assertSame(WidgetCounter.Outcome.NoOp, out)
    }

    @Test
    fun nullSession_isNoOp() {
        assertSame(WidgetCounter.Outcome.NoOp, WidgetCounter.evaluate(null, tasbih("a", 33, 3)))
    }

    @Test
    fun unknownTasbih_isNoOp() {
        val out = WidgetCounter.evaluate(session("a", 1, 1, 1), tasbih = null)
        assertSame(WidgetCounter.Outcome.NoOp, out)
    }

    @Test
    fun tasbihIdMismatch_isNoOp() {
        val out = WidgetCounter.evaluate(session("a", 1, 1, 1), tasbih("b", 33, 3))
        assertSame(WidgetCounter.Outcome.NoOp, out)
    }

    @Test
    fun previousPointers_areCarriedFromSnapshot() {
        val out = WidgetCounter.evaluate(
            state = session("a", count = 5, lap = 1, loggedTotal = 5),
            tasbih = tasbih("a", lapTarget = 33, lapCount = 3),
        )
        out as WidgetCounter.Outcome.Apply
        assertEquals(5, out.newState.previousCount)
        assertEquals(1, out.newState.previousLap)
        assertTrue(out.newState.running)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.counter.WidgetCounterTest"`
Expected: FAIL — `WidgetCounter` unresolved reference.

- [ ] **Step 3: Write `WidgetCounter`**

Create `app/src/main/java/com/dhikr/app/core/counter/WidgetCounter.kt`:

```kotlin
package com.dhikr.app.core.counter

import android.content.Context
import com.dhikr.app.DhikrApplication
import com.dhikr.app.core.database.HistoryRepository
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.database.entity.TasbihEntity
import com.dhikr.app.core.datastore.SessionRepository
import com.dhikr.app.core.model.CounterSessionState
import kotlinx.coroutines.flow.first

/**
 * The counter widget's [+] path. [evaluate] is the pure engine-math decision
 * (unit-tested); [applyIncrement] wires the repositories the same way
 * ReminderReceiver does and performs the writes.
 *
 * Invariant after an Apply: newState.loggedTotal == engineTotal, so when the
 * app next opens this session CounterViewModel.logCurrentSessionIfNonZero()
 * computes unlogged == 0 and logs nothing — no double count. History gains one
 * count = 1 row per widget tap.
 *
 * No haptics, no sound, no timer advance (elapsedSeconds untouched): a widget
 * tap is not an in-app tap.
 */
object WidgetCounter {

    sealed interface Outcome {
        data object NoOp : Outcome
        data class Apply(val newState: CounterSessionState, val engineTotal: Int) : Outcome
    }

    sealed interface Result {
        data object Applied : Result
        data object NoOp : Result
    }

    fun evaluate(state: CounterSessionState?, tasbih: TasbihEntity?): Outcome {
        if (state == null || state.routineId != null) return Outcome.NoOp
        if (tasbih == null || tasbih.id != state.activeDhikrId) return Outcome.NoOp

        val engine = TasbihCounter(tasbih.lapTarget, tasbih.lapCount)
        val previous = if (state.previousCount != null && state.previousLap != null) {
            state.previousCount to state.previousLap
        } else {
            null
        }
        engine.restore(count = state.count, lap = state.lap, previous = previous)
        val snap = engine.increment()
        val total = engine.totalCount()
        return Outcome.Apply(
            newState = state.copy(
                count = snap.count,
                lap = snap.lap,
                previousCount = snap.previousCount,
                previousLap = snap.previousLap,
                loggedTotal = total,
            ),
            engineTotal = total,
        )
    }

    suspend fun applyIncrement(context: Context): Result {
        val app = context.applicationContext as DhikrApplication
        val sessionRepository = SessionRepository(context.applicationContext)
        val tasbihRepository = TasbihRepository(
            app.database.tasbihDao(),
            app.database.routineDao(),
            app.database.tasbihProgressDao(),
            app.database.sessionDao(),
        )
        val historyRepository = HistoryRepository(app.database.sessionDao(), tasbihRepository)

        val state = sessionRepository.sessionFlow.first()
        if (state == null || state.routineId != null) return Result.NoOp
        val tasbih = tasbihRepository.getById(state.activeDhikrId) ?: return Result.NoOp

        val outcome = evaluate(state, tasbih)
        if (outcome !is Outcome.Apply) return Result.NoOp

        val now = System.currentTimeMillis()
        historyRepository.logSession(
            tasbihId = tasbih.id,
            routineId = null,
            count = 1,
            startedAt = now,
            endedAt = now,
        )
        sessionRepository.save(outcome.newState)
        tasbihRepository.saveSessionProgress(
            tasbihId = tasbih.id,
            count = outcome.newState.count,
            lap = outcome.newState.lap,
            loggedInSession = outcome.engineTotal,
        )
        return Result.Applied
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.counter.WidgetCounterTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Full build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/counter/WidgetCounter.kt app/src/test/java/com/dhikr/app/core/counter/WidgetCounterTest.kt
git commit -m "Add WidgetCounter increment engine for the counter widget"
```

---

## Task 3: `WidgetRenders` — pure formatting helpers

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/widget/WidgetRenders.kt` (helpers only in this task; `RemoteViews` builders added in Task 5)
- Test: `app/src/test/java/com/dhikr/app/core/widget/WidgetRendersTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `fun WidgetRenders.formatCountOfTarget(count: Int, target: Int): String` → `"$count / $target"`
  - `data class WidgetRenders.Progress(val progress: Int, val max: Int)`
  - `fun WidgetRenders.clampProgress(value: Int, target: Int): Progress` — `max = target.coerceAtLeast(1)`, `progress = value.coerceIn(0, max)`
  - `fun WidgetRenders.formatGrouped(value: Int): String` — `NumberFormat.getIntegerInstance(Locale.US).format(value.toLong())`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/dhikr/app/core/widget/WidgetRendersTest.kt`:

```kotlin
package com.dhikr.app.core.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetRendersTest {

    @Test
    fun formatCountOfTarget_isSlashSeparated() {
        assertEquals("7 / 33", WidgetRenders.formatCountOfTarget(7, 33))
        assertEquals("0 / 100", WidgetRenders.formatCountOfTarget(0, 100))
    }

    @Test
    fun clampProgress_targetZero_maxIsOne_progressZero() {
        val p = WidgetRenders.clampProgress(value = 0, target = 0)
        assertEquals(1, p.max)
        assertEquals(0, p.progress)
    }

    @Test
    fun clampProgress_valueAboveTarget_clampsToMax() {
        val p = WidgetRenders.clampProgress(value = 40, target = 33)
        assertEquals(33, p.max)
        assertEquals(33, p.progress)
    }

    @Test
    fun clampProgress_negativeValue_clampsToZero() {
        val p = WidgetRenders.clampProgress(value = -5, target = 33)
        assertEquals(0, p.progress)
        assertEquals(33, p.max)
    }

    @Test
    fun formatGrouped_insertsThousandsSeparators() {
        assertEquals("1,234", WidgetRenders.formatGrouped(1234))
        assertEquals("1,000,000", WidgetRenders.formatGrouped(1_000_000))
        assertEquals("0", WidgetRenders.formatGrouped(0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.widget.WidgetRendersTest"`
Expected: FAIL — `WidgetRenders` unresolved reference.

- [ ] **Step 3: Write `WidgetRenders` helpers**

Create `app/src/main/java/com/dhikr/app/core/widget/WidgetRenders.kt`:

```kotlin
package com.dhikr.app.core.widget

import java.text.NumberFormat
import java.util.Locale

/**
 * Pure formatting/layout helpers for both widgets. The RemoteViews builders
 * (added alongside the providers) call these; the helpers are split out so they
 * unit-test without an Android runtime.
 */
object WidgetRenders {

    data class Progress(val progress: Int, val max: Int)

    fun formatCountOfTarget(count: Int, target: Int): String = "$count / $target"

    /**
     * A ProgressBar needs max >= 1 and progress in 0..max. Clamp so goal <= 0
     * (or a count that ran past target after a goal change) still renders a
     * sane bar instead of crashing or showing a full/negative one.
     */
    fun clampProgress(value: Int, target: Int): Progress {
        val max = target.coerceAtLeast(1)
        return Progress(progress = value.coerceIn(0, max), max = max)
    }

    fun formatGrouped(value: Int): String =
        NumberFormat.getIntegerInstance(Locale.US).format(value.toLong())
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.widget.WidgetRendersTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Full build + full test run**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/widget/WidgetRenders.kt app/src/test/java/com/dhikr/app/core/widget/WidgetRendersTest.kt
git commit -m "Add WidgetRenders formatting helpers"
```

---

## Task 4: Widget resources (layouts, metadata, drawables, colors, strings)

**Files:**
- Create: `app/src/main/res/drawable/widget_background.xml`
- Create: `app/src/main/res/drawable/widget_progress.xml`
- Create: `app/src/main/res/layout/widget_counter.xml`
- Create: `app/src/main/res/layout/widget_insights.xml`
- Create: `app/src/main/res/xml/widget_counter_info.xml`
- Create: `app/src/main/res/xml/widget_insights_info.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values-night/colors.xml`
- Modify: `app/src/main/res/values/strings.xml`

No unit test — resource-only. Verified by `assembleDebug` (AAPT link) and the manual checklist in Task 8.

- [ ] **Step 1: Add widget colors (light)**

In `app/src/main/res/values/colors.xml`, before `</resources>` add (hex values mirror `LightDhikrColors` in `ui/theme/Color.kt`):

```xml
    <!-- Home-screen widgets (Phase 5B). Mirror DhikrColorTokens (Color.kt);
         values-night/colors.xml carries the dark equivalents. -->
    <color name="widget_surface">#F9F4ED</color>
    <color name="widget_text">#201E1D</color>
    <color name="widget_text_dim">#645C50</color>
    <color name="widget_progress_track">#1A201E1D</color>
    <color name="widget_progress_fill">#7A8A5E</color>
    <color name="widget_accent">#7A8A5E</color>
    <color name="widget_on_accent">#F9F4ED</color>
```

- [ ] **Step 2: Add widget colors (dark)**

In `app/src/main/res/values-night/colors.xml`, before `</resources>` add (mirror `DarkDhikrColors`):

```xml
    <!-- Home-screen widgets (Phase 5B) — dark. Mirror DarkDhikrColors (Color.kt). -->
    <color name="widget_surface">#332E26</color>
    <color name="widget_text">#F6EFE2</color>
    <color name="widget_text_dim">#C0B6A5</color>
    <color name="widget_progress_track">#1AF6EFE2</color>
    <color name="widget_progress_fill">#AEBF92</color>
    <color name="widget_accent">#AEBF92</color>
    <color name="widget_on_accent">#272E1B</color>
```

- [ ] **Step 3: Add widget strings**

In `app/src/main/res/values/strings.xml`, before `</resources>` add:

```xml
    <!-- Home-screen widgets (Phase 5B) -->
    <string name="widget_counter_label">Dhikr counter</string>
    <string name="widget_counter_description">Count your current session from the home screen.</string>
    <string name="widget_insights_label">Dhikr insights</string>
    <string name="widget_insights_description">Today, this week and all-time counts.</string>
    <string name="widget_increment_content_description">Add one count</string>
    <string name="widget_no_session_title">Dhikr</string>
    <string name="widget_no_session_body">Tap to start counting</string>
    <string name="widget_routine_hint">Open to continue</string>
    <string name="widget_insights_today">Today</string>
    <string name="widget_insights_week">This week</string>
    <string name="widget_insights_all_time">All time</string>
```

- [ ] **Step 4: Create the rounded background drawable**

Create `app/src/main/res/drawable/widget_background.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/widget_surface" />
    <corners android:radius="20dp" />
</shape>
```

- [ ] **Step 5: Create the progress drawable**

Create `app/src/main/res/drawable/widget_progress.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@android:id/background">
        <shape android:shape="rectangle">
            <corners android:radius="4dp" />
            <solid android:color="@color/widget_progress_track" />
        </shape>
    </item>
    <item android:id="@android:id/progress">
        <clip>
            <shape android:shape="rectangle">
                <corners android:radius="4dp" />
                <solid android:color="@color/widget_progress_fill" />
            </shape>
        </clip>
    </item>
</layer-list>
```

- [ ] **Step 6: Create the counter widget layout**

Create `app/src/main/res/layout/widget_counter.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_counter_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@drawable/widget_background"
    android:padding="16dp">

    <TextView
        android:id="@+id/widget_counter_name"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:maxLines="1"
        android:ellipsize="end"
        android:textColor="@color/widget_text_dim"
        android:textSize="13sp"
        tools:ignore="HardcodedText"
        android:text="SubhanAllah" />

    <TextView
        android:id="@+id/widget_counter_value"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="2dp"
        android:maxLines="1"
        android:textColor="@color/widget_text"
        android:textSize="26sp"
        android:textStyle="bold"
        tools:ignore="HardcodedText"
        android:text="0 / 33" />

    <ProgressBar
        android:id="@+id/widget_counter_progress"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="match_parent"
        android:layout_height="6dp"
        android:layout_marginTop="8dp"
        android:max="33"
        android:progress="0"
        android:progressDrawable="@drawable/widget_progress" />

    <Button
        android:id="@+id/widget_counter_plus"
        android:layout_width="match_parent"
        android:layout_height="52dp"
        android:layout_marginTop="10dp"
        android:background="@drawable/widget_background"
        android:backgroundTint="@color/widget_accent"
        android:textColor="@color/widget_on_accent"
        android:textSize="22sp"
        android:contentDescription="@string/widget_increment_content_description"
        tools:ignore="HardcodedText"
        android:text="+" />

</LinearLayout>
```

Add `xmlns:tools="http://schemas.android.com/tools"` to the root element.

- [ ] **Step 7: Create the insights widget layout**

Create `app/src/main/res/layout/widget_insights.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@drawable/widget_background"
    android:padding="16dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/widget_insights_today"
        android:textColor="@color/widget_text_dim"
        android:textSize="12sp" />

    <TextView
        android:id="@+id/widget_insights_today_value"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:maxLines="1"
        android:textColor="@color/widget_text"
        android:textSize="22sp"
        android:textStyle="bold"
        tools:ignore="HardcodedText"
        android:text="0 / 100" />

    <ProgressBar
        android:id="@+id/widget_insights_progress"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="match_parent"
        android:layout_height="6dp"
        android:layout_marginTop="6dp"
        android:max="100"
        android:progress="0"
        android:progressDrawable="@drawable/widget_progress" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:orientation="horizontal">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/widget_insights_week"
            android:textColor="@color/widget_text_dim"
            android:textSize="12sp" />

        <TextView
            android:id="@+id/widget_insights_week_value"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/widget_text"
            android:textSize="14sp"
            android:textStyle="bold"
            tools:ignore="HardcodedText"
            android:text="0" />
    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:orientation="horizontal">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/widget_insights_all_time"
            android:textColor="@color/widget_text_dim"
            android:textSize="12sp" />

        <TextView
            android:id="@+id/widget_insights_all_time_value"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/widget_text"
            android:textSize="14sp"
            android:textStyle="bold"
            tools:ignore="HardcodedText"
            android:text="0" />
    </LinearLayout>

</LinearLayout>
```

- [ ] **Step 8: Create counter widget metadata**

Create `app/src/main/res/xml/widget_counter_info.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="110dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/widget_counter"
    android:previewLayout="@layout/widget_counter"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:description="@string/widget_counter_description" />
```

- [ ] **Step 9: Create insights widget metadata**

Create `app/src/main/res/xml/widget_insights_info.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="110dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/widget_insights"
    android:previewLayout="@layout/widget_insights"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:description="@string/widget_insights_description" />
```

- [ ] **Step 10: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (AAPT links all new resources).

- [ ] **Step 11: Commit**

```bash
git add app/src/main/res
git commit -m "Add widget layouts, metadata, drawables, colors and strings"
```

---

## Task 5: Providers, receiver, `DhikrWidgets`, manifest wiring

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/core/widget/WidgetRenders.kt` (add `RemoteViews` builders)
- Create: `app/src/main/java/com/dhikr/app/core/widget/DhikrWidgets.kt`
- Create: `app/src/main/java/com/dhikr/app/core/widget/CounterWidgetProvider.kt`
- Create: `app/src/main/java/com/dhikr/app/core/widget/CounterWidgetReceiver.kt`
- Create: `app/src/main/java/com/dhikr/app/core/widget/InsightsWidgetProvider.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes:
  - `WidgetRenders.formatCountOfTarget`, `.clampProgress`, `.formatGrouped`, `.Progress`
  - `WidgetCounter.applyIncrement(context): WidgetCounter.Result`
  - `DayBounds.startOfTodayMillis()`
  - `SessionRepository(context).sessionFlow`
  - `TasbihRepository(...).getById(id)`
  - `SessionDao.totalSince(sinceMillis): Flow<Int>` via `app.database.sessionDao()`
  - `AppPreferencesRepository(context).dailyGoalTarget: Flow<Int>`
  - `MainActivity` (target for body/`[+]`-open `PendingIntent`s), `ReminderNotifications.EXTRA_ROUTINE_ID` constant
  - `MainActivity.EXTRA_OPEN` — **defined in Task 6.** For this task use the string literal `"com.dhikr.app.extra.OPEN"` in a local `private const val`; Task 6 step 4 replaces it with `MainActivity.EXTRA_OPEN`.
- Produces:
  - `object DhikrWidgets { fun refreshAll(context: Context) }`
  - `const val CounterWidgetReceiver.ACTION_INCREMENT = "com.dhikr.app.action.WIDGET_INCREMENT"`
  - `class CounterWidgetProvider : AppWidgetProvider`
  - `class InsightsWidgetProvider : AppWidgetProvider`
  - `class CounterWidgetReceiver : BroadcastReceiver`
  - `WidgetRenders.buildCounter(context, ...): RemoteViews`, `WidgetRenders.buildInsights(context, ...): RemoteViews`

- [ ] **Step 1: Add `RemoteViews` builders to `WidgetRenders`**

Append to `app/src/main/java/com/dhikr/app/core/widget/WidgetRenders.kt` (add imports at top):

```kotlin
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dhikr.app.MainActivity
import com.dhikr.app.R
import com.dhikr.app.core.notifications.ReminderNotifications
```

Inside `object WidgetRenders`, add:

```kotlin
    // Values MainActivity reads to route a widget body tap. Kept in sync with
    // MainActivity.EXTRA_OPEN / its accepted values.
    private const val EXTRA_OPEN = "com.dhikr.app.extra.OPEN"
    private const val OPEN_COUNTER = "counter"
    private const val OPEN_INSIGHTS = "insights"

    private fun openActivityIntent(context: Context, open: String, routineId: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_OPEN, open)
            if (routineId != null) putExtra(ReminderNotifications.EXTRA_ROUTINE_ID, routineId)
        }
        // Unique request code per (open,routineId) so distinct extras aren't
        // coalesced by PendingIntent's equality (which ignores extras).
        val reqCode = (open + (routineId ?: "")).hashCode()
        return PendingIntent.getActivity(
            context, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * @param session the active session, or null for the "no session" state.
     * @param tasbihName resolved name for [session] (null when no session or
     *   the Tasbih was deleted).
     * @param lapTarget the active Tasbih's per-lap target (0 when unknown).
     */
    fun buildCounter(
        context: Context,
        session: com.dhikr.app.core.model.CounterSessionState?,
        tasbihName: String?,
        lapTarget: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_counter)
        val isRoutine = session?.routineId != null
        val hasCountable = session != null && tasbihName != null

        if (!hasCountable) {
            views.setTextViewText(R.id.widget_counter_name, context.getString(R.string.widget_no_session_title))
            views.setTextViewText(R.id.widget_counter_value, context.getString(R.string.widget_no_session_body))
            val p = clampProgress(0, 1)
            views.setProgressBar(R.id.widget_counter_progress, p.max, p.progress, false)
            views.setViewVisibility(R.id.widget_counter_plus, android.view.View.GONE)
            val open = openActivityIntent(context, OPEN_COUNTER)
            views.setOnClickPendingIntent(R.id.widget_counter_root, open)
            return views
        }

        views.setTextViewText(R.id.widget_counter_name, tasbihName)
        views.setTextViewText(
            R.id.widget_counter_value,
            formatCountOfTarget(session!!.count, lapTarget),
        )
        val p = clampProgress(session.count, lapTarget)
        views.setProgressBar(R.id.widget_counter_progress, p.max, p.progress, false)

        val bodyIntent = if (isRoutine) {
            openActivityIntent(context, OPEN_COUNTER, session.routineId)
        } else {
            openActivityIntent(context, OPEN_COUNTER)
        }
        views.setOnClickPendingIntent(R.id.widget_counter_root, bodyIntent)

        if (isRoutine) {
            // Routine sessions render but [+] opens the app (guided flow).
            views.setViewVisibility(R.id.widget_counter_plus, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_counter_plus, context.getString(R.string.widget_routine_hint))
            views.setOnClickPendingIntent(
                R.id.widget_counter_plus,
                openActivityIntent(context, OPEN_COUNTER, session.routineId),
            )
        } else {
            views.setViewVisibility(R.id.widget_counter_plus, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_counter_plus, "+")
            val incIntent = Intent(context, CounterWidgetReceiver::class.java)
                .setAction(CounterWidgetReceiver.ACTION_INCREMENT)
            val incPending = PendingIntent.getBroadcast(
                context, 0, incIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_counter_plus, incPending)
        }
        return views
    }

    fun buildInsights(
        context: Context,
        today: Int,
        goal: Int,
        week: Int,
        allTime: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_insights)
        views.setTextViewText(R.id.widget_insights_today_value, formatCountOfTarget(today, goal))
        val p = clampProgress(today, goal)
        views.setProgressBar(R.id.widget_insights_progress, p.max, p.progress, false)
        views.setTextViewText(R.id.widget_insights_week_value, formatGrouped(week))
        views.setTextViewText(R.id.widget_insights_all_time_value, formatGrouped(allTime))
        views.setOnClickPendingIntent(
            android.R.id.background, // set on the root below instead
            openActivityIntent(context, OPEN_INSIGHTS),
        )
        // The insights layout root has no id; give the whole widget the tap by
        // setting it on the first child is fragile — add an id in the layout.
        views.setOnClickPendingIntent(R.id.widget_insights_root, openActivityIntent(context, OPEN_INSIGHTS))
        return views
    }
```

Then add `android:id="@+id/widget_insights_root"` to the root `LinearLayout` of `widget_insights.xml`, and delete the fragile `android.R.id.background` line above (kept here only to flag it — the final code sets the click on `R.id.widget_insights_root` only).

- [ ] **Step 2: Write `DhikrWidgets`**

Create `app/src/main/java/com/dhikr/app/core/widget/DhikrWidgets.kt`:

```kotlin
package com.dhikr.app.core.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Single entry point for refreshing the home-screen widgets. Broadcasts an
 * APPWIDGET_UPDATE to each provider with its current widget ids; no-ops for a
 * provider that has no widget placed, so callers never need to check.
 */
object DhikrWidgets {

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val providers = listOf(
            CounterWidgetProvider::class.java,
            InsightsWidgetProvider::class.java,
        )
        for (cls in providers) {
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isEmpty()) continue
            context.sendBroadcast(
                Intent(context, cls).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }
    }
}
```

- [ ] **Step 3: Write `CounterWidgetProvider`**

Create `app/src/main/java/com/dhikr/app/core/widget/CounterWidgetProvider.kt`:

```kotlin
package com.dhikr.app.core.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.dhikr.app.DhikrApplication
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.datastore.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CounterWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as DhikrApplication
        val sessionRepository = SessionRepository(context.applicationContext)
        val tasbihRepository = TasbihRepository(
            app.database.tasbihDao(),
            app.database.routineDao(),
            app.database.tasbihProgressDao(),
            app.database.sessionDao(),
        )
        val pending = goAsync()
        scope.launch {
            try {
                val session = sessionRepository.sessionFlow.first()
                val tasbih = session?.let { tasbihRepository.getById(it.activeDhikrId) }
                val views = WidgetRenders.buildCounter(
                    context = context,
                    session = session,
                    tasbihName = tasbih?.name,
                    lapTarget = tasbih?.lapTarget ?: 0,
                )
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }
}
```

- [ ] **Step 4: Write `CounterWidgetReceiver`**

Create `app/src/main/java/com/dhikr/app/core/widget/CounterWidgetReceiver.kt`:

```kotlin
package com.dhikr.app.core.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dhikr.app.core.counter.WidgetCounter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the counter widget's [+] tap: applies one increment to the persisted
 * session off the main thread, then refreshes both widgets. exported=false —
 * only our own PendingIntent fires it.
 */
class CounterWidgetReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INCREMENT) return
        val pending = goAsync()
        scope.launch {
            try {
                WidgetCounter.applyIncrement(context)
                DhikrWidgets.refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_INCREMENT = "com.dhikr.app.action.WIDGET_INCREMENT"
    }
}
```

- [ ] **Step 5: Write `InsightsWidgetProvider`**

Create `app/src/main/java/com/dhikr/app/core/widget/InsightsWidgetProvider.kt`:

```kotlin
package com.dhikr.app.core.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.dhikr.app.DhikrApplication
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.utilities.DayBounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class InsightsWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as DhikrApplication
        val sessionDao = app.database.sessionDao()
        val preferences = AppPreferencesRepository(context.applicationContext)
        val pending = goAsync()
        scope.launch {
            try {
                val startOfToday = DayBounds.startOfTodayMillis()
                val dayMillis = TimeUnit.DAYS.toMillis(1)
                val today = sessionDao.totalSince(startOfToday).first()
                val week = sessionDao.totalSince(startOfToday - 6 * dayMillis).first()
                val allTime = sessionDao.totalSince(0L).first()
                val goal = preferences.dailyGoalTarget.first()
                val views = WidgetRenders.buildInsights(context, today, goal, week, allTime)
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }
}
```

- [ ] **Step 6: Register the receivers in the manifest**

In `app/src/main/AndroidManifest.xml`, inside `<application>` after the `BootReceiver` block add:

```xml
        <receiver
            android:name=".core.widget.CounterWidgetProvider"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_counter_info" />
        </receiver>

        <receiver
            android:name=".core.widget.InsightsWidgetProvider"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_insights_info" />
        </receiver>

        <receiver
            android:name=".core.widget.CounterWidgetReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="com.dhikr.app.action.WIDGET_INCREMENT" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 7: Build**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all existing tests still pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/widget app/src/main/AndroidManifest.xml app/src/main/res/layout/widget_insights.xml
git commit -m "Add widget providers, increment receiver and refresh entry point"
```

---

## Task 6: `MainActivity` / `DhikrApp` deep-link (`EXTRA_OPEN`)

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/MainActivity.kt`
- Modify: `app/src/main/java/com/dhikr/app/DhikrApp.kt`
- Modify: `app/src/main/java/com/dhikr/app/core/widget/WidgetRenders.kt` (swap the local literal for `MainActivity.EXTRA_OPEN`)

**Interfaces:**
- Consumes: `pendingRoutineId` pattern already in `MainActivity` + `DhikrApp`; `ROUTE_INSIGHTS` const (private, same file `DhikrApp.kt`); `navController.graph.findStartDestination()` (already imported).
- Produces:
  - `const val MainActivity.EXTRA_OPEN = "com.dhikr.app.extra.OPEN"`
  - `MainActivity.EXTRA_OPEN_COUNTER = "counter"`, `MainActivity.EXTRA_OPEN_INSIGHTS = "insights"`
  - `DhikrApp(..., pendingOpen: String? = null, onPendingOpenConsumed: () -> Unit = {})`

- [ ] **Step 1: Add the constants + `pendingOpen` state to `MainActivity`**

In `app/src/main/java/com/dhikr/app/MainActivity.kt`:

Add a companion object:

```kotlin
    companion object {
        const val EXTRA_OPEN = "com.dhikr.app.extra.OPEN"
        const val OPEN_COUNTER = "counter"
        const val OPEN_INSIGHTS = "insights"
    }
```

Add the state var next to `pendingRoutineId`:

```kotlin
    private var pendingOpen by mutableStateOf<String?>(null)
```

In `onCreate`, after the `pendingRoutineId = ...` line:

```kotlin
        pendingOpen = intent?.getStringExtra(EXTRA_OPEN)
```

In `onNewIntent`, after the `pendingRoutineId = ...` line:

```kotlin
        pendingOpen = intent.getStringExtra(EXTRA_OPEN)
```

In the `DhikrApp(...)` call, add:

```kotlin
                pendingOpen = pendingOpen,
                onPendingOpenConsumed = { pendingOpen = null },
```

- [ ] **Step 2: Accept + consume `pendingOpen` in `DhikrApp`**

In `app/src/main/java/com/dhikr/app/DhikrApp.kt`, extend the signature:

```kotlin
fun DhikrApp(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    pendingRoutineId: String? = null,
    onPendingRoutineConsumed: () -> Unit = {},
    pendingOpen: String? = null,
    onPendingOpenConsumed: () -> Unit = {},
) {
```

After the existing `LaunchedEffect(pendingRoutineId) { ... }` block add:

```kotlin
        // Widget body tap: open the counter or insights tab. routineId (from a
        // reminder notification or a routine-state widget) takes precedence, so
        // when both are set this effect defers and lets the routine effect run.
        LaunchedEffect(pendingOpen) {
            val target = pendingOpen ?: return@LaunchedEffect
            if (pendingRoutineId == null) {
                val route = when (target) {
                    MainActivity.OPEN_INSIGHTS -> ROUTE_INSIGHTS
                    else -> ROUTE_COUNTER.substringBefore("?")
                }
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id)
                    launchSingleTop = true
                }
            }
            onPendingOpenConsumed()
        }
```

Add the import if not present: `import com.dhikr.app.MainActivity` — note `DhikrApp.kt` is in package `com.dhikr.app`, so `MainActivity` resolves without an import. Use `MainActivity.OPEN_INSIGHTS` directly.

- [ ] **Step 3: Point `WidgetRenders` at the real constants**

In `app/src/main/java/com/dhikr/app/core/widget/WidgetRenders.kt`, delete the local:

```kotlin
    private const val EXTRA_OPEN = "com.dhikr.app.extra.OPEN"
    private const val OPEN_COUNTER = "counter"
    private const val OPEN_INSIGHTS = "insights"
```

and replace every use with `MainActivity.EXTRA_OPEN`, `MainActivity.OPEN_COUNTER`, `MainActivity.OPEN_INSIGHTS` (`MainActivity` is already imported for the `PendingIntent` target).

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dhikr/app/MainActivity.kt app/src/main/java/com/dhikr/app/DhikrApp.kt app/src/main/java/com/dhikr/app/core/widget/WidgetRenders.kt
git commit -m "Deep-link widget body taps to the counter and insights tabs"
```

---

## Task 7: `CounterScreen` `ON_STOP` also refreshes widgets

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/feature/counter/CounterScreen.kt`

**Interfaces:**
- Consumes: `DhikrWidgets.refreshAll(context: Context)`; `LocalView.current` already in scope (used by the immersive-mode `DisposableEffect`).
- Produces: nothing.

- [ ] **Step 1: Add the refresh call to the existing `ON_STOP` observer**

In `app/src/main/java/com/dhikr/app/feature/counter/CounterScreen.kt`, in the `DisposableEffect(lifecycleOwner, viewModel)` block, change:

```kotlin
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.flushSession()
            }
        }
```

to:

```kotlin
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.flushSession()
                // Push the latest count to any placed widgets. flushSession()
                // writes DataStore synchronously enough that the subsequent
                // provider read (goAsync) sees it; a lost race just means the
                // widget catches up on its next 30-min tick.
                com.dhikr.app.core.widget.DhikrWidgets.refreshAll(view.context.applicationContext)
            }
        }
```

`view` is `LocalView.current`, already declared earlier in the composable (line ~122). If the `DisposableEffect(lifecycleOwner, viewModel)` block sits above that declaration, move `val view = LocalView.current` above this effect or add a dedicated `val appContext = LocalContext.current.applicationContext` and use that.

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/counter/CounterScreen.kt
git commit -m "Refresh home-screen widgets when leaving the counter"
```

---

## Task 8: Migrate repositories to `DayBounds` + manual verification

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/core/database/HistoryRepository.kt`
- Modify: `app/src/main/java/com/dhikr/app/core/database/TasbihRepository.kt`

**Interfaces:**
- Consumes: `DayBounds.startOfTodayMillis()`, `DayBounds.startOfMonthMillis()`.
- Produces: nothing (internal refactor, behavior-preserving).

- [ ] **Step 1: Replace `HistoryRepository`'s private helpers**

In `app/src/main/java/com/dhikr/app/core/database/HistoryRepository.kt`:

- Add import: `import com.dhikr.app.core.utilities.DayBounds`
- Delete the private `startOfTodayMillis()` and `startOfMonthMillis()` methods at the bottom.
- Replace every call `startOfTodayMillis()` → `DayBounds.startOfTodayMillis()` and `startOfMonthMillis()` → `DayBounds.startOfMonthMillis()`.
- Leave `previousMonthSummary()`'s inline `Calendar` block as-is (it needs the `Calendar` instance afterward for `get(Calendar.YEAR)` etc.).

- [ ] **Step 2: Replace `TasbihRepository`'s private helper**

In `app/src/main/java/com/dhikr/app/core/database/TasbihRepository.kt`:

- Add import: `import com.dhikr.app.core.utilities.DayBounds`
- Delete the private `startOfTodayMillis()` method.
- Replace both call sites (`saveSessionProgress`, `getSessionProgress`, `observeSessionProgressToday`) with `DayBounds.startOfTodayMillis()`.
- Remove the now-unused `import java.util.Calendar` if nothing else uses it (check: `TasbihRepository` uses `UUID` still; `Calendar` likely only for the helper — remove it).

- [ ] **Step 3: Build + full test run**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/database/HistoryRepository.kt app/src/main/java/com/dhikr/app/core/database/TasbihRepository.kt
git commit -m "Route repository day-boundary math through DayBounds"
```

- [ ] **Step 5: Manual verification checklist (device or emulator — only if the user runs it)**

Install: `./gradlew :app:installDebug`. Then:

1. Long-press home screen → Widgets → Dhikr. Both "Dhikr counter" and "Dhikr insights" appear with previews.
2. With **no active session**: counter widget shows "Dhikr" / "Tap to start counting", no `[+]`; tapping it opens the app on the counter tab.
3. Start a non-routine session in-app (count to ~5), press home. Counter widget shows the dhikr name and `5 / 33` with a partial bar within 30 min or immediately (ON_STOP refresh).
4. Tap `[+]` on the widget 3×. Value climbs to `8 / 33` without the app opening.
5. Open the app → counter. Count is `8` (widget writes adopted), and Insights "Today" increased by exactly 3 — not 6 (no double count).
6. Cross a lap boundary with `[+]` (get to `32`, tap once): shows `0 / 33`, lap advanced.
7. Start a routine session, press home: counter widget shows the step name + count + "Open to continue"; tapping `[+]` or the body opens the app on that routine.
8. Insights widget: "Today / <goal>" with bar, "This week" and "All time" with thousands separators. Tapping it opens the Insights tab.
9. Toggle system dark mode: both widgets recolor (surface, text, progress) on next refresh.
10. Set daily goal to 0 in Settings: insights widget "Today" bar renders empty, no crash.

Record results in the PR description.

---

## Self-Review

**1. Spec coverage**

| Spec section | Task |
|---|---|
| `core/widget/DhikrWidgets.kt` | Task 5 |
| `CounterWidgetProvider` / `InsightsWidgetProvider` | Task 5 |
| `CounterWidgetReceiver` (`[+]` → `applyIncrement` → `refreshAll`) | Task 5 |
| `WidgetRenders.kt` (builders + formatting) | Tasks 3 + 5 |
| `core/counter/WidgetCounter.kt` (evaluate + applyIncrement, 8 steps) | Task 2 |
| `loggedTotal == totalCount` invariant | Task 2 (test `nonRoutineSession_advancesByOne_andSyncsLoggedTotal`, `applyIncrement` sets `loggedInSession = engineTotal`) |
| `elapsedSeconds` untouched | Task 2 (`state.copy` omits it) |
| No haptics / no sound | Task 2 (documented, no such calls) |
| Counter widget layout | Task 4 (`widget_counter.xml`) |
| Counter widget states (non-routine / routine / none) | Task 5 (`buildCounter` branches) |
| Counter widget intents (`[+]` broadcast, body `getActivity`) | Task 5 |
| Insights widget layout | Task 4 (`widget_insights.xml`) |
| Insights widget data (today/week/all-time/goal via `totalSince`) | Task 5 (`InsightsWidgetProvider.onUpdate`) |
| Insights integer grouping | Tasks 3 + 5 (`formatGrouped`) |
| Insights body intent → Insights tab | Tasks 5 + 6 |
| `MainActivity.EXTRA_OPEN` + `pendingOpen` | Task 6 |
| `DhikrApp` consumes `pendingOpen`, routineId precedence | Task 6 |
| Refresh: `updatePeriodMillis = 1800000` | Task 4 (both `*_info.xml`) |
| Refresh: after `[+]` | Task 5 (`CounterWidgetReceiver`) |
| Refresh: `CounterScreen` `ON_STOP` | Task 7 |
| `refreshAll` no-ops when no widget placed | Task 5 (`getAppWidgetIds` empty → `continue`) |
| Manifest: 3 receivers, no new permission | Task 5 |
| `DayBounds` shared helper (spec leans shared) | Tasks 1 + 8 |
| Testing: JVM harness, `WidgetCounterTest`, `WidgetRendersTest` | Tasks 1–3 |
| Edge: deleted Tasbih → `NoOp` | Task 2 (`unknownTasbih_isNoOp`, `applyIncrement` `getById ?: return NoOp`) |
| Edge: `goal <= 0` clamp | Task 3 (`clampProgress` `coerceAtLeast(1)`) |
| `[+]` as `Button` with "+" (≥48dp, contentDescription) | Task 4 (`52dp`, `contentDescription`) |

**2. Placeholder scan:** No "TBD"/"handle edge cases"/"similar to". Task 5 flags one fragile line (`android.R.id.background`) with an explicit instruction to delete it and add `widget_insights_root` id — that is a concrete instruction, not a placeholder.

**3. Type consistency:**
- `WidgetCounter.Outcome` (NoOp/Apply) vs `WidgetCounter.Result` (Applied/NoOp) — deliberately distinct nested types; tests reference `WidgetCounter.Outcome.Apply` / `WidgetCounter.Outcome.NoOp`, wiring references `Result`. Consistent across Tasks 2 and 5.
- `WidgetRenders.clampProgress` returns `Progress(progress, max)` — same field names in Task 3 test, Task 3 impl, Task 5 `setProgressBar` calls.
- `DayBounds.startOfTodayMillis(now = ...)` — default-arg overload; callers in Tasks 5/8 pass no arg, test in Task 1 passes `now`. Consistent.
- `MainActivity.OPEN_COUNTER` / `OPEN_INSIGHTS` / `EXTRA_OPEN` — defined Task 6 step 1, consumed Task 6 step 3 (`WidgetRenders`) and Task 6 step 2 (`DhikrApp`). Task 5 uses local literals first, Task 6 step 3 swaps them. Consistent.
- `DhikrWidgets.refreshAll(context)` — Task 5 defines, Tasks 5/7 call. Consistent.
- `CounterWidgetReceiver.ACTION_INCREMENT` — companion const, matches the manifest `<action>` string `com.dhikr.app.action.WIDGET_INCREMENT` in Task 5 step 6.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-09-01-phase-5b-home-screen-widget.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
