package com.dhikr.app.feature.counter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.counter.TasbihCounter
import com.dhikr.app.core.database.HistoryRepository
import com.dhikr.app.core.database.RoutineRepository
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.database.dao.RoutineWithSteps
import com.dhikr.app.core.database.entity.RoutineStepEntity
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

    // Wall-clock start of the current in-progress session, used as the
    // `startedAt` field of the History row logged when this session ends
    // (goal reached, routine step advanced, or the screen leaves composition
    // with count > 0). Reset after each logged session so a later lap/step
    // within the same screen visit starts its own fresh window.
    private var sessionStartedAtMillis: Long = System.currentTimeMillis()

    // Cumulative count (across laps) already written to permanent History for
    // the session currently held by `engine`. Each history log records only
    // `engine.totalCount() - loggedTotal`, so leaving the screen and coming
    // back without any new taps records nothing the second time — the cause of
    // the "achievement keeps going up on its own" bug. Persisted in
    // CounterSessionState so it survives process death mid-session, and reset
    // to 0 whenever `engine` is replaced (routine step advance) or reset.
    private var loggedTotal = 0

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

    // B1: routine steps sorted by stepOrder, and the derived display list, are
    // invariant for the life of a loaded routine. Computed once in
    // initializeSession() instead of re-sorting/re-mapping on every tap in
    // buildState().
    private var sortedRoutineSteps: List<RoutineStepEntity> = emptyList()
    private var cachedRoutineStepDisplays: List<RoutineStepDisplay> = emptyList()

    private val _uiState = MutableStateFlow(CounterUiState.Empty)
    val uiState: StateFlow<CounterUiState> = _uiState.asStateFlow()

    // B2: elapsed session time as its own flow. The 1s timer tick updates only
    // this, so an idle running session no longer rebuilds CounterUiState (and
    // recomposes the whole counter screen) every second. persist() reads
    // `_elapsedSeconds.value` directly.
    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

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
        // The saved routine only resumes on a true bare "continue" (neither a
        // specific Tasbih nor a specific routine requested). If the caller
        // asked for a specific Tasbih (requestedStartingId set) but no
        // routine, that is an explicit pick that must win over — and exit —
        // whatever routine was left unfinished, same as it already wins over
        // a plain saved single-Tasbih session below (finding #3). Without
        // this, picking any other Tasbih while a routine is in progress
        // silently reopened the unfinished routine instead.
        val routineIdToLoad = requestedRoutineId
            ?: savedSession?.routineId.takeIf { requestedStartingId == null }
        if (routineIdToLoad != null) {
            val routine = routineRepository.getWithSteps(routineIdToLoad)
            if (routine != null && routine.steps.isNotEmpty()) {
                activeRoutine = routine
                val sortedSteps = routine.steps.sortedBy { it.stepOrder }
                routineStepNames = sortedSteps.map { step ->
                    tasbihRepository.getById(step.tasbihId)?.name ?: step.tasbihId
                }
                sortedRoutineSteps = sortedSteps
                cachedRoutineStepDisplays = sortedSteps.mapIndexed { i, step ->
                    RoutineStepDisplay(routineStepNames[i], step.targetCount)
                }
                // Per-routine saved position for today (survives opening other
                // routines, cleared at local midnight). Authoritative for
                // routines; the single DataStore session is only a fallback.
                val savedProgress = routineRepository.getProgress(routineIdToLoad)
                routineStepIndex = (savedProgress?.stepIndex ?: savedSession?.routineStep ?: 0)
                    .coerceIn(0, sortedSteps.lastIndex)
                val currentStep = sortedSteps[routineStepIndex]
                val stepTasbih = tasbihRepository.getById(currentStep.tasbihId)
                if (stepTasbih != null) {
                    dhikr = stepTasbih
                    engine = TasbihCounter(currentStep.targetCount, 1)
                    if (savedProgress != null) {
                        val restoredCount = savedProgress.countInStep
                            .coerceIn(0, (currentStep.targetCount - 1).coerceAtLeast(0))
                        engine.restore(count = restoredCount, lap = 1, previous = null)
                        // Adopt the already-logged watermark for this step so the
                        // taps History recorded when the screen was last left are
                        // not logged a second time on resume.
                        loggedTotal = savedProgress.loggedInStep.coerceIn(0, restoredCount)
                        _elapsedSeconds.value = savedSession?.elapsedSeconds?.takeIf {
                            savedSession.routineId == routineIdToLoad
                        } ?: 0
                    } else {
                        // The single DataStore session is one global slot. Only
                        // adopt its count/lap/elapsed here if it actually belongs
                        // to THIS routine — otherwise counting a dhikr in one
                        // routine bleeds into every other routine that has the
                        // same dhikr as its current step (they share activeDhikrId
                        // but not routineId).
                        applyRestoredCountIfMatching(savedSession, currentStep.tasbihId, routineIdToLoad)
                    }
                    sessionStartedAtMillis = if (savedSession != null &&
                        savedSession.routineId == routineIdToLoad &&
                        savedSession.activeDhikrId == currentStep.tasbihId
                    ) {
                        System.currentTimeMillis() - _elapsedSeconds.value * 1000L
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
        // Finding #3: an explicit navigation argument (tapping a specific
        // Tasbih from the library, a favorite on Home, etc.) must win over
        // whatever session was last saved — otherwise, since
        // SessionRepository.clear() is never called, every tap on a specific
        // Tasbih after the very first saved session would silently reopen
        // the old one instead of the one just tapped. The saved session is
        // only used as a fallback when nothing specific was requested (e.g.
        // the "Continue session" entry point, which navigates with no id).
        val idToLoad = requestedStartingId ?: savedSession?.activeDhikrId
        val loaded = idToLoad?.let { tasbihRepository.getById(it) }
            ?: tasbihRepository.observeAll().first().firstOrNull()
        if (loaded == null) return // nothing to load; _uiState stays at Empty
        dhikr = loaded
        engine = TasbihCounter(dhikr.lapTarget, dhikr.lapCount)
        // Per-Tasbih saved position for today (survives opening other Tasbih,
        // cleared at local midnight). Authoritative; the single DataStore
        // session is only a fallback for the pre-progress-row cold-start case.
        val savedTasbihProgress = tasbihRepository.getSessionProgress(loaded.id)
        if (savedTasbihProgress != null) {
            val restoredLap = savedTasbihProgress.lap.coerceIn(1, loaded.lapCount + 1)
            val restoredCount = savedTasbihProgress.count
                .coerceIn(0, (loaded.lapTarget - 1).coerceAtLeast(0))
            engine.restore(count = restoredCount, lap = restoredLap, previous = null)
            loggedTotal = savedTasbihProgress.loggedInSession.coerceAtLeast(0)
            if (savedSession != null && savedSession.activeDhikrId == loaded.id && savedSession.routineId == null) {
                _elapsedSeconds.value = savedSession.elapsedSeconds
                locked = savedSession.locked
                if (!savedSession.running) engine.pause()
            }
        } else {
            applyRestoredCountIfMatching(savedSession, loaded.id, expectedRoutineId = null)
        }
        sessionStartedAtMillis = if (savedSession != null && savedSession.activeDhikrId == loaded.id) {
            System.currentTimeMillis() - _elapsedSeconds.value * 1000L
        } else {
            System.currentTimeMillis()
        }
        sessionReady = true
        _uiState.value = buildState()
    }

    private fun applyRestoredCountIfMatching(
        savedSession: CounterSessionState?,
        loadedTasbihId: String,
        expectedRoutineId: String?,
    ) {
        // Match on routineId too, not just the dhikr id — the saved session is a
        // single global slot, so a session left behind by a different routine
        // (or a standalone Tasbih) that happens to use the same dhikr must not
        // be restored into this one.
        if (savedSession != null &&
            savedSession.activeDhikrId == loadedTasbihId &&
            savedSession.routineId == expectedRoutineId
        ) {
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
            _elapsedSeconds.value = savedSession.elapsedSeconds
            // Progress carried in on restore was already logged to History when
            // the previous screen visit ended (or is tracked as un-logged via
            // the persisted value) — either way, adopt it so we never re-log it.
            loggedTotal = savedSession.loggedTotal
            if (!savedSession.running) engine.pause()
        }
    }

    private fun startTimer() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (sessionReady && engine.isRunning()) {
                    // B2: only the elapsed flow — no buildState()/_uiState
                    // emission, so the tick doesn't recompose the whole screen.
                    _elapsedSeconds.value += 1
                }
            }
        }
    }

    fun onTap() {
        // Guard against a tap landing before initializeSession() has loaded a
        // Tasbih from Room (DhikrApplication seeds the database
        // asynchronously and fully decoupled from Compose navigation) — the
        // Empty UI state renders normally with no visual signal that the
        // ViewModel isn't ready yet, so without this guard a fast tap here
        // would dereference the lateinit `engine`/`dhikr` and crash with
        // UninitializedPropertyAccessException (finding #2).
        if (!sessionReady) return
        val snap = engine.increment()
        if (snap.isComplete && activeRoutine != null) {
            advanceRoutineStep()
            return
        }
        if (snap.isComplete) {
            // Goal reached for a plain (non-routine) Tasbih — an unambiguous
            // session-end signal per the spec, so log immediately rather than
            // waiting for the screen to leave composition. The UI update is
            // synchronous so the tap's visual feedback is never gated behind
            // the Room write; logging runs in a separate, detached coroutine.
            _uiState.value = buildState(justCompletedLap = snap.justCompletedLap)
            viewModelScope.launch {
                logCurrentSessionIfNonZero()
                sessionStartedAtMillis = System.currentTimeMillis()
            }
            return
        }
        _uiState.value = buildState(justCompletedLap = snap.justCompletedLap)
    }

    private fun advanceRoutineStep() {
        val routine = activeRoutine ?: return
        val sortedSteps = sortedRoutineSteps // B1: sorted once at load
        val nextIndex = routineStepIndex + 1
        if (nextIndex > sortedSteps.lastIndex) {
            // Last step just completed — signal routine completion, no
            // interruption to the current display (the completion overlay is
            // a Compose-level dialog in CounterScreen, not a state reset here).
            // The UI update is synchronous so it is never gated behind the
            // Room write; logging runs in a separate, detached coroutine.
            _uiState.value = buildState().copy(isRoutineComplete = true)
            viewModelScope.launch {
                logCurrentSessionIfNonZero()
                routineRepository.markRoutineComplete(routine.routine.id)
                sessionStartedAtMillis = System.currentTimeMillis()
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
            loggedTotal = 0 // fresh engine starts at 0; nothing logged for it yet
            // elapsedSeconds is intentionally left as-is — the session timer
            // runs continuously across routine steps, it does not reset per step.
            _uiState.value = buildState()
        }
    }

    private suspend fun logCurrentSessionIfNonZero() {
        if (!::engine.isInitialized) return
        // totalCount() spans every completed lap, not just the current one, so
        // a multi-lap session records its full tally. Only the portion not yet
        // logged (`- loggedTotal`) is written, so repeated leave/enter cycles
        // without new taps add nothing.
        val total = engine.totalCount()
        val unlogged = total - loggedTotal
        if (unlogged > 0) {
            historyRepository.logSession(
                tasbihId = dhikr.id,
                routineId = activeRoutine?.routine?.id,
                count = unlogged,
                startedAt = sessionStartedAtMillis,
                endedAt = System.currentTimeMillis(),
            )
            loggedTotal = total
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
            // Persist the bumped `loggedTotal` immediately (not just via the
            // debounced saver, which won't fire again after the screen is
            // gone). Without this, re-entering a session that survived to
            // DataStore but whose ViewModel was destroyed would re-log the
            // whole restored count — the "achievement climbs on its own" bug.
            persist()
            sessionStartedAtMillis = System.currentTimeMillis()
        }
    }

    /** Dismisses the "Routine complete" dialog. The routine's steps are all
     * already done at this point, so there is nothing else to reset. */
    fun onRoutineCompleteAcknowledged() {
        _uiState.value = _uiState.value.copy(isRoutineComplete = false)
    }

    fun onUndo() {
        // See onTap() — same not-ready-yet guard (finding #2).
        if (!sessionReady) return
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
        // See onTap() — same not-ready-yet guard (finding #2).
        if (!sessionReady) return
        engine.reset()
        engine.resume()
        // Reset clears the count to 0, so anything logged so far this session
        // is now "ahead" of the engine — drop the watermark to match, otherwise
        // fresh taps after a reset would be swallowed until they pass the old total.
        loggedTotal = 0
        _elapsedSeconds.value = 0
        _uiState.value = buildState()
        // persist() (debounced off the state change above) rewrites the saved
        // resume position at count 0. Today's already-logged History count is
        // intentionally kept — those reps still happened today, so the card's
        // progress fill stays where it is.
    }

    fun onTogglePause() {
        // See onTap() — same not-ready-yet guard (finding #2).
        if (!sessionReady) return
        if (engine.isRunning()) engine.pause() else engine.resume()
        _uiState.value = buildState()
    }

    fun onToggleLock() {
        // See onTap() — same not-ready-yet guard (finding #2). Unlike the
        // other handlers this one doesn't touch engine/dhikr directly, but
        // buildState() below does, so the guard is still required.
        if (!sessionReady) return
        locked = !locked
        _uiState.value = buildState()
    }

    private fun buildState(justCompletedLap: Boolean = false): CounterUiState {
        val snap = engine.snapshot()
        val routine = activeRoutine
        val steps = if (routine != null) cachedRoutineStepDisplays else emptyList()
        return CounterUiState(
            dhikr = dhikr,
            count = snap.count,
            lap = snap.lap,
            totalLaps = dhikr.lapCount,
            canUndo = snap.canUndo,
            running = engine.isRunning(),
            locked = locked,
            isComplete = snap.isComplete,
            justCompletedLap = justCompletedLap,
            sessionStartedAtMillis = sessionStartedAtMillis,
            routineSteps = steps,
            currentRoutineStepIndex = routineStepIndex,
            routineName = routine?.routine?.name,
            // buildState() is only ever called after sessionReady has been
            // set true (initializeSession()'s success path, or the handlers
            // above, all of which now guard on it first) — see finding #2.
            sessionReady = true,
        )
    }

    /**
     * Immediately persist the current session, bypassing the 500ms save debounce.
     * Called from CounterScreen's ON_STOP lifecycle observer so a session is never
     * lost to process death inside the debounce window. [onDone] runs after the
     * persist coroutine completes — callers that need to read the just-written
     * session back (e.g. a widget refresh) must use it rather than assuming
     * flushSession() finished synchronously on return.
     */
    fun flushSession(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            persist()
            onDone()
        }
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
                elapsedSeconds = _elapsedSeconds.value, // B2: flow, not _uiState
                locked = s.locked,
                routineId = activeRoutine?.routine?.id,
                routineStep = routineStepIndex.coerceAtLeast(0),
                loggedTotal = loggedTotal,
            )
        )
        // Per-routine progress row — the part that survives opening a different
        // routine. Only while a routine is genuinely mid-flight (not once its
        // last step is done: markRoutineComplete() clears the row and the card
        // switches to the "completed today" tint).
        val routineId = activeRoutine?.routine?.id
        if (routineId != null && !s.isRoutineComplete) {
            routineRepository.saveProgress(
                routineId = routineId,
                stepIndex = routineStepIndex.coerceAtLeast(0),
                countInStep = snap.count,
                loggedInStep = loggedTotal,
            )
        } else if (routineId == null) {
            // Per-Tasbih progress row — the part that survives opening a
            // different Tasbih. Kept even once the goal is reached (fraction
            // 1f = full green card) until it ages out at local midnight.
            tasbihRepository.saveSessionProgress(
                tasbihId = s.dhikr.id,
                count = snap.count,
                lap = snap.lap,
                loggedInSession = loggedTotal,
            )
        }
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
