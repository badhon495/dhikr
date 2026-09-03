package com.dhikr.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "com.badhon495.dhikr"
private const val TIMEOUT = 5_000L

/**
 * Baseline Profile generator. Plays the critical user journeys from plan.md §46 on-device so
 * AGP can capture a baseline profile (and, with [includeInStartupProfile], a startup profile).
 *
 * Not run in this task — only compiled. It executes on a connected device in Task 5 via
 * `./gradlew :app:generateBaselineProfile`.
 *
 * Assumes an English device locale: nav-tab labels (`Home`, `Tasbih`, `Insights`) resolve from
 * `res/values/strings.xml`; on a Bengali device these strings differ and the text selectors
 * would not match.
 */
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

        // Open the first Tasbih into the Counter. The list is a LazyColumn whose rows may be
        // wrapped, so `children` can be empty — fall back to the first built-in seed name.
        val list = device.wait(
            Until.findObject(By.res(PACKAGE, "tasbih_list")), TIMEOUT,
        )
        val firstRow = list?.children?.firstOrNull()
        if (firstRow != null) {
            firstRow.click()
        } else {
            device.findObject(By.text("SubhanAllah"))?.click()
        }
        val tapArea = device.wait(
            Until.findObject(By.res(PACKAGE, "counter_tap_area")), TIMEOUT,
        )

        // Count 50 taps.
        repeat(50) {
            tapArea?.click()
            device.waitForIdle()
        }

        // Undo once.
        device.findObject(By.textContains("Undo"))?.click()
        device.waitForIdle()

        // Insights tab, then the monthly-history link ("See all months").
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
