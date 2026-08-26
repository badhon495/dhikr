package com.dhikr.app.feature.counter

import com.dhikr.app.core.model.Dhikr

data class CounterUiState(
    val dhikr: Dhikr,
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
}
