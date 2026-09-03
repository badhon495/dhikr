package com.dhikr.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.core.notifications.ReminderNotifications
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.DialogShape
import com.dhikr.app.ui.theme.ListRowShape

/**
 * The reminder editor block shared by the routine and tasbih editors: an
 * enable toggle (which also asks for POST_NOTIFICATIONS on Android 13+), a
 * time picker, and a 7-day repeat selector. Pure state in, callbacks out —
 * the host owns persistence and alarm scheduling.
 *
 * [descriptionRes] is the one line that differs between hosts ("start this
 * routine" vs "recite this dhikr").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSection(
    enabled: Boolean,
    minuteOfDay: Int,
    daysMask: Int,
    descriptionRes: Int,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (Int) -> Unit,
    onDayToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DhikrTheme.colors
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> permissionDenied = !granted }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.reminder_section_title).uppercase(),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.dim,
            modifier = Modifier.padding(bottom = 6.dp),
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
                    text = stringResource(descriptionRes),
                    fontSize = 11.5.sp,
                    color = colors.faint,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    onEnabledChange(checked)
                    if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !ReminderNotifications.hasPermission(context)
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }

        if (enabled) {
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
                    text = formatTime12Hour(minuteOfDay),
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
                    val selected = daysMask == 0 || (daysMask and (1 shl bit)) != 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 3.dp)
                            .heightIn(min = 40.dp)
                            .clip(CircleShape)
                            .background(if (selected) colors.sage else colors.surface)
                            .clickable(role = Role.Button) { onDayToggle(bit) },
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
    }

    if (showTimePicker) {
        val picker = rememberTimePickerState(
            initialHour = minuteOfDay / 60,
            initialMinute = minuteOfDay % 60,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = colors.card,
            shape = DialogShape,
            text = { TimePicker(state = picker) },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(picker.hour * 60 + picker.minute)
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

private fun formatTime12Hour(minuteOfDay: Int): String {
    val hour24 = minuteOfDay / 60
    val minute = minuteOfDay % 60
    val hour12 = when (hour24 % 12) {
        0 -> 12
        else -> hour24 % 12
    }
    val period = if (hour24 < 12) "AM" else "PM"
    return "%d:%02d %s".format(hour12, minute, period)
}
