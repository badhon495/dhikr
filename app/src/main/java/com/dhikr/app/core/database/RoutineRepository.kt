package com.dhikr.app.core.database

import com.dhikr.app.core.database.dao.RoutineCompletionDao
import com.dhikr.app.core.database.dao.RoutineDao
import com.dhikr.app.core.database.dao.RoutineWithSteps
import com.dhikr.app.core.database.entity.RoutineCompletionEntity
import com.dhikr.app.core.database.entity.RoutineEntity
import com.dhikr.app.core.database.entity.RoutineStepEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.UUID

class RoutineRepository(
    private val routineDao: RoutineDao,
    private val completionDao: RoutineCompletionDao,
) {

    fun observeAllWithSteps(): Flow<List<RoutineWithSteps>> = routineDao.observeAllWithSteps()

    suspend fun getWithSteps(id: String): RoutineWithSteps? = routineDao.getWithSteps(id)

    suspend fun createRoutine(name: String, steps: List<Pair<String, Int>>): String {
        val id = newId()
        val now = System.currentTimeMillis()
        routineDao.insertRoutine(RoutineEntity(id = id, name = name, isPreset = false, createdAt = now, updatedAt = now))
        routineDao.insertSteps(steps.mapIndexed { index, (tasbihId, targetCount) ->
            RoutineStepEntity(routineId = id, tasbihId = tasbihId, stepOrder = index, targetCount = targetCount)
        })
        return id
    }

    suspend fun updateSteps(routineId: String, steps: List<Pair<String, Int>>) {
        routineDao.replaceSteps(
            routineId,
            steps.mapIndexed { index, (tasbihId, targetCount) ->
                RoutineStepEntity(routineId = routineId, tasbihId = tasbihId, stepOrder = index, targetCount = targetCount)
            },
        )
    }

    suspend fun renameRoutine(routineId: String, name: String) {
        val current = routineDao.getWithSteps(routineId)?.routine ?: return
        routineDao.updateRoutine(current.copy(name = name, updatedAt = System.currentTimeMillis()))
    }

    /** Rename a routine and replace its whole step list in one shot (routine editor, edit mode). */
    suspend fun updateRoutine(routineId: String, name: String, steps: List<Pair<String, Int>>) {
        val current = routineDao.getWithSteps(routineId)?.routine ?: return
        routineDao.updateRoutine(current.copy(name = name, updatedAt = System.currentTimeMillis()))
        updateSteps(routineId, steps)
    }

    suspend fun deleteRoutine(routine: RoutineEntity) = routineDao.deleteRoutine(routine)

    /** Records that [routineId] was completed today (local time). Idempotent per day. */
    suspend fun markRoutineComplete(routineId: String) {
        completionDao.markComplete(RoutineCompletionEntity(routineId, startOfTodayMillis()))
    }

    /**
     * Ids of routines whose last step was completed today (local time).
     *
     * "Today" is resolved once, when this flow is created. If a screen keeps
     * collecting across local midnight the set goes stale until the collector
     * is recreated (any navigation away and back, or process death, does it) —
     * an accepted limitation, not worth a ticker for a tab nobody watches
     * overnight.
     */
    fun observeCompletedToday(): Flow<Set<String>> =
        completionDao.observeCompletedOn(startOfTodayMillis()).map { it.toSet() }

    fun newId(): String = UUID.randomUUID().toString()

    private fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
