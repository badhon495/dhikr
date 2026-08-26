package com.dhikr.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dhikr.app.core.database.HistoryRepository
import com.dhikr.app.core.database.RoutineRepository
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.datastore.SessionRepository
import com.dhikr.app.feature.counter.CounterScreen
import com.dhikr.app.feature.counter.CounterViewModel
import com.dhikr.app.feature.home.HomeScreen
import com.dhikr.app.feature.home.HomeViewModel
import com.dhikr.app.ui.theme.DhikrTheme

private const val ROUTE_HOME = "home"
private const val ROUTE_COUNTER = "counter"

@Composable
fun DhikrApp() {
    DhikrTheme {
        val navController = rememberNavController()
        val context = LocalContext.current
        val sessionRepository = remember(context) {
            SessionRepository(context.applicationContext)
        }
        val tasbihRepository = remember(context) {
            val database = (context.applicationContext as DhikrApplication).database
            TasbihRepository(database.tasbihDao(), database.routineDao())
        }
        val routineRepository = remember(context) {
            val database = (context.applicationContext as DhikrApplication).database
            RoutineRepository(database.routineDao())
        }
        val historyRepository = remember(context) {
            val database = (context.applicationContext as DhikrApplication).database
            HistoryRepository(database.sessionDao(), tasbihRepository)
        }
        val preferencesRepository = remember(context) {
            AppPreferencesRepository(context.applicationContext)
        }

        NavHost(navController = navController, startDestination = ROUTE_HOME) {
            composable(ROUTE_HOME) {
                // Minimal wiring for Task 13; full nav wiring (favourites, routines,
                // library, continue-session) lands in Task 14.
                val viewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.Factory(
                        tasbihRepository = tasbihRepository,
                        routineRepository = routineRepository,
                        historyRepository = historyRepository,
                        sessionRepository = sessionRepository,
                        preferencesRepository = preferencesRepository,
                    ),
                )
                HomeScreen(
                    viewModel = viewModel,
                    onContinueSession = { navController.navigate(ROUTE_COUNTER) },
                    onOpenTasbih = {},
                    onOpenLibrary = {},
                    onStartRoutine = {},
                    onOpenRoutines = {},
                )
            }
            composable(ROUTE_COUNTER) {
                val viewModel: CounterViewModel = viewModel(
                    factory = CounterViewModel.Factory(
                        sessionRepository = sessionRepository,
                        tasbihRepository = tasbihRepository,
                        routineRepository = routineRepository,
                        startingDhikrId = "subhan",
                        historyRepository = historyRepository,
                    ),
                )
                CounterScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
