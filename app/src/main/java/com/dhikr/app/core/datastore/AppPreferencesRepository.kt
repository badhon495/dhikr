package com.dhikr.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.preferencesDataStore by preferencesDataStore(name = "preferences")

/** User's app-theme choice. SYSTEM defers to the device dark/light setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * When the counter vibrates.
 *  - OFF: never
 *  - EVERY_TAP: on every registered count
 *  - LAP_ONLY: only when a lap completes
 */
enum class HapticMode { OFF, EVERY_TAP, LAP_ONLY }

class AppPreferencesRepository(private val context: Context) {
    private val dailyGoalKey = intPreferencesKey("daily_goal_target")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val hapticsEnabledKey = booleanPreferencesKey("haptics_enabled")
    private val hapticModeKey = stringPreferencesKey("haptic_mode")
    private val reducedMotionKey = booleanPreferencesKey("reduced_motion")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")

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

    /**
     * When the counter vibrates. Falls back to the pre-3.x boolean
     * `haptics_enabled` so an existing user's choice carries over: absent →
     * EVERY_TAP, false → OFF, true → EVERY_TAP. An unknown stored string also
     * falls back to EVERY_TAP rather than crashing on a hand-edited store.
     */
    val hapticMode = context.preferencesDataStore.data.map { prefs ->
        when (prefs[hapticModeKey]) {
            HapticMode.OFF.name -> HapticMode.OFF
            HapticMode.EVERY_TAP.name -> HapticMode.EVERY_TAP
            HapticMode.LAP_ONLY.name -> HapticMode.LAP_ONLY
            else -> if (prefs[hapticsEnabledKey] == false) HapticMode.OFF else HapticMode.EVERY_TAP
        }
    }

    suspend fun setHapticMode(value: HapticMode) {
        context.preferencesDataStore.edit { it[hapticModeKey] = value.name }
    }

    val reducedMotion = context.preferencesDataStore.data.map { it[reducedMotionKey] ?: false }

    suspend fun setReducedMotion(value: Boolean) {
        context.preferencesDataStore.edit { it[reducedMotionKey] = value }
    }

    /** Material You: derive the app palette from the device wallpaper. Only
     *  honoured on Android 12+ (see [DhikrTheme]); the store value is kept
     *  regardless so toggling back and forth on an older device is harmless. */
    val dynamicColorEnabled = context.preferencesDataStore.data.map { it[dynamicColorKey] ?: false }

    suspend fun setDynamicColorEnabled(value: Boolean) {
        context.preferencesDataStore.edit { it[dynamicColorKey] = value }
    }

    /** One-shot read of every user-facing preference, for a backup export. */
    suspend fun snapshot(): PreferencesSnapshot = PreferencesSnapshot(
        dailyGoalTarget = dailyGoalTarget.first(),
        themeMode = themeMode.first(),
        hapticMode = hapticMode.first(),
        reducedMotion = reducedMotion.first(),
        dynamicColorEnabled = dynamicColorEnabled.first(),
    )

    /**
     * Apply a snapshot from a restored backup. Each field is optional: a null
     * leaves the current value untouched, so a backup written by an older app
     * version that lacked a field is harmless.
     */
    suspend fun restore(
        dailyGoalTarget: Int?,
        themeMode: ThemeMode?,
        hapticMode: HapticMode?,
        reducedMotion: Boolean?,
        dynamicColorEnabled: Boolean?,
    ) {
        context.preferencesDataStore.edit { prefs ->
            dailyGoalTarget?.let { prefs[dailyGoalKey] = it }
            themeMode?.let { prefs[themeModeKey] = it.name }
            hapticMode?.let { prefs[hapticModeKey] = it.name }
            reducedMotion?.let { prefs[reducedMotionKey] = it }
            dynamicColorEnabled?.let { prefs[dynamicColorKey] = it }
        }
    }
}

data class PreferencesSnapshot(
    val dailyGoalTarget: Int,
    val themeMode: ThemeMode,
    val hapticMode: HapticMode,
    val reducedMotion: Boolean,
    val dynamicColorEnabled: Boolean,
)
