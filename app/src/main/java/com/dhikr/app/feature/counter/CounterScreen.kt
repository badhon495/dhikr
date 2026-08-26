package com.dhikr.app.feature.counter

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dhikr.app.R
import com.dhikr.app.ui.theme.ArabicLineStyle
import com.dhikr.app.ui.theme.CounterCountLongTextStyle
import com.dhikr.app.ui.theme.CounterCountStyle
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.DialogShape
import com.dhikr.app.ui.theme.PillShape
import com.dhikr.app.ui.theme.TransliterationLongTextStyle
import com.dhikr.app.ui.theme.TransliterationStyle
import kotlin.math.min

private const val LONG_TEXT_THRESHOLD = 90

@Composable
fun CounterScreen(
    viewModel: CounterViewModel,
    onBack: () -> Unit,
    // Reports the lock toggle up to DhikrApp, which owns the Scaffold the
    // bottom nav bar lives in — CounterScreen has no reach to it directly.
    onLockedChanged: (Boolean) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors
    val haptic = LocalHapticFeedback.current
    var showResetDialog by remember { mutableStateOf(false) }

    // The on-screen back chevron is gated on !state.locked, but the system
    // back gesture/button bypasses that entirely — it doesn't go through
    // the chevron's clickable at all. Intercept and swallow it here so a
    // pocket-touch (or the OS back gesture) can't leave a locked session.
    BackHandler(enabled = state.locked) {}

    // Locking the counter goes beyond this screen's own controls: it also
    // hides the app's bottom nav bar (DhikrApp owns that Scaffold, so it
    // can't read CounterViewModel directly) and drops the system status/
    // navigation bars into immersive mode so a pocket-touch can't background
    // the app or pull down notifications mid-session. The nav-bar side is
    // reported upward here; the system-bar side is handled locally below.
    LaunchedEffect(state.locked) {
        onLockedChanged(state.locked)
    }

    val view = LocalView.current
    DisposableEffect(state.locked, view) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (state.locked) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            // Always restore system bars on leaving the screen, regardless of
            // lock state, so a locked session left via the system back
            // gesture/task switcher never strands the app in immersive mode.
            if (window != null) {
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Flush the in-flight session to disk on ON_STOP so a session is never lost
    // to process death inside the ViewModel's 500ms save debounce window.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.flushSession()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Log the in-progress session to permanent History when this composable
    // actually leaves composition (back navigation, navigating elsewhere) —
    // a different trigger than ON_STOP above, which also fires on a mere
    // app-background/foreground cycle (the composable stays in composition
    // through that). Kept as a separate DisposableEffect so the two triggers
    // are never conflated.
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.logAndClearOnLeave()
        }
    }

    val isLongText = state.dhikr.transliteration.length > LONG_TEXT_THRESHOLD
    val ringSize = if (isLongText) 178.dp else 252.dp
    val countStyle = if (isLongText) CounterCountLongTextStyle else CounterCountStyle
    val transliterationStyle = if (isLongText) TransliterationLongTextStyle else TransliterationStyle

    // Quick pop of the ring on every registered tap / lap rollover.
    val countScale = remember { Animatable(1f) }
    LaunchedEffect(state.count, state.lap) {
        countScale.snapTo(1.07f)
        countScale.animateTo(1f, animationSpec = tween(110))
    }

    val animatedProgress by animateFloatAsState(
        targetValue = min(1f, state.progressFraction),
        animationSpec = tween(160, easing = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)),
        label = "ring-progress",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        // ---- Top bar: back, dhikr name + session label, lock toggle ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .height(48.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    // Back navigation is blocked while the counter is locked, so a
                    // stray pocket-touch can't drop out of an active session.
                    .clickable(enabled = !state.locked) { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = backChevronIcon(),
                    contentDescription = stringResource(R.string.counter_back_content_description),
                    tint = if (state.locked) colors.faint else colors.dim,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = state.dhikr.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    maxLines = 1,
                )
                Text(
                    text = formatSessionLabel(state.elapsedSeconds, state.totalCount),
                    fontSize = 11.5.sp,
                    color = colors.faint,
                    maxLines = 1,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    // The lock toggle is otherwise always enabled — it is the only
                    // way back out of the locked state — but is still gated on
                    // sessionReady (finding #2): Room's seed data may not have
                    // loaded yet, and there is nothing to lock/unlock before then.
                    .clickable(enabled = state.sessionReady) { viewModel.onToggleLock() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = lockIcon(state.locked),
                    contentDescription = stringResource(R.string.counter_lock_content_description),
                    tint = if (state.locked) colors.terra else colors.faint,
                    modifier = Modifier.size(19.dp),
                )
            }
        }

        // ---- Routine progress chips: current sage-filled, completed faint,
        // upcoming dim. Only rendered when a routine is actively running. ----
        if (state.routineSteps.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 8.dp),
            ) {
                state.routineSteps.forEachIndexed { index, step ->
                    val isCurrent = index == state.currentRoutineStepIndex
                    val isCompleted = index < state.currentRoutineStepIndex
                    val bg = if (isCurrent) colors.sage else colors.surface
                    val fg = when {
                        isCurrent -> colors.onSage
                        isCompleted -> colors.faint
                        else -> colors.dim
                    }
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(bg)
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                    ) {
                        Text(
                            "${step.tasbihName.substringBefore(' ')} ${step.targetCount}",
                            fontSize = 11.5.sp,
                            color = fg,
                        )
                    }
                }
            }
        }

        // ---- Tap area: the whole middle of the screen counts ----
        // The tap gesture and the scroll container sit on the outer Box, which
        // fills all space left between the top bar and the control row. Inside a
        // scroller the content is measured with an unbounded height, so
        // Arrangement.Center alone would not centre anything; BoxWithConstraints
        // hands us the real viewport height and `heightIn(min = ...)` pins the
        // content to it. Longer dhikr text grows past that and scrolls normally.
        val tapInteractionSource = remember { MutableInteractionSource() }
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = tapInteractionSource,
                    indication = null,
                    // Room's seed data may not have loaded yet (finding #2) —
                    // disable the tap target rather than let it silently do
                    // nothing (or, pre-fix, crash the ViewModel).
                    enabled = state.sessionReady,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.onTap()
                },
        ) {
            val viewportHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight)
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (state.dhikr.arabic.isNotEmpty()) {
                    Text(
                        text = state.dhikr.arabic,
                        style = ArabicLineStyle,
                        color = colors.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                    )
                }
                Text(
                    text = state.dhikr.transliteration,
                    style = transliterationStyle,
                    color = colors.dim,
                    textAlign = if (isLongText) TextAlign.Justify else TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (isLongText) 16.dp else 22.dp),
                )

                // Progress ring + count
                Box(
                    modifier = Modifier
                        .size(ringSize)
                        .scale(countScale.value),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidthPx = 12.dp.toPx()
                        val inset = strokeWidthPx / 2f
                        val arcSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
                        drawArc(
                            color = colors.track,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                        )
                        if (animatedProgress > 0f) {
                            drawArc(
                                color = colors.terra,
                                startAngle = -90f,
                                sweepAngle = 360f * animatedProgress,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.82f)
                            .clip(CircleShape)
                            .background(colors.card),
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Text(
                            text = state.count.toString(),
                            style = countStyle,
                            color = colors.text,
                        )
                        Text(
                            text = stringResource(R.string.counter_of_target, state.dhikr.lapTarget),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.faint,
                        )
                    }
                }

                // Lap pips + lap label
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 20.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        for (i in 1..state.totalLaps) {
                            val pipWidth = if (i == state.lap) 26.dp else 8.dp
                            val pipColor = when {
                                i < state.lap -> colors.sage
                                i == state.lap -> colors.terra
                                else -> colors.track
                            }
                            Box(
                                modifier = Modifier
                                    .width(pipWidth)
                                    .height(8.dp)
                                    .clip(PillShape)
                                    .background(pipColor),
                            )
                        }
                    }
                    Text(
                        text = stringResource(
                            R.string.counter_lap_label,
                            state.lap,
                            state.totalLaps,
                            state.totalCount,
                            state.dhikr.lapTarget * state.totalLaps,
                        ),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.dim,
                        modifier = Modifier.padding(top = 9.dp),
                    )
                }

                Text(
                    text = if (state.locked) {
                        stringResource(R.string.counter_tap_hint_locked)
                    } else {
                        stringResource(R.string.counter_tap_hint)
                    },
                    fontSize = 11.5.sp,
                    color = colors.faint,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        // ---- Control row: undo, pause/resume, reset ----
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .clip(PillShape)
                    .background(colors.surface)
                    // Undo is blocked while locked, same as reset — a pocket
                    // touch shouldn't be able to unwind counted taps either.
                    .clickable(enabled = state.sessionReady && state.canUndo && !state.locked) { viewModel.onUndo() }
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            ) {
                Icon(
                    imageVector = undoIcon(),
                    contentDescription = null,
                    tint = if (state.canUndo && !state.locked) colors.text else colors.faint,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    text = stringResource(R.string.counter_undo),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (state.canUndo && !state.locked) colors.text else colors.faint,
                )
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(PillShape)
                    .background(if (state.locked) colors.surface else colors.sage)
                    // Room's seed data may not have loaded yet (finding #2).
                    // Pause/resume is blocked while locked too — the lock's
                    // whole point is a session that can't be knocked off
                    // course by a stray touch.
                    .clickable(enabled = state.sessionReady && !state.locked) { viewModel.onTogglePause() }
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            ) {
                Text(
                    text = if (state.running) {
                        stringResource(R.string.counter_pause)
                    } else {
                        stringResource(R.string.counter_resume)
                    },
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (state.locked) colors.faint else colors.onSage,
                )
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    // Reset is destructive, so it is blocked while locked and, when
                    // unlocked, only opens the confirmation dialog. Also gated on
                    // sessionReady (finding #2) — Room's seed data may not have
                    // loaded yet.
                    .clickable(enabled = state.sessionReady && !state.locked) { showResetDialog = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = resetIcon(),
                    contentDescription = stringResource(R.string.counter_reset_content_description),
                    tint = if (state.locked) colors.faint else colors.dim,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_dialog_title)) },
            text = { Text(stringResource(R.string.reset_dialog_body, state.totalCount)) },
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.dim,
            shape = DialogShape,
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onReset()
                    showResetDialog = false
                }) {
                    Text(
                        text = stringResource(R.string.reset_dialog_confirm),
                        color = colors.terra,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(
                        text = stringResource(R.string.reset_dialog_keep_counting),
                        color = colors.dim,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )
    }

    if (state.isRoutineComplete) {
        AlertDialog(
            onDismissRequest = { /* no-op: dismissal happens via the Done button only */ },
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.dim,
            shape = RoundedCornerShape(34.dp),
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(colors.sageSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = checkIcon(),
                            contentDescription = stringResource(R.string.routine_complete_check_content_description),
                            tint = colors.sage,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.routine_complete_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.routine_complete_body,
                            state.routineName ?: state.dhikr.name,
                            state.totalCount,
                            formatDuration(state.elapsedSeconds),
                        ),
                        fontSize = 13.5.sp,
                        color = colors.dim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    TextButton(
                        onClick = { viewModel.onRoutineCompleteAcknowledged() },
                        modifier = Modifier.padding(top = 14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.routine_complete_done),
                            color = colors.sage,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            },
            confirmButton = {},
        )
    }
}

/** Plain "MM:SS" duration, with no pace readout — use for dialog body text
 * where a bare duration is needed (see formatSessionLabel below for the
 * pace-augmented variant used in the top-bar session label). */
private fun formatDuration(elapsedSeconds: Int): String {
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

/** "12:34" once the session is a few seconds old, plus a "· 41/min" pace readout. */
private fun formatSessionLabel(elapsedSeconds: Int, total: Int): String {
    val time = formatDuration(elapsedSeconds)
    if (elapsedSeconds <= 4) return time
    val rate = (total.toFloat() / (elapsedSeconds / 60f)).toInt()
    return if (rate > 0) "$time  ·  $rate/min" else time
}
