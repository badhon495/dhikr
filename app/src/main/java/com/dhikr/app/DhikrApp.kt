package com.dhikr.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dhikr.app.R
import com.dhikr.app.core.ai.BenefitsRepository
import com.dhikr.app.core.ai.GeminiClient
import com.dhikr.app.core.ai.SecureKeyStore
import com.dhikr.app.core.backup.BackupRepository
import com.dhikr.app.core.database.HistoryRepository
import com.dhikr.app.core.database.RoutineRepository
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.datastore.CounterScript
import com.dhikr.app.core.datastore.HapticMode
import com.dhikr.app.core.datastore.SessionRepository
import com.dhikr.app.core.datastore.ThemeMode
import com.dhikr.app.core.share.AndroidBase64
import com.dhikr.app.core.share.RoutineShareCodec
import com.dhikr.app.core.share.RoutineShareRepository
import com.dhikr.app.feature.counter.CounterScreen
import com.dhikr.app.feature.counter.CounterViewModel
import com.dhikr.app.feature.home.HomeScreen
import com.dhikr.app.feature.home.HomeViewModel
import com.dhikr.app.feature.insights.InsightsScreen
import com.dhikr.app.feature.insights.InsightsViewModel
import com.dhikr.app.feature.insights.MonthlyHistoryScreen
import com.dhikr.app.feature.insights.MonthlyHistoryViewModel
import com.dhikr.app.feature.routines.RoutineEditorScreen
import com.dhikr.app.feature.routines.RoutineEditorViewModel
import com.dhikr.app.feature.routines.RoutineImportScreen
import com.dhikr.app.feature.routines.RoutineImportViewModel
import com.dhikr.app.feature.routines.RoutineShareViewModel
import com.dhikr.app.feature.routines.RoutinesScreen
import com.dhikr.app.feature.routines.RoutinesViewModel
import com.dhikr.app.feature.settings.BackupViewModel
import com.dhikr.app.feature.settings.SettingsScreen
import com.dhikr.app.feature.settings.SettingsViewModel
import com.dhikr.app.feature.tasbih.TasbihEditorScreen
import com.dhikr.app.feature.tasbih.TasbihEditorViewModel
import com.dhikr.app.feature.tasbih.TasbihLibraryScreen
import com.dhikr.app.feature.tasbih.TasbihLibraryViewModel
import com.dhikr.app.ui.NavCountIcon
import com.dhikr.app.ui.NavHomeIcon
import com.dhikr.app.ui.NavInsightsIcon
import com.dhikr.app.ui.NavSettingsIcon
import com.dhikr.app.ui.NavTasbihIcon
import com.dhikr.app.ui.LocalReducedMotion
import com.dhikr.app.ui.Motion
import com.dhikr.app.ui.theme.DhikrTheme

private const val ROUTE_HOME = "home"
private const val ROUTE_TASBIH_LIBRARY = "tasbih"
private const val ROUTE_TASBIH_EDITOR = "tasbih/editor?id={id}"
private const val ROUTE_COUNTER = "counter?dhikrId={dhikrId}&routineId={routineId}"
private const val ROUTE_INSIGHTS = "insights"
private const val ROUTE_MONTHLY_HISTORY = "insights/months"
private const val ROUTE_ROUTINES = "routines"
private const val ROUTE_ROUTINES_IMPORT = "routines/import"
private const val ROUTE_ROUTINE_EDITOR = "routines/editor?id={id}"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun DhikrApp(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    pendingRoutineId: String? = null,
    onPendingRoutineConsumed: () -> Unit = {},
    pendingOpen: String? = null,
    onPendingOpenConsumed: () -> Unit = {},
    pendingShareUri: Uri? = null,
    onPendingShareConsumed: () -> Unit = {},
) {
    DhikrTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
        val navController = rememberNavController()
        val context = LocalContext.current
        val app = context.applicationContext as DhikrApplication

        val sessionRepository = remember { SessionRepository(context.applicationContext) }
        val tasbihRepository = remember {
            TasbihRepository(
                app.database.tasbihDao(),
                app.database.routineDao(),
                app.database.tasbihProgressDao(),
                app.database.sessionDao(),
            )
        }
        val routineRepository = remember {
            RoutineRepository(
                app.database.routineDao(),
                app.database.routineCompletionDao(),
                app.database.routineProgressDao(),
            )
        }
        val historyRepository = remember { HistoryRepository(app.database.sessionDao(), tasbihRepository) }
        val preferencesRepository = remember { AppPreferencesRepository(context.applicationContext) }
        val backupRepository = remember { BackupRepository(app.database, preferencesRepository) }
        val secureKeyStore = remember { SecureKeyStore(context.applicationContext) }
        val geminiClient = remember { GeminiClient() }
        val benefitsRepository = remember {
            BenefitsRepository.create(secureKeyStore, geminiClient, tasbihRepository)
        }
        val appVersionName = remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull().orEmpty()
        }
        val routineShareCodec = remember { RoutineShareCodec(AndroidBase64) }
        val routineShareRepository = remember { RoutineShareRepository(app.database, routineShareCodec) }
        var importReader by remember { mutableStateOf<(suspend () -> String)?>(null) }
        val reminderScheduler = remember {
            com.dhikr.app.core.notifications.ReminderScheduler(context.applicationContext)
        }
        val hapticMode by preferencesRepository.hapticMode.collectAsState(initial = HapticMode.EVERY_TAP)
        val reducedMotion by preferencesRepository.reducedMotion.collectAsState(initial = false)
        val counterScript by preferencesRepository.counterScript.collectAsState(initial = CounterScript.PRONUNCIATION)

        // Hoisted here rather than read off CounterViewModel directly: this
        // Scaffold — and the bottom nav bar it owns — sits outside the
        // NavHost destination that creates the Counter screen's ViewModel.
        // CounterScreen reports lock changes up through onLockedChanged.
        var counterLocked by remember { mutableStateOf(false) }

        // Per-tab "scroll to top" signal. Tapping a bottom-nav tab you're
        // already on bumps its counter; the tab screen watches its own entry
        // and animates its scroll container back to the top on each change.
        val scrollTopSignals = remember { mutableStateMapOf<String, Int>() }
        fun signalOf(route: String) = scrollTopSignals[route] ?: 0

        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val onCounterRoute = currentBackStackEntry?.destination?.route?.substringBefore("?") ==
            ROUTE_COUNTER.substringBefore("?")

        // Reminder-notification tap: deep-link into the routine's counter. The
        // existing CounterViewModel routineId path handles the rest.
        LaunchedEffect(pendingRoutineId) {
            val id = pendingRoutineId ?: return@LaunchedEffect
            navController.navigate("counter?routineId=$id")
            onPendingRoutineConsumed()
        }

        // Widget body tap: open the counter or insights tab. routineId (from a
        // reminder notification or a routine-state widget) takes precedence, so
        // when both are set this effect defers and lets the routine effect run.
        LaunchedEffect(pendingOpen) {
            val target = pendingOpen ?: return@LaunchedEffect
            if (pendingRoutineId == null) {
                val route = when (target) {
                    MainActivity.OPEN_INSIGHTS -> ROUTE_INSIGHTS
                    else -> ROUTE_COUNTER.substringBefore("?")
                }
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id)
                    launchSingleTop = true
                }
            }
            onPendingOpenConsumed()
        }

        // A tapped .dhikrroutine file: stash a reader over its content-uri and
        // route to the import preview. The parser is the real gate on whether
        // the file is ours (the MIME filter is broad).
        LaunchedEffect(pendingShareUri) {
            val uri = pendingShareUri ?: return@LaunchedEffect
            val resolver = context.contentResolver
            importReader = {
                resolver.openInputStream(uri)?.use { it.reader().readText() }
                    ?: error("no input stream")
            }
            navController.navigate(ROUTE_ROUTINES_IMPORT)
            onPendingShareConsumed()
        }

        CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
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
                    DhikrBottomNav(
                        navController = navController,
                        onReselect = { base -> scrollTopSignals[base] = signalOf(base) + 1 },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = ROUTE_HOME,
                modifier = Modifier.padding(padding),
                enterTransition = { navEnter(reducedMotion, isTabSwitch(), forward = true) },
                exitTransition = { navExit(reducedMotion, isTabSwitch(), forward = true) },
                popEnterTransition = { navEnter(reducedMotion, isTabSwitch(), forward = false) },
                popExitTransition = { navExit(reducedMotion, isTabSwitch(), forward = false) },
            ) {
                composable(ROUTE_HOME) {
                    val viewModel: HomeViewModel = viewModel(
                        factory = HomeViewModel.Factory(tasbihRepository, routineRepository, historyRepository, sessionRepository, preferencesRepository),
                    )
                    HomeScreen(
                        viewModel = viewModel,
                        scrollToTopSignal = signalOf(ROUTE_HOME),
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
                        scrollToTopSignal = signalOf(ROUTE_TASBIH_LIBRARY),
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
                        factory = TasbihEditorViewModel.Factory(
                            tasbihRepository,
                            preferencesRepository,
                            editingId,
                            benefitsRepository,
                        ),
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
                        hapticMode = hapticMode,
                        counterScript = counterScript,
                        onBack = { navController.popBackStack() },
                        onLockedChanged = { counterLocked = it },
                    )
                }
                composable(ROUTE_INSIGHTS) {
                    val viewModel: InsightsViewModel = viewModel(
                        factory = InsightsViewModel.Factory(historyRepository),
                    )
                    InsightsScreen(
                        viewModel = viewModel,
                        scrollToTopSignal = signalOf(ROUTE_INSIGHTS),
                        onStartCounting = { navController.navigate("counter") },
                        onSeeAllMonths = { navController.navigate(ROUTE_MONTHLY_HISTORY) },
                    )
                }
                composable(ROUTE_MONTHLY_HISTORY) {
                    val viewModel: MonthlyHistoryViewModel = viewModel(
                        factory = MonthlyHistoryViewModel.Factory(historyRepository),
                    )
                    MonthlyHistoryScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
                composable(ROUTE_ROUTINES) {
                    val viewModel: RoutinesViewModel = viewModel(
                        factory = RoutinesViewModel.Factory(routineRepository, tasbihRepository, reminderScheduler),
                    )
                    val shareVm: RoutineShareViewModel = viewModel(
                        factory = RoutineShareViewModel.Factory(
                            routineShareRepository, routineRepository, routineShareCodec, appVersionName,
                        ),
                    )
                    RoutinesScreen(
                        viewModel = viewModel,
                        shareViewModel = shareVm,
                        onStartRoutine = { id -> navController.navigate("counter?routineId=$id") },
                        onNewRoutine = { navController.navigate("routines/editor") },
                        onEditRoutine = { id -> navController.navigate("routines/editor?id=$id") },
                        onImportRequested = { reader ->
                            importReader = reader
                            navController.navigate(ROUTE_ROUTINES_IMPORT)
                        },
                    )
                }
                composable(ROUTE_ROUTINES_IMPORT) {
                    val reader = importReader
                    val importVm: RoutineImportViewModel = viewModel(
                        factory = RoutineImportViewModel.Factory(routineShareRepository),
                    )
                    LaunchedEffect(reader) {
                        if (reader == null) {
                            navController.popBackStack()
                        } else {
                            importVm.load(reader)
                        }
                    }
                    RoutineImportScreen(
                        viewModel = importVm,
                        onClose = {
                            importReader = null
                            navController.popBackStack()
                        },
                    )
                }
                composable(
                    ROUTE_ROUTINE_EDITOR,
                    arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { backStackEntry ->
                    val editingId = backStackEntry.arguments?.getString("id")
                    val viewModel: RoutineEditorViewModel = viewModel(
                        factory = RoutineEditorViewModel.Factory(routineRepository, tasbihRepository, editingId, reminderScheduler),
                    )
                    RoutineEditorScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(ROUTE_SETTINGS) {
                    val viewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModel.Factory(
                            preferencesRepository,
                            appVersionName,
                            context.applicationContext,
                            secureKeyStore,
                        ),
                    )
                    val backupViewModel: BackupViewModel = viewModel(
                        factory = BackupViewModel.Factory(backupRepository, appVersionName),
                    )
                    SettingsScreen(
                        viewModel = viewModel,
                        backupViewModel = backupViewModel,
                        scrollToTopSignal = signalOf(ROUTE_SETTINGS),
                    )
                }
            }
        }
        }
    }
}

private val TAB_BASE_ROUTES = setOf(
    ROUTE_HOME,
    ROUTE_TASBIH_LIBRARY,
    ROUTE_COUNTER.substringBefore("?"),
    ROUTE_INSIGHTS,
    ROUTE_SETTINGS,
)

/** True when both ends of the transition are top-level bottom-nav destinations —
 *  those get a plain fade, no directional slide. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean {
    val from = initialState.destination.route?.substringBefore("?")
    val to = targetState.destination.route?.substringBefore("?")
    return from in TAB_BASE_ROUTES && to in TAB_BASE_ROUTES
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.navEnter(
    reduced: Boolean,
    tabSwitch: Boolean,
    forward: Boolean,
): EnterTransition {
    if (reduced) return EnterTransition.None
    val fade = fadeIn(tween(Motion.FAST_MS))
    if (tabSwitch) return fade
    val direction = if (forward) {
        AnimatedContentTransitionScope.SlideDirection.Start
    } else {
        AnimatedContentTransitionScope.SlideDirection.End
    }
    return fade + slideIntoContainer(
        direction,
        tween(Motion.STANDARD_MS, easing = Motion.StandardEasing),
        initialOffset = { it / 14 },
    )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.navExit(
    reduced: Boolean,
    tabSwitch: Boolean,
    forward: Boolean,
): ExitTransition {
    if (reduced) return ExitTransition.None
    val fade = fadeOut(tween(Motion.FAST_MS))
    if (tabSwitch) return fade
    val direction = if (forward) {
        AnimatedContentTransitionScope.SlideDirection.Start
    } else {
        AnimatedContentTransitionScope.SlideDirection.End
    }
    return fade + slideOutOfContainer(
        direction,
        tween(Motion.STANDARD_MS, easing = Motion.StandardEasing),
        targetOffset = { it / 14 },
    )
}

@Composable
private fun DhikrBottomNav(
    navController: NavController,
    onReselect: (String) -> Unit,
) {
    val colors = DhikrTheme.colors
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Stroke vectors ported from the prototype's NAV table, sharing
    // CounterIcons.kt's treatment (24x24, 2.75 stroke, round caps) — see
    // ui/NavIcons.kt.
    val items = listOf(
        Triple(ROUTE_HOME, R.string.nav_home, NavHomeIcon),
        Triple(ROUTE_TASBIH_LIBRARY, R.string.nav_tasbih, NavTasbihIcon),
        Triple(ROUTE_COUNTER, R.string.nav_count, NavCountIcon),
        Triple(ROUTE_INSIGHTS, R.string.nav_insights, NavInsightsIcon),
        Triple(ROUTE_SETTINGS, R.string.nav_settings, NavSettingsIcon),
    )

    NavigationBar(containerColor = colors.surface) {
        items.forEach { (route, labelRes, icon) ->
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
                        // Finding #7: popUpTo the graph's start destination so
                        // switching tabs doesn't grow the back stack unbounded
                        // (and the system back button doesn't walk the whole
                        // tab-switch history), launchSingleTop so repeated taps
                        // on the same tab don't stack duplicate entries.
                        //
                        // No saveState/restoreState: saveState keys the popped
                        // stack both by the popUpTo target (the start
                        // destination) and by the tab's own destination id, so
                        // restoreState on the very next tab tap re-inflates
                        // whatever was stacked above it — tapping a tab while a
                        // Counter (or editor) is open on top of it would bounce
                        // straight back to that screen instead of the tab root.
                        // These tabs are single-level roots with transient
                        // detail screens pushed on top; "resume where left off"
                        // isn't worth that.
                        navController.navigate(baseRoute) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    } else {
                        // Already on this tab — a repeat tap scrolls its
                        // screen back to the top.
                        onReselect(baseRoute)
                    }
                },
                icon = { Icon(imageVector = icon, contentDescription = label) },
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
