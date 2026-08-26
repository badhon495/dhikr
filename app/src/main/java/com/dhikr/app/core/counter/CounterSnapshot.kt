package com.dhikr.app.core.counter

data class CounterSnapshot(
    val count: Int,
    val lap: Int,
    val previousCount: Int?,
    val previousLap: Int?,
    val isComplete: Boolean,
    val justCompletedLap: Boolean,
) {
    val canUndo: Boolean get() = previousCount != null
}
