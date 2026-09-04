package com.dhikr.app.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DhikrSpeedTest {

    @Test
    fun zero_timed_millis_returns_null() {
        assertNull(dhikrSpeedPerMin(timedCount = 100, timedMillis = 0))
    }

    @Test
    fun negative_or_zero_count_with_no_duration_returns_null() {
        assertNull(dhikrSpeedPerMin(timedCount = 0, timedMillis = 0))
    }

    @Test
    fun computes_counts_per_minute() {
        // 60 counts over 120_000 ms (2 min) -> 30 per min
        assertEquals(30.0, dhikrSpeedPerMin(timedCount = 60, timedMillis = 120_000)!!, 0.0001)
    }

    @Test
    fun sub_minute_session_scales_up() {
        // 33 counts over 30_000 ms (0.5 min) -> 66 per min
        assertEquals(66.0, dhikrSpeedPerMin(timedCount = 33, timedMillis = 30_000)!!, 0.0001)
    }
}
