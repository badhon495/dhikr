package com.dhikr.app.core.counter

class TasbihCounter(lapTarget: Int, private val totalLaps: Int) {

    private val lapTarget: Int = if (lapTarget <= 0) 1 else lapTarget

    private var count = 0
    private var lap = 1
    private var previous: Pair<Int, Int>? = null // (count, lap) before the last increment/undo-eligible change
    private var running = true
    private var complete = false

    fun increment(): CounterSnapshot {
        previous = count to lap
        var justCompletedLap = false
        // True only on the tap that first reaches the final lap's target —
        // goal-reached signals (session log, routine advance) fire once here,
        // not on every subsequent tap.
        var justCompleted = false

        if (count + 1 < lapTarget) {
            count += 1
        } else {
            // The tap that reaches lapTarget rolls straight over to the next
            // lap on this same tap — count shows 0, lap advances — so every
            // lap costs exactly lapTarget taps, not lapTarget + 1. (Previously
            // the final lap displayed lapTarget and needed one more tap to
            // roll, making that lap 34 taps instead of 33.)
            count = 0
            justCompletedLap = true
            if (lap >= totalLaps && !complete) {
                // First time the final lap's target is reached — fire the
                // goal-reached signal (session log, routine advance) once.
                complete = true
                justCompleted = true
            }
            // Keep advancing lap even past totalLaps so bonus rounds after the
            // goal keep lapping with lapTarget as modulus.
            lap += 1
        }

        return snapshot().copy(justCompletedLap = justCompletedLap, isComplete = justCompleted)
    }

    fun undo(): CounterSnapshot {
        val prior = previous ?: return snapshot()
        count = prior.first
        lap = prior.second
        previous = null
        complete = false
        return snapshot()
    }

    fun reset(): CounterSnapshot {
        count = 0
        lap = 1
        previous = null
        complete = false
        return snapshot()
    }

    /**
     * Restores internal state from a previously persisted snapshot (cold-start
     * session recovery). Not part of the normal increment/undo/reset state
     * machine — called once by CounterViewModel after reading SessionRepository.
     */
    fun restore(count: Int, lap: Int, previous: Pair<Int, Int>?) {
        this.count = count
        this.lap = lap
        this.previous = previous
        // count never sits at lapTarget any more (the target tap rolls to 0 and
        // bumps lap), so a completed final lap is simply lap past totalLaps.
        // The `count >= lapTarget` arm keeps sessions persisted by the older
        // engine restoring as complete.
        this.complete = lap > totalLaps || (lap >= totalLaps && count >= lapTarget)
    }

    fun pause() {
        running = false
    }

    fun resume() {
        running = true
    }

    fun isRunning(): Boolean = running

    fun snapshot(): CounterSnapshot = CounterSnapshot(
        count = count,
        lap = lap,
        previousCount = previous?.first,
        previousLap = previous?.second,
        isComplete = complete,
        justCompletedLap = false,
    )

    fun progressFraction(): Float = count.toFloat() / lapTarget.toFloat()

    fun totalCount(): Int = (lap - 1) * lapTarget + count
}
