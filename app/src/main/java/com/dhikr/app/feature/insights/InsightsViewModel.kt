package com.dhikr.app.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.HistoryRepository
import com.dhikr.app.core.database.MonthSummary
import com.dhikr.app.core.database.TasbihHistoryGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import java.util.Calendar

data class InsightsUiState(
    val today: Int = 0,
    val week: Int = 0,
    val month: Int = 0,
    val allTime: Int = 0,
    val last7Days: List<Pair<String, Int>> = emptyList(),
    val calendarIntensity: Map<Int, Int> = emptyMap(),
    val historyByTasbih: List<TasbihHistoryGroup> = emptyList(),
    val previousMonth: MonthSummary? = null,
    val isEmpty: Boolean = true,
)

/**
 * The 2x2 totals tiles are driven by Room-backed Flows (finding #6) so they
 * refresh the moment a session is logged elsewhere (e.g. Home → Counter →
 * count → back to Insights via bottom nav, which keeps this ViewModel alive
 * in the back stack rather than recreating it). The 7-day bars, consistency
 * calendar and per-Tasbih history remain one-shot suspend reads recomputed
 * every time the totals Flow re-emits — `combine`'s `today` total already
 * changes on every insert, so using it as a re-fetch trigger via
 * `flatMapLatest` keeps everything in this screen consistent without needing
 * a second, independent invalidation source for the more complex queries.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(private val repository: HistoryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init {
        combine(
            repository.todayTotalFlow(),
            repository.weekTotalFlow(),
            repository.monthTotalFlow(),
            repository.allTimeTotalFlow(),
        ) { today, week, month, allTime -> InsightsTotals(today, week, month, allTime) }
            .mapLatest { totals ->
                val now = Calendar.getInstance()
                val last7Days = repository.last7DaysTotals()
                val calendar = repository.calendarIntensity(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
                val history = repository.historyByTasbih()
                val lastMonth = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                val previousMonth = repository.monthlySummaries().firstOrNull {
                    it.year == lastMonth.get(Calendar.YEAR) && it.month == lastMonth.get(Calendar.MONTH)
                }
                InsightsUiState(
                    today = totals.today,
                    week = totals.week,
                    month = totals.month,
                    allTime = totals.allTime,
                    last7Days = last7Days,
                    calendarIntensity = calendar,
                    historyByTasbih = history,
                    previousMonth = previousMonth,
                    isEmpty = totals.allTime == 0,
                )
            }
            .onEach { _uiState.value = it }
            .launchIn(viewModelScope)
    }

    private data class InsightsTotals(val today: Int, val week: Int, val month: Int, val allTime: Int)

    class Factory(private val repository: HistoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            InsightsViewModel(repository) as T
    }
}
