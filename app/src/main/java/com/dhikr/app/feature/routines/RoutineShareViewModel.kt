package com.dhikr.app.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.RoutineRepository
import com.dhikr.app.core.share.RoutineShareCodec
import com.dhikr.app.core.share.RoutineShareRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Drives the "Share routines" checklist + payload build. The screen owns every
 * Context / Uri / Intent / clipboard touch and this ViewModel emits plain
 * strings — same split as [com.dhikr.app.feature.settings.BackupViewModel].
 */
class RoutineShareViewModel(
    private val shareRepository: RoutineShareRepository,
    private val routineRepository: RoutineRepository,
    private val codec: RoutineShareCodec,
    private val appVersionName: String,
) : ViewModel() {

    data class Selectable(
        val id: String,
        val name: String,
        val isPreset: Boolean,
        val checked: Boolean,
    )

    data class SharePayload(
        val fileText: String,
        val clipboardText: String,
        val suggestedFileName: String,
    )

    sealed interface Status {
        data object Idle : Status
        data object Working : Status
        data class Ready(val payload: SharePayload) : Status
        data class Error(val message: String) : Status
    }

    private val _selectable = MutableStateFlow<List<Selectable>>(emptyList())
    val selectable: StateFlow<List<Selectable>> = _selectable.asStateFlow()

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Loads the routine list with [preselectId] pre-checked. Safe to call again
     *  when the dialog reopens. */
    fun open(preselectId: String) {
        _status.value = Status.Idle
        viewModelScope.launch {
            val routines = routineRepository.observeAllWithSteps().first()
            _selectable.value = routines
                .sortedBy { it.routine.name.lowercase(Locale.getDefault()) }
                .map {
                    Selectable(
                        id = it.routine.id,
                        name = it.routine.name,
                        isPreset = it.routine.isPreset,
                        checked = it.routine.id == preselectId,
                    )
                }
        }
    }

    fun toggle(id: String) {
        _selectable.update { list ->
            list.map { if (it.id == id) it.copy(checked = !it.checked) else it }
        }
    }

    fun setAll(checked: Boolean) {
        _selectable.update { list -> list.map { it.copy(checked = checked) } }
    }

    fun dismiss() {
        _selectable.value = emptyList()
        _status.value = Status.Idle
    }

    fun buildPayload() {
        val checked = _selectable.value.filter { it.checked }
        if (checked.isEmpty() || _status.value == Status.Working) return
        _status.value = Status.Working
        viewModelScope.launch {
            _status.value = try {
                withContext(Dispatchers.IO) {
                    val file = shareRepository.buildShare(checked.map { it.id }, appVersionName)
                    val name = if (file.routines.size == 1) {
                        slug(file.routines.first().name) + ".dhikrroutine"
                    } else {
                        "dhikr-routines-" +
                            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) +
                            ".dhikrroutine"
                    }
                    Status.Ready(
                        SharePayload(
                            fileText = codec.encodeFile(file),
                            clipboardText = codec.encodeText(file),
                            suggestedFileName = name,
                        ),
                    )
                }
            } catch (e: Exception) {
                Status.Error("Couldn't prepare the routines to share.")
            }
        }
    }

    private fun slug(raw: String): String {
        val cleaned = raw.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return cleaned.ifEmpty { "routine" }
    }

    class Factory(
        private val shareRepository: RoutineShareRepository,
        private val routineRepository: RoutineRepository,
        private val codec: RoutineShareCodec,
        private val appVersionName: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RoutineShareViewModel(shareRepository, routineRepository, codec, appVersionName) as T
    }
}
