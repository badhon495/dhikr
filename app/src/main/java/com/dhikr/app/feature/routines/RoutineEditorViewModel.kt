package com.dhikr.app.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.RoutineRepository
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.database.entity.TasbihEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One step being edited: which Tasbih, and its target count for this routine. */
data class StepDraft(val tasbihId: String, val targetCount: Int)

data class RoutineEditorUiState(
    val name: String = "",
    val steps: List<StepDraft> = emptyList(),
    // Every Tasbih the user can add as a step (built-in + custom).
    val availableTasbih: List<TasbihEntity> = emptyList(),
    val tasbihNamesById: Map<String, String> = emptyMap(),
    val canSave: Boolean = false,
    // True when editing an existing routine (preset or custom) rather than creating one.
    val isEditing: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderMinuteOfDay: Int = 8 * 60, // default 08:00
    val reminderDays: Int = 0, // 0 = every day
)

/**
 * Backs the routine editor screen. Kept deliberately small: name + an ordered
 * list of steps, each a (Tasbih, count) pair, with add / remove / reorder /
 * set-count. When [editingRoutineId] is non-null the screen loads that routine
 * (preset or custom) and saving replaces it in place; otherwise it creates a
 * new one.
 */
class RoutineEditorViewModel(
    private val routineRepository: RoutineRepository,
    private val tasbihRepository: TasbihRepository,
    private val editingRoutineId: String? = null,
    private val reminderScheduler: com.dhikr.app.core.notifications.ReminderScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoutineEditorUiState())
    val uiState: StateFlow<RoutineEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val all = tasbihRepository.observeAll().first()
            update {
                it.copy(
                    availableTasbih = all,
                    tasbihNamesById = all.associate { t -> t.id to t.name },
                )
            }
            if (editingRoutineId != null) {
                val existing = routineRepository.getWithSteps(editingRoutineId)
                if (existing != null) {
                    update { state ->
                        state.copy(
                            name = existing.routine.name,
                            steps = existing.steps
                                .sortedBy { s -> s.stepOrder }
                                .map { s -> StepDraft(s.tasbihId, s.targetCount) },
                            isEditing = true,
                            reminderEnabled = existing.routine.reminderEnabled,
                            reminderMinuteOfDay = existing.routine.reminderMinuteOfDay,
                            reminderDays = existing.routine.reminderDays,
                        ).withCanSave()
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = update { it.copy(name = value).withCanSave() }

    fun onReminderEnabledChange(value: Boolean) = update { it.copy(reminderEnabled = value) }

    fun onReminderTimeChange(minuteOfDay: Int) =
        update { it.copy(reminderMinuteOfDay = minuteOfDay.coerceIn(0, 24 * 60 - 1)) }

    fun onReminderDayToggle(dayBit: Int) = update { state ->
        state.copy(reminderDays = state.reminderDays xor (1 shl dayBit))
    }

    fun onAddStep(tasbihId: String) = update { state ->
        val defaultCount = state.availableTasbih.firstOrNull { it.id == tasbihId }?.lapTarget ?: 33
        state.copy(steps = state.steps + StepDraft(tasbihId, defaultCount)).withCanSave()
    }

    fun onRemoveStep(index: Int) = update { state ->
        state.copy(steps = state.steps.filterIndexed { i, _ -> i != index }).withCanSave()
    }

    fun onStepCountChange(index: Int, count: Int) = update { state ->
        state.copy(
            steps = state.steps.mapIndexed { i, step ->
                if (i == index) step.copy(targetCount = count.coerceAtLeast(1)) else step
            },
        ).withCanSave()
    }

    fun onMoveStep(index: Int, up: Boolean) = update { state ->
        val target = if (up) index - 1 else index + 1
        if (target !in state.steps.indices) return@update state
        val reordered = state.steps.toMutableList()
        val moved = reordered.removeAt(index)
        reordered.add(target, moved)
        state.copy(steps = reordered)
    }

    fun onSave(onSaved: (String) -> Unit) {
        val s = _uiState.value
        if (!s.canSave) return
        viewModelScope.launch {
            val steps = s.steps.map { it.tasbihId to it.targetCount }
            val id = if (editingRoutineId != null) {
                routineRepository.updateRoutine(editingRoutineId, s.name.trim(), steps)
                editingRoutineId
            } else {
                routineRepository.createRoutine(name = s.name.trim(), steps = steps)
            }
            val daysMask = s.reminderDays and 0x7F
            routineRepository.setReminder(id, s.reminderEnabled, s.reminderMinuteOfDay, daysMask)
            if (s.reminderEnabled) {
                reminderScheduler.schedule(id, s.reminderMinuteOfDay, daysMask)
            } else {
                reminderScheduler.cancel(id)
            }
            onSaved(id)
        }
    }

    private fun RoutineEditorUiState.withCanSave(): RoutineEditorUiState =
        copy(canSave = name.isNotBlank() && steps.isNotEmpty())

    private inline fun update(block: (RoutineEditorUiState) -> RoutineEditorUiState) {
        _uiState.value = block(_uiState.value)
    }

    class Factory(
        private val routineRepository: RoutineRepository,
        private val tasbihRepository: TasbihRepository,
        private val editingRoutineId: String? = null,
        private val reminderScheduler: com.dhikr.app.core.notifications.ReminderScheduler,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RoutineEditorViewModel(routineRepository, tasbihRepository, editingRoutineId, reminderScheduler) as T
    }
}
