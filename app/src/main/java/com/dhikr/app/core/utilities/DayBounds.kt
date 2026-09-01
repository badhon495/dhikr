package com.dhikr.app.core.utilities

import java.util.Calendar

/**
 * Local-time day/month boundaries. Extracted from the private helpers that
 * HistoryRepository and TasbihRepository each carried, now that widget code
 * needs the same "start of today" cutoff. Recomputed per call (never cached) so
 * a DST transition or device-timezone change mid-process stays correct — same
 * reasoning as HistoryRepository.localOffsetMillis().
 */
object DayBounds {

    fun startOfTodayMillis(now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun startOfMonthMillis(now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
