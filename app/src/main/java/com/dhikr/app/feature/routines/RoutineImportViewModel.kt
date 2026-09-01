package com.dhikr.app.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.share.ImportPreview
import com.dhikr.app.core.share.RoutineShareRepository
import com.dhikr.app.core.share.ShareFormatException
import com.dhikr.app.core.share.ShareImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State machine for the import-preview screen:
 * `Loading -> Preview -> Working -> Done` / `Error`. [load] parses only (no DB
 * writes); [confirm] runs the single-transaction import.
 */
class RoutineImportViewModel(
    private val repository: RoutineShareRepository,
) : ViewModel() {

    sealed interface State {
        data object Loading : State
        data class Preview(val preview: ImportPreview) : State
        data object Working : State
        data class Done(val result: ShareImportResult) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private var payload: String? = null
    private var started = false

    fun load(readText: suspend () -> String) {
        if (started) return
        started = true
        viewModelScope.launch {
            _state.value = try {
                val raw = withContext(Dispatchers.IO) { readText() }
                payload = raw
                State.Preview(withContext(Dispatchers.IO) { repository.preview(raw) })
            } catch (e: ShareFormatException) {
                State.Error(e.message ?: "This isn't a Dhikr routine file.")
            } catch (e: Exception) {
                State.Error("Couldn't read that file.")
            }
        }
    }

    fun confirm() {
        val raw = payload ?: return
        if (_state.value == State.Working) return
        _state.value = State.Working
        viewModelScope.launch {
            _state.value = try {
                State.Done(withContext(Dispatchers.IO) { repository.import(raw) })
            } catch (e: ShareFormatException) {
                State.Error(e.message ?: "This shared file is incomplete.")
            } catch (e: Exception) {
                State.Error("Import failed. Your routines haven't changed.")
            }
        }
    }

    class Factory(
        private val repository: RoutineShareRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RoutineImportViewModel(repository) as T
    }
}
