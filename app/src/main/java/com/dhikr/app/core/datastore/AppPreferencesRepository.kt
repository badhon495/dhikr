package com.dhikr.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.preferencesDataStore by preferencesDataStore(name = "preferences")

/** User's app-theme choice. SYSTEM defers to the device dark/light setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

class AppPreferencesRepository(private val context: Context) {
    private val dailyGoalKey = intPreferencesKey("daily_goal_target")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val hapticsEnabledKey = booleanPreferencesKey("haptics_enabled")
    private val reducedMotionKey = booleanPreferencesKey("reduced_motion")

    val dailyGoalTarget = context.preferencesDataStore.data.map { it[dailyGoalKey] ?: 100 }

    suspend fun setDailyGoalTarget(value: Int) {
        context.preferencesDataStore.edit { it[dailyGoalKey] = value }
    }

    val themeMode = context.preferencesDataStore.data.map { prefs ->
        // Unknown/absent value falls back to SYSTEM rather than crashing on a
        // stale or hand-edited store (plan.md §57).
        when (prefs[themeModeKey]) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(value: ThemeMode) {
        context.preferencesDataStore.edit { it[themeModeKey] = value.name }
    }

    val hapticsEnabled = context.preferencesDataStore.data.map { it[hapticsEnabledKey] ?: true }

    suspend fun setHapticsEnabled(value: Boolean) {
        context.preferencesDataStore.edit { it[hapticsEnabledKey] = value }
    }

    val reducedMotion = context.preferencesDataStore.data.map { it[reducedMotionKey] ?: false }

    suspend fun setReducedMotion(value: Boolean) {
        context.preferencesDataStore.edit { it[reducedMotionKey] = value }
    }
}
