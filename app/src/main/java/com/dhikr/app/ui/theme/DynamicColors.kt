package com.dhikr.app.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color

/** True on devices where the wallpaper-derived Material You palette is available. */
fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Projects the device's Material You [ColorScheme] onto the app's own
 * [DhikrColorTokens] so every screen — which paints from these tokens, not from
 * MaterialTheme.colorScheme — picks up the wallpaper colours.
 *
 * The [line] and [track] tokens carry a low alpha in the static palettes; the
 * same alphas are re-applied here over `outlineVariant` so hairlines stay subtle.
 */
@RequiresApi(Build.VERSION_CODES.S)
fun dynamicDhikrColors(context: Context, dark: Boolean): DhikrColorTokens {
    val scheme: ColorScheme =
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return DhikrColorTokens(
        bg = scheme.surface,
        surface = scheme.surfaceContainerLow,
        card = scheme.surfaceContainerHighest,
        text = scheme.onSurface,
        dim = scheme.onSurfaceVariant,
        faint = scheme.outline,
        line = scheme.outlineVariant.copy(alpha = 0.13f),
        sage = scheme.primary,
        sageSoft = scheme.primaryContainer,
        sageMid = scheme.secondary,
        terra = scheme.tertiary,
        terraSoft = scheme.tertiaryContainer,
        track = scheme.outlineVariant.copy(alpha = 0.10f),
        onSage = scheme.onPrimary,
    )
}

/** The Material You [ColorScheme] itself, for the MaterialTheme wrapper. */
@RequiresApi(Build.VERSION_CODES.S)
fun dynamicColorScheme(context: Context, dark: Boolean): ColorScheme =
    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
