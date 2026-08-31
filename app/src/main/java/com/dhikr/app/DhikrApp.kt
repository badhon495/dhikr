package com.dhikr.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dhikr.app.R
import com.dhikr.app.core.database.HistoryRepository
import com.dhikr.app.core.database.RoutineRepository
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.datastore.SessionRepository
import com.dhikr.app.core.datastore.ThemeMode
import com.dhikr.app.feature.counter.CounterScreen
import com.dhikr.app.feature.counter.CounterViewModel
import com.dhikr.app.feature.home.HomeScreen
import com.dhikr.app.feature.home.HomeViewModel
import com.dhikr.app.feature.insights.InsightsScreen
import com.dhikr.app.feature.insights.InsightsViewModel
import com.dhikr.app.feature.routines.RoutinesScreen
import com.dhikr.app.feature.routines.RoutinesViewModel
import com.dhikr.app.feature.settings.SettingsScreen
import com.dhikr.app.feature.settings.SettingsViewModel
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
fun DhikrApp(themeMode: ThemeMode = ThemeMode.SYSTEM) {
    DhikrTheme(themeMode = themeMode) {
        val navController = rememberNavController()
        val context = LocalContext.current
        val app = context.applicationContext as DhikrApplication

        val sessionRepository = remember { SessionRepository(context.applicationContext) }
        val tasbihRepository = remember { TasbihRepository(app.database.tasbihDao(), app.database.routineDao()) }
        val routineRepository = remember { RoutineRepository(app.database.routineDao()) }
        val historyRepository = remember { HistoryRepository(app.database.sessionDao(), tasbihRepository) }
        val preferencesRepository = remember { AppPreferencesRepository(context.applicationContext) }
        val hapticsEnabled by preferencesRepository.hapticsEnabled.collectAsState(initial = true)

        // Hoisted here rather than read off CounterViewModel directly: this
        // Scaffold — and the bottom nav bar it owns — sits outside the
        // NavHost destination that creates the Counter screen's ViewModel.
        // CounterScreen reports lock changes up through onLockedChanged.
        var counterLocked by remember { mutableStateOf(false) }
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val onCounterRoute = currentBackStackEntry?.destination?.route?.substringBefore("?") ==
            ROUTE_COUNTER.substringBefore("?")

        Scaffold(
            containerColor = DhikrTheme.colors.bg,
            bottomBar = {
                // Locking the counter hides the bottom nav entirely, on top
                // of CounterScreen locking its own back/undo/pause/reset
                // controls — a pocket touch shouldn't be able to switch tabs
                // out of a locked session either. Guarded on onCounterRoute
                // too so a stale true from a just-left session can't hide
                // the bar elsewhere in the app.
                if (!(counterLocked && onCounterRoute)) {
                    DhikrBottomNav(navController)
                }
            },
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
                        onEditTasbih = { id -> navController.navigate("tasbih/editor?id=$id") },
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
                    CounterScreen(
                        viewModel = viewModel,
                        hapticsEnabled = hapticsEnabled,
                        onBack = { navController.popBackStack() },
                        onLockedChanged = { counterLocked = it },
                    )
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
                    val appVersion = remember {
                        runCatching {
                            context.packageManager
                                .getPackageInfo(context.packageName, 0)
                                .versionName
                        }.getOrNull().orEmpty()
                    }
                    val viewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModel.Factory(preferencesRepository, appVersion),
                    )
                    SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
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
        Triple(ROUTE_HOME, R.string.nav_home, android.R.drawable.ic_menu_myplaces),
        Triple(ROUTE_TASBIH_LIBRARY, R.string.nav_tasbih, android.R.drawable.ic_menu_agenda),
        Triple(ROUTE_COUNTER, R.string.nav_count, android.R.drawable.ic_menu_add),
        Triple(ROUTE_INSIGHTS, R.string.nav_insights, android.R.drawable.ic_menu_sort_by_size),
        Triple(ROUTE_SETTINGS, R.string.nav_settings, android.R.drawable.ic_menu_preferences),
    )

    NavigationBar(containerColor = colors.surface) {
        items.forEach { (route, labelRes, iconRes) ->
            val baseRoute = route.substringBefore("?")
            // Finding #7: an exact match against the route's own base path
            // (not startsWith) so a sub-route sharing the same prefix — e.g.
            // ROUTE_TASBIH_EDITOR ("tasbih/editor?id={id}") vs
            // ROUTE_TASBIH_LIBRARY ("tasbih") — doesn't fool the Tasbih tab
            // into showing selected while the editor screen is open.
            // currentRoute is the *route template* (e.g. "tasbih/editor?id={id}"),
            // not the resolved path, so comparing its own substringBefore("?")
            // against baseRoute is the correct like-for-like comparison.
            val selected = currentRoute?.substringBefore("?") == baseRoute
            val label = stringResource(labelRes)
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        // Finding #7: standard Compose Navigation bottom-nav
                        // pattern — popUpTo the graph's start destination with
                        // saveState so switching tabs doesn't grow the back
                        // stack unbounded (and the system back button doesn't
                        // walk the whole tab-switch history), launchSingleTop
                        // so repeated taps on the same tab don't stack
                        // duplicate entries, and restoreState so returning to
                        // a previously-visited tab resumes where it left off.
                        navController.navigate(baseRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
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
