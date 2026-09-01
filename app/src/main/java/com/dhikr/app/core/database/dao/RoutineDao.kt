package com.dhikr.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.dhikr.app.core.database.entity.RoutineEntity
import com.dhikr.app.core.database.entity.RoutineStepEntity
import kotlinx.coroutines.flow.Flow

data class RoutineWithSteps(
    @Embedded val routine: RoutineEntity,
    @Relation(parentColumn = "id", entityColumn = "routineId")
    val steps: List<RoutineStepEntity>,
)

@Dao
interface RoutineDao {

    @Transaction
    @Query("SELECT * FROM routine ORDER BY isFavorite DESC, isPreset DESC, name ASC")
    fun observeAllWithSteps(): Flow<List<RoutineWithSteps>>

    @Transaction
    @Query("SELECT * FROM routine WHERE id = :id LIMIT 1")
    suspend fun getWithSteps(id: String): RoutineWithSteps?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRoutine(routine: RoutineEntity)

    /** Backup restore: an id already present is overwritten by the backup row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutine(routine: RoutineEntity)

    @Transaction
    @Query("SELECT * FROM routine WHERE isPreset = 0")
    suspend fun getAllCustomWithSteps(): List<RoutineWithSteps>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRoutines(routines: List<RoutineEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSteps(steps: List<RoutineStepEntity>)

    @Query("DELETE FROM routine_step WHERE routineId = :routineId")
    suspend fun deleteStepsForRoutine(routineId: String)

    @Transaction
    suspend fun replaceSteps(routineId: String, steps: List<RoutineStepEntity>) {
        deleteStepsForRoutine(routineId)
        insertSteps(steps)
    }

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Query("UPDATE routine SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Delete
    suspend fun deleteRoutine(routine: RoutineEntity)

    @Query("SELECT COUNT(*) FROM routine_step WHERE tasbihId = :tasbihId")
    suspend fun countStepsUsingTasbih(tasbihId: String): Int

    @Query(
        """
        SELECT DISTINCT r.name FROM routine r
        INNER JOIN routine_step s ON s.routineId = r.id
        WHERE s.tasbihId = :tasbihId
        """
    )
    suspend fun routineNamesUsingTasbih(tasbihId: String): List<String>

    @Query("SELECT COUNT(*) FROM routine")
    suspend fun count(): Int

    @Query(
        "UPDATE routine SET reminderEnabled = :enabled, reminderMinuteOfDay = :minuteOfDay, " +
            "reminderDays = :days, updatedAt = :now WHERE id = :id"
    )
    suspend fun setReminder(id: String, enabled: Boolean, minuteOfDay: Int, days: Int, now: Long)

    @Query("SELECT * FROM routine WHERE reminderEnabled = 1")
    suspend fun routinesWithRemindersRaw(): List<RoutineEntity>

    @Query("SELECT * FROM routine WHERE id = :id LIMIT 1")
    suspend fun getRoutineRaw(id: String): RoutineEntity?
}
