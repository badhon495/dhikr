package com.dhikr.app

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Explicit transparent style, re-applied via SideEffect whenever
            // the dark/light signal flips, so status/nav bar icon contrast
            // tracks the same isSystemInDarkTheme() DhikrTheme uses for its
            // own colors — instead of the plain no-arg enableEdgeToEdge()
            // default, which (combined with the window background left at
            // platform white, see themes.xml) was what made the status bar
            // render as an opaque white bar.
            val darkTheme = isSystemInDarkTheme()
            SideEffect {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(TRANSPARENT)
                } else {
                    SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            DhikrApp()
        }
    }
}
