package com.dhikr.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dhikr.app.core.database.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

data class TasbihDailyTotal(val tasbihId: String, val dayStartMillis: Long, val total: Int)

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity)

    // Flow-returning so Room's invalidation tracker re-emits on every insert
    // into `session` — HomeViewModel/InsightsViewModel collect this reactively
    // instead of reading it once in init, so counting from the Counter screen
    // is reflected immediately when the user navigates back (finding #6).
    @Query("SELECT COALESCE(SUM(count), 0) FROM session WHERE startedAt >= :sinceMillis")
    fun totalSince(sinceMillis: Long): Flow<Int>

    // tasbihId-filtered total, used by HistoryRepository.historyByTasbih() for each
    // Tasbih's lifetime total — totalSince() alone sums across all Tasbih, which
    // would be wrong here (see HistoryRepository for details).
    @Query("SELECT COALESCE(SUM(count), 0) FROM session WHERE tasbihId = :tasbihId")
    suspend fun totalForTasbih(tasbihId: String): Int

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
