package com.dhikr.app.core.counter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCounterDetectorTest {

    private fun detector(
        threshold: Float = 12f,
        debounceMillis: Long = 400L,
    ) = AutoCounterDetector(threshold = threshold, debounceMillis = debounceMillis)

    @Test
    fun risingEdgeAboveThreshold_firesOnce() {
        val d = detector()
        assertFalse(d.onSample(magnitude = 9.8f, timestampMillis = 0L)) // resting, gravity only
        assertTrue(d.onSample(magnitude = 20f, timestampMillis = 50L)) // sharp flick crosses threshold
    }

    @Test
    fun stayingAboveThreshold_doesNotRefireEverySample() {
        val d = detector()
        assertTrue(d.onSample(magnitude = 20f, timestampMillis = 0L))
        // Still above threshold on the very next sample — same motion, not a
        // second flick — must not fire again.
        assertFalse(d.onSample(magnitude = 18f, timestampMillis = 10L))
    }

    @Test
    fun secondFlickWithinDebounceWindow_isSuppressed() {
        val d = detector(debounceMillis = 400L)
        assertTrue(d.onSample(magnitude = 20f, timestampMillis = 0L))
        assertFalse(d.onSample(magnitude = 9.8f, timestampMillis = 50L)) // drops back down
        // A second genuine peak arrives 200ms later — inside the 400ms debounce
        // window, so it must be swallowed to avoid double-counting one flick.
        assertFalse(d.onSample(magnitude = 20f, timestampMillis = 250L))
    }

    @Test
    fun secondFlickAfterDebounceWindow_fires() {
        val d = detector(debounceMillis = 400L)
        assertTrue(d.onSample(magnitude = 20f, timestampMillis = 0L))
        assertFalse(d.onSample(magnitude = 9.8f, timestampMillis = 50L))
        assertTrue(d.onSample(magnitude = 20f, timestampMillis = 450L))
    }

    @Test
    fun belowThreshold_neverFires() {
        val d = detector(threshold = 12f)
        assertFalse(d.onSample(magnitude = 9.8f, timestampMillis = 0L))
        assertFalse(d.onSample(magnitude = 11.9f, timestampMillis = 10L))
    }

    @Test
    fun reset_clearsDebounceState() {
        val d = detector()
        assertTrue(d.onSample(magnitude = 20f, timestampMillis = 0L))
        d.reset()
        // Without reset() this would still be inside the debounce window and
        // suppressed; after reset() the very next sample can fire immediately.
        assertTrue(d.onSample(magnitude = 20f, timestampMillis = 10L))
    }

    @Test
    fun negativeOrZeroDebounce_stillDebouncesUsingZero() {
        // Defensive: a caller passing a bad config must not crash or loop —
        // zero-or-less collapses to "no minimum gap", not undefined behavior.
        val d = detector(debounceMillis = 0L)
        assertTrue(d.onSample(magnitude = 20f, timestampMillis = 0L))
        assertFalse(d.onSample(magnitude = 20f, timestampMillis = 0L)) // still same instant, still above thresh continuously
        assertFalse(d.onSample(magnitude = 9.8f, timestampMillis = 5L))
        assertTrue(d.onSample(magnitude = 20f, timestampMillis = 5L))
    }

    @Test
    fun equalToThreshold_doesNotFire() {
        // Strictly-greater semantics: a sample exactly at threshold is treated
        // as not-a-flick, matching the "rising edge above" contract precisely.
        val d = detector(threshold = 12f)
        assertFalse(d.onSample(magnitude = 12f, timestampMillis = 0L))
    }
}
