package com.dhikr.app

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.datastore.ThemeMode
import com.dhikr.app.ui.theme.resolveIsDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val preferencesRepository = remember {
                AppPreferencesRepository(context.applicationContext)
            }
            val themeMode by preferencesRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

            // Explicit transparent style, re-applied via SideEffect whenever the
            // resolved dark/light signal flips — now driven by the user's theme
            // choice as well as isSystemInDarkTheme(), the same input DhikrTheme
            // resolves for its own colors — instead of the plain no-arg
            // enableEdgeToEdge() default, which (combined with the window
            // background left at platform white, see themes.xml) was what made
            // the status bar render as an opaque white bar.
            val darkTheme = themeMode.resolveIsDark()
            SideEffect {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(TRANSPARENT)
                } else {
                    SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            DhikrApp(themeMode = themeMode)
        }
    }
}
