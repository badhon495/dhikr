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

data class AllDhikrUiState(
    val query: String = "",
    val groups: List<TasbihHistoryGroup> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Filters [all] by [query] against the Dhikr name: case-insensitive substring,
 * query trimmed first. A blank query returns the list unchanged. Match order
 * follows the input order (already lifetime-total descending from the repo).
 */
fun filterDhikrGroups(all: List<TasbihHistoryGroup>, query: String): List<TasbihHistoryGroup> {
    val needle = query.trim()
    if (needle.isEmpty()) return all
    return all.filter { it.tasbihName.contains(needle, ignoreCase = true) }
}

/**
 * One-shot read of every Dhikr's history (same source as the Insights preview,
 * which caps the same list at 3). Like [MonthlyHistoryViewModel] this screen is
 * not kept alive in the bottom-nav back stack, so a plain suspend read in init
 * is enough. The search query is held in UI state and re-filters in memory.
 */
class AllDhikrViewModel(private val repository: HistoryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AllDhikrUiState())
    val uiState: StateFlow<AllDhikrUiState> = _uiState.asStateFlow()

    private var allGroups: List<TasbihHistoryGroup> = emptyList()

    init {
        viewModelScope.launch {
            allGroups = repository.historyByTasbih()
            _uiState.value = _uiState.value.copy(
                groups = filterDhikrGroups(allGroups, _uiState.value.query),
                isLoading = false,
            )
        }
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(
            query = query,
            groups = filterDhikrGroups(allGroups, query),
        )
    }

    class Factory(private val repository: HistoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AllDhikrViewModel(repository) as T
    }
}
