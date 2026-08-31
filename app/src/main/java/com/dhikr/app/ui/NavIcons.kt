package com.dhikr.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Bottom-navigation icons. Path data lifted verbatim from the prototype's NAV
// table (design/Dhikr Android App.dc.html line 541-545). Same stroke treatment
// as CounterIcons.kt — 24x24 viewport, 2.75 stroke, round caps/joins, no fill —
// so the two icon sets read as one family. Unlike CounterIcons.kt these keep the
// SVG arc commands as-is: addPathNodes() parses elliptical arcs directly, so no
// manual bezier conversion is needed for these simple small-radius corners.
// stroke colour is a placeholder Black; NavigationBarItem tints it per state.
private const val NAV_STROKE_WIDTH = 2.75f

private fun navIcon(name: String, pathData: String): ImageVector = ImageVector.Builder(
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
        strokeLineWidth = NAV_STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}.build()

val NavHomeIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    navIcon("NavHome", "M4 11l8-7 8 7v9a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z")
}
val NavTasbihIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    navIcon("NavTasbih", "M5 19V5a1 1 0 0 1 1-1h11a2 2 0 0 1 2 2v13H7a2 2 0 0 0-2 2M9 8h7")
}
val NavCountIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    navIcon("NavCount", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18M12 8v8M8 12h8")
}
val NavInsightsIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    navIcon("NavInsights", "M4 20h16M7 20v-6M12 20v-11M17 20v-4")
}
val NavSettingsIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    navIcon(
        "NavSettings",
        "M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4" +
            "M19.5 14.4a1.7 1.7 0 0 0 .3 1.9 1.9 1.9 0 1 1-2.7 2.7 1.7 1.7 0 0 0-2.9 1.2 " +
            "1.9 1.9 0 1 1-3.8 0 1.7 1.7 0 0 0-2.9-1.2 1.9 1.9 0 1 1-2.7-2.7 1.7 1.7 0 0 0-1.2-2.9 " +
            "1.9 1.9 0 1 1 0-3.8 1.7 1.7 0 0 0 1.2-2.9 1.9 1.9 0 1 1 2.7-2.7 1.7 1.7 0 0 0 2.9-1.2 " +
            "1.9 1.9 0 1 1 3.8 0 1.7 1.7 0 0 0 2.9 1.2 1.9 1.9 0 1 1 2.7 2.7 1.7 1.7 0 0 0 1.2 2.9 " +
            "1.9 1.9 0 1 1 0 3.8 1.7 1.7 0 0 0-1.5 1.1",
    )
}
