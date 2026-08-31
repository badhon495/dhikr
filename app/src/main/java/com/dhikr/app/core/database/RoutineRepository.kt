package com.dhikr.app.core.database

import com.dhikr.app.core.database.dao.RoutineDao
import com.dhikr.app.core.database.dao.RoutineWithSteps
import com.dhikr.app.core.database.entity.RoutineEntity
import com.dhikr.app.core.database.entity.RoutineStepEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class RoutineRepository(private val routineDao: RoutineDao) {

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

    fun newId(): String = UUID.randomUUID().toString()
}
