package com.dhikr.app.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * App-wide "reduce motion" flag, provided from the `reduced_motion` preference
 * in DhikrApp. Read with `LocalReducedMotion.current`. Defaults to false so
 * @Preview and tests animate normally.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Shared motion tokens so the counter, the goal ring and screen transitions
 *  all move on the same durations and curve. */
object Motion {
    const val FAST_MS = 120
    const val STANDARD_MS = 160
    const val PULSE_MS = 300

    /** Ported from the prototype's ring transition — cubic-bezier(.2,.7,.3,1). */
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)
}

/** `tween(durationMs, easing)`, or an instant `snap()` when reduce-motion is on. */
fun <T> motionSpec(
    reduced: Boolean,
    durationMs: Int = Motion.STANDARD_MS,
    easing: Easing = Motion.StandardEasing,
): AnimationSpec<T> = if (reduced) snap() else tween(durationMs, easing = easing)
