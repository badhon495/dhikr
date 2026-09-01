package com.dhikr.app.feature.routines

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.DialogShape
import com.dhikr.app.ui.theme.ListRowShape
import com.dhikr.app.ui.theme.PillShape

@Composable
fun RoutineEditorScreen(
    viewModel: RoutineEditorViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors
    var showPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // ---- Header: back chevron + title (title hugs the top of the row) ----
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onBack() }
                    .minTapTarget()
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = colors.text,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = stringResource(
                    if (state.isEditing) R.string.routine_editor_title_edit
                    else R.string.routine_editor_title_new,
                ),
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
            )
        }

        FieldLabel(stringResource(R.string.routine_editor_name_label))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(PillShape)
                .background(colors.card)
                .border(1.dp, colors.line, PillShape)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (state.name.isEmpty()) {
                Text(
                    text = stringResource(R.string.routine_editor_name_placeholder),
                    fontSize = 14.sp,
                    color = colors.faint,
                )
            }
            BasicTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = colors.text),
                cursorBrush = SolidColor(colors.text),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FieldLabel(
            text = stringResource(R.string.routine_editor_steps_label),
            modifier = Modifier.padding(top = 20.dp),
        )

        if (state.steps.isEmpty()) {
            Text(
                text = stringResource(R.string.routine_editor_no_steps),
                fontSize = 13.sp,
                color = colors.faint,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            // A plain Column (not LazyColumn) — the whole screen already
            // scrolls, and routines are short (a handful of steps).
            state.steps.forEachIndexed { index, step ->
                StepCard(
                    position = index + 1,
                    name = state.tasbihNamesById[step.tasbihId] ?: step.tasbihId,
                    count = step.targetCount,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.steps.lastIndex,
                    onCountChange = { viewModel.onStepCountChange(index, it) },
                    onMoveUp = { viewModel.onMoveStep(index, up = true) },
                    onMoveDown = { viewModel.onMoveStep(index, up = false) },
                    onRemove = { viewModel.onRemoveStep(index) },
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .heightIn(min = 48.dp)
                .clip(PillShape)
                .background(colors.surface)
                .clickable(role = Role.Button, enabled = state.availableTasbih.isNotEmpty()) {
                    showPicker = true
                }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.routines_add_step),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
            )
        }

        ReminderSection(viewModel = viewModel, state = state)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .heightIn(min = 52.dp)
                .clip(PillShape)
                .background(if (state.canSave) colors.terra else colors.track)
                .clickable(enabled = state.canSave, role = Role.Button) { viewModel.onSave { onBack() } }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.routine_editor_save),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.card,
            )
        }
        Text(
            text = stringResource(R.string.routine_editor_footer),
            fontSize = 11.5.sp,
            color = colors.faint,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 16.dp),
        )
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.routine_editor_pick_tasbih_title)) },
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.dim,
            shape = DialogShape,
            text = {
                LazyColumn {
                    itemsIndexed(state.availableTasbih, key = { _, t -> t.id }) { _, tasbih ->
                        Text(
                            text = tasbih.name,
                            fontSize = 14.5.sp,
                            color = colors.text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) {
                                    viewModel.onAddStep(tasbih.id)
                                    showPicker = false
                                }
                                .minTapTarget()
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSection(
    viewModel: RoutineEditorViewModel,
    state: RoutineEditorUiState,
) {
    val colors = DhikrTheme.colors
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> permissionDenied = !granted }

    FieldLabel(
        text = stringResource(R.string.reminder_section_title),
        modifier = Modifier.padding(top = 24.dp),
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(ListRowShape)
            .background(colors.card)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.reminder_toggle_label),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
            )
            Text(
                text = stringResource(R.string.reminder_toggle_desc),
                fontSize = 11.5.sp,
                color = colors.faint,
            )
        }
        Switch(
            checked = state.reminderEnabled,
            onCheckedChange = { checked ->
                viewModel.onReminderEnabledChange(checked)
                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !com.dhikr.app.core.notifications.ReminderNotifications.hasPermission(context)
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }

    if (state.reminderEnabled) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .heightIn(min = 48.dp)
                .clip(ListRowShape)
                .background(colors.card)
                .clickable(role = Role.Button) { showTimePicker = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.reminder_time_label),
                fontSize = 13.sp,
                color = colors.dim,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "%02d:%02d".format(
                    state.reminderMinuteOfDay / 60,
                    state.reminderMinuteOfDay % 60,
                ),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
            )
        }

        Text(
            text = stringResource(R.string.reminder_days_label).uppercase(),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.dim,
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
        )
        val dayLabels = listOf(
            R.string.reminder_day_sun, R.string.reminder_day_mon, R.string.reminder_day_tue,
            R.string.reminder_day_wed, R.string.reminder_day_thu, R.string.reminder_day_fri,
            R.string.reminder_day_sat,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            dayLabels.forEachIndexed { bit, labelRes ->
                val selected = state.reminderDays == 0 || (state.reminderDays and (1 shl bit)) != 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 3.dp)
                        .heightIn(min = 40.dp)
                        .clip(CircleShape)
                        .background(if (selected) colors.sage else colors.surface)
                        .clickable(role = Role.Button) { viewModel.onReminderDayToggle(bit) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(labelRes),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) colors.onSage else colors.dim,
                    )
                }
            }
        }

        if (permissionDenied) {
            Text(
                text = stringResource(R.string.reminder_permission_hint),
                fontSize = 11.5.sp,
                color = colors.faint,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (showTimePicker) {
        val picker = rememberTimePickerState(
            initialHour = state.reminderMinuteOfDay / 60,
            initialMinute = state.reminderMinuteOfDay % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = colors.card,
            shape = DialogShape,
            text = { TimePicker(state = picker) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onReminderTimeChange(picker.hour * 60 + picker.minute)
                    showTimePicker = false
                }) { Text(stringResource(R.string.routine_complete_done), color = colors.text) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.routines_delete_cancel_action), color = colors.dim)
                }
            },
        )
    }
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = DhikrTheme.colors.dim,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun StepCard(
    position: Int,
    name: String,
    count: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onCountChange: (Int) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = DhikrTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(ListRowShape)
            .background(colors.card)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("$position", fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(end = 10.dp))
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            SquareButton(label = "✕", contentDescription = stringResource(R.string.routine_editor_remove_step), onClick = onRemove)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.routine_editor_step_count),
                fontSize = 11.5.sp,
                color = colors.dim,
                modifier = Modifier.padding(end = 8.dp),
            )
            SquareButton(label = "−", contentDescription = null, onClick = { onCountChange(count - 1) })
            NumberField(
                value = count,
                onValueChange = onCountChange,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            SquareButton(label = "+", contentDescription = null, bg = colors.sage, fg = colors.onSage, onClick = { onCountChange(count + 1) })
            Box(modifier = Modifier.weight(1f))
            SquareButton(
                label = "▲",
                contentDescription = stringResource(R.string.routine_editor_move_up),
                enabled = canMoveUp,
                onClick = onMoveUp,
            )
            SquareButton(
                label = "▼",
                contentDescription = stringResource(R.string.routine_editor_move_down),
                enabled = canMoveDown,
                onClick = onMoveDown,
            )
        }
    }
}

/**
 * Inline number pill the user can type into directly. Digits-only, coerced by
 * the caller's [onValueChange]; a blank field commits nothing so the last value
 * stands. Stays in sync when the value changes elsewhere (e.g. −/+ buttons).
 */
@Composable
private fun NumberField(value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = DhikrTheme.colors
    var text by rememberSaveable { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (text.toIntOrNull() != value) text = value.toString()
    }
    Box(
        modifier = modifier
            .widthIn(min = 56.dp)
            .heightIn(min = 36.dp)
            .clip(PillShape)
            .background(colors.surface)
            .border(1.dp, colors.line, PillShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(5)
                text = digits
                digits.toIntOrNull()?.let(onValueChange)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(colors.text),
        )
    }
}

@Composable
private fun SquareButton(
    label: String,
    contentDescription: String?,
    enabled: Boolean = true,
    bg: Color = DhikrTheme.colors.surface,
    fg: Color = DhikrTheme.colors.text,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(bg)
            .clickable(role = Role.Button, enabled = enabled, onClickLabel = contentDescription) { onClick() }
            .minTapTarget(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}
