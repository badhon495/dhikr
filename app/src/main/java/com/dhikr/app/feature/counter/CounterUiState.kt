package com.dhikr.app.feature.counter

import androidx.compose.runtime.Immutable
import com.dhikr.app.core.database.entity.TasbihEntity

@Immutable
data class RoutineStepDisplay(val tasbihName: String, val targetCount: Int)

// Elapsed session time is deliberately NOT a field here — the 1s timer tick
// would otherwise rebuild this whole object every second and recompose the
// entire counter screen while the user is idle. It lives on
// CounterViewModel.elapsedSeconds (its own StateFlow) instead, collected
// separately by the two nodes that display it.
@Immutable
data class CounterUiState(
    val dhikr: TasbihEntity,
    val count: Int,
    val lap: Int,
    val totalLaps: Int,
    val canUndo: Boolean,
    val running: Boolean,
    val locked: Boolean,
    val isComplete: Boolean,
    val justCompletedLap: Boolean,
    // Wall-clock time the current session window started (see
    // CounterViewModel.sessionStartedAtMillis). Only for the session-summary
    // dialog's "started at HH:MM" line — elapsedSeconds remains the source of
    // truth for duration/persistence.
    val sessionStartedAtMillis: Long = 0L,
    val routineSteps: List<RoutineStepDisplay> = emptyList(),
    val currentRoutineStepIndex: Int = -1,
    val isRoutineComplete: Boolean = false,
    val routineName: String? = null,
    // False only for the transient window before Room's seed data has loaded
    // a Tasbih (see CounterViewModel.sessionReady). CounterScreen gates the
    // tap area and control row on this so they don't present live-looking
    // affordances while there is nothing to count yet (finding #2).
    val sessionReady: Boolean = true,
    // Control-row prev/next: true only outside a routine (a routine already
    // advances its own steps in sequence) and when a neighbor exists in
    // Tasbih-Library order — see CounterViewModel's tasbihOrder cache.
    val canGoToPrevious: Boolean = false,
    val canGoToNext: Boolean = false,
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
            locked = false, isComplete = false, justCompletedLap = false,
            sessionStartedAtMillis = 0L, sessionReady = false,
        )
    }
}
