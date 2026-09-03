package com.dhikr.app.core.database

import com.dhikr.app.core.database.dao.RoutineDao
import com.dhikr.app.core.database.dao.SessionDao
import com.dhikr.app.core.database.dao.TasbihDao
import com.dhikr.app.core.database.dao.TasbihProgressDao
import com.dhikr.app.core.database.entity.TasbihEntity
import com.dhikr.app.core.database.entity.TasbihProgressEntity
import com.dhikr.app.core.utilities.DayBounds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID

sealed interface DeleteResult {
    data object Success : DeleteResult
    data class BlockedByRoutines(val routineNames: List<String>) : DeleteResult
}

class TasbihRepository(
    private val tasbihDao: TasbihDao,
    private val routineDao: RoutineDao,
    private val progressDao: TasbihProgressDao,
    private val sessionDao: SessionDao,
) {

    fun observeAll(): Flow<List<TasbihEntity>> = tasbihDao.observeAll()

    fun observeFavorites(): Flow<List<TasbihEntity>> = tasbihDao.observeFavorites()

    fun search(query: String): Flow<List<TasbihEntity>> = tasbihDao.search(query)

    suspend fun getById(id: String): TasbihEntity? = tasbihDao.getById(id)

    suspend fun getAll(): List<TasbihEntity> = tasbihDao.getAll()

    suspend fun insert(tasbih: TasbihEntity) = tasbihDao.insert(tasbih)

    suspend fun update(tasbih: TasbihEntity) = tasbihDao.update(tasbih)

    suspend fun setReminder(id: String, enabled: Boolean, minuteOfDay: Int, daysMask: Int) {
        tasbihDao.setReminder(id, enabled, minuteOfDay, daysMask, System.currentTimeMillis())
    }

    suspend fun tasbihWithReminders(): List<TasbihEntity> = tasbihDao.tasbihWithRemindersRaw()

    suspend fun toggleFavorite(id: String, currentlyFavorite: Boolean) {
        tasbihDao.setFavorite(id, !currentlyFavorite)
    }

    /** Persist a freshly generated benefits write-up for [id]. */
    suspend fun saveBenefits(id: String, text: String, generatedAt: Long) =
        tasbihDao.updateBenefits(id, text, generatedAt)

    suspend fun delete(tasbih: TasbihEntity): DeleteResult {
        val blockingRoutineNames = routineDao.routineNamesUsingTasbih(tasbih.id)
        if (blockingRoutineNames.isNotEmpty()) {
            return DeleteResult.BlockedByRoutines(blockingRoutineNames)
        }
        tasbihDao.delete(tasbih)
        return DeleteResult.Success
    }

    // ---- Per-day session progress (mirrors RoutineRepository's progress store) ----

    /**
     * Saves where [tasbihId] currently sits — engine [count]/[lap], with
     * [loggedInSession] taps already in History — for today. Overwrites the
     * Tasbih's previous row and drops any rows from earlier days in the same call.
     */
    suspend fun saveSessionProgress(tasbihId: String, count: Int, lap: Int, loggedInSession: Int) {
        val today = DayBounds.startOfTodayMillis()
        progressDao.deleteStale(today)
        progressDao.upsert(
            TasbihProgressEntity(
                tasbihId = tasbihId,
                dayStartMillis = today,
                count = count,
                lap = lap,
                loggedInSession = loggedInSession,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Today's saved position for [tasbihId], or null if none (or only a stale one). */
    suspend fun getSessionProgress(tasbihId: String): TasbihProgressEntity? =
        progressDao.getForTasbih(tasbihId, DayBounds.startOfTodayMillis())

    /**
     * tasbihId -> fraction 0f..1f of today's total count for that Tasbih toward
     * its goal (lapTarget × lapCount). Sourced from logged History, so counting
     * a Tasbih inside a routine feeds this too (each routine step logs a
     * session under its own tasbihId). "Today" is resolved once when the flow
     * is created; a collector alive across local midnight goes stale until
     * recreated (any navigation away and back does it).
     */
    fun observeSessionProgressToday(): Flow<Map<String, Float>> = combine(
        tasbihDao.observeAll(),
        sessionDao.totalsByTasbihSince(DayBounds.startOfTodayMillis()),
    ) { tasbihs, totals ->
        val totalById = totals.associate { it.tasbihId to it.total }
        tasbihs.mapNotNull { tasbih ->
            val goal = tasbih.lapTarget * tasbih.lapCount
            if (goal <= 0) return@mapNotNull null
            val done = totalById[tasbih.id] ?: return@mapNotNull null
            if (done <= 0) return@mapNotNull null
            tasbih.id to (done.toFloat() / goal).coerceIn(0f, 1f)
        }.toMap()
    }

    fun newId(): String = UUID.randomUUID().toString()
}
