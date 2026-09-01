package com.dhikr.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dhikr.app.core.database.entity.RoutineProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineProgressDao {

    /** Upsert — one row per routine, overwritten as the count advances. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: RoutineProgressEntity)

    @Query("SELECT * FROM routine_progress WHERE routineId = :routineId AND dayStartMillis = :dayStartMillis LIMIT 1")
    suspend fun getForRoutine(routineId: String, dayStartMillis: Long): RoutineProgressEntity?

    @Query("SELECT * FROM routine_progress WHERE dayStartMillis = :dayStartMillis")
    fun observeForDay(dayStartMillis: Long): Flow<List<RoutineProgressEntity>>

    @Query("DELETE FROM routine_progress WHERE routineId = :routineId")
    suspend fun deleteForRoutine(routineId: String)

    /** Drops rows from previous days so the table doesn't accumulate stale progress. */
    @Query("DELETE FROM routine_progress WHERE dayStartMillis <> :dayStartMillis")
    suspend fun deleteStale(dayStartMillis: Long)
}
