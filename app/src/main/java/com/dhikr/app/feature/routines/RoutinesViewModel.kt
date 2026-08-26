package com.dhikr.app.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.RoutineRepository
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.database.dao.RoutineWithSteps
import com.dhikr.app.core.database.entity.RoutineEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutinesUiState(
    val routines: List<RoutineWithSteps> = emptyList(),
    // tasbihId -> display name, so step rows can show the real Tasbih name
    // instead of the raw id RoutineStepEntity stores.
    val tasbihNamesById: Map<String, String> = emptyMap(),
)

class RoutinesViewModel(
    private val repository: RoutineRepository,
    private val tasbihRepository: TasbihRepository,
) : ViewModel() {

    val uiState: StateFlow<RoutinesUiState> = combine(
        repository.observeAllWithSteps(),
        tasbihRepository.observeAll(),
    ) { routines, tasbihs ->
        RoutinesUiState(
            routines = routines,
            tasbihNamesById = tasbihs.associate { it.id to it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RoutinesUiState())

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
