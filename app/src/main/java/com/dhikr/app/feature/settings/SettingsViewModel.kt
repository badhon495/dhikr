package com.dhikr.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.datastore.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hapticsEnabled: Boolean = true,
    val dailyGoalTarget: Int = 100,
    val appVersion: String = "",
) {
    /** Common tasbih counts offered as quick-pick daily-goal targets. */
    val dailyGoalOptions: List<Int> get() = DAILY_GOAL_OPTIONS

    companion object {
        val DAILY_GOAL_OPTIONS = listOf(33, 100, 300, 500, 1000)
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
            preferencesRepository.hapticsEnabled,
            preferencesRepository.dailyGoalTarget,
        ) { themeMode, hapticsEnabled, dailyGoal ->
            SettingsUiState(
                themeMode = themeMode,
                hapticsEnabled = hapticsEnabled,
                dailyGoalTarget = dailyGoal,
                appVersion = appVersion,
            )
        }
            .onEach { _uiState.value = it }
            .launchIn(viewModelScope)
    }

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun onHapticsEnabledChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setHapticsEnabled(enabled) }
    }

    fun onDailyGoalChange(value: Int) {
        viewModelScope.launch {
            preferencesRepository.setDailyGoalTarget(value.coerceAtLeast(1))
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
