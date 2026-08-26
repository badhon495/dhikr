package com.dhikr.app.feature.counter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.counter.TasbihCounter
import com.dhikr.app.core.database.HistoryRepository
import com.dhikr.app.core.database.RoutineRepository
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.database.dao.RoutineWithSteps
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
    private val routineRepository: RoutineRepository,
    startingDhikrId: String? = null,
    startingRoutineId: String? = null,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private lateinit var dhikr: TasbihEntity
    private lateinit var engine: TasbihCounter
    private var locked = false
    private var elapsedSeconds = 0

    // Wall-clock start of the current in-progress session, used as the
    // `startedAt` field of the History row logged when this session ends
    // (goal reached, routine step advanced, or the screen leaves composition
    // with count > 0). Reset after each logged session so a later lap/step
    // within the same screen visit starts its own fresh window.
    private var sessionStartedAtMillis: Long = System.currentTimeMillis()

    // True once initializeSession() has actually loaded a Tasbih and assigned
    // dhikr/engine. Every method that touches those lateinit properties from
    // outside the initializeSession() call chain (startTimer()'s recurring
    // loop, persist() reachable via ON_STOP-triggered flushSession()) must
    // check this first — Room can be empty at cold start (DhikrApplication
    // seeds it asynchronously, decoupled from Compose navigation), in which
    // case dhikr/engine are never assigned and _uiState stays at Empty.
    private var sessionReady = false

    // Routine state — empty/−1 means "not running a routine".
    private var activeRoutine: RoutineWithSteps? = null
    private var routineStepIndex = -1
    private var routineStepNames: List<String> = emptyList()

    private val _uiState = MutableStateFlow(CounterUiState.Empty)
    val uiState: StateFlow<CounterUiState> = _uiState.asStateFlow()

    private val requestedStartingId = startingDhikrId
    private val requestedRoutineId = startingRoutineId

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
        val routineIdToLoad = requestedRoutineId ?: savedSession?.routineId
        if (routineIdToLoad != null) {
            val routine = routineRepository.getWithSteps(routineIdToLoad)
            if (routine != null && routine.steps.isNotEmpty()) {
                activeRoutine = routine
                val sortedSteps = routine.steps.sortedBy { it.stepOrder }
                routineStepNames = sortedSteps.map { step ->
                    tasbihRepository.getById(step.tasbihId)?.name ?: step.tasbihId
                }
                routineStepIndex = (savedSession?.routineStep ?: 0).coerceIn(0, sortedSteps.lastIndex)
                val currentStep = sortedSteps[routineStepIndex]
                val stepTasbih = tasbihRepository.getById(currentStep.tasbihId)
                if (stepTasbih != null) {
                    dhikr = stepTasbih
                    engine = TasbihCounter(currentStep.targetCount, 1)
                    applyRestoredCountIfMatching(savedSession, currentStep.tasbihId)
                    sessionStartedAtMillis = if (savedSession != null && savedSession.activeDhikrId == currentStep.tasbihId) {
                        System.currentTimeMillis() - elapsedSeconds * 1000L
                    } else {
                        System.currentTimeMillis()
                    }
                    sessionReady = true
                    _uiState.value = buildState()
                    return
                }
            }
        }
        // Not a routine (or the routine/step Tasbih couldn't be resolved —
        // fall through to plain single-Tasbih behavior rather than crashing).
        // Never crash if the requested/saved Tasbih can't be found (deleted
        // custom Tasbih, corrupted DataStore referencing a stale id, etc.) —
        // fall back to whatever Tasbih Room actually has, per plan.md §57's
        // "never crash because of... corrupted data" requirement. An empty
        // Room table at this point would mean seeding (DhikrApplication)
        // hasn't completed yet or failed; falling back to CounterUiState.Empty
        // and leaving `dhikr`/`engine` uninitialized in that one pathological
        // case is acceptable since the screen has nothing to count without at
        // least one Tasbih existing.
        val idToLoad = savedSession?.activeDhikrId ?: requestedStartingId
        val loaded = idToLoad?.let { tasbihRepository.getById(it) }
            ?: tasbihRepository.observeAll().first().firstOrNull()
        if (loaded == null) return // nothing to load; _uiState stays at Empty
        dhikr = loaded
        engine = TasbihCounter(dhikr.lapTarget, dhikr.lapCount)
        applyRestoredCountIfMatching(savedSession, loaded.id)
        sessionStartedAtMillis = if (savedSession != null && savedSession.activeDhikrId == loaded.id) {
            System.currentTimeMillis() - elapsedSeconds * 1000L
        } else {
            System.currentTimeMillis()
        }
        sessionReady = true
        _uiState.value = buildState()
    }

    private fun applyRestoredCountIfMatching(savedSession: CounterSessionState?, loadedTasbihId: String) {
        if (savedSession != null && savedSession.activeDhikrId == loadedTasbihId) {
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
        if (snap.isComplete && activeRoutine != null) {
            advanceRoutineStep()
            return
        }
        if (snap.isComplete) {
            // Goal reached for a plain (non-routine) Tasbih — an unambiguous
            // session-end signal per the spec, so log immediately rather than
            // waiting for the screen to leave composition.
            viewModelScope.launch {
                logCurrentSessionIfNonZero()
                sessionStartedAtMillis = System.currentTimeMillis()
                _uiState.value = buildState(justCompletedLap = snap.justCompletedLap)
            }
            return
        }
        _uiState.value = buildState(justCompletedLap = snap.justCompletedLap)
    }

    private fun advanceRoutineStep() {
        val routine = activeRoutine ?: return
        val sortedSteps = routine.steps.sortedBy { it.stepOrder }
        val nextIndex = routineStepIndex + 1
        if (nextIndex > sortedSteps.lastIndex) {
            // Last step just completed — signal routine completion, no
            // interruption to the current display (the completion overlay is
            // a Compose-level dialog in CounterScreen, not a state reset here).
            // Logging is now suspend (logCurrentSessionIfNonZero), so this
            // branch — previously synchronous since it didn't need suspend —
            // is wrapped in viewModelScope.launch. buildState().copy(...) still
            // runs after the log call completes, preserving the existing
            // "state updates once, after routine-complete is decided" behavior.
            viewModelScope.launch {
                logCurrentSessionIfNonZero()
                sessionStartedAtMillis = System.currentTimeMillis()
                _uiState.value = buildState().copy(isRoutineComplete = true)
            }
            return
        }
        viewModelScope.launch {
            logCurrentSessionIfNonZero()
            sessionStartedAtMillis = System.currentTimeMillis()
            val nextStep = sortedSteps[nextIndex]
            val nextTasbih = tasbihRepository.getById(nextStep.tasbihId) ?: return@launch
            routineStepIndex = nextIndex
            dhikr = nextTasbih
            engine = TasbihCounter(nextStep.targetCount, 1)
            // elapsedSeconds is intentionally left as-is — the session timer
            // runs continuously across routine steps, it does not reset per step.
            _uiState.value = buildState()
        }
    }

    private suspend fun logCurrentSessionIfNonZero() {
        if (!::engine.isInitialized) return
        val snap = engine.snapshot()
        if (snap.count > 0) {
            historyRepository.logSession(
                tasbihId = dhikr.id,
                routineId = activeRoutine?.routine?.id,
                count = snap.count,
                startedAt = sessionStartedAtMillis,
                endedAt = System.currentTimeMillis(),
            )
        }
    }

    /**
     * Called by CounterScreen when the composable leaves composition (back
     * navigation, navigating elsewhere) — logs whatever count is in progress
     * (if any) as a completed session, then clears the in-memory session-start
     * marker so a later session on the same screen instance doesn't double-count.
     * Distinct from the two completion-triggered log calls above, which fire
     * mid-session on goal-reached, before any navigation happens.
     */
    fun logAndClearOnLeave() {
        viewModelScope.launch {
            logCurrentSessionIfNonZero()
            sessionStartedAtMillis = System.currentTimeMillis()
        }
    }

    /** Dismisses the "Routine complete" dialog. The routine's steps are all
     * already done at this point, so there is nothing else to reset. */
    fun onRoutineCompleteAcknowledged() {
        _uiState.value = _uiState.value.copy(isRoutineComplete = false)
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
        val routine = activeRoutine
        val steps = if (routine != null) {
            val sortedSteps = routine.steps.sortedBy { it.stepOrder }
            routineStepNames.mapIndexed { i, name -> RoutineStepDisplay(name, sortedSteps[i].targetCount) }
        } else {
            emptyList()
        }
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
            routineSteps = steps,
            currentRoutineStepIndex = routineStepIndex,
            routineName = routine?.routine?.name,
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
                routineId = activeRoutine?.routine?.id,
                routineStep = routineStepIndex.coerceAtLeast(0),
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
        private val routineRepository: RoutineRepository,
        private val startingDhikrId: String? = null,
        private val startingRoutineId: String? = null,
        private val historyRepository: HistoryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CounterViewModel(
                sessionRepository,
                tasbihRepository,
                routineRepository,
                startingDhikrId,
                startingRoutineId,
                historyRepository,
            ) as T
        }
    }
}
