package com.dhikr.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// One-off icons that aren't part of the bottom-nav family but still need to
// match its treatment (24x24 viewport, round stroke, no fill) — see
// NavIcons.kt. Kept here so `material-icons-extended` is not pulled in for a
// single glyph.
private const val MISC_STROKE_WIDTH = 2.75f

private fun miscIcon(name: String, pathData: String): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    addPath(
        pathData = addPathNodes(pathData),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = MISC_STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}.build()

/** Clock face — replaces `Icons.Filled.Schedule` (extended-only). */
val ScheduleIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    miscIcon("Schedule", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18M12 7.5V12l3.4 2")
}
