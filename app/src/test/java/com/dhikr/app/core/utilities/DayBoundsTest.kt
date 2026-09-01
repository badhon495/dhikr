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
