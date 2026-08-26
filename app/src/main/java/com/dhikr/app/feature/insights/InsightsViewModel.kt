package com.dhikr.app.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.HistoryRepository
import com.dhikr.app.core.database.TasbihHistoryGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class InsightsUiState(
    val today: Int = 0,
    val week: Int = 0,
    val month: Int = 0,
    val allTime: Int = 0,
    val last7Days: List<Pair<String, Int>> = emptyList(),
    val calendarIntensity: Map<Int, Int> = emptyMap(),
    val historyByTasbih: List<TasbihHistoryGroup> = emptyList(),
    val isEmpty: Boolean = true,
)

class InsightsViewModel(private val repository: HistoryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val now = Calendar.getInstance()
            val today = repository.todayTotal()
            val week = repository.weekTotal()
            val month = repository.monthTotal()
            val allTime = repository.allTimeTotal()
            val last7Days = repository.last7DaysTotals()
            val calendar = repository.calendarIntensity(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
            val history = repository.historyByTasbih()
            _uiState.value = InsightsUiState(
                today = today,
                week = week,
                month = month,
                allTime = allTime,
                last7Days = last7Days,
                calendarIntensity = calendar,
                historyByTasbih = history,
                isEmpty = allTime == 0,
            )
        }
    }

    class Factory(private val repository: HistoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            InsightsViewModel(repository) as T
    }
}
