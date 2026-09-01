package com.dhikr.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.HistoryRepository
import com.dhikr.app.core.database.RoutineRepository
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.database.dao.RoutineWithSteps
import com.dhikr.app.core.database.entity.TasbihEntity
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.datastore.SessionRepository
import com.dhikr.app.core.model.CounterSessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ContinueSessionInfo(val tasbihName: String, val count: Int, val target: Int)

data class HomeUiState(
    val dateLabel: String = "",
    val dailyGoalTarget: Int = 100,
    val todayTotal: Int = 0,
    val continueSession: ContinueSessionInfo? = null,
    val favorites: List<TasbihEntity> = emptyList(),
    val routines: List<RoutineWithSteps> = emptyList(),
)

/**
 * Every input here is now a live Flow — `historyRepository.todayTotalFlow()`
 * (Room-backed, invalidates on every `session` insert), DataStore's
 * `dailyGoalTarget`/`sessionFlow`, and Room's `observeFavorites()` /
 * `observeAllWithSteps()` — combined and re-collected reactively instead of
 * read once in init (finding #6). Because the bottom nav keeps this
 * ViewModel alive across Home → Counter → Home navigation, a one-shot read
 * would otherwise go stale until the ViewModel is recreated.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val tasbihRepository: TasbihRepository,
    private val routineRepository: RoutineRepository,
    private val historyRepository: HistoryRepository,
    private val sessionRepository: SessionRepository,
    private val preferencesRepository: AppPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        combine(
            preferencesRepository.dailyGoalTarget,
            historyRepository.todayTotalFlow(),
            tasbihRepository.observeFavorites(),
            routineRepository.observeAllWithSteps(),
            sessionRepository.sessionFlow,
        ) { dailyGoal, todayTotal, favorites, routines, session ->
            HomeInputs(dailyGoal, todayTotal, favorites, routines, session)
        }
            .mapLatest { inputs ->
                val continueInfo = inputs.session?.let { s ->
                    tasbihRepository.getById(s.activeDhikrId)?.let { tasbih ->
                        ContinueSessionInfo(tasbihName = tasbih.name, count = s.count, target = tasbih.lapTarget)
                    }
                }
                HomeUiState(
                    dateLabel = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()),
                    dailyGoalTarget = inputs.dailyGoal,
                    todayTotal = inputs.todayTotal,
                    continueSession = continueInfo,
                    favorites = inputs.favorites,
                    // Favorited routines if the user has marked any; otherwise
                    // fall back to the first few so the section is never empty.
                    // Capped so the home Row layout (weight-split cards) stays legible.
                    routines = inputs.routines
                        .filter { it.routine.isFavorite }
                        .ifEmpty { inputs.routines }
                        .take(4),
                )
            }
            .onEach { _uiState.value = it }
            .launchIn(viewModelScope)
    }

    private data class HomeInputs(
        val dailyGoal: Int,
        val todayTotal: Int,
        val favorites: List<TasbihEntity>,
        val routines: List<RoutineWithSteps>,
        val session: CounterSessionState?,
    )

    class Factory(
        private val tasbihRepository: TasbihRepository,
        private val routineRepository: RoutineRepository,
        private val historyRepository: HistoryRepository,
        private val sessionRepository: SessionRepository,
        private val preferencesRepository: AppPreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(tasbihRepository, routineRepository, historyRepository, sessionRepository, preferencesRepository) as T
    }
}
