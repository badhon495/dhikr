package com.dhikr.app.feature.onboarding

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Same stroke treatment as CounterIcons.kt/NavIcons.kt — 24x24 viewport, 2.75
// stroke, round caps/joins, no fill — so onboarding reads as the same icon
// family as the rest of the app. Built with addPathNodes() (like NavIcons.kt)
// rather than hand-split beziers, since these shapes are simple enough for
// raw SVG arc/line commands.
private const val STROKE_WIDTH = 2.75f

private fun onboardingIcon(name: String, pathData: String): ImageVector = ImageVector.Builder(
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
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}.build()

/** Concentric rings: a lap completing inside a larger goal ring. */
val OnboardingLapsIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    onboardingIcon("OnboardingLaps", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7")
}

/** A simple shield outline for the privacy/offline page. */
val OnboardingPrivacyIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    onboardingIcon(
        "OnboardingPrivacy",
        "M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6z",
    )
}

/** A waving hand / tap gesture for the "tap to count" page — a circle with a
 *  short radiating tick, echoing NavCountIcon's target-ring shape. */
val OnboardingTapIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    onboardingIcon("OnboardingTap", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18M12 9v3l2 2")
}
