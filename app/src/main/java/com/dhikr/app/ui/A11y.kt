package com.dhikr.app.ui

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Material / WCAG minimum interactive size. Apply to a clickable whose visual
 * box is smaller than 48dp so the touch and TalkBack target still meet the
 * floor — the drawn content keeps its own smaller size, only the hit area grows.
 * Place it after `.clickable(...)` in the chain.
 */
fun Modifier.minTapTarget(): Modifier = sizeIn(minWidth = 48.dp, minHeight = 48.dp)

/** Marks a text node as a section heading for screen-reader navigation. */
fun Modifier.headingSemantics(): Modifier = semantics { heading() }

/**
 * Caps the effective font scale for [content] at [max]. Used for the oversized
 * counter number, which at the system's largest font settings would otherwise
 * grow past the screen — everything else in the app scales freely.
 */
@Composable
fun ClampedFontScale(max: Float = 1.3f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    if (density.fontScale <= max) {
        content()
    } else {
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, max),
            content = content,
        )
    }
}
