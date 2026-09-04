package com.dhikr.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dhikr.app.core.database.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

data class TasbihDailyTotal(val tasbihId: String, val dayStartMillis: Long, val total: Int)

data class TasbihTotal(val tasbihId: String, val total: Int)

/**
 * One row per Tasbih: lifetime total plus week/month totals, and the count +
 * elapsed millis of only those sessions that had a real duration
 * (`endedAt > startedAt`) so an average speed can be derived without
 * instantaneous/widget sessions dragging it toward infinity.
 */
data class TasbihStats(
    val tasbihId: String,
    val total: Int,
    val weekTotal: Int,
    val monthTotal: Int,
    val timedCount: Int,
    val timedMillis: Long,
)

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity)

    @Query("SELECT * FROM session")
    suspend fun getAll(): List<SessionEntity>

    // Flow-returning so Room's invalidation tracker re-emits on every insert
    // into `session` — HomeViewModel/InsightsViewModel collect this reactively
    // instead of reading it once in init, so counting from the Counter screen
    // is reflected immediately when the user navigates back (finding #6).
    @Query("SELECT COALESCE(SUM(count), 0) FROM session WHERE startedAt >= :sinceMillis")
    fun totalSince(sinceMillis: Long): Flow<Int>

    // One row per Tasbih: lifetime total, plus week/month windowed totals and
    // the timed-session count/millis used for average speed. The CASE-inside-SUM
    // form keeps this a single scan/GROUP BY instead of several queries.
    @Query(
        """
        SELECT tasbihId,
               COALESCE(SUM(count), 0) AS total,
               COALESCE(SUM(CASE WHEN startedAt >= :weekSince THEN count ELSE 0 END), 0) AS weekTotal,
               COALESCE(SUM(CASE WHEN startedAt >= :monthSince THEN count ELSE 0 END), 0) AS monthTotal,
               COALESCE(SUM(CASE WHEN endedAt > startedAt THEN count ELSE 0 END), 0) AS timedCount,
               COALESCE(SUM(CASE WHEN endedAt > startedAt THEN endedAt - startedAt ELSE 0 END), 0) AS timedMillis
        FROM session
        GROUP BY tasbihId
        """
    )
    suspend fun statsByTasbih(weekSince: Long, monthSince: Long): List<TasbihStats>

    // Per-Tasbih totals since a cutoff, Flow-backed so the Tasbih card's
    // progress fill re-emits whenever a session is logged — including the
    // per-step sessions a routine logs (each carries its step's tasbihId).
    @Query(
        """
        SELECT tasbihId, COALESCE(SUM(count), 0) AS total FROM session
        WHERE startedAt >= :sinceMillis GROUP BY tasbihId
        """
    )
    fun totalsByTasbihSince(sinceMillis: Long): Flow<List<TasbihTotal>>

    // Buckets are computed in LOCAL time, not UTC: `offsetMillis` is the
    // caller's current local UTC offset (see HistoryRepository, which
    // recomputes it per call so DST transitions stay correct). Shifting
    // startedAt by the offset before dividing by dayMillis makes each bucket
    // boundary land on local midnight instead of a UTC epoch-day boundary,
    // then subtracting the offset back off converts dayStartMillis back to a
    // real UTC instant (still exactly local midnight) so it can be compared
    // against/formatted like any other millis timestamp elsewhere in the app.
    @Query(
        """
        SELECT tasbihId, ((startedAt + :offsetMillis) / :dayMillis) * :dayMillis - :offsetMillis AS dayStartMillis, SUM(count) AS total
        FROM session
        WHERE startedAt >= :sinceMillis
        GROUP BY tasbihId, dayStartMillis
        """
    )
    suspend fun dailyTotalsSince(sinceMillis: Long, dayMillis: Long, offsetMillis: Long): List<TasbihDailyTotal>

    @Query(
        """
        SELECT ((startedAt + :offsetMillis) / :dayMillis) * :dayMillis - :offsetMillis AS dayStartMillis, SUM(count) AS total
        FROM session
        WHERE startedAt >= :sinceMillis
        GROUP BY dayStartMillis
        """
    )
    suspend fun allTasbihDailyTotalsSince(sinceMillis: Long, dayMillis: Long, offsetMillis: Long): List<DayTotal>

    @Query("SELECT DISTINCT tasbihId FROM session")
    suspend fun distinctTasbihIds(): List<String>

    // Every day-bucket across all of history (no `since` filter), local-time
    // bucketed the same way as dailyTotalsSince(). HistoryRepository folds
    // these into per-calendar-month summaries for the monthly history screen.
    @Query(
        """
        SELECT ((startedAt + :offsetMillis) / :dayMillis) * :dayMillis - :offsetMillis AS dayStartMillis, SUM(count) AS total
        FROM session
        GROUP BY dayStartMillis
        ORDER BY dayStartMillis
        """
    )
    suspend fun allDailyTotals(dayMillis: Long, offsetMillis: Long): List<DayTotal>
}

data class DayTotal(val dayStartMillis: Long, val total: Int)
