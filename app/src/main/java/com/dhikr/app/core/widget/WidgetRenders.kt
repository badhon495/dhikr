package com.dhikr.app.core.widget

import java.text.NumberFormat
import java.util.Locale

/**
 * Pure formatting/layout helpers for both widgets. The RemoteViews builders
 * (added alongside the providers) call these; the helpers are split out so they
 * unit-test without an Android runtime.
 */
object WidgetRenders {

    data class Progress(val progress: Int, val max: Int)

    fun formatCountOfTarget(count: Int, target: Int): String = "$count / $target"

    /**
     * A ProgressBar needs max >= 1 and progress in 0..max. Clamp so goal <= 0
     * (or a count that ran past target after a goal change) still renders a
     * sane bar instead of crashing or showing a full/negative one.
     */
    fun clampProgress(value: Int, target: Int): Progress {
        val max = target.coerceAtLeast(1)
        return Progress(progress = value.coerceIn(0, max), max = max)
    }

    fun formatGrouped(value: Int): String =
        NumberFormat.getIntegerInstance(Locale.US).format(value.toLong())
}
