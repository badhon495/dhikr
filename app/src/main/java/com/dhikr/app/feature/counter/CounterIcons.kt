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
// and joins, no fill. Elliptical arcs from the SVG source ("A9 9 0 1 0 ...") are
// approximated with cubic beziers, since ImageVector has no arc-to helper; the
// standard circular-arc bezier constant (kappa ~= 0.5523 of the radius) is used
// so quarter-turns are visually indistinguishable from true arcs.
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
    // SVG: M3 13a9 9 0 1 0 3-7.7L3 8
    // A ~285-degree clockwise sweep on a radius-9 circle centred at (12, 13),
    // from (3, 13) round to roughly (6, 5.3). Built from three exact quarter
    // arcs plus a shorter closing segment. k = 9 * 0.5523.
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(3f, 13f)
        // (3,13) -> (12,4), top-left quarter
        curveTo(3f, 8.03f, 7.03f, 4f, 12f, 4f)
        // (12,4) -> (21,13), top-right quarter
        curveTo(16.97f, 4f, 21f, 8.03f, 21f, 13f)
        // (21,13) -> (12,22), bottom-right quarter
        curveTo(21f, 17.97f, 16.97f, 22f, 12f, 22f)
        // (12,22) -> (6.0,20.3), short arc closing the ~285-degree sweep
        curveTo(9.83f, 22f, 7.73f, 21.36f, 6.0f, 20.2f)
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
    // A ~285-degree counter-clockwise sweep on a radius-9 circle centred at
    // (12, 12), from (21, 12) round to roughly (18, 5.3).
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(21f, 12f)
        // (21,12) -> (12,21), bottom-right quarter
        curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
        // (12,21) -> (3,12), bottom-left quarter
        curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
        // (3,12) -> (12,3), top-left quarter
        curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
        // (12,3) -> (18.0,5.2), short arc closing the ~285-degree sweep
        curveTo(14.17f, 3f, 16.27f, 3.64f, 18.0f, 4.8f)
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
