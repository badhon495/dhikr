package com.dhikr.app.core.counter

/**
 * Optional, off-by-default advanced feature (plan.md §40): turns a wrist-flick
 * motion into a single tap event, so a user can count without touching the
 * screen. Pure peak-detection over accelerometer magnitude — no Android
 * dependency, so it is directly unit-testable without Robolectric/mocking a
 * SensorManager. The Android-facing side (registering a SensorEventListener
 * and feeding it samples) lives separately in AutoCounterSensorListener.
 *
 * Algorithm: a "flick" is a rising edge that crosses [threshold] from a
 * sample that was at-or-below it. Staying above threshold across consecutive
 * samples (the motion's peak spans more than one sensor reading) does not
 * refire — only the crossing does. A detected flick then starts a
 * [debounceMillis] cooldown during which further crossings are ignored, so
 * one physical flick cannot register as two counts.
 *
 * Deliberately minimal: no filtering, no gravity compensation beyond relying
 * on the threshold sitting above resting magnitude (~9.8 m/s² on a still
 * device), no per-axis analysis. Good enough for a clearly-labeled
 * "experimental" toggle; not a substitute for the manual tap, which remains
 * the primary and only fully reliable input (plan.md §40's own requirement).
 */
class AutoCounterDetector(
    private val threshold: Float,
    debounceMillis: Long,
) {
    private val debounceMillis = debounceMillis.coerceAtLeast(0L)
    private var wasAboveThreshold = false
    private var lastFireMillis: Long? = null

    /** Feed one accelerometer-magnitude sample. Returns true exactly when this
     *  sample should register as one tap. */
    fun onSample(magnitude: Float, timestampMillis: Long): Boolean {
        val isAbove = magnitude > threshold
        val risingEdge = isAbove && !wasAboveThreshold
        wasAboveThreshold = isAbove

        if (!risingEdge) return false

        val last = lastFireMillis
        if (last != null && timestampMillis - last < debounceMillis) return false

        lastFireMillis = timestampMillis
        return true
    }

    /** Clears debounce/edge state — call when auto-counter is toggled off then
     *  back on, or when the counter screen restarts, so a stale cooldown from
     *  a previous session can't suppress the first genuine flick. */
    fun reset() {
        wasAboveThreshold = false
        lastFireMillis = null
    }
}
