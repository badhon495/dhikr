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
    fun formatGroupedCountOfTarget_groupsBothNumbers() {
        assertEquals("1,500 / 5,000", WidgetRenders.formatGroupedCountOfTarget(1500, 5000))
        assertEquals("0 / 100", WidgetRenders.formatGroupedCountOfTarget(0, 100))
        assertEquals("12,345 / 1,000,000", WidgetRenders.formatGroupedCountOfTarget(12_345, 1_000_000))
    }

    @Test
    fun formatGrouped_insertsThousandsSeparators() {
        assertEquals("1,234", WidgetRenders.formatGrouped(1234))
        assertEquals("1,000,000", WidgetRenders.formatGrouped(1_000_000))
        assertEquals("0", WidgetRenders.formatGrouped(0))
    }
}
