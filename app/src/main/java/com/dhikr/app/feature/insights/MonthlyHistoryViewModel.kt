package com.dhikr.app.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.HistoryRepository
import com.dhikr.app.core.database.MonthSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MonthlyHistoryUiState(
    val months: List<MonthSummary> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * One-shot read of every month with activity. Unlike InsightsViewModel this
 * screen isn't kept alive in the bottom-nav back stack while counting happens
 * elsewhere, so a plain suspend read in init is enough — no Flow needed.
 */
class MonthlyHistoryViewModel(private val repository: HistoryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MonthlyHistoryUiState())
    val uiState: StateFlow<MonthlyHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val months = repository.monthlySummaries()
            _uiState.value = MonthlyHistoryUiState(months = months, isLoading = false)
        }
    }

    class Factory(private val repository: HistoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MonthlyHistoryViewModel(repository) as T
    }
}
