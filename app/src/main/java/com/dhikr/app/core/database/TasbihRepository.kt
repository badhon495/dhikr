package com.dhikr.app.core.database

import com.dhikr.app.core.database.dao.TasbihDao
import com.dhikr.app.core.database.entity.TasbihEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

sealed interface DeleteResult {
    data object Success : DeleteResult
    data class BlockedByRoutines(val routineNames: List<String>) : DeleteResult
}

class TasbihRepository(private val tasbihDao: TasbihDao) {

    fun observeAll(): Flow<List<TasbihEntity>> = tasbihDao.observeAll()

    fun observeFavorites(): Flow<List<TasbihEntity>> = tasbihDao.observeFavorites()

    fun search(query: String): Flow<List<TasbihEntity>> = tasbihDao.search(query)

    suspend fun getById(id: String): TasbihEntity? = tasbihDao.getById(id)

    suspend fun insert(tasbih: TasbihEntity) = tasbihDao.insert(tasbih)

    suspend fun update(tasbih: TasbihEntity) = tasbihDao.update(tasbih)

    suspend fun toggleFavorite(id: String, currentlyFavorite: Boolean) {
        tasbihDao.setFavorite(id, !currentlyFavorite)
    }

    suspend fun delete(tasbih: TasbihEntity): DeleteResult {
        // Task 9 replaces this body with a real routine-reference check once
        // RoutineDao exists. For now, plain delete.
        tasbihDao.delete(tasbih)
        return DeleteResult.Success
    }

    fun newId(): String = UUID.randomUUID().toString()
}
