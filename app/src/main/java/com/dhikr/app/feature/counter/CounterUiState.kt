package com.dhikr.app.feature.counter

import com.dhikr.app.core.database.entity.TasbihEntity

data class RoutineStepDisplay(val tasbihName: String, val targetCount: Int)

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
    val routineSteps: List<RoutineStepDisplay> = emptyList(),
    val currentRoutineStepIndex: Int = -1,
    val isRoutineComplete: Boolean = false,
    val routineName: String? = null,
    // False only for the transient window before Room's seed data has loaded
    // a Tasbih (see CounterViewModel.sessionReady). CounterScreen gates the
    // tap area and control row on this so they don't present live-looking
    // affordances while there is nothing to count yet (finding #2).
    val sessionReady: Boolean = true,
) {
    val totalCount: Int get() = (lap - 1) * dhikr.lapTarget + count
    val progressFraction: Float get() = count.toFloat() / dhikr.lapTarget.toFloat()

    companion object {
        val Empty = CounterUiState(
            dhikr = TasbihEntity(
                id = "", name = "", arabic = "", pronunciation = "", translation = "",
                lapTarget = 1, lapCount = 1, isBuiltIn = true, createdAt = 0, updatedAt = 0,
            ),
            count = 0, lap = 1, totalLaps = 1, canUndo = false, running = false,
            locked = false, elapsedSeconds = 0, isComplete = false, justCompletedLap = false,
            sessionReady = false,
        )
    }
}
