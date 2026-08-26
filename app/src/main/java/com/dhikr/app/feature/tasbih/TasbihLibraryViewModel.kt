package com.dhikr.app.feature.tasbih

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.DeleteResult
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.database.entity.TasbihEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TasbihLibraryUiState(
    val query: String = "",
    val results: List<TasbihEntity> = emptyList(),
    val builtInCount: Int = 0,
    val customCount: Int = 0,
)

/** One-shot event for TasbihLibraryScreen to show when a delete is blocked
 * by an existing routine reference (finding #4) — modeled as a SharedFlow
 * rather than UI state so it fires exactly once per delete attempt instead
 * of persisting across recomposition/rotation. */
data class TasbihDeleteBlocked(val tasbihName: String, val routineNames: List<String>)

/**
 * Search re-queries the DAO (via `flatMapLatest`) rather than filtering an
 * in-memory list, so it stays correct as the Tasbih table grows past what's
 * comfortable to hold client-side. `flatMapLatest` on the query StateFlow
 * cancels any in-flight search Flow the moment the query changes, so rapid
 * typing never fans out one live query per keystroke — the same
 * responsive-but-not-wasteful principle as the Counter screen's persistence
 * debounce from Phase 1+2, just expressed as flow-switching instead of
 * `debounce()` since here we want the LATEST query's results immediately, not
 * a delayed settle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TasbihLibraryViewModel(private val repository: TasbihRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<TasbihLibraryUiState> = combine(
        query,
        query.flatMapLatest { q ->
            if (q.isBlank()) repository.observeAll() else repository.search(q)
        },
        repository.observeAll(), // stable built-in/custom counts, independent of the filter
    ) { q, results, all ->
        TasbihLibraryUiState(
            query = q,
            results = results,
            builtInCount = all.count { it.isBuiltIn },
            customCount = all.count { !it.isBuiltIn },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TasbihLibraryUiState(),
    )

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    fun onToggleFavorite(id: String, currentlyFavorite: Boolean) {
        viewModelScope.launch { repository.toggleFavorite(id, currentlyFavorite) }
    }

    private val _deleteBlocked = MutableSharedFlow<TasbihDeleteBlocked>(extraBufferCapacity = 1)
    val deleteBlocked: SharedFlow<TasbihDeleteBlocked> = _deleteBlocked

    /**
     * Deletes a custom (non-built-in) Tasbih (finding #4). Callers must not
     * invoke this for a built-in Tasbih — TasbihLibraryScreen guards the
     * long-press menu on `!isBuiltIn` before this is ever reachable, matching
     * the repository's own FK-RESTRICT check for routine references.
     */
    fun onDeleteTasbih(tasbih: TasbihEntity) {
        viewModelScope.launch {
            when (val result = repository.delete(tasbih)) {
                is DeleteResult.Success -> Unit // observeAll()/search() Flows update the list automatically
                is DeleteResult.BlockedByRoutines -> {
                    _deleteBlocked.tryEmit(TasbihDeleteBlocked(tasbih.name, result.routineNames))
                }
            }
        }
    }

    class Factory(private val repository: TasbihRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TasbihLibraryViewModel(repository) as T
    }
}
