package com.dhikr.app.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/**
 * Shared constants and UI-driving helpers for the Macrobenchmarks (plan.md §47).
 *
 * Not run in this task — only compiled. These execute on a connected device in Task 5 via
 * `./gradlew :benchmark:connectedBenchmarkAndroidTest`.
 *
 * Assumes an English device locale: nav-tab labels (`Home`, `Tasbih`, `Count`, `Insights`,
 * `Settings`) resolve from `res/values/strings.xml`; on a Bengali device these strings differ
 * and the [By.text] selectors would not match.
 */
const val PACKAGE = "com.badhon495.dhikr"
const val TIMEOUT = 5_000L

/** Wait until the Home screen has composed (testTag `home_screen`, Task 2). */
fun MacrobenchmarkScope.waitForHome() {
    device.wait(Until.hasObject(By.res(PACKAGE, "home_screen")), TIMEOUT)
}

/**
 * Navigate Home -> Tasbih tab -> first visible Tasbih row -> Counter session.
 *
 * The Tasbih list is a LazyColumn whose rows may be wrapped, so `children` can be empty — fall
 * back to the first built-in seed name. Note the default list order is
 * `ORDER BY isFavorite DESC, isBuiltIn DESC, name ASC`, so the first VISIBLE row is
 * `Alhamdulillah`; `SubhanAllah` (no space) is only a last-resort selector.
 */
fun MacrobenchmarkScope.openCounterSession() {
    device.findObject(By.text("Tasbih"))?.click()
    device.wait(Until.hasObject(By.res(PACKAGE, "tasbih_list")), TIMEOUT)

    val list = device.wait(Until.findObject(By.res(PACKAGE, "tasbih_list")), TIMEOUT)
    val firstRow = list?.children?.firstOrNull()
    if (firstRow != null) {
        firstRow.click()
    } else {
        (device.findObject(By.text("Alhamdulillah"))
            ?: device.findObject(By.text("SubhanAllah")))?.click()
    }
    device.wait(Until.hasObject(By.res(PACKAGE, "counter_tap_area")), TIMEOUT)
}

/** Tap the Counter tap area [times] times, waiting for idle after each tap. */
fun MacrobenchmarkScope.tap(times: Int) {
    val area = device.findObject(By.res(PACKAGE, "counter_tap_area"))
    repeat(times) {
        area?.click()
        device.waitForIdle()
    }
}
