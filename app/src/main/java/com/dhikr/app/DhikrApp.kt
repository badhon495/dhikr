package com.dhikr.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dhikr.app.core.database.HistoryRepository
import com.dhikr.app.core.database.RoutineRepository
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.datastore.SessionRepository
import com.dhikr.app.feature.counter.CounterScreen
import com.dhikr.app.feature.counter.CounterViewModel
import com.dhikr.app.feature.home.HomeScreen
import com.dhikr.app.feature.home.HomeViewModel
import com.dhikr.app.feature.insights.InsightsScreen
import com.dhikr.app.feature.insights.InsightsViewModel
import com.dhikr.app.feature.routines.RoutinesScreen
import com.dhikr.app.feature.routines.RoutinesViewModel
import com.dhikr.app.feature.tasbih.TasbihEditorScreen
import com.dhikr.app.feature.tasbih.TasbihEditorViewModel
import com.dhikr.app.feature.tasbih.TasbihLibraryScreen
import com.dhikr.app.feature.tasbih.TasbihLibraryViewModel
import com.dhikr.app.ui.theme.DhikrTheme

private const val ROUTE_HOME = "home"
private const val ROUTE_TASBIH_LIBRARY = "tasbih"
private const val ROUTE_TASBIH_EDITOR = "tasbih/editor?id={id}"
private const val ROUTE_COUNTER = "counter?dhikrId={dhikrId}&routineId={routineId}"
private const val ROUTE_INSIGHTS = "insights"
private const val ROUTE_ROUTINES = "routines"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun DhikrApp() {
    DhikrTheme {
        val navController = rememberNavController()
        val context = LocalContext.current
        val app = context.applicationContext as DhikrApplication

        val sessionRepository = remember { SessionRepository(context.applicationContext) }
        val tasbihRepository = remember { TasbihRepository(app.database.tasbihDao(), app.database.routineDao()) }
        val routineRepository = remember { RoutineRepository(app.database.routineDao()) }
        val historyRepository = remember { HistoryRepository(app.database.sessionDao(), tasbihRepository) }
        val preferencesRepository = remember { AppPreferencesRepository(context.applicationContext) }

        Scaffold(
            bottomBar = { DhikrBottomNav(navController) },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = ROUTE_HOME,
                modifier = Modifier.padding(padding),
            ) {
                composable(ROUTE_HOME) {
                    val viewModel: HomeViewModel = viewModel(
                        factory = HomeViewModel.Factory(tasbihRepository, routineRepository, historyRepository, sessionRepository, preferencesRepository),
                    )
                    HomeScreen(
                        viewModel = viewModel,
                        onContinueSession = { navController.navigate("counter") },
                        onOpenTasbih = { id -> navController.navigate("counter?dhikrId=$id") },
                        onOpenLibrary = { navController.navigate(ROUTE_TASBIH_LIBRARY) },
                        onStartRoutine = { id -> navController.navigate("counter?routineId=$id") },
                        onOpenRoutines = { navController.navigate(ROUTE_ROUTINES) },
                    )
                }
                composable(ROUTE_TASBIH_LIBRARY) {
                    val viewModel: TasbihLibraryViewModel = viewModel(
                        factory = TasbihLibraryViewModel.Factory(tasbihRepository),
                    )
                    TasbihLibraryScreen(
                        viewModel = viewModel,
                        onOpenTasbih = { id -> navController.navigate("counter?dhikrId=$id") },
                        onNewTasbih = { navController.navigate("tasbih/editor") },
                    )
                }
                composable(
                    ROUTE_TASBIH_EDITOR,
                    arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { backStackEntry ->
                    val editingId = backStackEntry.arguments?.getString("id")
                    val viewModel: TasbihEditorViewModel = viewModel(
                        factory = TasbihEditorViewModel.Factory(tasbihRepository, editingId),
                    )
                    TasbihEditorScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
                composable(
                    ROUTE_COUNTER,
                    arguments = listOf(
                        navArgument("dhikrId") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("routineId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { backStackEntry ->
                    val dhikrId = backStackEntry.arguments?.getString("dhikrId")
                    val routineId = backStackEntry.arguments?.getString("routineId")
                    val viewModel: CounterViewModel = viewModel(
                        factory = CounterViewModel.Factory(
                            sessionRepository, tasbihRepository, routineRepository,
                            dhikrId, routineId, historyRepository,
                        ),
                    )
                    CounterScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
                composable(ROUTE_INSIGHTS) {
                    val viewModel: InsightsViewModel = viewModel(
                        factory = InsightsViewModel.Factory(historyRepository),
                    )
                    InsightsScreen(viewModel = viewModel, onStartCounting = { navController.navigate("counter") })
                }
                composable(ROUTE_ROUTINES) {
                    val viewModel: RoutinesViewModel = viewModel(
                        factory = RoutinesViewModel.Factory(routineRepository, tasbihRepository),
                    )
                    RoutinesScreen(
                        viewModel = viewModel,
                        onStartRoutine = { id -> navController.navigate("counter?routineId=$id") },
                    )
                }
                composable(ROUTE_SETTINGS) {
                    SettingsStub()
                }
            }
        }
    }
}

@Composable
private fun SettingsStub() {
    val colors = DhikrTheme.colors
    Text("Settings", modifier = Modifier.background(colors.bg).padding(24.dp), color = colors.text)
}

@Composable
private fun DhikrBottomNav(navController: NavController) {
    val colors = DhikrTheme.colors
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Placeholder system icons — see task-14 brief: a future pass should
    // replace these with proper Lucide-style stroke vectors matching
    // CounterIcons.kt's pattern for visual consistency with the rest of the app.
    val items = listOf(
        Triple(ROUTE_HOME, "Home", android.R.drawable.ic_menu_myplaces),
        Triple(ROUTE_TASBIH_LIBRARY, "Tasbih", android.R.drawable.ic_menu_agenda),
        Triple(ROUTE_COUNTER, "Count", android.R.drawable.ic_menu_add),
        Triple(ROUTE_INSIGHTS, "Insights", android.R.drawable.ic_menu_sort_by_size),
        Triple(ROUTE_SETTINGS, "Settings", android.R.drawable.ic_menu_preferences),
    )

    NavigationBar(containerColor = colors.surface) {
        items.forEach { (route, label, iconRes) ->
            val selected = currentRoute?.startsWith(route.substringBefore("?")) == true
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) navController.navigate(route.substringBefore("?")) },
                icon = { Icon(painterResource(iconRes), contentDescription = label) },
                label = { Text(label, fontSize = 10.5.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.text,
                    selectedTextColor = colors.text,
                    indicatorColor = colors.sageSoft,
                    unselectedIconColor = colors.faint,
                    unselectedTextColor = colors.faint,
                ),
            )
        }
    }
}
