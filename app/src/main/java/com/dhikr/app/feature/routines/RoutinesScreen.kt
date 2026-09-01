package com.dhikr.app.feature.routines

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.core.database.dao.RoutineWithSteps
import com.dhikr.app.ui.headingSemantics
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.DialogShape
import com.dhikr.app.ui.theme.PillShape

@Composable
fun RoutinesScreen(
    viewModel: RoutinesViewModel,
    onStartRoutine: (String) -> Unit,
    onNewRoutine: () -> Unit,
    onEditRoutine: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors

    // Long-press action menu (Edit/Delete) target — which routine, if any, has
    // its menu open. Presets are included: the user can edit and delete them.
    var actionMenuTarget by remember { mutableStateOf<RoutineWithSteps?>(null) }
    var deleteConfirmTarget by remember { mutableStateOf<RoutineWithSteps?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        // ---- Header: title + "+ New" pill ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Text(
                stringResource(R.string.routines_title),
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                modifier = Modifier.headingSemantics(),
            )
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(colors.sage)
                    .clickable(role = Role.Button) { onNewRoutine() }
                    .minTapTarget()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.routines_new_short),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSage,
                )
            }
        }

        // ---- Search pill ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .heightIn(min = 48.dp)
                .clip(PillShape)
                .background(colors.surface)
                .padding(horizontal = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = stringResource(R.string.routines_search_content_description),
                tint = colors.faint,
                modifier = Modifier.size(18.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                if (state.query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.routines_search_placeholder),
                        fontSize = 14.sp,
                        color = colors.faint,
                    )
                }
                BasicTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = colors.text),
                    cursorBrush = SolidColor(colors.text),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---- Result-count line ----
        Text(
            text = if (state.query.isBlank()) {
                stringResource(R.string.routines_result_count_all, state.builtInCount, state.customCount)
            } else {
                stringResource(
                    R.string.routines_result_count_filtered,
                    state.routines.size,
                    state.totalCount,
                    state.query,
                )
            },
            fontSize = 11.5.sp,
            color = colors.faint,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )

        // ---- Results: empty state or the list ----
        if (state.routines.isEmpty() && state.query.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.routines_empty),
                    fontSize = 14.sp,
                    color = colors.faint,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(state.routines, key = { it.routine.id }) { routineWithSteps ->
                    RoutineCard(
                        routineWithSteps = routineWithSteps,
                        tasbihNamesById = state.tasbihNamesById,
                        completedToday = routineWithSteps.routine.id in state.completedTodayIds,
                        progress = state.progressByRoutineId[routineWithSteps.routine.id] ?: 0f,
                        onStart = { onStartRoutine(routineWithSteps.routine.id) },
                        onLongPress = { actionMenuTarget = routineWithSteps },
                        onToggleFavorite = {
                            viewModel.onToggleFavorite(
                                routineWithSteps.routine.id,
                                routineWithSteps.routine.isFavorite,
                            )
                        },
                    )
                }
                item { Box(modifier = Modifier.height(8.dp)) }
            }
        }
    }

    actionMenuTarget?.let { routineWithSteps ->
        RoutineActionMenu(
            name = routineWithSteps.routine.name,
            onDismiss = { actionMenuTarget = null },
            onEdit = {
                actionMenuTarget = null
                onEditRoutine(routineWithSteps.routine.id)
            },
            onDelete = {
                actionMenuTarget = null
                deleteConfirmTarget = routineWithSteps
            },
        )
    }

    deleteConfirmTarget?.let { routineWithSteps ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            title = { Text(stringResource(R.string.routines_delete_confirm_title)) },
            text = { Text(stringResource(R.string.routines_delete_confirm_body)) },
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.dim,
            shape = DialogShape,
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDeleteRoutine(routineWithSteps.routine)
                    deleteConfirmTarget = null
                }) {
                    Text(
                        text = stringResource(R.string.routines_delete_confirm_action),
                        color = colors.terra,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTarget = null }) {
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

@Composable
private fun RoutineActionMenu(
    name: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = DhikrTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routines_actions_title, name)) },
        containerColor = colors.card,
        titleContentColor = colors.text,
        shape = DialogShape,
        text = {
            Column {
                Text(
                    text = stringResource(R.string.routines_actions_edit),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onEdit() }
                        .minTapTarget()
                        .padding(vertical = 12.dp),
                )
                Text(
                    text = stringResource(R.string.routines_actions_delete),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.terra,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onDelete() }
                        .minTapTarget()
                        .padding(vertical = 12.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.routines_delete_cancel_action),
                    color = colors.dim,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoutineCard(
    routineWithSteps: RoutineWithSteps,
    tasbihNamesById: Map<String, String>,
    completedToday: Boolean,
    progress: Float,
    onStart: () -> Unit,
    onLongPress: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val colors = DhikrTheme.colors
    val steps = routineWithSteps.steps.sortedBy { it.stepOrder }
    val totalCount = steps.sumOf { it.targetCount }
    val isFavorite = routineWithSteps.routine.isFavorite
    val favoriteDescription = stringResource(R.string.routines_favorite_content_description)
    val favoriteState = stringResource(
        if (isFavorite) R.string.routines_favorite_state_on
        else R.string.routines_favorite_state_off,
    )

    val partialProgress = if (completedToday) 0f else progress.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(if (completedToday) colors.sageSoft else colors.card)
            .combinedClickable(
                // Card body tap starts the routine in the counter. Long-press
                // opens the Edit/Delete action menu (presets included).
                onClick = onStart,
                onLongClick = onLongPress,
            ),
    ) {
      // Green fill that grows left-to-right with today's progress through the
      // routine. Sits behind the content; a fully completed routine uses the
      // solid `sageSoft` card background above instead.
      if (partialProgress > 0f) {
          Box(modifier = Modifier.matchParentSize()) {
              Box(
                  modifier = Modifier
                      .fillMaxHeight()
                      .fillMaxWidth(partialProgress)
                      .background(colors.sageSoft),
              )
          }
      }

      Column(modifier = Modifier.padding(18.dp)) {
        Column {
            Text(
                routineWithSteps.routine.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                // Keep long names clear of the corner heart.
                modifier = Modifier.padding(end = 32.dp),
            )
            Text(
                stringResource(R.string.routines_step_count, steps.size, totalCount),
                fontSize = 12.5.sp,
                color = colors.dim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Column(modifier = Modifier.padding(top = 6.dp)) {
            steps.forEachIndexed { index, step ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehindLine(colors.line)
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        "${index + 1}",
                        fontSize = 12.sp,
                        color = colors.faint,
                        modifier = Modifier.width(22.dp),
                    )
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
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .widthIn(min = 40.dp),
                    )
                }
            }
        }
      }

      // Favourite heart, pinned to the card's top-right corner as an overlay so
      // it never distorts the header layout. Nested clickable: a tap here toggles
      // the favourite and does not propagate to the card's combinedClickable
      // (which starts the routine), matching TasbihRow's heart.
      Box(
          modifier = Modifier
              // Sits near the right edge, vertically centred on the name +
              // step-count block (top offset ≈ header centre − half the tap target).
              .align(Alignment.TopEnd)
              .padding(top = 12.dp, end = 4.dp)
              .clip(CircleShape)
              .clickable(role = Role.Switch, onClickLabel = favoriteDescription) { onToggleFavorite() }
              .minTapTarget()
              .semantics {
                  contentDescription = favoriteDescription
                  stateDescription = favoriteState
              },
          contentAlignment = Alignment.Center,
      ) {
          Icon(
              imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
              contentDescription = null,
              tint = if (isFavorite) colors.terra else colors.faint,
              modifier = Modifier.size(20.dp),
          )
      }
    }
}

/** Thin top divider line for a routine step row. */
private fun Modifier.drawBehindLine(color: Color): Modifier = drawBehind {
    drawLine(
        color = color,
        start = Offset(0f, 0f),
        end = Offset(size.width, 0f),
        strokeWidth = 1.dp.toPx(),
    )
}
