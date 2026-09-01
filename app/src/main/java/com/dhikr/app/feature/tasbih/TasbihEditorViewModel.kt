package com.dhikr.app.feature.tasbih

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.database.entity.TasbihEntity
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.datastore.CounterScript
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class TasbihEditorUiState(
    val isEditingExisting: Boolean = false,
    val name: String = "",
    val arabic: String = "",
    val pronunciation: String = "",
    val translation: String = "",
    val note: String = "",
    val lapTarget: Int = 33,
    val dailyGoal: Int? = null,
    // The counter-script preference. Whichever script it names is the field the
    // editor requires before a save is allowed.
    val requiredScript: CounterScript = CounterScript.PRONUNCIATION,
    val canSave: Boolean = false,
) {
    val arabicRequired: Boolean get() = requiredScript == CounterScript.ARABIC
    val pronunciationRequired: Boolean get() = requiredScript == CounterScript.PRONUNCIATION
}

class TasbihEditorViewModel(
    private val repository: TasbihRepository,
    private val preferencesRepository: AppPreferencesRepository,
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
                    update {
                        it.copy(
                            isEditingExisting = true,
                            name = entity.name,
                            arabic = entity.arabic,
                            pronunciation = entity.pronunciation,
                            translation = entity.translation,
                            note = entity.note,
                            lapTarget = entity.lapTarget,
                            dailyGoal = entity.dailyGoal,
                        )
                    }
                    recomputeCanSave()
                }
            }
        }
        preferencesRepository.counterScript
            .onEach { script ->
                update { it.copy(requiredScript = script) }
                recomputeCanSave()
            }
            .launchIn(viewModelScope)
    }

    fun onNameChange(value: String) = updateField { it.copy(name = value) }
    fun onArabicChange(value: String) = updateField { it.copy(arabic = value) }
    fun onPronunciationChange(value: String) = updateField { it.copy(pronunciation = value) }
    fun onTranslationChange(value: String) = updateField { it.copy(translation = value) }
    fun onNoteChange(value: String) = updateField { it.copy(note = value) }
    fun onLapTargetChange(value: Int) = updateField { it.copy(lapTarget = value.coerceAtLeast(1)) }
    fun onDailyGoalChange(value: Int?) = updateField { it.copy(dailyGoal = value) }

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
                        pronunciation = s.pronunciation,
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
                        pronunciation = s.pronunciation,
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

    private inline fun updateField(block: (TasbihEditorUiState) -> TasbihEditorUiState) {
        update(block)
        recomputeCanSave()
    }

    private fun recomputeCanSave() {
        update { s ->
            val scriptFilled = when (s.requiredScript) {
                CounterScript.ARABIC -> s.arabic.isNotBlank()
                CounterScript.PRONUNCIATION -> s.pronunciation.isNotBlank()
            }
            s.copy(canSave = s.name.isNotBlank() && scriptFilled)
        }
    }

    private inline fun update(block: (TasbihEditorUiState) -> TasbihEditorUiState) {
        _uiState.value = block(_uiState.value)
    }

    class Factory(
        private val repository: TasbihRepository,
        private val preferencesRepository: AppPreferencesRepository,
        private val editingId: String? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TasbihEditorViewModel(repository, preferencesRepository, editingId) as T
    }
}
