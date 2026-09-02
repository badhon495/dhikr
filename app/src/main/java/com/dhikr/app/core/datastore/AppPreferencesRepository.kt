package com.dhikr.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dhikr.app.core.ai.BenefitsLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

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

/** Which script the counter screen shows for a dhikr. The chosen one also
 *  becomes the mandatory field in the tasbih editor. */
enum class CounterScript { PRONUNCIATION, ARABIC }

class AppPreferencesRepository(private val context: Context) {
    private val dailyGoalKey = intPreferencesKey("daily_goal_target")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val hapticsEnabledKey = booleanPreferencesKey("haptics_enabled")
    private val hapticModeKey = stringPreferencesKey("haptic_mode")
    private val reducedMotionKey = booleanPreferencesKey("reduced_motion")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val counterScriptKey = stringPreferencesKey("counter_script")
    private val autoCounterEnabledKey = booleanPreferencesKey("auto_counter_enabled")
    private val hasSeenOnboardingKey = booleanPreferencesKey("has_seen_onboarding")
    private val benefitsLanguageKey = stringPreferencesKey("benefits_language")
    private val benefitsPromptOverrideKey = stringPreferencesKey("benefits_prompt_override")

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
     *  regardless so toggling back and forth on an older device is harmless.
     *  Defaults on: unset means the user has never touched the toggle, and
     *  Material You is the intended out-of-the-box look on supported devices. */
    val dynamicColorEnabled = context.preferencesDataStore.data.map { it[dynamicColorKey] ?: true }

    suspend fun setDynamicColorEnabled(value: Boolean) {
        context.preferencesDataStore.edit { it[dynamicColorKey] = value }
    }

    /** Script shown on the counter. Absent/unknown falls back to PRONUNCIATION. */
    val counterScript = context.preferencesDataStore.data.map { prefs ->
        when (prefs[counterScriptKey]) {
            CounterScript.ARABIC.name -> CounterScript.ARABIC
            else -> CounterScript.PRONUNCIATION
        }
    }

    suspend fun setCounterScript(value: CounterScript) {
        context.preferencesDataStore.edit { it[counterScriptKey] = value.name }
    }

    /** Off by default (plan.md §40: "keep it disabled by default"). */
    val autoCounterEnabled = context.preferencesDataStore.data.map { it[autoCounterEnabledKey] ?: false }

    suspend fun setAutoCounterEnabled(value: Boolean) {
        context.preferencesDataStore.edit { it[autoCounterEnabledKey] = value }
    }

    /** Language the AI benefits write-up is generated in. Absent/unknown falls
     *  back to ENGLISH. Only touches the Gemini prompt/response, not the app UI. */
    val benefitsLanguage = context.preferencesDataStore.data.map { prefs ->
        when (prefs[benefitsLanguageKey]) {
            BenefitsLanguage.BANGLA.name -> BenefitsLanguage.BANGLA
            else -> BenefitsLanguage.ENGLISH
        }
    }

    suspend fun setBenefitsLanguage(value: BenefitsLanguage) {
        context.preferencesDataStore.edit { it[benefitsLanguageKey] = value.name }
    }

    /** User's edited benefits prompt template, or null to use the built-in
     *  template for [benefitsLanguage]. Blank is stored as "cleared" (null). */
    val benefitsPromptOverride = context.preferencesDataStore.data.map {
        it[benefitsPromptOverrideKey]?.takeIf { s -> s.isNotBlank() }
    }

    suspend fun setBenefitsPromptOverride(value: String?) {
        context.preferencesDataStore.edit { prefs ->
            val trimmed = value?.takeIf { it.isNotBlank() }
            if (trimmed == null) prefs.remove(benefitsPromptOverrideKey)
            else prefs[benefitsPromptOverrideKey] = trimmed
        }
    }

    /** One-shot UX flag, not a real preference — never wired into backup/restore
     *  (plan.md §25 onboarding is per-device, not something worth carrying
     *  across a backup). Absent (fresh install) means false: show onboarding. */
    val hasSeenOnboarding = context.preferencesDataStore.data.map { it[hasSeenOnboardingKey] ?: false }

    suspend fun setHasSeenOnboarding(value: Boolean) {
        context.preferencesDataStore.edit { it[hasSeenOnboardingKey] = value }
    }

    /**
     * Synchronous read of every preference the Settings screen shows. It seeds
     * its initial UI state with this so the first frame already reflects the
     * saved choices rather than the data-class defaults — otherwise every pill
     * and toggle (and the sections whose height depends on a value) visibly
     * snaps once the Flows emit, reflowing the page on each visit.
     *
     * Safe to block here: by the time Settings is reachable the DataStore file
     * has already been read (MainActivity opens the same store on launch), so
     * every `first()` returns from the in-memory cache without disk I/O.
     */
    fun settingsInitialValues(): SettingsInitialValues = runBlocking {
        SettingsInitialValues(
            themeMode = themeMode.first(),
            hapticMode = hapticMode.first(),
            reducedMotion = reducedMotion.first(),
            dailyGoalTarget = dailyGoalTarget.first(),
            dynamicColorEnabled = dynamicColorEnabled.first(),
            counterScript = counterScript.first(),
            autoCounterEnabled = autoCounterEnabled.first(),
            benefitsLanguage = benefitsLanguage.first(),
            benefitsPromptOverride = benefitsPromptOverride.first(),
        )
    }

    /** One-shot read of every user-facing preference, for a backup export. */
    suspend fun snapshot(): PreferencesSnapshot = PreferencesSnapshot(
        dailyGoalTarget = dailyGoalTarget.first(),
        themeMode = themeMode.first(),
        hapticMode = hapticMode.first(),
        reducedMotion = reducedMotion.first(),
        dynamicColorEnabled = dynamicColorEnabled.first(),
        counterScript = counterScript.first(),
        benefitsLanguage = benefitsLanguage.first(),
        benefitsPromptOverride = benefitsPromptOverride.first(),
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
        counterScript: CounterScript?,
        benefitsLanguage: BenefitsLanguage?,
        benefitsPromptOverride: String?,
    ) {
        context.preferencesDataStore.edit { prefs ->
            dailyGoalTarget?.let { prefs[dailyGoalKey] = it }
            themeMode?.let { prefs[themeModeKey] = it.name }
            hapticMode?.let { prefs[hapticModeKey] = it.name }
            reducedMotion?.let { prefs[reducedMotionKey] = it }
            dynamicColorEnabled?.let { prefs[dynamicColorKey] = it }
            counterScript?.let { prefs[counterScriptKey] = it.name }
            benefitsLanguage?.let { prefs[benefitsLanguageKey] = it.name }
            benefitsPromptOverride?.takeIf { it.isNotBlank() }?.let { prefs[benefitsPromptOverrideKey] = it }
        }
    }
}

/** The Settings screen's initial UI values, read synchronously at construction
 *  — see [AppPreferencesRepository.settingsInitialValues]. */
data class SettingsInitialValues(
    val themeMode: ThemeMode,
    val hapticMode: HapticMode,
    val reducedMotion: Boolean,
    val dailyGoalTarget: Int,
    val dynamicColorEnabled: Boolean,
    val counterScript: CounterScript,
    val autoCounterEnabled: Boolean,
    val benefitsLanguage: BenefitsLanguage,
    val benefitsPromptOverride: String?,
)

data class PreferencesSnapshot(
    val dailyGoalTarget: Int,
    val themeMode: ThemeMode,
    val hapticMode: HapticMode,
    val reducedMotion: Boolean,
    val dynamicColorEnabled: Boolean,
    val counterScript: CounterScript,
    val benefitsLanguage: BenefitsLanguage,
    val benefitsPromptOverride: String?,
)
