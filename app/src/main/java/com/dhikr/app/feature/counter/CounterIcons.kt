package com.dhikr.app.feature.counter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Vector paths ported from the prototype's inline SVGs
// (design/Dhikr Android App.dc.html lines 40, 47, 92, 95). All icons share the
// prototype's stroke treatment: 24x24 viewport, 2.75 stroke width, round caps
// and joins, no fill. Elliptical arcs from the SVG source ("a9 9 0 1 0 ...") are
// converted to cubic beziers, since ImageVector has no arc-to helper. Each arc's
// centre and sweep are solved from the SVG endpoint parameterisation, then split
// into equal sub-90-degree segments using the exact circular-arc control-point
// formula (k = 4/3 * tan(step/4)) so every on-curve point lies on the true
// circle. Both the sweep DIRECTION and the terminal point matter visually — see
// the per-icon notes below.
private const val STROKE_WIDTH = 2.75f

fun backChevronIcon(): ImageVector = ImageVector.Builder(
    name = "BackChevron",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    // SVG: M15 18l-6-6 6-6
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(15f, 18f)
        lineTo(9f, 12f)
        lineTo(15f, 6f)
    }
}.build()

fun undoIcon(): ImageVector = ImageVector.Builder(
    name = "Undo",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    // SVG: M3 7v6h6
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(3f, 7f)
        lineTo(3f, 13f)
        lineTo(9f, 13f)
    }
    // SVG: M3 13a9 9 0 1 0 3-7.7
    // Solving the SVG arc endpoint parameterisation for r=9 through (3,13) and
    // (6,5.3) gives centre (11.95, 12.05) — NOT (12,13) — and sweep=0 means a
    // counter-clockwise on-screen sweep of -305.3 degrees. The arc must end
    // ABOVE its start, so the gap in the spiral meets the arrowhead chevron at
    // upper-left; traversing the other way mirrors the icon vertically and
    // leaves the arrowhead detached.
    // Split into 4 equal sub-90-degree segments, each an exact circular bezier
    // (k = 4/3 * tan(step/4)), so every point lies on the true radius-9 circle.
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(3.00f, 12.99f)
        curveTo(3.43f, 17.12f, 6.63f, 20.42f, 10.75f, 20.97f)
        curveTo(14.86f, 21.53f, 18.82f, 19.20f, 20.33f, 15.33f)
        curveTo(21.84f, 11.46f, 20.51f, 7.07f, 17.12f, 4.68f)
        curveTo(13.72f, 2.30f, 9.13f, 2.55f, 6.01f, 5.29f)
    }
}.build()

fun resetIcon(): ImageVector = ImageVector.Builder(
    name = "Reset",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    // SVG: M21 12a9 9 0 1 1-3-6.7
    // Centre solves to (12, 12.01) and sweep=1 gives a clockwise on-screen
    // sweep of +311.9 degrees, from (21,12) round to (18, 5.3) — direction
    // confirmed correct. Split into 4 equal sub-90-degree segments, each an
    // exact circular bezier, so the terminus lands on the true radius-9 circle
    // instead of bulging off it.
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(21.00f, 12.00f)
        curveTo(21.00f, 16.25f, 18.04f, 19.92f, 13.88f, 20.81f)
        curveTo(9.73f, 21.70f, 5.52f, 19.56f, 3.78f, 15.68f)
        curveTo(2.05f, 11.81f, 3.26f, 7.24f, 6.69f, 4.74f)
        curveTo(10.13f, 2.23f, 14.84f, 2.47f, 18.00f, 5.30f)
    }
    // SVG: M21 3v6h-6
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(21f, 3f)
        lineTo(21f, 9f)
        lineTo(15f, 9f)
    }
}.build()

fun lockIcon(locked: Boolean): ImageVector = ImageVector.Builder(
    name = if (locked) "LockClosed" else "LockOpen",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    // SVG: <rect x="4" y="11" width="16" height="10" rx="3" />
    // Rounded rectangle body, drawn clockwise from the top-left straight edge.
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(7f, 11f)
        lineTo(17f, 11f)
        curveTo(18.66f, 11f, 20f, 12.34f, 20f, 14f)
        lineTo(20f, 18f)
        curveTo(20f, 19.66f, 18.66f, 21f, 17f, 21f)
        lineTo(7f, 21f)
        curveTo(5.34f, 21f, 4f, 19.66f, 4f, 18f)
        lineTo(4f, 14f)
        curveTo(4f, 12.34f, 5.34f, 11f, 7f, 11f)
        close()
    }
    // Shackle. SVG closed: M8 11V8a4 4 0 0 1 8 0v3
    //          SVG open:   M8 11V8a4 4 0 0 1 7-2.6
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(8f, 11f)
        lineTo(8f, 8f)
        if (locked) {
            // Half circle radius 4 centred at (12, 8): (8,8) -> (12,4) -> (16,8)
            curveTo(8f, 5.79f, 9.79f, 4f, 12f, 4f)
            curveTo(14.21f, 4f, 16f, 5.79f, 16f, 8f)
            lineTo(16f, 11f)
        } else {
            // Open shackle: (8,8) -> (12,4) -> (15,5.4), stopping short so the
            // hook stands clear of the body's right edge.
            curveTo(8f, 5.79f, 9.79f, 4f, 12f, 4f)
            curveTo(13.19f, 4f, 14.26f, 4.52f, 15f, 5.35f)
        }
    }
}.build()
