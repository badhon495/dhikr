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

/**
 * Scroll frame timing (plan.md §47). Flings the Tasbih list and Insights screen up and down,
 * capturing [FrameTimingMetric] to detect scroll jank.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private val mode = CompilationMode.Partial(BaselineProfileMode.Require)

    @Test
    fun scrollTasbihList() = fling("Tasbih", "tasbih_list")

    @Test
    fun scrollInsights() = fling("Insights", "insights_screen")

    private fun fling(tabLabel: String, tag: String) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = 8,
        setupBlock = {
            pressHome()
            startActivityAndWait()
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
