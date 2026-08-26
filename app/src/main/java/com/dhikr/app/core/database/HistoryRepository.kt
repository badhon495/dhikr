package com.dhikr.app.core.database

import com.dhikr.app.core.database.dao.SessionDao
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class TasbihHistoryGroup(val tasbihId: String, val tasbihName: String, val lifetimeTotal: Int, val dailyTotals: List<Pair<Long, Int>>)

class HistoryRepository(
    private val sessionDao: SessionDao,
    private val tasbihRepository: TasbihRepository,
) {
    private val dayMillis = TimeUnit.DAYS.toMillis(1)

    suspend fun logSession(tasbihId: String, routineId: String?, count: Int, startedAt: Long, endedAt: Long) {
        if (count <= 0) return // nothing to record — matches the spec's "resetting to 0 writes nothing"
        sessionDao.insert(
            com.dhikr.app.core.database.entity.SessionEntity(
                tasbihId = tasbihId,
                routineId = routineId,
                count = count,
                startedAt = startedAt,
                endedAt = endedAt,
            )
        )
    }

    suspend fun todayTotal(): Int = sessionDao.totalSince(startOfTodayMillis())

    suspend fun weekTotal(): Int = sessionDao.totalSince(startOfTodayMillis() - 6 * dayMillis)

    suspend fun monthTotal(): Int = sessionDao.totalSince(startOfMonthMillis())

    suspend fun allTimeTotal(): Int = sessionDao.totalSince(0L)

    suspend fun last7DaysTotals(): List<Pair<String, Int>> {
        val since = startOfTodayMillis() - 6 * dayMillis
        val totals = sessionDao.allTasbihDailyTotalsSince(since, dayMillis).associateBy { it.dayStartMillis }
        return (0..6).map { offset ->
            val dayStart = since + offset * dayMillis
            val label = SimpleDateFormatCache.weekdayFormat.format(java.util.Date(dayStart))
            label to (totals[dayStart]?.total ?: 0)
        }
    }

    suspend fun calendarIntensity(year: Int, month: Int): Map<Int, Int> {
        val calendar = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val monthStart = calendar.timeInMillis
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val totals = sessionDao.dailyTotalsSince(monthStart, dayMillis)
        val byDay = totals.groupBy { ((it.dayStartMillis - monthStart) / dayMillis).toInt() + 1 }
            .mapValues { (_, rows) -> rows.sumOf { it.total } }
        return (1..daysInMonth).associateWith { day -> byDay[day] ?: 0 }
    }

    suspend fun historyByTasbih(): List<TasbihHistoryGroup> {
        val since = startOfTodayMillis() - 6 * dayMillis
        val tasbihIds = sessionDao.distinctTasbihIds()
        return tasbihIds.mapNotNull { id ->
            val tasbih = tasbihRepository.getById(id) ?: return@mapNotNull null
            // Fixed: per-Tasbih lifetime total must be filtered by tasbihId — the
            // draft's sessionDao.totalSince(0L) would sum ALL Tasbih's sessions,
            // making every group show the same wrong grand-total.
            val lifetimeTotal = sessionDao.totalForTasbih(id)
            val daily = sessionDao.dailyTotalsSince(since, dayMillis)
                .filter { it.tasbihId == id }
                .map { it.dayStartMillis to it.total }
            TasbihHistoryGroup(tasbihId = id, tasbihName = tasbih.name, lifetimeTotal = lifetimeTotal, dailyTotals = daily)
        }
    }

    private fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfMonthMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private object SimpleDateFormatCache {
    val weekdayFormat = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
}
