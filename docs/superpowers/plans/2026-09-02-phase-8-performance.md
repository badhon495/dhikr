# Phase 8 — Performance & Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Baseline Profile + Macrobenchmark infrastructure, then make only the measurement-justified code and build optimizations that complete plan.md Phase 8.

**Architecture:** Two new `com.android.test` Gradle modules (`:baselineprofile`, `:benchmark`) target `:app`. `:app` gains `profileinstaller`, a `baselineProfile` consumer, and a `benchmark` build type. Workstream A (infra) is a hard prerequisite: it produces a committed baseline table. Workstreams B (code) and C (build) then each land as its own task, every one gated on a before/after benchmark delta recorded in the spec — a candidate with no measurable benefit is reverted, not merged.

**Tech Stack:** Kotlin, Jetpack Compose, AGP 9.3.0, Gradle 9.5, Kotlin 2.3.20, Room 2.8.4, `androidx.benchmark` (Macrobenchmark), `androidx.baselineprofile`, `androidx.profileinstaller`, UIAutomator, JUnit4.

**Spec:** `docs/superpowers/specs/2026-09-02-phase-8-performance-design.md`

## Global Constraints

- Use the latest stable versions of Android/Kotlin/AndroidX libraries compatible with AGP `9.3.0` and `compileSdk = 37` (plan.md §54, spec A3).
- `minSdk = 24`, `targetSdk = 37`, `compileSdk = 37` — unchanged.
- Every Workstream B / C change is gated on plan.md §66: measure before, make the change, measure after, keep only if a meaningful benefit or a clear architectural improvement with no regression. An item with no measurable effect is reverted and its "After" table row reads `no measurable effect — reverted`.
- No new user-facing feature, no Wear OS, no `security-crypto` / Tink replacement (audit and note only).
- Do not add a dependency merely for convenience — each addition is justified in the spec (plan.md §25).
- Benchmarks and profile generation run on the connected device only: POCO F3 (`alioth`), API 35, `adb` at `192.168.31.252:5555`. The executing engineer runs these Gradle tasks on that device and pastes results back; do not assume instrumented tests can be run headlessly.
- New `testTag`s added to production Compose code are named as `const val <NAME>_TEST_TAG = "..."` and kept to the minimum the benchmark selectors need.
- Commit after every task. Branch: `phase-8-performance` (already created).
- Do not commit signing keys or secrets (plan.md §63).
- Co-author trailer on every commit: `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.

---

## File Structure

**New files:**

- `settings.gradle.kts` — add `include(":benchmark", ":baselineprofile")` (modify).
- `gradle/libs.versions.toml` — add benchmark / profileinstaller / uiautomator / baselineprofile-plugin entries (modify).
- `build.gradle.kts` (root) — add `androidx.baselineprofile` plugin alias `apply false` (modify).
- `baselineprofile/build.gradle.kts` — `com.android.test` + `androidx.baselineprofile` module config (create).
- `baselineprofile/src/main/AndroidManifest.xml` — empty test manifest (create; AGP may generate).
- `baselineprofile/src/main/java/com/dhikr/app/baselineprofile/BaselineProfileGenerator.kt` — the §46 journey (create).
- `benchmark/build.gradle.kts` — `com.android.test` + `androidx.benchmark` module config (create).
- `benchmark/src/main/AndroidManifest.xml` — test manifest with `<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>` tools-remove and profileable (create; AGP may generate most).
- `benchmark/src/main/java/com/dhikr/app/benchmark/BenchmarkHelpers.kt` — shared UIAutomator navigation helpers (create).
- `benchmark/src/main/java/com/dhikr/app/benchmark/StartupBenchmark.kt` (create).
- `benchmark/src/main/java/com/dhikr/app/benchmark/CounterBenchmark.kt` (create).
- `benchmark/src/main/java/com/dhikr/app/benchmark/ScrollBenchmark.kt` (create).
- `benchmark/src/main/java/com/dhikr/app/benchmark/NavigationBenchmark.kt` (create).
- `app/src/main/baseline-prof.txt` — generated, committed (create via task).
- `app/src/test/java/com/dhikr/app/feature/counter/CounterViewModelTest.kt` — characterization tests for B1/B2 (create).

**Modified production files:**

- `app/build.gradle.kts` — `profileinstaller` dep, `baselineProfile` plugin + consumer, `benchmark` build type, icons dependency swap.
- `app/src/main/java/com/dhikr/app/feature/counter/CounterUiState.kt` — drop `elapsedSeconds` (B2).
- `app/src/main/java/com/dhikr/app/feature/counter/CounterViewModel.kt` — cache sorted routine steps (B1); `elapsedSeconds` as its own `StateFlow` (B2).
- `app/src/main/java/com/dhikr/app/feature/counter/CounterScreen.kt` — collect `elapsedSeconds` separately (B2); testTags for benchmark selectors.
- `app/src/main/java/com/dhikr/app/DhikrApp.kt` — move AI / backup / share repositories into their destinations (B4).
- `app/src/main/java/com/dhikr/app/DhikrApplication.kt` — seed on `Dispatchers.IO` (B7).
- `app/src/main/java/com/dhikr/app/feature/**/` — 6 Material icon import sites (B5).
- `app/proguard-rules.pro` — temporary R8 diagnostics, then reverted (C2).
- `gradle.properties` — dev-loop flags (C5), optional `enableR8.fullMode` (C2).
- `README.md` — performance section.
- `docs/superpowers/specs/2026-09-02-phase-8-performance-design.md` — measurement tables.

---

## Task 1: Scaffold `:baselineprofile` and `:benchmark` modules and wire `:app`

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)
- Create: `baselineprofile/build.gradle.kts`
- Create: `benchmark/build.gradle.kts`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: nothing (first task).
- Produces:
  - Module `:baselineprofile` applying `com.android.test` + `androidx.baselineprofile`, `targetProjectPath = ":app"`, `namespace = "com.dhikr.app.baselineprofile"`.
  - Module `:benchmark` applying `com.android.test` + `androidx.benchmark`, `namespace = "com.dhikr.app.benchmark"`, tests build against the `:app` `benchmark` build type.
  - `:app` has build type `benchmark` (release-like, debug-signed, `isDebuggable = false`), `implementation(libs.androidx.profileinstaller)`, `androidx.baselineprofile` plugin applied, and `baselineProfile(project(":baselineprofile"))`.
  - Version catalog keys: `androidx-profileinstaller`, `androidx-benchmark-macro-junit4`, `androidx-uiautomator`, and plugin `androidx-baselineprofile`.

- [ ] **Step 1: Add version-catalog entries**

In `gradle/libs.versions.toml`, under `[versions]` add (pick the newest stable each — check https://developer.android.com/jetpack/androidx/releases/benchmark and `.../profileinstaller`; the values below are known-good floors, bump if a newer stable exists):

```toml
profileinstaller = "1.4.1"
benchmarkMacro = "1.4.1"
uiautomator = "2.3.0"
baselineprofilePlugin = "1.4.1"
```

Under `[libraries]` add:

```toml
androidx-profileinstaller = { group = "androidx.profileinstaller", name = "profileinstaller", version.ref = "profileinstaller" }
androidx-benchmark-macro-junit4 = { group = "androidx.benchmark", name = "benchmark-macro-junit4", version.ref = "benchmarkMacro" }
androidx-uiautomator = { group = "androidx.test.uiautomator", name = "uiautomator", version.ref = "uiautomator" }
```

Under `[plugins]` add:

```toml
androidx-baselineprofile = { id = "androidx.baselineprofile", version.ref = "baselineprofilePlugin" }
android-test = { id = "com.android.test", version.ref = "agp" }
```

- [ ] **Step 2: Register the plugin in the root build**

In `build.gradle.kts` (root), inside `plugins { }`:

```kotlin
alias(libs.plugins.androidx.baselineprofile) apply false
alias(libs.plugins.android.test) apply false
```

- [ ] **Step 3: Include the modules**

In `settings.gradle.kts`, change the include line to:

```kotlin
include(":app")
include(":benchmark")
include(":baselineprofile")
```

- [ ] **Step 4: Create `baselineprofile/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.dhikr.app.baselineprofile"
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    // Run only on the connected physical device (API 33+ needed for
    // non-rooted profile generation).
    testOptions.managedDevices.devices  // no managed device; use connected

    experimentalProperties["android.experimental.self-instrumenting"] = true
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
```

If `libs.plugins.kotlin.android` is not yet in the catalog, add to `[plugins]`:

```toml
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

and to the root `build.gradle.kts` `plugins { }`: `alias(libs.plugins.kotlin.android) apply false`.

- [ ] **Step 5: Create `benchmark/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.dhikr.app.benchmark"
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = false
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
        }
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
```

- [ ] **Step 6: Wire `:app` — profileinstaller, plugin, consumer, build type**

In `app/build.gradle.kts`:

Add to `plugins { }`:

```kotlin
alias(libs.plugins.androidx.baselineprofile)
```

Add to `dependencies { }`:

```kotlin
implementation(libs.androidx.profileinstaller)
baselineProfile(project(":baselineprofile"))
```

Add to `android { buildTypes { } }`, after `debug { }`:

```kotlin
create("benchmark") {
    initWith(getByName("release"))
    signingConfig = signingConfigs.getByName("debug")
    matchingFallbacks += listOf("release")
    isDebuggable = false
    // Keep R8 on so the benchmark measures a release-like binary.
    isMinifyEnabled = true
    isShrinkResources = true
}
```

- [ ] **Step 7: Sync and verify the build configures**

Run: `./gradlew :app:assembleBenchmark --dry-run`
Expected: configuration succeeds, task graph prints, no "project ':benchmark' not found" or unresolved-dependency errors.

Run: `./gradlew :baselineprofile:tasks --group="Baseline Profile"`
Expected: lists `generateBaselineProfile` (and `generateReleaseBaselineProfile`).

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle/libs.versions.toml \
  baselineprofile/build.gradle.kts benchmark/build.gradle.kts app/build.gradle.kts
git commit -m "build: scaffold :baselineprofile and :benchmark modules

Adds two com.android.test modules targeting :app, a benchmark build
type, and the androidx.profileinstaller + baselineProfile consumer
wiring. No benchmarks implemented yet.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 2: Add benchmark testTags to production Compose code

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/feature/counter/CounterScreen.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/tasbih/TasbihLibraryScreen.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/insights/InsightsScreen.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: stable resource-testable identifiers for benchmark UIAutomator selectors:
  - `HomeScreen` root scroll container: `testTag("home_screen")`
  - `TasbihLibraryScreen` list: `testTag("tasbih_list")`
  - `InsightsScreen` scroll container: `testTag("insights_screen")`
  - `CounterScreen` tap area already has `onClickLabel = counter_tap_action_label` — add `testTag("counter_tap_area")` to the same `BoxWithConstraints` modifier.
  - Constants file `app/src/main/java/com/dhikr/app/ui/BenchmarkTags.kt` holding the `const val` names.

- [ ] **Step 1: Create the tag constants file**

Create `app/src/main/java/com/dhikr/app/ui/BenchmarkTags.kt`:

```kotlin
package com.dhikr.app.ui

/**
 * Stable UI identifiers referenced by the :benchmark module's UIAutomator
 * selectors. Kept in one place so a rename can't silently break a
 * Macrobenchmark. Not used by production logic.
 */
const val HOME_SCREEN_TEST_TAG = "home_screen"
const val TASBIH_LIST_TEST_TAG = "tasbih_list"
const val INSIGHTS_SCREEN_TEST_TAG = "insights_screen"
const val COUNTER_TAP_AREA_TEST_TAG = "counter_tap_area"
```

- [ ] **Step 2: Apply the tags**

In each screen, add `.testTag(<CONST>)` to the outermost scrollable / interactive container's `Modifier` chain, importing `androidx.compose.ui.platform.testTag` and the constant. For `CounterScreen`, add it to the `BoxWithConstraints` modifier that already has `.clickable(...)` for the tap area. Use `Modifier.semantics { testTagsAsResourceId = true }` on each screen's root (or set it once app-wide — see Step 3).

- [ ] **Step 3: Enable resource-id exposure app-wide**

In `app/src/main/java/com/dhikr/app/DhikrApp.kt`, on the outer `Box(modifier = Modifier.fillMaxSize())` (line ~221), add:

```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .semantics { testTagsAsResourceId = true },
)
```

Import `androidx.compose.ui.semantics.semantics` and `androidx.compose.ui.semantics.testTagsAsResourceId` (the latter is `@ExperimentalComposeUiApi` — add `@OptIn(ExperimentalComposeUiApi::class)` to `DhikrApp`).

- [ ] **Step 4: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dhikr/app/ui/BenchmarkTags.kt \
  app/src/main/java/com/dhikr/app/feature/ app/src/main/java/com/dhikr/app/DhikrApp.kt
git commit -m "chore: add testTags for Macrobenchmark selectors

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 3: Implement the Baseline Profile generator

**Files:**
- Create: `baselineprofile/src/main/java/com/dhikr/app/baselineprofile/BaselineProfileGenerator.kt`

**Interfaces:**
- Consumes: testTags from Task 2, `applicationId = "com.dhikr.app"`.
- Produces: a `generateBaselineProfile` run that writes `app/src/main/generated/baselineProfiles/baseline-prof.txt` (AGP default location for a consumer-wired app) — copied to `app/src/main/baseline-prof.txt` in Task 5.

- [ ] **Step 1: Write the generator**

Create the file:

```kotlin
package com.dhikr.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "com.dhikr.app"
private const val TIMEOUT = 5_000L

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        // Home rendered.
        device.wait(Until.hasObject(By.res(PACKAGE, "home_screen")), TIMEOUT)

        // Home -> Tasbih tab.
        device.findObject(By.text("Tasbih"))?.click()
        device.wait(Until.hasObject(By.res(PACKAGE, "tasbih_list")), TIMEOUT)

        // Open the first Tasbih into the Counter.
        device.findObject(By.res(PACKAGE, "tasbih_list"))
            ?.children?.firstOrNull()?.click()
        val tapArea = device.wait(
            Until.findObject(By.res(PACKAGE, "counter_tap_area")), TIMEOUT,
        )

        // Count 50 taps.
        repeat(50) {
            tapArea?.click()
            device.waitForIdle()
        }

        // Undo once, reset once (reset opens a confirm dialog).
        device.findObject(By.textContains("Undo"))?.click()

        // Insights tab, then Monthly history.
        device.findObject(By.text("Insights"))?.click()
        device.wait(Until.hasObject(By.res(PACKAGE, "insights_screen")), TIMEOUT)
        device.findObject(By.textContains("months"))?.click()
        device.waitForIdle()

        // Back to Home.
        device.pressBack()
        device.findObject(By.text("Home"))?.click()
        device.wait(Until.hasObject(By.res(PACKAGE, "home_screen")), TIMEOUT)
    }
}
```

Note: nav-tab labels (`Home`, `Tasbih`, `Insights`) come from `strings.xml` `nav_*` — if the device locale is Bengali these differ; the generator assumes an English device. Document this in the README.

- [ ] **Step 2: Compile-check**

Run: `./gradlew :baselineprofile:compileBenchmarkKotlin` (or `:baselineprofile:compileNonMinifiedReleaseKotlin` — whichever variant AGP creates; use `./gradlew :baselineprofile:tasks` to confirm).
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add baselineprofile/src/
git commit -m "test: baseline profile generator for the plan.md 46 journeys

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 4: Implement the Macrobenchmarks

**Files:**
- Create: `benchmark/src/main/java/com/dhikr/app/benchmark/BenchmarkHelpers.kt`
- Create: `benchmark/src/main/java/com/dhikr/app/benchmark/StartupBenchmark.kt`
- Create: `benchmark/src/main/java/com/dhikr/app/benchmark/CounterBenchmark.kt`
- Create: `benchmark/src/main/java/com/dhikr/app/benchmark/ScrollBenchmark.kt`
- Create: `benchmark/src/main/java/com/dhikr/app/benchmark/NavigationBenchmark.kt`

**Interfaces:**
- Consumes: testTags from Task 2, `PACKAGE = "com.dhikr.app"`.
- Produces: JUnit test classes runnable via `./gradlew :benchmark:connectedBenchmarkAndroidTest`.

- [ ] **Step 1: Shared helpers**

Create `BenchmarkHelpers.kt`:

```kotlin
package com.dhikr.app.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

const val PACKAGE = "com.dhikr.app"
const val TIMEOUT = 5_000L

fun MacrobenchmarkScope.waitForHome() {
    device.wait(Until.hasObject(By.res(PACKAGE, "home_screen")), TIMEOUT)
}

fun MacrobenchmarkScope.openCounterSession() {
    device.findObject(By.text("Tasbih"))?.click()
    device.wait(Until.hasObject(By.res(PACKAGE, "tasbih_list")), TIMEOUT)
    device.findObject(By.res(PACKAGE, "tasbih_list"))?.children?.firstOrNull()?.click()
    device.wait(Until.hasObject(By.res(PACKAGE, "counter_tap_area")), TIMEOUT)
}

fun MacrobenchmarkScope.tap(times: Int) {
    val area = device.findObject(By.res(PACKAGE, "counter_tap_area"))
    repeat(times) {
        area?.click()
        device.waitForIdle()
    }
}
```

- [ ] **Step 2: StartupBenchmark**

```kotlin
package com.dhikr.app.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun startupNoCompilation() = startup(CompilationMode.None())

    @Test fun startupBaselineProfile() = startup(
        CompilationMode.Partial(BaselineProfileMode.Require),
    )

    @Test fun startupFullCompilation() = startup(CompilationMode.Full())

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        waitForHome()
    }
}
```

- [ ] **Step 3: CounterBenchmark**

```kotlin
package com.dhikr.app.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CounterBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    private val mode = CompilationMode.Partial(BaselineProfileMode.Require)

    @Test fun tap100() = run(taps = 100)

    @Test fun tap1000() = run(taps = 1000)

    @Test fun idleWithTimer() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome(); startActivityAndWait(); openCounterSession(); tap(3)
        },
    ) {
        // Session running, timer ticking, no taps: frame production should
        // be near zero between 1s ticks (spec B2).
        Thread.sleep(10_000)
    }

    private fun run(taps: Int) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { pressHome(); startActivityAndWait(); openCounterSession() },
    ) {
        tap(taps)
    }
}
```

- [ ] **Step 4: ScrollBenchmark**

```kotlin
package com.dhikr.app.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()
    private val mode = CompilationMode.Partial(BaselineProfileMode.Require)

    @Test fun scrollTasbihList() = fling("Tasbih", "tasbih_list")

    @Test fun scrollInsights() = fling("Insights", "insights_screen")

    private fun fling(tabLabel: String, tag: String) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = 8,
        setupBlock = {
            pressHome(); startActivityAndWait()
            device.findObject(By.text(tabLabel))?.click()
            device.wait(Until.hasObject(By.res(PACKAGE, tag)), TIMEOUT)
        },
    ) {
        val list = device.findObject(By.res(PACKAGE, tag))
        list?.setGestureMargin(device.displayWidth / 5)
        repeat(3) {
            list?.fling(Direction.DOWN)
            list?.fling(Direction.UP)
        }
    }
}
```

- [ ] **Step 5: NavigationBenchmark**

```kotlin
package com.dhikr.app.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun tabHops() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 8,
        setupBlock = { pressHome(); startActivityAndWait(); waitForHome() },
    ) {
        listOf("Tasbih", "Insights", "Settings", "Home").forEach { label ->
            device.findObject(By.text(label))?.click()
            device.waitForIdle()
        }
    }
}
```

- [ ] **Step 6: Compile-check**

Run: `./gradlew :benchmark:compileBenchmarkKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add benchmark/src/
git commit -m "test: Macrobenchmarks for startup, counter, scroll, navigation

Covers plan.md 47. Not yet run on device.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 5: Generate the Baseline Profile and record the benchmark baseline (device — user)

**Files:**
- Create: `app/src/main/baseline-prof.txt`
- Modify: `docs/superpowers/specs/2026-09-02-phase-8-performance-design.md`

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces: a committed `app/src/main/baseline-prof.txt` and a filled-in "Baseline (before)" table. **All of Workstream B and C (Tasks 6+) are blocked on this task.**

- [ ] **Step 1: Confirm the device is connected**

Run: `adb devices`
Expected: `192.168.31.252:5555   device` listed. If not: `adb connect 192.168.31.252:5555`.

- [ ] **Step 2: Generate the Baseline Profile**

Run: `./gradlew :app:generateBaselineProfile`
Expected: the `BaselineProfileGenerator` test runs on device (takes several minutes; app installs, journey plays), then AGP writes the profile. Note the output path from the Gradle log (typically `app/src/main/generated/baselineProfiles/baseline-prof.txt`).

If the journey fails on a selector (label text mismatch, timing), fix `BaselineProfileGenerator.kt`, re-run, and amend Task 3's commit.

- [ ] **Step 3: Place the profile where `:app` consumes it**

Copy the generated file to `app/src/main/baseline-prof.txt` (the location `profileinstaller` + AGP pick up automatically for a non-flavored app). Verify a subsequent `./gradlew :app:assembleRelease` log shows `baseline-prof.txt` being merged.

- [ ] **Step 4: Run the full benchmark suite**

Run: `./gradlew :benchmark:connectedBenchmarkAndroidTest`
Expected: all benchmark classes run on device. Results print to console and land in `benchmark/build/outputs/connected_android_test_additional_output/` as JSON.

- [ ] **Step 5: Fill in the "Baseline (before)" table**

Transcribe the median values into the spec's "Baseline (before)" table. For each row use: startup `timeToInitialDisplay` median (ms); counter/scroll/nav `frameDurationCpuMs` P50/P90/P99; `idleWithTimer` `frameOverrunCount` (or frame count). Leave the AAB size row for Task 12.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/baseline-prof.txt \
  docs/superpowers/specs/2026-09-02-phase-8-performance-design.md
git commit -m "perf: baseline profile + measured performance baseline

Generated on POCO F3 / API 35. Baseline table recorded in the Phase 8
spec; Workstream B/C optimizations are gated against these numbers.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 6: B1 — Cache sorted routine steps in CounterViewModel

**Files:**
- Create: `app/src/test/java/com/dhikr/app/feature/counter/CounterViewModelTest.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/counter/CounterViewModel.kt`

**Interfaces:**
- Consumes: `CounterViewModel` public API (`uiState: StateFlow<CounterUiState>`, `onTap()`, factory).
- Produces: `CounterViewModel` no longer sorts `routine.steps` or rebuilds `routineSteps` inside `buildState()`; a private `cachedRoutineSteps: List<RoutineStepDisplay>` and `sortedRoutineSteps: List<RoutineStepEntity>` populated once per routine/step load.

- [ ] **Step 1: Write a characterization test for routine step display**

Create `CounterViewModelTest.kt`. The ViewModel takes repository interfaces; use the real Room DB in-memory (matches how `TasbihBenefitsColumnsTest` builds one) or fakes. Minimal fake-based test:

```kotlin
package com.dhikr.app.feature.counter

import com.dhikr.app.core.database.dao.RoutineWithSteps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class CounterViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun routineSteps_areDisplayedInStepOrder_afterTaps() = runTest {
        val vm = buildRoutineViewModel(
            // steps deliberately out of stepOrder in the list
            stepsOutOfOrder = true,
        )
        // let init() settle
        vm.uiState.first { it.sessionReady }

        val before = vm.uiState.value.routineSteps
        repeat(5) { vm.onTap() }
        val after = vm.uiState.value.routineSteps

        assertEquals(before, after)
        assertEquals(listOf("First", "Second", "Third"), after.map { it.tasbihName })
    }
}
```

(`buildRoutineViewModel` is a local helper you write, constructing `CounterViewModel` with in-memory fakes or an in-memory Room DB seeded with a 3-step routine whose `RoutineStepEntity.stepOrder` is 0,1,2 but returned in the list as 2,0,1. Follow the fake style already used in `BenefitsRepositoryTest`.)

- [ ] **Step 2: Run it — expect PASS (characterizes current correct behavior)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.feature.counter.CounterViewModelTest"`
Expected: PASS. This is a characterization test — current code already sorts correctly, just wastefully. The test locks the behavior so the refactor can't change it.

- [ ] **Step 3: Refactor — sort once at load**

In `CounterViewModel.kt`:

Add fields near `activeRoutine`:

```kotlin
private var sortedRoutineSteps: List<com.dhikr.app.core.database.entity.RoutineStepEntity> = emptyList()
private var cachedRoutineStepDisplays: List<RoutineStepDisplay> = emptyList()
```

In `initializeSession()` where `activeRoutine = routine` is set, right after computing `routineStepNames`, add:

```kotlin
sortedRoutineSteps = sortedSteps
cachedRoutineStepDisplays = sortedSteps.mapIndexed { i, step ->
    RoutineStepDisplay(routineStepNames[i], step.targetCount)
}
```

In `advanceRoutineStep()`, the `sortedSteps` local is already computed once per advance — that is fine (it runs once per step, not per tap). Leave it, but it can reuse `sortedRoutineSteps` instead of re-sorting:

```kotlin
val sortedSteps = sortedRoutineSteps
```

In `buildState()`, replace the `steps` computation:

```kotlin
val steps = if (activeRoutine != null) cachedRoutineStepDisplays else emptyList()
```

Remove the now-unused `sortedBy { it.stepOrder }` and `routineStepNames.mapIndexed { ... }` block inside `buildState()`.

- [ ] **Step 4: Run the test + full counter test suite**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.feature.counter.*" --tests "com.dhikr.app.core.counter.*"`
Expected: PASS.

- [ ] **Step 5: Device measurement**

Run (user, on device): `./gradlew :benchmark:connectedBenchmarkAndroidTest --tests "com.dhikr.app.benchmark.CounterBenchmark.tap1000"`

Compare `frameDurationCpuMs` P90 against the Task 5 baseline. Record in the spec "After" table, row B1.

- [ ] **Step 6: Keep or revert**

If P90 improved meaningfully OR is unchanged (the change is a clear allocation reduction with no regression — keep): commit. If P90 regressed: revert the production change, keep the test, record `no measurable effect — reverted`.

- [ ] **Step 7: Commit**

```bash
git add app/src/test/java/com/dhikr/app/feature/counter/CounterViewModelTest.kt \
  app/src/main/java/com/dhikr/app/feature/counter/CounterViewModel.kt \
  docs/superpowers/specs/2026-09-02-phase-8-performance-design.md
git commit -m "perf: cache sorted routine steps instead of re-sorting per tap (B1)

Measured: <before P90> -> <after P90> ms on POCO F3.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 7: B2 — Split elapsedSeconds into its own StateFlow

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/feature/counter/CounterUiState.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/counter/CounterViewModel.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/counter/CounterScreen.kt`
- Modify: `app/src/test/java/com/dhikr/app/feature/counter/CounterViewModelTest.kt`

**Interfaces:**
- Consumes: B1's `CounterViewModel`.
- Produces:
  - `CounterUiState` no longer has `elapsedSeconds`. `totalCount` / `progressFraction` unchanged.
  - `CounterViewModel` exposes `val elapsedSeconds: StateFlow<Int>`.
  - `CounterViewModel.persist()` reads elapsed from the private `elapsedSeconds` backing field (unchanged) — only the `_uiState` copy is removed.
  - `CounterScreen` collects `viewModel.elapsedSeconds` separately and passes the `Int` into `formatSessionLabel`, the routine-complete dialog, and `SessionSummaryDialog`.

- [ ] **Step 1: Add a test for the elapsed flow**

Add to `CounterViewModelTest.kt`:

```kotlin
@Test
fun elapsedSeconds_ticksIndependentlyOfUiState() = runTest {
    val vm = buildSingleTasbihViewModel()
    vm.uiState.first { it.sessionReady }

    val uiStateBefore = vm.uiState.value
    advanceTimeBy(3_100)  // 3 timer ticks
    runCurrent()

    assertEquals(3, vm.elapsedSeconds.value)
    // The count/lap-bearing state object is not replaced by a mere tick.
    assertSame(uiStateBefore, vm.uiState.value)
}
```

- [ ] **Step 2: Run — expect FAIL (compile error: no `elapsedSeconds` flow / `assertSame` fails)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.feature.counter.CounterViewModelTest.elapsedSeconds_ticksIndependentlyOfUiState"`
Expected: FAIL / does not compile.

- [ ] **Step 3: Remove `elapsedSeconds` from `CounterUiState`**

In `CounterUiState.kt`: delete the `val elapsedSeconds: Int,` field and its doc comment. Remove `elapsedSeconds = 0` from the `Empty` companion. `totalCount` and `progressFraction` are untouched.

- [ ] **Step 4: Add the flow in `CounterViewModel`**

Keep the existing `private var elapsedSeconds = 0` field (persist() reads it). Add:

```kotlin
private val _elapsedSeconds = MutableStateFlow(0)
val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()
```

Wherever `elapsedSeconds` is mutated (`startTimer()`, `onReset()`, `initializeSession()` restore paths, `advanceRoutineStep()` comment says it's left as-is), after each assignment add `_elapsedSeconds.value = elapsedSeconds`. In `startTimer()`:

```kotlin
private fun startTimer() {
    viewModelScope.launch {
        while (true) {
            delay(1000)
            if (sessionReady && engine.isRunning()) {
                elapsedSeconds += 1
                _elapsedSeconds.value = elapsedSeconds
                // No buildState() here any more — the tick no longer
                // touches the count/lap/routine UI state.
            }
        }
    }
}
```

Remove `elapsedSeconds = ...` from the `CounterUiState(...)` construction in `buildState()`.

Note: the `init { }` block's debounced persister keys off `_uiState` changes. With the timer no longer emitting to `_uiState`, a long paused-then-edited session still persists via the other `_uiState` emissions and the ON_STOP `flushSession()`. Elapsed time alone advancing no longer triggers a debounced save — acceptable, elapsed is recomputed from `sessionStartedAtMillis` on restore and re-saved on the next real state change or ON_STOP. Verify `persist()` still writes `s.elapsedSeconds`… — change that line to read the field directly: `elapsedSeconds = elapsedSeconds,` (the private field) instead of `s.elapsedSeconds`.

- [ ] **Step 5: Update `CounterScreen`**

```kotlin
val state by viewModel.uiState.collectAsState()
val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
```

Replace `state.elapsedSeconds` with `elapsedSeconds` at all three sites: `formatSessionLabel(elapsedSeconds, state.totalCount)`, the routine-complete dialog body `formatDuration(elapsedSeconds)`, and `SessionSummaryDialog(... elapsedSeconds = elapsedSeconds ...)`. The `onReset()` path in the ViewModel already zeroes it.

- [ ] **Step 6: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.feature.counter.*"`
Expected: PASS.

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (catches any missed `state.elapsedSeconds` reference).

- [ ] **Step 7: Device measurement**

Run (user): `./gradlew :benchmark:connectedBenchmarkAndroidTest --tests "com.dhikr.app.benchmark.CounterBenchmark.idleWithTimer"`
Compare `frameOverrunCount` / frame count against baseline — expect a large drop (near-zero frames produced during the 10s idle). Record in spec "After" row B2.

- [ ] **Step 8: Keep or revert + commit**

Keep if idle frame production dropped and `tap100`/`tap1000` did not regress. Commit:

```bash
git add app/src/main/java/com/dhikr/app/feature/counter/ \
  app/src/test/java/com/dhikr/app/feature/counter/CounterViewModelTest.kt \
  docs/superpowers/specs/2026-09-02-phase-8-performance-design.md
git commit -m "perf: move counter elapsed time to its own StateFlow (B2)

The 1s timer tick no longer emits a whole CounterUiState, so an idle
running session stops recomposing the counter screen every second.
Measured: <before> -> <after> idle frames on POCO F3.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 8: B3 — Verify and fix CounterUiState Compose stability

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/feature/counter/CounterUiState.kt` (only if flagged)
- Modify: `app/build.gradle.kts` (temporary compiler-report flag, then reverted)

**Interfaces:**
- Consumes: Task 7's `CounterUiState`.
- Produces: a documented stability verdict; `@Immutable` on `RoutineStepDisplay` and `CounterUiState` if the compiler reports them unstable.

- [ ] **Step 1: Enable Compose compiler reports for one build**

In `app/build.gradle.kts`, temporarily add to `kotlin { compilerOptions { } }`:

```kotlin
freeCompilerArgs.addAll(
    "-P", "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" +
        layout.buildDirectory.dir("compose_reports").get().asFile.absolutePath,
)
```

- [ ] **Step 2: Build and inspect**

Run: `./gradlew :app:compileReleaseKotlin`
Then read `app/build/compose_reports/*-classes.txt`. Find the `CounterUiState` entry.
Expected: it will likely read `unstable class CounterUiState` because of `routineSteps: List<RoutineStepDisplay>` and possibly `dhikr: TasbihEntity`.

- [ ] **Step 3: Apply `@Immutable` if unstable**

If flagged, in `CounterUiState.kt`:

```kotlin
import androidx.compose.runtime.Immutable

@Immutable
data class RoutineStepDisplay(val tasbihName: String, val targetCount: Int)

@Immutable
data class CounterUiState(
    // ...unchanged fields...
)
```

`routineSteps` stays `List<RoutineStepDisplay>` — `@Immutable` on the holder tells the compiler the whole object (list reference included) never changes after construction, which is true (a new `CounterUiState` is built for every change). Do **not** add `kotlinx.collections.immutable` (avoids a dependency, spec B3).

If `TasbihEntity` is also flagged unstable, add `@Immutable` to it in `core/database/entity/TasbihEntity.kt` — it is a `data class` of `String`/`Int`/`Long`/`Boolean` only, genuinely immutable.

- [ ] **Step 4: Re-run the report, confirm stable**

Run: `./gradlew :app:compileReleaseKotlin`
Expected: `stable class CounterUiState` in the report.

- [ ] **Step 5: Remove the report flag**

Revert the `freeCompilerArgs` addition from `app/build.gradle.kts`.

- [ ] **Step 6: Device measurement**

Run (user): `./gradlew :benchmark:connectedBenchmarkAndroidTest --tests "com.dhikr.app.benchmark.CounterBenchmark.tap1000"`
Record P90 vs baseline in spec "After" row B3.

- [ ] **Step 7: Keep or revert + commit**

`@Immutable` annotations with a confirmed-stable report are a clear correctness/architecture improvement — keep even if the benchmark delta is within noise (record the delta honestly). Commit:

```bash
git add app/src/main/java/com/dhikr/app/feature/counter/CounterUiState.kt \
  app/src/main/java/com/dhikr/app/core/database/entity/TasbihEntity.kt \
  docs/superpowers/specs/2026-09-02-phase-8-performance-design.md
git commit -m "perf: mark CounterUiState @Immutable for Compose stability (B3)

Compose compiler report now classifies it stable; skippable
recompositions of the counter screen. Measured tap1000 P90:
<before> -> <after> ms.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 9: B4 — Move AI / backup / share repositories into their destinations

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/DhikrApp.kt`

**Interfaces:**
- Consumes: existing `DhikrApp` composable structure, all repository constructors.
- Produces: `GeminiClient`, `BenefitsRepository`, `SecureKeyStore`, `BackupRepository`, `RoutineShareCodec`, `RoutineShareRepository` (and `reminderScheduler` if measured worthwhile) constructed inside `composable(ROUTE_TASBIH_EDITOR)`, `composable(ROUTE_SETTINGS)`, and `composable(ROUTE_ROUTINES)` / `composable(ROUTE_ROUTINES_IMPORT)` rather than in the top-level `DhikrApp` body. No behavior change.

- [ ] **Step 1: Baseline the startup number for this specific change**

Run (user): `./gradlew :benchmark:connectedBenchmarkAndroidTest --tests "com.dhikr.app.benchmark.StartupBenchmark.startupBaselineProfile"`
Record the current `timeToInitialDisplay` median (should match Task 5's baseline; re-confirm since Tasks 6–8 landed).

- [ ] **Step 2: Move the AI repositories into the Tasbih editor destination**

In `DhikrApp.kt`, delete these top-level `remember` lines (~137–143):

```kotlin
val secureKeyStore = remember { SecureKeyStore(context.applicationContext) }
val geminiClient = remember { GeminiClient() }
val benefitsRepository = remember { BenefitsRepository.create(secureKeyStore, geminiClient, tasbihRepository) }
```

`secureKeyStore` is also used by `composable(ROUTE_SETTINGS)` (passed to `SettingsViewModel.Factory`). So:

- Inside `composable(ROUTE_TASBIH_EDITOR) { ... }`, before the `viewModel(...)` call:

```kotlin
val secureKeyStore = remember { SecureKeyStore(context.applicationContext) }
val geminiClient = remember { GeminiClient() }
val benefitsRepository = remember {
    BenefitsRepository.create(secureKeyStore, geminiClient, tasbihRepository)
}
```

- Inside `composable(ROUTE_SETTINGS) { ... }`, before its `viewModel(...)`:

```kotlin
val secureKeyStore = remember { SecureKeyStore(context.applicationContext) }
```

(Two independent `SecureKeyStore` instances — it is a thin wrapper over `EncryptedSharedPreferences`; both point at the same underlying store. If `SecureKeyStore` holds meaningful per-instance state, hoist it one level into a shared parent instead — check the class first.)

- [ ] **Step 3: Move backup repository into Settings**

Delete top-level (~138):

```kotlin
val backupRepository = remember { BackupRepository(app.database, preferencesRepository) }
```

Add inside `composable(ROUTE_SETTINGS) { ... }`:

```kotlin
val backupRepository = remember { BackupRepository(app.database, preferencesRepository) }
```

- [ ] **Step 4: Move routine-share repositories into Routines + Import**

Delete top-level (~149–150):

```kotlin
val routineShareCodec = remember { RoutineShareCodec(AndroidBase64) }
val routineShareRepository = remember { RoutineShareRepository(app.database, routineShareCodec) }
```

`routineShareCodec` is used by both `composable(ROUTE_ROUTINES)` (for `RoutineShareViewModel.Factory`) and — indirectly — `routineShareRepository` by `ROUTE_ROUTINES` and `ROUTE_ROUTINES_IMPORT`. Add to each of those two `composable` blocks:

```kotlin
val routineShareCodec = remember { RoutineShareCodec(AndroidBase64) }
val routineShareRepository = remember { RoutineShareRepository(app.database, routineShareCodec) }
```

- [ ] **Step 5: Decide on `reminderScheduler`**

`reminderScheduler` (~152) is used by `ROUTE_ROUTINES` and `ROUTE_ROUTINE_EDITOR`. `ReminderScheduler(context)` is a `Context` + `AlarmManager` wrapper — cheap. Check the class: if the constructor does no I/O, leave it hoisted (moving it saves nothing and duplicates it across two destinations). If it touches DataStore / disk in its constructor, move it into both destinations. Document the decision in the commit message.

- [ ] **Step 6: Compile + smoke build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Fix any now-out-of-scope reference (e.g. `appVersionName` is still top-level and used in several places — keep it hoisted).

- [ ] **Step 7: Manual smoke pass (user, on device)**

Install the debug build. Verify: open Tasbih editor → "Explain" / benefits still work; Settings → API key entry + backup export/import still work; Routines → share a routine, import a `.dhikrroutine` file. All destinations open without crash.

- [ ] **Step 8: Device measurement**

Run (user): `./gradlew :benchmark:connectedBenchmarkAndroidTest --tests "com.dhikr.app.benchmark.StartupBenchmark"`
Compare `startupBaselineProfile` `timeToInitialDisplay` median vs Step 1. Record in spec "After" row B4.

- [ ] **Step 9: Keep or revert + commit**

Keep if startup improved, OR if `SecureKeyStore`/Tink init was measurably on the startup path and is now off it (that alone justifies the move per spec B4), AND the smoke pass is clean. Otherwise revert.

```bash
git add app/src/main/java/com/dhikr/app/DhikrApp.kt \
  docs/superpowers/specs/2026-09-02-phase-8-performance-design.md
git commit -m "perf: construct AI/backup/share repositories at their destinations (B4)

They were built in remember{} at the top of the composable tree,
i.e. at startup, though only reached from the Tasbih editor, Settings,
and Routines screens. Moves Tink/EncryptedSharedPreferences init off
the cold-start path. Measured cold TTID: <before> -> <after> ms.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 10: B5 — Replace material-icons-extended with core icons

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/dhikr/app/feature/routines/RoutineEditorScreen.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/tasbih/TasbihLibraryScreen.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/routines/RoutinesScreen.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/tasbih/TasbihEditorScreen.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/insights/InsightsScreen.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `libs.compose.material.icons.extended` removed; the 6 usages resolved against `androidx.compose.material:material-icons-core` (transitively present via Material3, or added explicitly).

- [ ] **Step 1: Confirm the 6 icons are in core**

`Icons.Filled.Search`, `Icons.Filled.PlayArrow`, `Icons.AutoMirrored.Filled.ArrowBack`, `Icons.Filled.Schedule`, `Icons.Filled.Favorite`, `Icons.Outlined.FavoriteBorder`.
`Search`, `PlayArrow`, `ArrowBack`, `Favorite`, `FavoriteBorder` are in `material-icons-core`. `Schedule` is **not** — it is extended-only.

- [ ] **Step 2: Hand-author a Schedule icon**

Create `app/src/main/java/com/dhikr/app/ui/MiscIcons.kt` with a clock `ImageVector` in the style of the existing `ui/NavIcons.kt` (24×24, stroke, round caps — a circle + two hands). Follow the exact builder pattern in `NavIcons.kt`. Name it `ScheduleIcon`.

- [ ] **Step 3: Swap the dependency**

In `gradle/libs.versions.toml` `[libraries]`, remove `compose-material-icons-extended`, add:

```toml
compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }
```

In `app/build.gradle.kts` `dependencies { }`, replace:

```kotlin
implementation(libs.compose.material.icons.extended)
```

with:

```kotlin
implementation(libs.compose.material.icons.core)
```

- [ ] **Step 4: Fix the import sites**

In `InsightsScreen.kt`, replace `Icons.Filled.Schedule` with `com.dhikr.app.ui.ScheduleIcon` and drop the `import androidx.compose.material.icons.filled.Schedule`. The other 5 sites keep working (core package). Verify each file's `import androidx.compose.material.icons.*` lines resolve — `filled.Search`, `filled.PlayArrow`, `automirrored.filled.ArrowBack`, `filled.Favorite`, `outlined.FavoriteBorder` are all core.

- [ ] **Step 5: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (no test references the removed dependency).

- [ ] **Step 6: Measurement — deferred to Task 12**

AAB size is measured in the release-size task. Note in spec "After" row B5: `see C1 size comparison`.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
  app/src/main/java/com/dhikr/app/ui/MiscIcons.kt \
  app/src/main/java/com/dhikr/app/feature/
git commit -m "perf: drop material-icons-extended, use core + one hand icon (B5)

Only 6 Material icons were used; 5 are in material-icons-core and the
Schedule icon is now hand-authored alongside the nav icons. Removes a
large icon dependency (plan.md 55).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 11: B6 — Room index audit + B7 seed dispatcher

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/DhikrApplication.kt` (B7)
- Modify: entity files + `AppDatabase.kt` **only if** the audit finds a missing index (B6)

**Interfaces:**
- Consumes: nothing.
- Produces: a documented index-audit verdict; `DhikrApplication` seeding on `Dispatchers.IO`.

- [ ] **Step 1: Audit indices against query columns**

Review every `@Query` in `SessionDao`, `TasbihDao`, `RoutineDao`, `RoutineProgressDao`, `RoutineCompletionDao`, `TasbihProgressDao`, `RoutineDao`'s `RoutineWithSteps`. Cross-check `WHERE` / `JOIN` / `ORDER BY` columns against declared `indices` on the entity. Known state:
- `session`: `Index("startedAt")`, `Index("tasbihId")` — covers `totalSince`, `totalForTasbih`, `totalsByTasbihSince`. GROUP-BY-on-expression queries cannot be indexed — acceptable.
- `routine_step`: `Index("routineId")`, `Index("tasbihId")` — covers the join + FK.
- `tasbih_progress`, `routine_progress`: single-column primary key (`tasbihId` / `routineId`) — lookups are by PK, already indexed.
- `routine_completion`: composite PK `(routineId, dayStartMillis)` — covers by-routine and by-day+routine lookups. A lookup by `dayStartMillis` alone would need its own index — check if any query does that.

- [ ] **Step 2: Build with Room warnings visible**

Run: `./gradlew :app:kspDebugKotlin --info 2>&1 | grep -i "room\|index"`
Expected: no `"column ... is part of a foreign key but doesn't have an index"` warnings. If any appear, add the `Index(...)` to that entity and bump `AppDatabase.version` + 1 with a `// vN: added index on X` comment (fallbackToDestructiveMigration handles the rebuild — no hand migration).

- [ ] **Step 3: Measurement — list rendering with 1000 rows**

This needs seeded data. Add a benchmark that seeds via `adb shell` before the measured block, OR (simpler) accept that current history-screen data volume is small and record B6 as `indices verified adequate; no schema change` if Step 1–2 found nothing. Only build the 1000-row benchmark if a missing index was found and fixed.

- [ ] **Step 4: B7 — seed on Dispatchers.IO**

In `DhikrApplication.kt`, change:

```kotlin
private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
```

The `onCreate` seed launch does Room I/O. Change that specific launch:

```kotlin
applicationScope.launch(Dispatchers.IO) {
    if (database.tasbihDao().count() == 0) { ... }
    ...
}
```

(Keep the scope's default dispatcher as-is for any other future use, or change the whole scope to `Dispatchers.IO` since seeding is its only current user — prefer the narrower `launch(Dispatchers.IO)`.)

- [ ] **Step 5: Build + test**

Run: `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`
Expected: PASS / SUCCESSFUL.

- [ ] **Step 6: Device measurement**

Run (user): `./gradlew :benchmark:connectedBenchmarkAndroidTest --tests "com.dhikr.app.benchmark.StartupBenchmark.startupBaselineProfile"`
Record cold TTID vs baseline in spec "After" row B7.

- [ ] **Step 7: Keep or revert + commit**

B7 (`Dispatchers.IO` for I/O) is correct regardless of the benchmark delta — keep, record the delta honestly. B6: commit only if an index was added.

```bash
git add app/src/main/java/com/dhikr/app/DhikrApplication.kt \
  docs/superpowers/specs/2026-09-02-phase-8-performance-design.md
# plus entity/AppDatabase files if B6 changed anything
git commit -m "perf: seed database on Dispatchers.IO; Room index audit (B6, B7)

Index audit: <verdict>. Seeding moved off Dispatchers.Default.
Cold TTID: <before> -> <after> ms.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 12: C1 — Release size baseline and C4 profile-embedding check

**Files:**
- Modify: `docs/superpowers/specs/2026-09-02-phase-8-performance-design.md`

**Interfaces:**
- Consumes: Tasks 1, 5, 10.
- Produces: measured download + install size in the spec, filling the "Baseline (before)" AAB row and the B5 "After" row; confirmation the Baseline Profile ships in the release AAB.

- [ ] **Step 1: Check out the pre-B5 tree size (reference point)**

Run: `git stash` is not needed — instead, build release at the commit *before* Task 10 for the "before" number:

```bash
git log --oneline | head -20   # find the commit before B5 (Task 10)
git checkout <that-sha> -- app/build.gradle.kts gradle/libs.versions.toml
./gradlew :app:bundleRelease
```

Record the AAB from `app/build/outputs/bundle/release/app-release.aab` size and, via `bundletool build-apks --mode=default` + `bundletool get-size total`, the download size. Then restore: `git checkout HEAD -- app/build.gradle.kts gradle/libs.versions.toml`.

(If the release keystore is absent, the AAB is unsigned — size comparison is still valid.)

- [ ] **Step 2: Build current release**

Run: `./gradlew :app:bundleRelease`
Record current AAB + download size. The delta from Step 1 is B5's effect (plus baseline profile addition from Task 1).

- [ ] **Step 3: Confirm the Baseline Profile is embedded**

Run:

```bash
unzip -l app/build/outputs/bundle/release/app-release.aab | grep -i "prof\|dexopt"
```

Expected: `BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof` (and `.profm`). If absent, the `baselineProfile(project(":baselineprofile"))` consumer wiring from Task 1 is wrong — fix and re-check.

- [ ] **Step 4: Record in spec**

Fill the "Baseline (before)" AAB row (Step 1 number) and the "After" B5 row (Step 2 number, note "includes baseline.prof addition").

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-09-02-phase-8-performance-design.md
git commit -m "docs: record release AAB size + confirm baseline profile ships (C1, C4)

Download <X> MB -> <Y> MB after the icon dependency swap. baseline.prof
verified present in BUNDLE-METADATA.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 13: C2 — R8 audit and optional fullMode

**Files:**
- Modify: `app/proguard-rules.pro` (temporary diagnostics, then reverted)
- Modify: `gradle.properties` (only if fullMode is adopted)
- Modify: `docs/superpowers/specs/2026-09-02-phase-8-performance-design.md`

**Interfaces:**
- Consumes: Task 12's release build.
- Produces: a documented R8 verdict; `android.enableR8.fullMode=true` in `gradle.properties` if it shrinks output with all tests + smoke passing.

- [ ] **Step 1: Add R8 diagnostics**

Append to `app/proguard-rules.pro`:

```
-printusage build/r8-usage.txt
-printseeds build/r8-seeds.txt
-printconfiguration build/r8-config.txt
```

- [ ] **Step 2: Build and inspect**

Run: `./gradlew :app:bundleRelease`
Read `app/build/r8-usage.txt` — scan for large packages being stripped (expected: unused Compose material, unused Room). Read `app/build/r8-config.txt` — confirm the only project `-keep` rules are the Tink `-dontwarn`s; everything else should come from AAR consumer rules. Note anything over-broad.

- [ ] **Step 3: Remove diagnostics**

Revert the three `-print*` lines from `proguard-rules.pro`.

- [ ] **Step 4: Try fullMode**

Add to `gradle.properties`:

```
android.enableR8.fullMode=true
```

Run: `./gradlew :app:bundleRelease`
Record AAB size vs Task 12.

- [ ] **Step 5: Verify fullMode didn't break anything**

Run: `./gradlew :app:testDebugUnitTest`
Run (user, on device): install the fullMode release build. Smoke: backup export → import round-trip (exercises `kotlinx.serialization`); open every screen (exercises Room codegen); AI benefits call if an API key is set.
Run (user): full `./gradlew :benchmark:connectedBenchmarkAndroidTest`.

- [ ] **Step 6: Keep or revert**

Keep `fullMode` only if AAB shrank AND all unit tests pass AND the smoke pass is clean AND no benchmark regressed. Otherwise remove the line.

- [ ] **Step 7: Record + commit**

```bash
git add app/proguard-rules.pro gradle.properties \
  docs/superpowers/specs/2026-09-02-phase-8-performance-design.md
git commit -m "build: R8 audit; <adopt|reject> fullMode (C2)

R8 usage/seeds/config inspected — <verdict>. fullMode <kept: -Z MB |
rejected: broke serialization / no size win>.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 14: C3 — Dependency audit + C5 dev-loop config

**Files:**
- Modify: `gradle.properties`
- Modify: `docs/superpowers/specs/2026-09-02-phase-8-performance-design.md`

**Interfaces:**
- Consumes: nothing.
- Produces: a documented dependency-size table; `org.gradle.caching` / `configuration-cache` / `parallel` in `gradle.properties` if the build stays green.

- [ ] **Step 1: Dump the release classpath**

Run: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath > build/deps.txt`
Identify each top-level dependency's transitive weight (use the AGP APK analyzer on the Task 12 AAB for on-device method/size contribution). Record:
- `androidx.security-crypto` → Tink: size cost. Note in spec C3 — **audit only, no change** (needs its own spec + security review per plan.md §25, §54).
- Anything else large/surprising.

- [ ] **Step 2: Enable dev-loop flags one at a time**

Add to `gradle.properties`:

```
org.gradle.parallel=true
org.gradle.caching=true
```

Run: `./gradlew clean :app:assembleDebug` then `./gradlew :app:assembleDebug` again (incremental).
Expected: both succeed.

- [ ] **Step 3: Try configuration cache**

Add:

```
org.gradle.configuration-cache=true
```

Run: `./gradlew :app:assembleDebug` twice.
Expected: second run prints `Reusing configuration cache`. If it fails with a `keystore.properties` / `FileInputStream` configuration-time read error in `app/build.gradle.kts`, fix that read to use `providers.fileContents(layout.projectDirectory.file("keystore.properties"))` lazily, or gate it, then re-run. If it can't be made compatible cheaply, remove the line and note why.

- [ ] **Step 4: Full build + benchmark sanity**

Run: `./gradlew clean build -x connectedBenchmarkAndroidTest -x connectedBaselineProfileAndroidTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add gradle.properties app/build.gradle.kts \
  docs/superpowers/specs/2026-09-02-phase-8-performance-design.md
git commit -m "build: dependency audit; enable Gradle caching/parallel/config-cache (C3, C5)

Dependency size table recorded. security-crypto/Tink noted as a future
candidate (own spec). Dev-loop flags enabled; build verified green.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 15: Documentation — README performance section + final measurement pass

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-09-02-phase-8-performance-design.md`

**Interfaces:**
- Consumes: all prior tasks.
- Produces: a complete README performance section and a fully-filled spec measurement table.

- [ ] **Step 1: Final full benchmark run (user)**

Run: `./gradlew :app:generateBaselineProfile` (regenerate against the optimized code) then `./gradlew :benchmark:connectedBenchmarkAndroidTest`.
Commit the regenerated `app/src/main/baseline-prof.txt` if it changed.

- [ ] **Step 2: Complete the spec "After" table**

Every B/C row filled with before → after → kept?. Add a short "Summary" paragraph under the table: which changes were kept, which reverted, net startup / frame / size effect.

- [ ] **Step 3: Write the README section**

Add to `README.md` a `## Performance` section covering:
- The `:benchmark` and `:baselineprofile` modules and what each measures.
- Commands: `./gradlew :app:generateBaselineProfile`, `./gradlew :benchmark:connectedBenchmarkAndroidTest`, and the `benchmark` build type.
- The device requirement (physical device / API 33+, English locale for the profile generator's text selectors).
- The measured baseline numbers (startup TTID with/without profile, counter frame timings, AAB size) as a table.
- A one-line statement of the §66 measure-before/after discipline the phase followed.
- Where the full record lives (`docs/superpowers/specs/2026-09-02-phase-8-performance-design.md`).

Also add `## Architecture` / `## Project structure` / `## Testing` / `## Release build` stubs if the README doesn't already have them (it is currently 55 lines — check and fill the plan.md §64 list: architecture, structure, setup, build commands, testing, release build, performance benchmarking, Baseline Profiles, database structure, backup format, AI configuration, widget implementation).

- [ ] **Step 4: Commit**

```bash
git add README.md docs/superpowers/specs/2026-09-02-phase-8-performance-design.md \
  app/src/main/baseline-prof.txt
git commit -m "docs: Phase 8 performance results + README performance section

Final benchmark table committed. README documents the benchmark
modules, commands, device requirements, and measured numbers
(plan.md 64).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 16: Finish the branch

**Files:** none (integration).

- [ ] **Step 1: Full verification**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`
Expected: all green. Record the lint result; fix any new warning this phase introduced.

- [ ] **Step 2: Confirm all spec deliverables exist**

Check against the spec "Deliverables" list: both modules present, `baseline-prof.txt` committed, `benchmark` build type present, both measurement tables filled, README section written.

- [ ] **Step 3: Invoke `superpowers:finishing-a-development-branch`**

Follow it to decide merge / PR for the `phase-8-performance` branch.

---

## Self-Review

**Spec coverage:**

- Spec Workstream A1 (baselineprofile module) → Task 1 (scaffold) + Task 3 (generator) + Task 5 (generate).
- Spec A2 (benchmark module) → Task 1 (scaffold) + Task 4 (tests) + Task 5 (run).
- Spec A3 (:app wiring) → Task 1.
- Spec A4 (baseline run) → Task 5.
- Spec B1 (routine step caching) → Task 6.
- Spec B2 (timer flow split) → Task 7.
- Spec B3 (state stability) → Task 8.
- Spec B4 (lazy repositories) → Task 9.
- Spec B5 (icons swap) → Task 10, size measured in Task 12.
- Spec B6 (index audit) → Task 11.
- Spec B7 (seed dispatcher) → Task 11.
- Spec C1 (release size) → Task 12.
- Spec C2 (R8 audit + fullMode) → Task 13.
- Spec C3 (dependency audit) → Task 14.
- Spec C4 (profile embedded) → Task 12 Step 3.
- Spec C5 (dev-loop config) → Task 14.
- Spec "Deliverables" (README, tables) → Task 15.
- Spec "Testing" (unit tests pass, smoke passes, per-item measurement rows) → Tasks 6–14 each have a measurement + keep/revert step; Task 16 final verification.
- Task 2 (testTags) is plan-added infrastructure the spec implies in A1/A2 ("testTags added to production code kept minimal").

**Placeholder scan:** Measurement values are `<before>`/`<after>` fill-ins by design (they don't exist until the device run) — every other step has concrete code or an exact command. No "TBD/TODO/handle edge cases" in actionable steps.

**Type consistency:** `elapsedSeconds: StateFlow<Int>` (Task 7) consumed by Task 8's stability check and referenced in Task 9's baseline. `cachedRoutineStepDisplays: List<RoutineStepDisplay>` (Task 6) — `RoutineStepDisplay` is the existing type in `CounterUiState.kt`, `@Immutable`-annotated in Task 8. testTag constants (`HOME_SCREEN_TEST_TAG` etc., Task 2) used verbatim as string literals in Tasks 3–4 selectors (`By.res(PACKAGE, "home_screen")`). `PACKAGE`/`TIMEOUT` defined in `BenchmarkHelpers.kt` (Task 4 Step 1), also redefined locally in `BaselineProfileGenerator.kt` (Task 3) since the two modules don't share source — consistent values, intentional.
