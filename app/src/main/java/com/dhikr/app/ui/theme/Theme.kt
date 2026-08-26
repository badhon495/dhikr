package com.dhikr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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
    CompositionLocalProvider(LocalDhikrColors provides tokens) {
        MaterialTheme(content = content)
    }
}
