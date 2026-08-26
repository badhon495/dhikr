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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        viewModelScope.launch {
            val dailyGoal = preferencesRepository.dailyGoalTarget.first()
            val todayTotal = historyRepository.todayTotal()
            val favorites = tasbihRepository.observeFavorites().first()
            val routines = routineRepository.observeAllWithSteps().first().take(3)
            val session = sessionRepository.sessionFlow.first()
            val continueInfo = session?.let { s ->
                tasbihRepository.getById(s.activeDhikrId)?.let { tasbih ->
                    ContinueSessionInfo(tasbihName = tasbih.name, count = s.count, target = tasbih.lapTarget)
                }
            }
            _uiState.value = HomeUiState(
                dateLabel = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()),
                dailyGoalTarget = dailyGoal,
                todayTotal = todayTotal,
                continueSession = continueInfo,
                favorites = favorites,
                routines = routines,
            )
        }
    }

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
