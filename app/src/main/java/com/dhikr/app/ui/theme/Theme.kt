package com.dhikr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

val LocalDhikrColors = compositionLocalOf { LightDhikrColors }

object DhikrTheme {
    val colors: DhikrColorTokens
        @Composable get() = LocalDhikrColors.current
}

@Composable
fun DhikrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) DarkDhikrColors else LightDhikrColors
    // Material3's default colorScheme (background ~white, surface ~white) is
    // otherwise left untouched by DhikrColorTokens — components that read it
    // directly instead of DhikrTheme.colors (Scaffold's containerColor,
    // NavigationBar's default containerColor, etc.) paint that default
    // regardless of dark/light app theme. Mapping the two closest fields onto
    // it is what keeps Scaffold's own background — the area under the status
    // bar, which app content never draws into — from showing as a stray white
    // strip in dark mode.
    val colorScheme = if (darkTheme) {
        darkColorScheme(background = tokens.bg, surface = tokens.surface)
    } else {
        lightColorScheme(background = tokens.bg, surface = tokens.surface)
    }
    CompositionLocalProvider(LocalDhikrColors provides tokens) {
        MaterialTheme(colorScheme = colorScheme, typography = DhikrTypography, content = content)
    }
}
