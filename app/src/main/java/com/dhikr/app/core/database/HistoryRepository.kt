package com.dhikr.app.core.database

import com.dhikr.app.core.database.dao.SessionDao
import com.dhikr.app.core.database.entity.SessionEntity
import com.dhikr.app.core.utilities.DayBounds
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
    fun todayTotalFlow(): Flow<Int> = sessionDao.totalSince(DayBounds.startOfTodayMillis())

    fun weekTotalFlow(): Flow<Int> = sessionDao.totalSince(DayBounds.startOfTodayMillis() - 6 * dayMillis)

    fun monthTotalFlow(): Flow<Int> = sessionDao.totalSince(DayBounds.startOfMonthMillis())

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
        val since = DayBounds.startOfTodayMillis() - 6 * dayMillis
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

    /**
     * Three flat queries instead of the old per-Tasbih loop (which re-ran the
     * whole-table `dailyTotalsSince` GROUP BY, plus `getById` and
     * `totalForTasbih`, once per Tasbih): one lifetime-totals GROUP BY, one
     * name lookup, one last-7-days GROUP BY, all folded in memory. Groups are
     * ordered by lifetime total, descending.
     */
    suspend fun historyByTasbih(): List<TasbihHistoryGroup> {
        val since = DayBounds.startOfTodayMillis() - 6 * dayMillis
        val lifetimeTotals = sessionDao.lifetimeTotalsByTasbih()
        if (lifetimeTotals.isEmpty()) return emptyList()
        val names = tasbihRepository.getAll().associate { it.id to it.name }
        val dailyByTasbih = sessionDao.dailyTotalsSince(since, dayMillis, localOffsetMillis())
            .groupBy { it.tasbihId }
        return lifetimeTotals
            .sortedByDescending { it.total }
            .mapNotNull { row ->
                val name = names[row.tasbihId] ?: return@mapNotNull null
                val daily = dailyByTasbih[row.tasbihId].orEmpty().map { it.dayStartMillis to it.total }
                TasbihHistoryGroup(row.tasbihId, name, row.total, daily)
            }
    }

    /**
     * Summary of the previous calendar month only. Reads just that month's
     * day-buckets instead of bucketing all of history via `monthlySummaries()`.
     */
    suspend fun previousMonthSummary(): MonthSummary? {
        val prev = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val prevStart = prev.timeInMillis
        val thisMonthStart = DayBounds.startOfMonthMillis()
        val buckets = sessionDao.allTasbihDailyTotalsSince(prevStart, dayMillis, localOffsetMillis())
            .filter { it.dayStartMillis < thisMonthStart }
        if (buckets.isEmpty()) return null
        return MonthSummary(
            year = prev.get(Calendar.YEAR),
            month = prev.get(Calendar.MONTH),
            monthStartMillis = prevStart,
            total = buckets.sumOf { it.total },
            consistentDays = buckets.count { it.total > 0 },
            daysInMonth = prev.getActualMaximum(Calendar.DAY_OF_MONTH),
        )
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
}

private object SimpleDateFormatCache {
    val weekdayFormat = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
}
