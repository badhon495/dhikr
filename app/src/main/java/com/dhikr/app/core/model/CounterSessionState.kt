package com.dhikr.app.core.model

data class CounterSessionState(
    val activeDhikrId: String,
    val count: Int,
    val lap: Int,
    val previousCount: Int?,
    val previousLap: Int?,
    val running: Boolean,
    val elapsedSeconds: Int,
    val locked: Boolean,
    val routineId: String?,
    val routineStep: Int,
    // Cumulative count (across laps) already written to permanent History for
    // this session. Every history log records only `totalCount - loggedTotal`
    // so a session that is left and re-entered without new taps records
    // nothing the second time — see CounterViewModel.logCurrentSessionIfNonZero().
    val loggedTotal: Int = 0,
) {
    companion object {
        fun fresh(dhikrId: String) = CounterSessionState(
            activeDhikrId = dhikrId,
            count = 0,
            lap = 1,
            previousCount = null,
            previousLap = null,
            running = true,
            elapsedSeconds = 0,
            locked = false,
            routineId = null,
            routineStep = 0,
            loggedTotal = 0,
        )
    }
}
