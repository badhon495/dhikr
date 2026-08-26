package com.dhikr.app.feature.counter

import com.dhikr.app.core.database.entity.TasbihEntity

data class CounterUiState(
    val dhikr: TasbihEntity,
    val count: Int,
    val lap: Int,
    val totalLaps: Int,
    val canUndo: Boolean,
    val running: Boolean,
    val locked: Boolean,
    val elapsedSeconds: Int,
    val isComplete: Boolean,
    val justCompletedLap: Boolean,
) {
    val totalCount: Int get() = (lap - 1) * dhikr.lapTarget + count
    val progressFraction: Float get() = count.toFloat() / dhikr.lapTarget.toFloat()

    companion object {
        val Empty = CounterUiState(
            dhikr = TasbihEntity(
                id = "", name = "", arabic = "", transliteration = "", translation = "",
                lapTarget = 1, lapCount = 1, isBuiltIn = true, createdAt = 0, updatedAt = 0,
            ),
            count = 0, lap = 1, totalLaps = 1, canUndo = false, running = false,
            locked = false, elapsedSeconds = 0, isComplete = false, justCompletedLap = false,
        )
    }
}
