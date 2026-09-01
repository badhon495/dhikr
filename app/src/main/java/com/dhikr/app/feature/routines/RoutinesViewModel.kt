package com.dhikr.app.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.RoutineRepository
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.database.dao.RoutineWithSteps
import com.dhikr.app.core.database.entity.RoutineEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutinesUiState(
    val query: String = "",
    // The full routine list is small, so search filters it in memory by name
    // rather than re-querying the DAO the way the Tasbih library does.
    val routines: List<RoutineWithSteps> = emptyList(),
    val totalCount: Int = 0,
    val builtInCount: Int = 0,
    val customCount: Int = 0,
    // tasbihId -> display name, so step rows can show the real Tasbih name
    // instead of the raw id RoutineStepEntity stores.
    val tasbihNamesById: Map<String, String> = emptyMap(),
    // Ids of routines whose last step was completed today (local time). Their
    // cards render with a sage tint; the set clears on its own the next day.
    val completedTodayIds: Set<String> = emptySet(),
)

class RoutinesViewModel(
    private val repository: RoutineRepository,
    private val tasbihRepository: TasbihRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<RoutinesUiState> = combine(
        query,
        repository.observeAllWithSteps(),
        tasbihRepository.observeAll(),
        repository.observeCompletedToday(),
    ) { q, routines, tasbihs, completedToday ->
        val filtered = if (q.isBlank()) {
            routines
        } else {
            routines.filter { it.routine.name.contains(q.trim(), ignoreCase = true) }
        }
        RoutinesUiState(
            query = q,
            routines = filtered,
            totalCount = routines.size,
            builtInCount = routines.count { it.routine.isPreset },
            customCount = routines.count { !it.routine.isPreset },
            tasbihNamesById = tasbihs.associate { it.id to it.name },
            completedTodayIds = completedToday,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RoutinesUiState())

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    fun onDeleteRoutine(routine: RoutineEntity) {
        viewModelScope.launch { repository.deleteRoutine(routine) }
    }

    fun onReorderSteps(routineId: String, steps: List<Pair<String, Int>>) {
        viewModelScope.launch { repository.updateSteps(routineId, steps) }
    }

    class Factory(
        private val repository: RoutineRepository,
        private val tasbihRepository: TasbihRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RoutinesViewModel(repository, tasbihRepository) as T
    }
}
