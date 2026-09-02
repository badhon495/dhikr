package com.dhikr.app.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.HistoryRepository
import com.dhikr.app.core.database.MonthSummary
import com.dhikr.app.core.database.TasbihHistoryGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
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
    // False for the synthetic default emitted before the totals Flow produces
    // its first value. The screen paints nothing until this is true so the
    // "no data yet" empty state doesn't flash for a frame before real totals
    // arrive (which is every time the Insights tab is opened afresh).
    val loaded: Boolean = false,
)

/**
 * The 2x2 totals tiles are driven by Room-backed Flows (finding #6) so they
 * refresh the moment a session is logged elsewhere (e.g. Home → Counter →
 * count → back to Insights via bottom nav, which keeps this ViewModel alive
 * in the back stack rather than recreating it).
 *
 * Load order matters for perceived speed: when the totals Flow emits we push
 * the tiles into `_uiState` immediately, then fan the four heavier reads
 * (7-day bars, consistency calendar, per-Tasbih history, previous month) out
 * concurrently with `async` and patch them in as one update. So the screen
 * paints the tiles right away instead of waiting on the slowest query, and
 * the heavy reads overlap instead of running back to back.
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
            .distinctUntilChanged()
            .mapLatest { totals ->
                _uiState.value = _uiState.value.copy(
                    today = totals.today,
                    week = totals.week,
                    month = totals.month,
                    allTime = totals.allTime,
                    isEmpty = totals.allTime == 0,
                    loaded = true,
                )
                if (totals.allTime == 0) return@mapLatest

                coroutineScope {
                    val now = Calendar.getInstance()
                    val last7Days = async { repository.last7DaysTotals() }
                    val calendar = async {
                        repository.calendarIntensity(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
                    }
                    val history = async { repository.historyByTasbih() }
                    val previousMonth = async { repository.previousMonthSummary() }
                    _uiState.value = _uiState.value.copy(
                        last7Days = last7Days.await(),
                        calendarIntensity = calendar.await(),
                        historyByTasbih = history.await(),
                        previousMonth = previousMonth.await(),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private data class InsightsTotals(val today: Int, val week: Int, val month: Int, val allTime: Int)

    class Factory(private val repository: HistoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            InsightsViewModel(repository) as T
    }
}
