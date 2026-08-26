package com.dhikr.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dhikr.app.core.database.entity.SessionEntity

data class TasbihDailyTotal(val tasbihId: String, val dayStartMillis: Long, val total: Int)

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity)

    @Query("SELECT COALESCE(SUM(count), 0) FROM session WHERE startedAt >= :sinceMillis")
    suspend fun totalSince(sinceMillis: Long): Int

    // tasbihId-filtered total, used by HistoryRepository.historyByTasbih() for each
    // Tasbih's lifetime total — totalSince() alone sums across all Tasbih, which
    // would be wrong here (see HistoryRepository for details).
    @Query("SELECT COALESCE(SUM(count), 0) FROM session WHERE tasbihId = :tasbihId")
    suspend fun totalForTasbih(tasbihId: String): Int

    @Query(
        """
        SELECT tasbihId, (startedAt / :dayMillis) * :dayMillis AS dayStartMillis, SUM(count) AS total
        FROM session
        WHERE startedAt >= :sinceMillis
        GROUP BY tasbihId, dayStartMillis
        """
    )
    suspend fun dailyTotalsSince(sinceMillis: Long, dayMillis: Long): List<TasbihDailyTotal>

    @Query(
        """
        SELECT (startedAt / :dayMillis) * :dayMillis AS dayStartMillis, SUM(count) AS total
        FROM session
        WHERE startedAt >= :sinceMillis
        GROUP BY dayStartMillis
        """
    )
    suspend fun allTasbihDailyTotalsSince(sinceMillis: Long, dayMillis: Long): List<DayTotal>

    @Query("SELECT DISTINCT tasbihId FROM session")
    suspend fun distinctTasbihIds(): List<String>
}

data class DayTotal(val dayStartMillis: Long, val total: Int)
