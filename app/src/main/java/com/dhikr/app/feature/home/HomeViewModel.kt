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
    // tasbihId -> display name, for the routine cards' step preview.
    val tasbihNamesById: Map<String, String> = emptyMap(),
    // Routines completed today — their Home card shows the full sage tint.
    val completedRoutineIds: Set<String> = emptySet(),
    // routineId -> 0f..1f of today's in-progress position; drives the card's
    // green fill. Both clear on their own the next day.
    val routineProgress: Map<String, Float> = emptyMap(),
    // tasbihId -> 0f..1f of today's counting position toward its total goal;
    // drives the favourite row's green fill. Clears on its own the next day.
    val tasbihProgress: Map<String, Float> = emptyMap(),
    // False for the synthetic default emitted before the Room/DataStore combine
    // produces its first value. The routines/favourites sections stay unpainted
    // until this is true so their "nothing here yet" hint doesn't flash for a
    // frame before the real lists arrive.
    val loaded: Boolean = false,
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
            .combine(routineRepository.observeDayProgress()) { inputs, dayProgress ->
                inputs to dayProgress
            }
            .combine(tasbihRepository.observeSessionProgressToday()) { (inputs, dayProgress), tasbihProgress ->
                Triple(inputs, dayProgress, tasbihProgress)
            }
            .combine(tasbihRepository.observeAll()) { (inputs, dayProgress, tasbihProgress), allTasbihs ->
                HomeCombined(inputs, dayProgress, tasbihProgress, allTasbihs.associate { it.id to it.name })
            }
            .mapLatest { (inputs, dayProgress, tasbihProgress, tasbihNamesById) ->
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
                    tasbihNamesById = tasbihNamesById,
                    // Home shows exactly the favorited routines. Unstarring the
                    // last one on the Routines page clears the section (Home then
                    // renders a hint). Full-width card list, so no cap.
                    routines = inputs.routines.filter { it.routine.isFavorite },
                    completedRoutineIds = dayProgress.completedRoutineIds,
                    routineProgress = dayProgress.fractionByRoutineId,
                    tasbihProgress = tasbihProgress,
                    loaded = true,
                )
            }
            .onEach { _uiState.value = it }
            .launchIn(viewModelScope)
    }

    private data class HomeCombined(
        val inputs: HomeInputs,
        val dayProgress: com.dhikr.app.core.database.RoutineDayProgress,
        val tasbihProgress: Map<String, Float>,
        val tasbihNamesById: Map<String, String>,
    )

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
