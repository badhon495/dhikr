package com.dhikr.app.feature.counter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.counter.TasbihCounter
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.database.entity.TasbihEntity
import com.dhikr.app.core.datastore.SessionRepository
import com.dhikr.app.core.model.CounterSessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.FlowPreview::class)
class CounterViewModel(
    private val sessionRepository: SessionRepository,
    private val tasbihRepository: TasbihRepository,
    startingDhikrId: String? = null,
) : ViewModel() {

    private lateinit var dhikr: TasbihEntity
    private lateinit var engine: TasbihCounter
    private var locked = false
    private var elapsedSeconds = 0

    // True once initializeSession() has actually loaded a Tasbih and assigned
    // dhikr/engine. Every method that touches those lateinit properties from
    // outside the initializeSession() call chain (startTimer()'s recurring
    // loop, persist() reachable via ON_STOP-triggered flushSession()) must
    // check this first — Room can be empty at cold start (DhikrApplication
    // seeds it asynchronously, decoupled from Compose navigation), in which
    // case dhikr/engine are never assigned and _uiState stays at Empty.
    private var sessionReady = false

    private val _uiState = MutableStateFlow(CounterUiState.Empty)
    val uiState: StateFlow<CounterUiState> = _uiState.asStateFlow()

    private val requestedStartingId = startingDhikrId

    init {
        viewModelScope.launch {
            initializeSession()
            // debounced persistence + timer only start once the engine exists
            _uiState
                .drop(1) // skip the initial emission — nothing to persist yet
                .debounce(500)
                .onEach { persist() }
                .launchIn(viewModelScope)
            startTimer()
        }
    }

    private suspend fun initializeSession() {
        val savedSession = sessionRepository.sessionFlow.first()
        val idToLoad = savedSession?.activeDhikrId ?: requestedStartingId
        // Never crash if the requested/saved Tasbih can't be found (deleted
        // custom Tasbih, corrupted DataStore referencing a stale id, etc.) —
        // fall back to whatever Tasbih Room actually has, per plan.md §57's
        // "never crash because of... corrupted data" requirement. An empty
        // Room table at this point would mean seeding (DhikrApplication)
        // hasn't completed yet or failed; falling back to CounterUiState.Empty
        // and leaving `dhikr`/`engine` uninitialized in that one pathological
        // case is acceptable since the screen has nothing to count without at
        // least one Tasbih existing.
        val loaded = idToLoad?.let { tasbihRepository.getById(it) }
            ?: tasbihRepository.observeAll().first().firstOrNull()
        if (loaded == null) return // nothing to load; _uiState stays at Empty
        dhikr = loaded
        engine = TasbihCounter(dhikr.lapTarget, dhikr.lapCount)
        if (savedSession != null && savedSession.activeDhikrId == loaded.id) {
            // Engine has no public state setter beyond increment/undo/reset by
            // design (keeps it a minimal state machine) — for restore we
            // reconstruct via the package-private restore hook instead of
            // replaying taps.
            engine.restore(
                count = savedSession.count,
                lap = savedSession.lap,
                previous = if (savedSession.previousCount != null && savedSession.previousLap != null) {
                    savedSession.previousCount to savedSession.previousLap
                } else null,
            )
            locked = savedSession.locked
            elapsedSeconds = savedSession.elapsedSeconds
            if (!savedSession.running) engine.pause()
        }
        sessionReady = true
        _uiState.value = buildState()
    }

    private fun startTimer() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (sessionReady && engine.isRunning()) {
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
        // If the session was complete, increment() had paused the engine as part
        // of finishing — undoing past that point should un-stick it. An ordinary
        // undo of a normal tap while the user had manually paused must NOT force
        // a resume, so only auto-resume when completion is what caused the
        // paused state.
        val wasComplete = engine.snapshot().isComplete
        engine.undo()
        if (wasComplete && !engine.isRunning()) engine.resume()
        _uiState.value = buildState()
    }

    fun onReset() {
        engine.reset()
        engine.resume()
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
        // flushSession() can be triggered by ON_STOP (see CounterScreen's
        // DisposableEffect) before initializeSession() has finished its Room
        // read — e.g. the user backgrounds the app within the same sub-frame
        // window, or Room's tasbih table was still empty at cold start.
        // engine/dhikr are lateinit in that case, so guard rather than let
        // this crash: there is nothing meaningful to persist yet.
        if (!sessionReady) return
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
        private val tasbihRepository: TasbihRepository,
        private val startingDhikrId: String? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CounterViewModel(sessionRepository, tasbihRepository, startingDhikrId) as T
        }
    }
}
