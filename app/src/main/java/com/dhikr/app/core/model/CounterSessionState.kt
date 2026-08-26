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
        )
    }
}
