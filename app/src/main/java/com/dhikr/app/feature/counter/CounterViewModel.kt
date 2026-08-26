package com.dhikr.app.feature.counter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.counter.TasbihCounter
import com.dhikr.app.core.datastore.SessionRepository
import com.dhikr.app.core.model.BuiltInDhikr
import com.dhikr.app.core.model.CounterSessionState
import com.dhikr.app.core.model.Dhikr
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.FlowPreview::class)
class CounterViewModel(
    private val sessionRepository: SessionRepository,
    startingDhikrId: String? = null,
) : ViewModel() {

    private var dhikr: Dhikr = BuiltInDhikr.byId(startingDhikrId ?: BuiltInDhikr.all.first().id)
    private var engine = TasbihCounter(dhikr.lapTarget, dhikr.lapCount)
    private var locked = false
    private var elapsedSeconds = 0

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<CounterUiState> = _uiState.asStateFlow()

    init {
        restoreSession()
        _uiState
            .drop(1) // skip the initial emission — nothing to persist yet
            .debounce(500)
            .onEach { persist() }
            .launchIn(viewModelScope)
        startTimer()
    }

    private fun restoreSession() {
        viewModelScope.launch {
            // Collect just the first emission to restore, then stop — this is a
            // one-shot read on cold start, not a continuous observer.
            sessionRepository.sessionFlow.first()?.let { session ->
                dhikr = BuiltInDhikr.byId(session.activeDhikrId)
                engine = TasbihCounter(dhikr.lapTarget, dhikr.lapCount)
                // Engine has no public state setter beyond increment/undo/reset by
                // design (keeps it a minimal state machine) — for restore we
                // reconstruct via the package-private restore hook instead of
                // replaying taps.
                engine.restore(
                    count = session.count,
                    lap = session.lap,
                    previous = if (session.previousCount != null && session.previousLap != null) {
                        session.previousCount to session.previousLap
                    } else null,
                )
                locked = session.locked
                elapsedSeconds = session.elapsedSeconds
                if (!session.running) engine.pause()
                _uiState.value = buildState()
            }
        }
    }

    private fun startTimer() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (engine.isRunning()) {
                    elapsedSeconds += 1
                    _uiState.value = buildState()
                }
            }
        }
    }

    fun onTap() {
        val snap = engine.increment()
        _uiState.value = buildState(justCompletedLap = snap.justCompletedLap)
    }

    fun onUndo() {
        engine.undo()
        _uiState.value = buildState()
    }

    fun onReset() {
        engine.reset()
        elapsedSeconds = 0
        _uiState.value = buildState()
    }

    fun onTogglePause() {
        if (engine.isRunning()) engine.pause() else engine.resume()
        _uiState.value = buildState()
    }

    fun onToggleLock() {
        locked = !locked
        _uiState.value = buildState()
    }

    private fun buildState(justCompletedLap: Boolean = false): CounterUiState {
        val snap = engine.snapshot()
        return CounterUiState(
            dhikr = dhikr,
            count = snap.count,
            lap = snap.lap,
            totalLaps = dhikr.lapCount,
            canUndo = snap.canUndo,
            running = engine.isRunning(),
            locked = locked,
            elapsedSeconds = elapsedSeconds,
            isComplete = snap.isComplete,
            justCompletedLap = justCompletedLap,
        )
    }

    /**
     * Immediately persist the current session, bypassing the 500ms save debounce.
     * Called from CounterScreen's ON_STOP lifecycle observer so a session is never
     * lost to process death inside the debounce window.
     */
    fun flushSession() {
        viewModelScope.launch { persist() }
    }

    private suspend fun persist() {
        // Read previousCount/previousLap from the engine's snapshot directly
        // (not from _uiState, which only exposes the derived canUndo boolean) so
        // undo state round-trips correctly across process death.
        val snap = engine.snapshot()
        val s = _uiState.value
        sessionRepository.save(
            CounterSessionState(
                activeDhikrId = s.dhikr.id,
                count = snap.count,
                lap = snap.lap,
                previousCount = snap.previousCount,
                previousLap = snap.previousLap,
                running = s.running,
                elapsedSeconds = s.elapsedSeconds,
                locked = s.locked,
                routineId = null,
                routineStep = 0,
            )
        )
    }

    override fun onCleared() {
        // Flush synchronously isn't possible from onCleared (no suspend context) —
        // rely on the ON_STOP-triggered flush instead; see the DisposableEffect
        // lifecycle observer in CounterScreen.kt, which calls flushSession().
        // viewModelScope is already cancelled here so no launch{} is started.
        super.onCleared()
    }

    class Factory(
        private val sessionRepository: SessionRepository,
        private val startingDhikrId: String? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CounterViewModel(sessionRepository, startingDhikrId) as T
        }
    }
}
