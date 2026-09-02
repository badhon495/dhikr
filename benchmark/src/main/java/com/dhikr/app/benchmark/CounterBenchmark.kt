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

/**
 * Counter tap-loop frame timing (plan.md §47, spec B2). Drives rapid taps on the Counter tap
 * area and an idle-with-timer case, capturing [FrameTimingMetric] to check jank under load.
 */
@RunWith(AndroidJUnit4::class)
class CounterBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private val mode = CompilationMode.Partial(BaselineProfileMode.Require)

    @Test
    fun tap100() = run(taps = 100)

    @Test
    fun tap1000() = run(taps = 1000)

    @Test
    fun idleWithTimer() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            openCounterSession()
            tap(3)
        },
    ) {
        // Session running, timer ticking, no taps: frame production should be near zero
        // between 1s ticks (spec B2).
        Thread.sleep(10_000)
    }

    private fun run(taps: Int) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            openCounterSession()
        },
    ) {
        tap(taps)
    }
}
