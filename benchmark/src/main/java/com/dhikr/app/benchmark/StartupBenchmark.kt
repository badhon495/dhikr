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

/**
 * Cold-startup timing (plan.md §47). Measures `timeToInitialDisplay` across three compilation
 * modes so Task 5 can compare no-compilation, baseline-profile, and full-AOT startup.
 *
 * `CompilationMode.Partial(BaselineProfileMode.Require)` requires a baseline profile at run time;
 * Task 5 generates it first via `./gradlew :app:generateBaselineProfile`.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() = startup(
        CompilationMode.Partial(BaselineProfileMode.Require),
    )

    @Test
    fun startupFullCompilation() = startup(CompilationMode.Full())

    // Warm startup (spec A2 requires both cold and warm).
    @Test
    fun startupWarmNoCompilation() = startup(CompilationMode.None(), StartupMode.WARM)

    @Test
    fun startupWarmBaselineProfile() = startup(
        CompilationMode.Partial(BaselineProfileMode.Require),
        StartupMode.WARM,
    )

    private fun startup(
        mode: CompilationMode,
        startupMode: StartupMode = StartupMode.COLD,
    ) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = startupMode,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        waitForHome()
    }
}
