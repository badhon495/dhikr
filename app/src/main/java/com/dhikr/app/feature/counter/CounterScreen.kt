package com.dhikr.app.feature.counter

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
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
import com.dhikr.app.core.counter.AutoCounterSensorListener
import com.dhikr.app.core.datastore.CounterScript
import com.dhikr.app.core.datastore.HapticMode
import com.dhikr.app.core.haptics.rememberHaptics
import com.dhikr.app.ui.COUNTER_TAP_AREA_TEST_TAG
import com.dhikr.app.ui.ClampedFontScale
import com.dhikr.app.ui.LocalReducedMotion
import com.dhikr.app.ui.Motion
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.ArabicLineStyle
import com.dhikr.app.ui.theme.CounterCountLongTextStyle
import com.dhikr.app.ui.theme.CounterCountStyle
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.DialogShape
import com.dhikr.app.ui.theme.PillShape
import com.dhikr.app.ui.theme.PronunciationLongTextStyle
import com.dhikr.app.ui.theme.PronunciationPrimaryStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.delay

private const val LONG_TEXT_THRESHOLD = 90

@Composable
fun CounterScreen(
    viewModel: CounterViewModel,
    // From the Haptics setting (DataStore) — hoisted in DhikrApp and passed in.
    // OFF: no vibration. EVERY_TAP: buzz on every count. LAP_ONLY: buzz only
    // when a lap completes.
    hapticMode: HapticMode = HapticMode.EVERY_TAP,
    // From the "Counter shows" setting. ARABIC shows the Arabic line only;
    // PRONUNCIATION shows the pronunciation line only.
    counterScript: CounterScript = CounterScript.PRONUNCIATION,
    // From the "Count on wrist flick" setting (off by default, plan.md §40).
    autoCounterEnabled: Boolean = false,
    onBack: () -> Unit,
    // Reports the lock toggle up to DhikrApp, which owns the Scaffold the
    // bottom nav bar lives in — CounterScreen has no reach to it directly.
    onLockedChanged: (Boolean) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    // B2: collected separately so a 1s timer tick recomposes only the nodes
    // that read it (top-bar label, summary dialogs), not the whole screen.
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val colors = DhikrTheme.colors
    val haptics = rememberHaptics()
    val reducedMotion = LocalReducedMotion.current
    var showResetDialog by remember { mutableStateOf(false) }
    var showSessionSummary by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }

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
                // Refresh placed widgets only after the session persist actually
                // completes, so the provider's DataStore read sees the latest
                // count instead of racing the async write.
                val appContext = view.context.applicationContext
                viewModel.flushSession {
                    com.dhikr.app.core.widget.DhikrWidgets.refreshAll(appContext)
                }
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

    // Auto counter (plan.md §40): registers the accelerometer listener only
    // while the setting is on, a session is actually loaded, and the counter
    // isn't locked — locking exists specifically to stop accidental counts
    // (§35), so a pocketed, locked phone must not auto-count from motion.
    // Unregistered on every key change (including the composable leaving
    // composition), so nothing samples the sensor once this screen is gone.
    val appContext = LocalContext.current.applicationContext
    DisposableEffect(autoCounterEnabled, state.sessionReady, state.locked, viewModel) {
        val active = autoCounterEnabled && state.sessionReady && !state.locked
        val sensorListener = if (active) AutoCounterSensorListener(appContext) else null
        if (sensorListener != null) {
            sensorListener.start { viewModel.onTap() }
        }
        onDispose {
            sensorListener?.stop()
        }
    }

    // Only one script is shown, per the "Counter shows" setting.
    val showArabic = counterScript == CounterScript.ARABIC
    val scriptText = if (showArabic) state.dhikr.arabic else state.dhikr.pronunciation
    val isLongText = scriptText.length > LONG_TEXT_THRESHOLD
    val ringSize = if (isLongText) 178.dp else 252.dp
    val countStyle = if (isLongText) CounterCountLongTextStyle else CounterCountStyle
    val pronunciationStyle = when {
        isLongText -> PronunciationLongTextStyle
        else -> PronunciationPrimaryStyle
    }

    // Quick pop of the ring on every registered tap / lap rollover — skipped
    // entirely under Reduce motion.
    val countScale = remember { Animatable(1f) }
    LaunchedEffect(state.count, state.lap, reducedMotion) {
        if (reducedMotion) {
            countScale.snapTo(1f)
        } else {
            countScale.snapTo(1.07f)
            countScale.animateTo(1f, animationSpec = tween(110))
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = min(1f, state.progressFraction),
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(Motion.STANDARD_MS, easing = Motion.StandardEasing)
        },
        label = "ring-progress",
    )

    // On every lap rollover the ring fills to full, holds a beat, then visibly
    // unwinds back down to 0 before the new lap starts filling again. For a
    // 1-per-lap dhikr every tap is a rollover, so every tap gets a fresh
    // fill -> unwind sweep (otherwise progressFraction is stuck near 0 and
    // nothing visibly moves).
    val ringFlourish = remember { Animatable(0f) }
    var flourishing by remember { mutableStateOf(false) }
    var showLapTargetNumber by remember { mutableStateOf(false) }
    var flourishLap by remember { mutableStateOf(state.lap) }
    var flourishBaselineSynced by remember { mutableStateOf(false) }
    LaunchedEffect(state.sessionReady, state.lap) {
        if (!state.sessionReady) return@LaunchedEffect
        if (!flourishBaselineSynced) {
            flourishLap = state.lap
            flourishBaselineSynced = true
            return@LaunchedEffect
        }
        val rolledOver = state.lap == flourishLap + 1
        flourishLap = state.lap
        if (rolledOver && !reducedMotion) {
            flourishing = true
            showLapTargetNumber = true
            // Snap to full (the tap that completed the lap) and hold briefly.
            ringFlourish.snapTo(1f)
            delay(140)
            showLapTargetNumber = false
            // Unwind the fill all the way back to empty.
            ringFlourish.animateTo(
                0f,
                animationSpec = tween(Motion.PULSE_MS, easing = Motion.StandardEasing),
            )
            flourishing = false
        }
    }
    // While the rollover flourish plays, the ring shows the flourish sweep alone
    // (full -> 0) so the unwind is visible; otherwise it tracks real progress.
    val ringProgress = if (flourishing) ringFlourish.value else animatedProgress

    // Subtle lap-complete feedback (plan.md §14): the progress arc tints from
    // terra toward sage and back over ~300ms when the lap number ticks up.
    val lapPulse = remember { Animatable(0f) }
    var lastLap by remember { mutableStateOf(state.lap) }
    var lapBaselineSynced by remember { mutableStateOf(false) }
    LaunchedEffect(state.sessionReady, state.lap) {
        // The ViewModel emits Empty (lap 0, sessionReady false) first, then
        // settles to the restored session in a single jump. Wait for that
        // settle and sync the baseline off it — otherwise a session restored
        // at exactly lap 1 looks like a fresh 0 -> 1 rollover to the +1 guard
        // below and buzzes every time the screen is (re)entered, e.g. via the
        // "Count" nav tab.
        if (!state.sessionReady) return@LaunchedEffect
        if (!lapBaselineSynced) {
            lastLap = state.lap
            lapBaselineSynced = true
            return@LaunchedEffect
        }
        // Exactly +1 — a real lap rollover only ever advances by one.
        if (state.lap == lastLap + 1) {
            // LAP_ONLY: this is the only place the counter vibrates. EVERY_TAP
            // also gets a stronger buzz here to mark the lap boundary.
            if (hapticMode != HapticMode.OFF) haptics.lapTick()
            if (!reducedMotion) {
                lapPulse.snapTo(1f)
                lapPulse.animateTo(0f, animationSpec = tween(Motion.PULSE_MS))
            }
        }
        lastLap = state.lap
    }

    // A routine step boundary is a lap boundary too, but each step runs its own
    // single-lap engine so `state.lap` never rolls over across steps — the guard
    // above misses it. Buzz here when the routine's current-step index advances,
    // and when the whole routine completes, so every step transition gets the
    // same emphatic feedback as a lap rollover.
    var lastRoutineStepIndex by remember { mutableStateOf(state.currentRoutineStepIndex) }
    var routineStepBaselineSynced by remember { mutableStateOf(false) }
    LaunchedEffect(state.sessionReady, state.currentRoutineStepIndex, state.isRoutineComplete) {
        if (!state.sessionReady) return@LaunchedEffect
        if (!routineStepBaselineSynced) {
            lastRoutineStepIndex = state.currentRoutineStepIndex
            routineStepBaselineSynced = true
            return@LaunchedEffect
        }
        val stepAdvanced = state.currentRoutineStepIndex == lastRoutineStepIndex + 1
        if (stepAdvanced || state.isRoutineComplete) {
            if (hapticMode != HapticMode.OFF) haptics.lapTick()
        }
        lastRoutineStepIndex = state.currentRoutineStepIndex
    }
    val progressArcColor = lerp(colors.terra, colors.sage, lapPulse.value)

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
            val backLabel = stringResource(R.string.counter_back_content_description)
            IconButtonTooltip(label = backLabel) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        // Back navigation is blocked while the counter is locked, so a
                        // stray pocket-touch can't drop out of an active session.
                        .clickable(enabled = !state.locked) { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = backChevronIcon(),
                        contentDescription = backLabel,
                        tint = if (state.locked) colors.faint else colors.dim,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            val sessionLabelDescription = stringResource(R.string.counter_session_label_content_description)
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
                    text = formatSessionLabel(elapsedSeconds, state.totalCount),
                    fontSize = 11.5.sp,
                    color = colors.faint,
                    maxLines = 1,
                    modifier = Modifier
                        .clickable(
                            enabled = state.sessionReady,
                            role = Role.Button,
                        ) { showSessionSummary = true }
                        .semantics {
                            contentDescription = sessionLabelDescription
                        },
                )
            }
            val notesLabel = stringResource(R.string.counter_notes_content_description)
            IconButtonTooltip(label = notesLabel) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        // Read-only, so it stays gated on sessionReady/locked like the
                        // rest of the row rather than the note's own emptiness — an
                        // empty note still opens the dialog (with a placeholder line)
                        // rather than looking broken/unresponsive.
                        .clickable(enabled = state.sessionReady && !state.locked) { showNotesDialog = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = noteIcon(),
                        contentDescription = notesLabel,
                        tint = if (state.locked) colors.faint else {
                            if (state.dhikr.note.isNotBlank()) colors.dim else colors.faint
                        },
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            val lockLabel = stringResource(R.string.counter_lock_content_description)
            IconButtonTooltip(label = lockLabel) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
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
                        contentDescription = lockLabel,
                        tint = if (state.locked) colors.terra else colors.faint,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }

        // ---- Routine progress chips: current sage-filled, completed faint,
        // upcoming dim. Only rendered when a routine is actively running. A
        // routine can have many steps (the Asma-ul-Husna preset has 99), so the
        // strip scrolls horizontally and keeps the current step in view. ----
        if (state.routineSteps.isNotEmpty()) {
            val chipListState = rememberLazyListState()
            LaunchedEffect(state.currentRoutineStepIndex) {
                val target = state.currentRoutineStepIndex
                if (target >= 0 && target < state.routineSteps.size) {
                    // Bias one item earlier so the current chip is not flush to
                    // the leading edge.
                    chipListState.animateScrollToItem((target - 1).coerceAtLeast(0))
                }
            }
            LazyRow(
                state = chipListState,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                itemsIndexed(state.routineSteps) { index, step ->
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
                            maxLines = 1,
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
        val tapActionLabel = stringResource(R.string.counter_tap_action_label)
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
                    // TalkBack: "double-tap to Count" instead of the generic
                    // "double-tap to activate" on this full-screen tap zone.
                    onClickLabel = tapActionLabel,
                ) {
                    if (hapticMode == HapticMode.EVERY_TAP) {
                        haptics.tick()
                    }
                    viewModel.onTap()
                }
                .testTag(COUNTER_TAP_AREA_TEST_TAG),
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
                if (scriptText.isNotEmpty()) {
                    Text(
                        text = scriptText,
                        style = if (showArabic) ArabicLineStyle else pronunciationStyle,
                        color = if (showArabic) colors.text else colors.dim,
                        textAlign = if (isLongText) TextAlign.Justify else TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (isLongText) 16.dp else 22.dp),
                    )
                }

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
                        if (ringProgress > 0f) {
                            drawArc(
                                color = progressArcColor,
                                startAngle = -90f,
                                sweepAngle = 360f * ringProgress,
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
                    val countDescription = stringResource(
                        R.string.counter_count_description,
                        state.count,
                        state.dhikr.lapTarget,
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        // Read as one polite live-region node ("437 of 1000")
                        // rather than the number and label as separate nodes.
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = countDescription
                        },
                    ) {
                        ClampedFontScale {
                            // During a lap-rollover flourish the number shows the
                            // lap target with the ring full, then snaps back.
                            // For a 1-per-lap dhikr the engine count is always 0,
                            // so between taps show reps done (lap - 1) instead.
                            val shownCount = when {
                                showLapTargetNumber -> state.dhikr.lapTarget
                                state.dhikr.lapTarget == 1 -> (state.lap - 1).coerceAtLeast(0)
                                else -> state.count
                            }
                            Text(
                                text = shownCount.toString(),
                                style = countStyle,
                                color = colors.text,
                            )
                        }
                        Text(
                            // A 1-per-lap dhikr counts whole laps, so its target
                            // is the lap count, not the (always 1) per-lap target.
                            text = stringResource(
                                R.string.counter_of_target,
                                if (state.dhikr.lapTarget == 1) state.totalLaps else state.dhikr.lapTarget,
                            ),
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

        // ---- Control row: prev, undo, pause/resume, reset, next. (Notes moved
        // to the top bar, beside the lock toggle — it's a look-something-up
        // action, not a counting control, and living here made the row
        // asymmetric once the top bar had a free slot for it.)
        // Icon-only throughout (no visible labels) — every button's meaning is
        // still exposed to TalkBack via contentDescription. Prev/Next switch
        // between tasbih in Tasbih-Library order and are only ever shown
        // outside a routine — a routine already advances its own steps in
        // sequence, so paging through unrelated tasbih there would be
        // meaningless (and could desync the routine's step tracking).
        val isRoutineActive = state.routineSteps.isNotEmpty()
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 16.dp),
        ) {
            if (!isRoutineActive) {
                val previousLabel = stringResource(R.string.counter_previous_content_description)
                IconButtonTooltip(label = previousLabel) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(colors.surface)
                            .clickable(enabled = state.canGoToPrevious && !state.locked) { viewModel.onPrevious() }
                            .minTapTarget(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = backChevronIcon(),
                            contentDescription = previousLabel,
                            tint = if (state.canGoToPrevious && !state.locked) colors.dim else colors.faint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            val undoLabel = stringResource(R.string.counter_undo)
            IconButtonTooltip(label = undoLabel) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        // Undo is blocked while locked, same as reset — a pocket
                        // touch shouldn't be able to unwind counted taps either.
                        .clickable(enabled = state.sessionReady && state.canUndo && !state.locked) { viewModel.onUndo() }
                        .minTapTarget(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = undoIcon(),
                        contentDescription = undoLabel,
                        tint = if (state.canUndo && !state.locked) colors.text else colors.faint,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            val pauseResumeLabel = if (state.running) {
                stringResource(R.string.counter_pause)
            } else {
                stringResource(R.string.counter_resume)
            }
            IconButtonTooltip(label = pauseResumeLabel) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (state.locked) colors.surface else colors.sage)
                        // Room's seed data may not have loaded yet (finding #2).
                        // Pause/resume is blocked while locked too — the lock's
                        // whole point is a session that can't be knocked off
                        // course by a stray touch.
                        .clickable(enabled = state.sessionReady && !state.locked) { viewModel.onTogglePause() }
                        .minTapTarget(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (state.running) pauseIcon() else playIcon(),
                        contentDescription = pauseResumeLabel,
                        tint = if (state.locked) colors.faint else colors.onSage,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            val resetLabel = stringResource(R.string.counter_reset_content_description)
            IconButtonTooltip(label = resetLabel) {
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
                        contentDescription = resetLabel,
                        tint = if (state.locked) colors.faint else colors.dim,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (!isRoutineActive) {
                val nextLabel = stringResource(R.string.counter_next_content_description)
                IconButtonTooltip(label = nextLabel) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(colors.surface)
                            .clickable(enabled = state.canGoToNext && !state.locked) { viewModel.onNext() }
                            .minTapTarget(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = forwardChevronIcon(),
                            contentDescription = nextLabel,
                            tint = if (state.canGoToNext && !state.locked) colors.dim else colors.faint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
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
                            formatDuration(elapsedSeconds),
                        ),
                        fontSize = 13.5.sp,
                        color = colors.dim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 14.dp),
                    ) {
                        // "Again" replays the routine from step 1 in place — no
                        // navigation, the dialog just closes and counting resumes.
                        TextButton(onClick = { viewModel.onRoutineRestart() }) {
                            Text(
                                text = stringResource(R.string.routine_complete_again),
                                color = colors.dim,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        // "Done" clears the flag and pops back (Home when the
                        // counter was opened from a notification, otherwise the
                        // originating screen).
                        TextButton(
                            onClick = {
                                viewModel.onRoutineCompleteAcknowledged()
                                onBack()
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.routine_complete_done),
                                color = colors.sage,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (showSessionSummary) {
        SessionSummaryDialog(
            startedAtMillis = state.sessionStartedAtMillis,
            elapsedSeconds = elapsedSeconds,
            totalCount = state.totalCount,
            onDismiss = { showSessionSummary = false },
        )
    }

    if (showNotesDialog) {
        AlertDialog(
            onDismissRequest = { showNotesDialog = false },
            title = { Text(stringResource(R.string.counter_notes_dialog_title)) },
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.dim,
            shape = DialogShape,
            text = {
                Text(
                    text = state.dhikr.note.ifBlank { stringResource(R.string.counter_notes_empty) },
                    fontSize = 14.sp,
                    color = colors.dim,
                )
            },
            confirmButton = {
                TextButton(onClick = { showNotesDialog = false }) {
                    Text(
                        text = stringResource(R.string.session_summary_close),
                        color = colors.sage,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )
    }
}

/**
 * Session-summary dialog opened by tapping the top-bar elapsed-time label.
 * Pure display of state already tracked by CounterViewModel — started-at
 * clock time, duration, total counts, and pace — no new persistence.
 */
@Composable
private fun SessionSummaryDialog(
    startedAtMillis: Long,
    elapsedSeconds: Int,
    totalCount: Int,
    onDismiss: () -> Unit,
) {
    val colors = DhikrTheme.colors
    val startedAtLabel = remember(startedAtMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startedAtMillis))
    }
    val rateText = if (elapsedSeconds > 4) {
        val rate = (totalCount.toFloat() / (elapsedSeconds / 60f)).toInt()
        if (rate > 0) stringResource(R.string.session_summary_rate, rate) else null
    } else {
        null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_summary_title)) },
        containerColor = colors.card,
        titleContentColor = colors.text,
        textContentColor = colors.dim,
        shape = DialogShape,
        text = {
            Column {
                SessionSummaryLine(stringResource(R.string.session_summary_started, startedAtLabel))
                SessionSummaryLine(stringResource(R.string.session_summary_duration, formatDuration(elapsedSeconds)))
                SessionSummaryLine(stringResource(R.string.session_summary_counts, totalCount))
                SessionSummaryLine(rateText ?: stringResource(R.string.session_summary_rate_unavailable))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.session_summary_close),
                    color = colors.sage,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

/**
 * Wraps an icon-only button so a long-press pops up its name as a small
 * tooltip label — every button on this screen is icon-only (no visible
 * text), so this is the only way to surface what each one does short of
 * TalkBack. [label] doubles as the tooltip text and should be the same
 * string already passed as the wrapped button's contentDescription.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconButtonTooltip(
    label: String,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        content = content,
    )
}

@Composable
private fun SessionSummaryLine(text: String) {
    val colors = DhikrTheme.colors
    Text(
        text = text,
        fontSize = 14.sp,
        color = colors.dim,
        modifier = Modifier.padding(top = 4.dp),
    )
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
