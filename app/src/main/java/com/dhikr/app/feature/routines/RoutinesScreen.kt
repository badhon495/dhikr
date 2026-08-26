package com.dhikr.app.feature.routines

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.core.database.dao.RoutineWithSteps
import com.dhikr.app.core.database.entity.RoutineEntity
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.DialogShape
import com.dhikr.app.ui.theme.PillShape

@Composable
fun RoutinesScreen(
    viewModel: RoutinesViewModel,
    onStartRoutine: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors

    Column(modifier = Modifier.fillMaxSize().background(colors.bg).padding(16.dp)) {
        Text(stringResource(R.string.routines_title), fontSize = 23.sp, color = colors.text)
        LazyColumn(
            modifier = Modifier.padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(state.routines, key = { it.routine.id }) { routineWithSteps ->
                RoutineCard(
                    routineWithSteps = routineWithSteps,
                    tasbihNamesById = state.tasbihNamesById,
                    onStart = { onStartRoutine(routineWithSteps.routine.id) },
                    onDelete = { viewModel.onDeleteRoutine(routineWithSteps.routine) },
                )
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(top = 4.dp)
                        .dashedBorder(color = colors.faint, shape = PillShape)
                        .clip(PillShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.routines_new), color = colors.dim)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoutineCard(
    routineWithSteps: RoutineWithSteps,
    tasbihNamesById: Map<String, String>,
    onStart: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = DhikrTheme.colors
    val steps = routineWithSteps.steps.sortedBy { it.stepOrder }
    val totalCount = steps.sumOf { it.targetCount }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(colors.card)
            .combinedClickable(
                // Card body tap has no destination — editing a routine is a
                // known, intentionally deferred gap (no editor screen exists
                // yet). Long-press-to-delete is the only whole-card gesture.
                onClick = {},
                onLongClick = { showDeleteDialog = true },
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(routineWithSteps.routine.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.text)
                Text(
                    stringResource(R.string.routines_step_count, steps.size, totalCount),
                    fontSize = 12.5.sp,
                    color = colors.dim,
                )
            }
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(colors.sage)
                    .clickable { onStart() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.routines_start), color = colors.onSage)
            }
        }
        steps.forEachIndexed { index, step ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            color = colors.line,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    .padding(top = 10.dp, bottom = 2.dp),
            ) {
                Text("${index + 1}", fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(end = 10.dp))
                Text(
                    text = tasbihNamesById[step.tasbihId] ?: step.tasbihId,
                    fontSize = 13.5.sp,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${step.targetCount}",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.terra,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = stringResource(R.string.routines_drag_handle_content_description),
                    tint = colors.faint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.routines_delete_confirm_title)) },
            text = { Text(stringResource(R.string.routines_delete_confirm_body)) },
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.dim,
            shape = DialogShape,
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text(
                        text = stringResource(R.string.routines_delete_confirm_action),
                        color = colors.terra,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(
                        text = stringResource(R.string.routines_delete_cancel_action),
                        color = colors.dim,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )
    }
}

/**
 * Compose has no built-in dashed border, so this draws one directly: a stroke
 * path with [PathEffect.dashPathEffect] traced along the given [shape]'s
 * outline. Used for the Routines footer's "+ New routine" pill per
 * design/README.md §5 ("a 50dp dashed-border pill").
 */
private fun Modifier.dashedBorder(
    color: Color,
    shape: Shape,
    strokeWidth: Dp = 1.dp,
    dashLength: Dp = 6.dp,
    gapLength: Dp = 4.dp,
): Modifier = drawBehind {
    val strokeWidthPx = strokeWidth.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength.toPx(), gapLength.toPx()), 0f)
    when (outline) {
        is androidx.compose.ui.graphics.Outline.Generic ->
            drawPath(outline.path, color = color, style = Stroke(width = strokeWidthPx, pathEffect = dashPathEffect))
        is androidx.compose.ui.graphics.Outline.Rounded ->
            drawRoundRect(
                color = color,
                cornerRadius = CornerRadius(outline.roundRect.topLeftCornerRadius.x, outline.roundRect.topLeftCornerRadius.y),
                size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidthPx / 2f, strokeWidthPx / 2f),
                style = Stroke(width = strokeWidthPx, pathEffect = dashPathEffect),
            )
        is androidx.compose.ui.graphics.Outline.Rectangle ->
            drawRect(color = color, style = Stroke(width = strokeWidthPx, pathEffect = dashPathEffect))
    }
}
