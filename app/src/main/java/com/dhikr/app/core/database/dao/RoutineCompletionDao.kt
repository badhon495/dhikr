package com.dhikr.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dhikr.app.core.database.entity.RoutineCompletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineCompletionDao {

    /** A completion already recorded for this routine + day is left untouched. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markComplete(completion: RoutineCompletionEntity)

    @Query("SELECT routineId FROM routine_completion WHERE dayStartMillis = :dayStartMillis")
    fun observeCompletedOn(dayStartMillis: Long): Flow<List<String>>
}
