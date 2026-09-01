package com.dhikr.app

import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.datastore.ThemeMode
import com.dhikr.app.core.notifications.ReminderNotifications
import com.dhikr.app.ui.theme.resolveIsDark

class MainActivity : ComponentActivity() {

    // Set from the launch Intent (reminder-notification tap) and consumed once
    // by DhikrApp, which navigates to that routine's counter.
    private var pendingRoutineId by mutableStateOf<String?>(null)

    // Set from the launch Intent (widget body tap) and consumed once by
    // DhikrApp, which navigates to the counter or insights tab.
    private var pendingOpen by mutableStateOf<String?>(null)

    // Set from an ACTION_VIEW launch (a tapped .dhikrroutine file) and consumed
    // once by DhikrApp, which navigates to the import preview.
    private var pendingShareUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingRoutineId = intent?.getStringExtra(ReminderNotifications.EXTRA_ROUTINE_ID)
        pendingOpen = intent?.getStringExtra(EXTRA_OPEN)
        pendingShareUri = if (intent?.action == Intent.ACTION_VIEW) intent?.data else null
        setContent {
            val context = LocalContext.current
            val preferencesRepository = remember {
                AppPreferencesRepository(context.applicationContext)
            }
            val themeMode by preferencesRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val dynamicColor by preferencesRepository.dynamicColorEnabled.collectAsState(initial = false)

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
            DhikrApp(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                pendingRoutineId = pendingRoutineId,
                onPendingRoutineConsumed = { pendingRoutineId = null },
                pendingOpen = pendingOpen,
                onPendingOpenConsumed = { pendingOpen = null },
                pendingShareUri = pendingShareUri,
                onPendingShareConsumed = { pendingShareUri = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoutineId = intent.getStringExtra(ReminderNotifications.EXTRA_ROUTINE_ID)
        pendingOpen = intent.getStringExtra(EXTRA_OPEN)
        pendingShareUri = if (intent.action == Intent.ACTION_VIEW) intent.data else null
    }

    companion object {
        const val EXTRA_OPEN = "com.dhikr.app.extra.OPEN"
        const val OPEN_COUNTER = "counter"
        const val OPEN_INSIGHTS = "insights"
    }
}
