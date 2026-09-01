package com.dhikr.app.core.notifications

import java.util.Calendar
import java.util.TimeZone

/**
 * Computes the next epoch-millis a routine reminder should fire, given a
 * wall-clock minute of day and a 7-bit weekday mask (bit 0 = Sunday ..
 * bit 6 = Saturday; 0 = every day). Pure — no Android, no AlarmManager.
 *
 * Uses [Calendar] rather than java.time: the project's minSdk is 24 and
 * java.time is API 26+ without core-library desugaring (see also
 * HistoryRepository).
 */
object NextReminderTime {

    fun next(
        nowMillis: Long,
        minuteOfDay: Int,
        daysMask: Int,
        zone: TimeZone = TimeZone.getDefault(),
    ): Long {
        val minute = minuteOfDay.coerceIn(0, 24 * 60 - 1)
        val effectiveMask = if (daysMask and 0x7F == 0) 0x7F else daysMask and 0x7F

        // Start from the beginning of today in the given zone, then walk
        // forward a day at a time; the first matching, still-future slot wins.
        val cal = Calendar.getInstance(zone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        for (dayOffset in 0..7) {
            // Calendar.DAY_OF_WEEK: SUNDAY = 1 .. SATURDAY = 7 -> bit = value - 1.
            val bit = cal.get(Calendar.DAY_OF_WEEK) - 1
            if (effectiveMask and (1 shl bit) != 0) {
                val candidate = (cal.clone() as Calendar).apply {
                    add(Calendar.MINUTE, minute)
                }.timeInMillis
                if (candidate > nowMillis) return candidate
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        // Unreachable with a non-zero mask, but return a safe far-future value.
        return nowMillis + 7L * 24 * 60 * 60 * 1000
    }
}
