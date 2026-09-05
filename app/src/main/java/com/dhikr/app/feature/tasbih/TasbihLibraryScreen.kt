package com.dhikr.app.feature.tasbih

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.core.database.entity.TasbihEntity
import com.dhikr.app.feature.counter.noteIcon
import com.dhikr.app.ui.TASBIH_LIST_TEST_TAG
import com.dhikr.app.ui.headingSemantics
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.DialogShape
import com.dhikr.app.ui.theme.ListRowShape
import com.dhikr.app.ui.theme.PillShape

@Composable
fun TasbihLibraryScreen(
    viewModel: TasbihLibraryViewModel,
    onOpenTasbih: (String) -> Unit,
    onNewTasbih: () -> Unit,
    onEditTasbih: (String) -> Unit,
    scrollToTopSignal: Int = 0,
) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors
    val listState = rememberLazyListState()

    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) listState.animateScrollToItem(0)
    }

    // Long-press action menu (Edit/Delete) state — which Tasbih, if any,
    // currently has its menu open. Built-in Tasbih are included: the user can
    // delete them too (the repository still blocks a delete while a routine
    // references the Tasbih, surfacing the "blocked" dialog below).
    var actionMenuTarget by remember { mutableStateOf<TasbihEntity?>(null) }
    var deleteConfirmTarget by remember { mutableStateOf<TasbihEntity?>(null) }
    var deleteBlockedMessage by remember { mutableStateOf<TasbihDeleteBlocked?>(null) }
    var notesTarget by remember { mutableStateOf<TasbihEntity?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.deleteBlocked.collect { deleteBlockedMessage = it }
    }

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
                text = stringResource(R.string.tasbih_library_title),
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                modifier = Modifier.headingSemantics(),
            )
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(colors.sage)
                    .clickable(role = Role.Button) { onNewTasbih() }
                    .minTapTarget()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.tasbih_library_new),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSage,
                )
            }
        }

        // ---- Search pill: surface fill, 1px line border, magnifier in faint ----
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
                contentDescription = stringResource(R.string.tasbih_library_search_content_description),
                tint = colors.faint,
                modifier = Modifier.size(18.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                if (state.query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.tasbih_library_search_placeholder),
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

        // ---- Result-count line: switches wording depending on query state ----
        Text(
            text = if (state.query.isBlank()) {
                stringResource(
                    R.string.tasbih_library_result_count_all,
                    state.builtInCount,
                    state.customCount,
                )
            } else {
                stringResource(
                    R.string.tasbih_library_result_count_filtered,
                    state.results.size,
                    state.builtInCount + state.customCount,
                    state.query,
                )
            },
            fontSize = 11.5.sp,
            color = colors.faint,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )

        // ---- Results: empty state or the scrollable list ----
        if (state.results.isEmpty() && state.query.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.tasbih_library_empty),
                    fontSize = 14.sp,
                    color = colors.faint,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().testTag(TASBIH_LIST_TEST_TAG),
            ) {
                items(state.results, key = { it.id }) { tasbih ->
                    TasbihRow(
                        tasbih = tasbih,
                        progress = state.progressByTasbihId[tasbih.id] ?: 0f,
                        onClick = { onOpenTasbih(tasbih.id) },
                        onToggleFavorite = {
                            viewModel.onToggleFavorite(tasbih.id, tasbih.isFavorite)
                        },
                        onOpenNotes = { notesTarget = tasbih },
                        // Long-press opens the Edit/Delete menu for every
                        // Tasbih, built-in included.
                        onLongPress = { actionMenuTarget = tasbih },
                    )
                }
                item { Box(modifier = Modifier.height(8.dp)) }
            }
        }
    }

    actionMenuTarget?.let { tasbih ->
        TasbihActionMenu(
            tasbih = tasbih,
            onDismiss = { actionMenuTarget = null },
            onEdit = {
                actionMenuTarget = null
                onEditTasbih(tasbih.id)
            },
            onDelete = {
                actionMenuTarget = null
                deleteConfirmTarget = tasbih
            },
        )
    }

    deleteConfirmTarget?.let { tasbih ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            title = { Text(stringResource(R.string.tasbih_library_delete_confirm_title)) },
            text = { Text(stringResource(R.string.tasbih_library_delete_confirm_body)) },
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.dim,
            shape = DialogShape,
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDeleteTasbih(tasbih)
                    deleteConfirmTarget = null
                }) {
                    Text(
                        text = stringResource(R.string.tasbih_library_delete_confirm_action),
                        color = colors.terra,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTarget = null }) {
                    Text(
                        text = stringResource(R.string.tasbih_library_delete_cancel_action),
                        color = colors.dim,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )
    }

    deleteBlockedMessage?.let { blocked ->
        AlertDialog(
            onDismissRequest = { deleteBlockedMessage = null },
            title = { Text(stringResource(R.string.tasbih_library_delete_blocked_title, blocked.tasbihName)) },
            text = { Text(stringResource(R.string.tasbih_library_delete_blocked_body, blocked.routineNames.joinToString())) },
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.dim,
            shape = DialogShape,
            confirmButton = {
                TextButton(onClick = { deleteBlockedMessage = null }) {
                    Text(
                        text = stringResource(R.string.tasbih_library_delete_blocked_dismiss),
                        color = colors.terra,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )
    }

    notesTarget?.let { tasbih ->
        AlertDialog(
            onDismissRequest = { notesTarget = null },
            title = { Text(stringResource(R.string.tasbih_library_notes_dialog_title)) },
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.dim,
            shape = DialogShape,
            text = {
                Text(
                    text = tasbih.note.ifBlank { stringResource(R.string.tasbih_library_notes_empty) },
                    fontSize = 14.sp,
                    color = colors.dim,
                )
            },
            confirmButton = {
                TextButton(onClick = { notesTarget = null }) {
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

@Composable
private fun TasbihActionMenu(
    tasbih: TasbihEntity,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = DhikrTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tasbih_library_actions_title, tasbih.name)) },
        containerColor = colors.card,
        titleContentColor = colors.text,
        shape = DialogShape,
        text = {
            Column {
                Text(
                    text = stringResource(R.string.tasbih_library_actions_edit),
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
                    text = stringResource(R.string.tasbih_library_actions_delete),
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
                    text = stringResource(R.string.tasbih_library_delete_cancel_action),
                    color = colors.dim,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TasbihRow(
    tasbih: TasbihEntity,
    progress: Float,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenNotes: () -> Unit,
    onLongPress: (() -> Unit)?,
) {
    val colors = DhikrTheme.colors
    val favoriteDescription = stringResource(R.string.tasbih_library_favorite_content_description)
    val favoriteState = stringResource(
        if (tasbih.isFavorite) R.string.tasbih_library_favorite_state_on
        else R.string.tasbih_library_favorite_state_off,
    )
    val notesDescription = stringResource(R.string.tasbih_library_notes_content_description)
    val fill = progress.coerceIn(0f, 1f)

    Box(
        // The row's own tap target (opens the Dhikr) is this outer
        // combinedClickable. The favorite heart below sits in its own nested
        // `clickable` Box; a click consumed by a nested clickable region does
        // not propagate to an ancestor's clickable in Compose, so tapping the
        // heart toggles the favorite WITHOUT also firing this row's
        // onClick/opening the Dhikr. Long-press (only wired for non-built-in
        // Tasbih — onLongPress is null for built-in rows) opens the
        // Edit/Delete action menu (finding #4 + #5), matching
        // RoutinesScreen's long-press-to-delete pattern.
        modifier = Modifier
            .fillMaxWidth()
            .clip(ListRowShape)
            .background(colors.card)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        // Green fill growing left-to-right with today's counting progress
        // toward this Tasbih's total goal.
        if (fill > 0f) {
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill)
                        .background(colors.sageSoft),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tasbih.name,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = metaText(tasbih),
                fontSize = 11.5.sp,
                color = colors.dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = tasbih.pronunciation,
                fontSize = 12.sp,
                color = colors.faint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            text = tasbih.arabic,
            fontSize = 18.sp,
            color = colors.dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier
                .widthIn(max = 96.dp)
                .padding(horizontal = 8.dp),
        )
        // Note + favorite stacked vertically (not side by side) so the two
        // buttons share one column's width instead of two.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    // Read-only view of the Tasbih's note (edited via the editor
                    // screen); an empty note still opens the dialog with a
                    // placeholder line rather than looking unresponsive.
                    .clickable(role = Role.Button, onClickLabel = notesDescription) { onOpenNotes() }
                    .minTapTarget()
                    .semantics { contentDescription = notesDescription }
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = noteIcon(),
                    contentDescription = null,
                    tint = if (tasbih.note.isNotBlank()) colors.dim else colors.faint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Switch, onClickLabel = favoriteDescription) { onToggleFavorite() }
                    .minTapTarget()
                    .semantics {
                        contentDescription = favoriteDescription
                        stateDescription = favoriteState
                    }
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (tasbih.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = if (tasbih.isFavorite) colors.terra else colors.faint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        }
    }
}

/** "33 × 3 laps" when the Tasbih has more than one lap, else "100 per lap". */
@Composable
private fun metaText(tasbih: TasbihEntity): String = if (tasbih.lapCount > 1) {
    stringResource(R.string.tasbih_library_meta_laps, tasbih.lapTarget, tasbih.lapCount)
} else {
    stringResource(R.string.tasbih_library_meta_per_lap, tasbih.lapTarget)
}
