package com.dhikr.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.datastore.SessionRepository
import com.dhikr.app.feature.counter.CounterScreen
import com.dhikr.app.feature.counter.CounterViewModel
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.PillShape

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

        NavHost(navController = navController, startDestination = ROUTE_HOME) {
            composable(ROUTE_HOME) {
                HomeStub(onOpenCounter = { navController.navigate(ROUTE_COUNTER) })
            }
            composable(ROUTE_COUNTER) {
                val viewModel: CounterViewModel = viewModel(
                    factory = CounterViewModel.Factory(
                        sessionRepository = sessionRepository,
                        tasbihRepository = tasbihRepository,
                        startingDhikrId = "subhan",
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

/**
 * Placeholder Home screen. Phase 1+2 only needs an entry point into the Counter;
 * the real Home (dhikr library, routines, stats) lands in a later phase.
 */
@Composable
private fun HomeStub(onOpenCounter: () -> Unit) {
    val colors = DhikrTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.home_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.text,
        )
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .clip(PillShape)
                .background(colors.sage)
                .clickable { onOpenCounter() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.home_start_counter),
                fontWeight = FontWeight.SemiBold,
                color = colors.onSage,
            )
        }
    }
}
