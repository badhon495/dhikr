package com.dhikr.app.feature.tasbih

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.database.entity.TasbihEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TasbihEditorUiState(
    val isEditingExisting: Boolean = false,
    val name: String = "",
    val arabic: String = "",
    val translation: String = "",
    val note: String = "",
    val lapTarget: Int = 33,
    val dailyGoal: Int? = null,
    val canSave: Boolean = false,
)

class TasbihEditorViewModel(
    private val repository: TasbihRepository,
    private val editingId: String? = null,
) : ViewModel() {

    private var loadedEntity: TasbihEntity? = null
    private val _uiState = MutableStateFlow(TasbihEditorUiState())
    val uiState: StateFlow<TasbihEditorUiState> = _uiState.asStateFlow()

    init {
        if (editingId != null) {
            viewModelScope.launch {
                repository.getById(editingId)?.let { entity ->
                    loadedEntity = entity
                    _uiState.value = TasbihEditorUiState(
                        isEditingExisting = true,
                        name = entity.name,
                        arabic = entity.arabic,
                        translation = entity.translation,
                        note = entity.note,
                        lapTarget = entity.lapTarget,
                        dailyGoal = entity.dailyGoal,
                        canSave = entity.name.isNotBlank(),
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) = update { it.copy(name = value, canSave = value.isNotBlank()) }
    fun onArabicChange(value: String) = update { it.copy(arabic = value) }
    fun onTranslationChange(value: String) = update { it.copy(translation = value) }
    fun onNoteChange(value: String) = update { it.copy(note = value) }
    fun onLapTargetChange(value: Int) = update { it.copy(lapTarget = value.coerceAtLeast(1)) }
    fun onDailyGoalChange(value: Int?) = update { it.copy(dailyGoal = value) }

    fun onSave(onSaved: () -> Unit) {
        val s = _uiState.value
        if (!s.canSave) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = loadedEntity
            if (existing != null) {
                repository.update(
                    existing.copy(
                        name = s.name,
                        arabic = s.arabic,
                        translation = s.translation,
                        note = s.note,
                        lapTarget = s.lapTarget,
                        dailyGoal = s.dailyGoal,
                        updatedAt = now,
                    )
                )
            } else {
                repository.insert(
                    TasbihEntity(
                        id = repository.newId(),
                        name = s.name,
                        arabic = s.arabic,
                        transliteration = "",
                        translation = s.translation,
                        note = s.note,
                        lapTarget = s.lapTarget,
                        lapCount = 1,
                        dailyGoal = s.dailyGoal,
                        isBuiltIn = false,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
            onSaved()
        }
    }

    private inline fun update(block: (TasbihEditorUiState) -> TasbihEditorUiState) {
        _uiState.value = block(_uiState.value)
    }

    class Factory(
        private val repository: TasbihRepository,
        private val editingId: String? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TasbihEditorViewModel(repository, editingId) as T
    }
}
