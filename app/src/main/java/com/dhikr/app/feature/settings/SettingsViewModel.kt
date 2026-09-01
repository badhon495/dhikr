package com.dhikr.app.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.ai.SecureKeyStore
import com.dhikr.app.core.counter.AutoCounterSensorListener
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.widget.DhikrWidgets
import com.dhikr.app.core.datastore.CounterScript
import com.dhikr.app.core.datastore.HapticMode
import com.dhikr.app.core.datastore.ThemeMode
import com.dhikr.app.ui.theme.supportsDynamicColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hapticMode: HapticMode = HapticMode.EVERY_TAP,
    val reducedMotion: Boolean = false,
    val dailyGoalTarget: Int = 100,
    val dynamicColorEnabled: Boolean = false,
    val dynamicColorSupported: Boolean = supportsDynamicColor(),
    val counterScript: CounterScript = CounterScript.PRONUNCIATION,
    val appVersion: String = "",
    val hasGeminiKey: Boolean = false,
    val autoCounterEnabled: Boolean = false,
    val autoCounterSupported: Boolean = true,
) {
    /** Common tasbih counts offered as quick-pick daily-goal targets. */
    val dailyGoalOptions: List<Int> get() = DAILY_GOAL_OPTIONS

    /** True when the current target isn't one of the quick-pick presets, i.e.
     *  it was set through the custom number field. */
    val isCustomGoal: Boolean get() = dailyGoalTarget !in DAILY_GOAL_OPTIONS

    companion object {
        val DAILY_GOAL_OPTIONS = listOf(500, 1000)
        const val DAILY_GOAL_MIN = 1
        const val DAILY_GOAL_MAX = 99_999
    }
}

class SettingsViewModel(
    private val preferencesRepository: AppPreferencesRepository,
    private val appVersion: String,
    private val appContext: Context,
    private val secureKeyStore: SecureKeyStore,
) : ViewModel() {

    // Cheap, synchronous hardware check (getSystemService + getDefaultSensor,
    // no listener registered) — safe to read once up front rather than as a
    // Flow, since it can't change while the app is running.
    private val autoCounterSupported = AutoCounterSensorListener(appContext).isSupported

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            appVersion = appVersion,
            hasGeminiKey = secureKeyStore.hasKey,
            autoCounterSupported = autoCounterSupported,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        combine(
            preferencesRepository.themeMode,
            preferencesRepository.hapticMode,
            preferencesRepository.reducedMotion,
            preferencesRepository.dailyGoalTarget,
            preferencesRepository.dynamicColorEnabled,
        ) { themeMode, hapticMode, reducedMotion, dailyGoal, dynamicColor ->
            SettingsUiState(
                themeMode = themeMode,
                hapticMode = hapticMode,
                reducedMotion = reducedMotion,
                dailyGoalTarget = dailyGoal,
                dynamicColorEnabled = dynamicColor,
                appVersion = appVersion,
                hasGeminiKey = secureKeyStore.hasKey,
                autoCounterSupported = autoCounterSupported,
            )
        }
            .combine(preferencesRepository.counterScript) { state, counterScript ->
                state.copy(counterScript = counterScript)
            }
            .combine(preferencesRepository.autoCounterEnabled) { state, autoCounterEnabled ->
                state.copy(autoCounterEnabled = autoCounterEnabled)
            }
            .onEach { _uiState.value = it }
            .launchIn(viewModelScope)
    }

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun onHapticModeChange(mode: HapticMode) {
        viewModelScope.launch { preferencesRepository.setHapticMode(mode) }
    }

    fun onReducedMotionChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setReducedMotion(enabled) }
    }

    fun onDynamicColorChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDynamicColorEnabled(enabled) }
    }

    fun onCounterScriptChange(value: CounterScript) {
        viewModelScope.launch { preferencesRepository.setCounterScript(value) }
    }

    fun onAutoCounterEnabledChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoCounterEnabled(enabled) }
    }

    fun onDailyGoalChange(value: Int) {
        viewModelScope.launch {
            preferencesRepository.setDailyGoalTarget(
                value.coerceIn(SettingsUiState.DAILY_GOAL_MIN, SettingsUiState.DAILY_GOAL_MAX),
            )
            // The insights widget's "Today / goal" line is otherwise stale until
            // its next 30-min tick — push a refresh now that the goal changed.
            DhikrWidgets.refreshAll(appContext)
        }
    }

    fun saveGeminiKey(raw: String) {
        viewModelScope.launch {
            secureKeyStore.setGeminiKey(raw)
            _uiState.value = _uiState.value.copy(hasGeminiKey = secureKeyStore.hasKey)
        }
    }

    fun clearGeminiKey() {
        viewModelScope.launch {
            secureKeyStore.setGeminiKey(null)
            _uiState.value = _uiState.value.copy(hasGeminiKey = false)
        }
    }

    class Factory(
        private val preferencesRepository: AppPreferencesRepository,
        private val appVersion: String,
        private val appContext: Context,
        private val secureKeyStore: SecureKeyStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(preferencesRepository, appVersion, appContext, secureKeyStore) as T
    }
}
