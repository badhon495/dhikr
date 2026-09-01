package com.dhikr.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.datastore.AppPreferencesRepository
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(appVersion = appVersion))
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
            )
        }
            .combine(preferencesRepository.counterScript) { state, counterScript ->
                state.copy(counterScript = counterScript)
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

    fun onDailyGoalChange(value: Int) {
        viewModelScope.launch {
            preferencesRepository.setDailyGoalTarget(
                value.coerceIn(SettingsUiState.DAILY_GOAL_MIN, SettingsUiState.DAILY_GOAL_MAX),
            )
        }
    }

    class Factory(
        private val preferencesRepository: AppPreferencesRepository,
        private val appVersion: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(preferencesRepository, appVersion) as T
    }
}
