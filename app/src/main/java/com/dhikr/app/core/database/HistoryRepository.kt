package com.dhikr.app.core.database

import com.dhikr.app.core.database.dao.SessionDao
import com.dhikr.app.core.database.entity.SessionEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class TasbihHistoryGroup(val tasbihId: String, val tasbihName: String, val lifetimeTotal: Int, val dailyTotals: List<Pair<Long, Int>>)

/**
 * Summary of one calendar month. `consistentDays` is the number of days that
 * had any activity (count > 0); it is not tied to the daily goal. `daysInMonth`
 * lets the UI render "X of Y days" without recomputing the month length.
 */
data class MonthSummary(
    val year: Int,
    val month: Int, // 0-based, matches Calendar.MONTH
    val monthStartMillis: Long,
    val total: Int,
    val consistentDays: Int,
    val daysInMonth: Int,
)

class HistoryRepository(
    private val sessionDao: SessionDao,
    private val tasbihRepository: TasbihRepository,
) {
    private val dayMillis = TimeUnit.DAYS.toMillis(1)

    suspend fun logSession(tasbihId: String, routineId: String?, count: Int, startedAt: Long, endedAt: Long) {
        if (count <= 0) return // nothing to record — matches the spec's "resetting to 0 writes nothing"
        sessionDao.insert(
            SessionEntity(
                tasbihId = tasbihId,
                routineId = routineId,
                count = count,
                startedAt = startedAt,
                endedAt = endedAt,
            )
        )
    }

    // Flow-returning (backed by Room's invalidation tracker on the `session`
    // table) so HomeViewModel/InsightsViewModel can collect reactively and
    // stay fresh after a count is logged, instead of reading once in init
    // (finding #6).
    fun todayTotalFlow(): Flow<Int> = sessionDao.totalSince(startOfTodayMillis())

    fun weekTotalFlow(): Flow<Int> = sessionDao.totalSince(startOfTodayMillis() - 6 * dayMillis)

    fun monthTotalFlow(): Flow<Int> = sessionDao.totalSince(startOfMonthMillis())

    fun allTimeTotalFlow(): Flow<Int> = sessionDao.totalSince(0L)

    /**
     * Local UTC offset (millis) at the current moment. Recomputed on every
     * call rather than cached, per finding #1 — caching it would freeze the
     * DST/timezone state as of whenever the singleton HistoryRepository was
     * constructed (effectively app-process start), which silently goes wrong
     * across a DST transition or a device timezone change that happens while
     * the process stays alive.
     */
    private fun localOffsetMillis(): Long = TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()

    suspend fun last7DaysTotals(): List<Pair<String, Int>> {
        val since = startOfTodayMillis() - 6 * dayMillis
        val totals = sessionDao.allTasbihDailyTotalsSince(since, dayMillis, localOffsetMillis()).associateBy { it.dayStartMillis }
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
        val totals = sessionDao.dailyTotalsSince(monthStart, dayMillis, localOffsetMillis())
        val byDay = totals.groupBy { ((it.dayStartMillis - monthStart) / dayMillis).toInt() + 1 }
            .mapValues { (_, rows) -> rows.sumOf { it.total } }
        return (1..daysInMonth).associateWith { day -> byDay[day] ?: 0 }
    }

    suspend fun historyByTasbih(): List<TasbihHistoryGroup> {
        val since = startOfTodayMillis() - 6 * dayMillis
        val tasbihIds = sessionDao.distinctTasbihIds()
        val offsetMillis = localOffsetMillis()
        return tasbihIds.mapNotNull { id ->
            val tasbih = tasbihRepository.getById(id) ?: return@mapNotNull null
            // Fixed: per-Tasbih lifetime total must be filtered by tasbihId — the
            // draft's sessionDao.totalSince(0L) would sum ALL Tasbih's sessions,
            // making every group show the same wrong grand-total.
            val lifetimeTotal = sessionDao.totalForTasbih(id)
            val daily = sessionDao.dailyTotalsSince(since, dayMillis, offsetMillis)
                .filter { it.tasbihId == id }
                .map { it.dayStartMillis to it.total }
            TasbihHistoryGroup(tasbihId = id, tasbihName = tasbih.name, lifetimeTotal = lifetimeTotal, dailyTotals = daily)
        }
    }

    /**
     * One summary per calendar month that had at least one session, newest
     * first. Months with no activity are omitted — there is no stored install
     * date, so the list simply starts at the first-ever session's month.
     */
    suspend fun monthlySummaries(): List<MonthSummary> {
        val dayBuckets = sessionDao.allDailyTotals(dayMillis, localOffsetMillis())
        if (dayBuckets.isEmpty()) return emptyList()
        val calendar = Calendar.getInstance()
        return dayBuckets
            .groupBy { bucket ->
                calendar.timeInMillis = bucket.dayStartMillis
                calendar.get(Calendar.YEAR) to calendar.get(Calendar.MONTH)
            }
            .map { (yearMonth, buckets) ->
                val (year, month) = yearMonth
                val monthCalendar = Calendar.getInstance().apply {
                    clear()
                    set(year, month, 1, 0, 0, 0)
                }
                MonthSummary(
                    year = year,
                    month = month,
                    monthStartMillis = monthCalendar.timeInMillis,
                    total = buckets.sumOf { it.total },
                    consistentDays = buckets.count { it.total > 0 },
                    daysInMonth = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH),
                )
            }
            .sortedByDescending { it.monthStartMillis }
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
