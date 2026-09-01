package com.dhikr.app.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.ui.LocalReducedMotion
import com.dhikr.app.ui.Motion
import com.dhikr.app.ui.headingSemantics
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.Caprasimo
import com.dhikr.app.ui.theme.DhikrTheme
import kotlin.math.min

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onContinueSession: () -> Unit,
    onOpenTasbih: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onStartRoutine: (String) -> Unit,
    onOpenRoutines: () -> Unit,
    scrollToTopSignal: Int = 0,
) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors
    val scrollState = rememberScrollState()

    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) scrollState.animateScrollTo(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Greeting + goal ring
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    stringResource(R.string.home_greeting),
                    fontFamily = Caprasimo,
                    fontSize = 29.sp,
                    color = colors.text,
                )
                Text(
                    state.dateLabel,
                    fontSize = 13.5.sp,
                    color = colors.dim,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            GoalRing(
                progress = if (state.dailyGoalTarget > 0) state.todayTotal.toFloat() / state.dailyGoalTarget else 0f,
                contentDescription = stringResource(
                    R.string.home_goal_ring_description,
                    state.todayTotal,
                    state.dailyGoalTarget,
                ),
            )
        }

        // Continue session
        state.continueSession?.let { info ->
            val continueDescription = stringResource(
                R.string.home_continue_session_description,
                info.tasbihName,
                info.count,
                info.target,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(colors.sageSoft)
                    .border(1.dp, colors.line, RoundedCornerShape(30.dp))
                    .clickable { onContinueSession() }
                    .semantics(mergeDescendants = true) { contentDescription = continueDescription }
                    .padding(20.dp),
            ) {
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(colors.sage),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = colors.onSage,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(
                        stringResource(R.string.home_continue_session_kicker),
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = colors.dim,
                    )
                    Text(
                        info.tasbihName,
                        fontSize = 17.sp,
                        color = colors.text,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Text("${info.count}/${info.target}", fontSize = 14.5.sp, color = colors.dim)
            }
        }

        // Routines — favorited ones if the user marked any, else the first few.
        Column {
            SectionHeader(
                title = stringResource(R.string.home_routines_title),
                actionLabel = stringResource(R.string.home_routines_manage),
                onAction = onOpenRoutines,
            )
            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.routines.forEach { routineWithSteps ->
                    val totalCount = routineWithSteps.steps.sumOf { it.targetCount }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                            .clickable { onStartRoutine(routineWithSteps.routine.id) }
                            .padding(14.dp),
                    ) {
                        Text(
                            routineWithSteps.routine.name,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.text,
                            maxLines = 2,
                        )
                        Text(
                            stringResource(R.string.routines_step_count, routineWithSteps.steps.size, totalCount),
                            fontSize = 10.5.sp,
                            color = colors.dim,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }

        // Favourites
        Column {
            SectionHeader(
                title = stringResource(R.string.home_favorites_title),
                actionLabel = stringResource(R.string.home_favorites_all),
                onAction = onOpenLibrary,
            )
            state.favorites.forEach { tasbih ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.card)
                        .clickable { onOpenTasbih(tasbih.id) }
                        .padding(14.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tasbih.name, fontSize = 14.5.sp, color = colors.text)
                        Text(tasbih.pronunciation, fontSize = 12.sp, color = colors.faint, maxLines = 1)
                    }
                    Text(tasbih.arabic, fontSize = 14.sp, color = colors.dim)
                }
            }
        }
    }
}

/** Section label + trailing action link, shared by the Home sections. */
@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val colors = DhikrTheme.colors
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            color = colors.text,
            modifier = Modifier.headingSemantics(),
        )
        Text(
            actionLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.terra,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(role = Role.Button) { onAction() }
                .minTapTarget()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun GoalRing(progress: Float, contentDescription: String) {
    val colors = DhikrTheme.colors
    val reducedMotion = LocalReducedMotion.current
    // Sweep the arc up to a changed daily total instead of snapping, on the same
    // curve the counter ring uses.
    val animatedProgress by animateFloatAsState(
        targetValue = min(1f, progress),
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(Motion.STANDARD_MS, easing = Motion.StandardEasing)
        },
        label = "goal-ring",
    )
    Box(
        modifier = Modifier
            .size(86.dp)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val inset = strokeWidth / 2
            drawArc(
                color = colors.track,
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = colors.terra,
                startAngle = -90f, sweepAngle = 360f * animatedProgress, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Text("${(animatedProgress * 100).toInt()}%", fontSize = 17.sp, color = colors.text)
    }
}
