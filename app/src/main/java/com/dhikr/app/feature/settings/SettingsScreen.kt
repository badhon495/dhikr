package com.dhikr.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.core.datastore.ThemeMode
import com.dhikr.app.ui.headingSemantics
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.ListRowShape
import com.dhikr.app.ui.theme.PillShape

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            fontSize = 23.sp,
            color = colors.text,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )

        // ---- Appearance ----
        SettingsSection(stringResource(R.string.settings_appearance)) {
            val themeLabels = listOf(
                ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
            )
            ChoiceRow(
                options = themeLabels,
                selected = state.themeMode,
                onSelect = viewModel::onThemeModeChange,
            )
        }

        // ---- Counting ----
        SettingsSection(stringResource(R.string.settings_counting)) {
            SwitchRow(
                title = stringResource(R.string.settings_haptics),
                description = stringResource(R.string.settings_haptics_desc),
                checked = state.hapticsEnabled,
                onCheckedChange = viewModel::onHapticsEnabledChange,
            )
            Column(modifier = Modifier.padding(top = 14.dp)) {
                Text(
                    stringResource(R.string.settings_daily_goal),
                    fontSize = 14.sp,
                    color = colors.text,
                )
                Text(
                    stringResource(R.string.settings_daily_goal_desc),
                    fontSize = 12.sp,
                    color = colors.faint,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                )
                ChoiceRow(
                    options = state.dailyGoalOptions.map { it to it.toString() },
                    selected = state.dailyGoalTarget,
                    onSelect = viewModel::onDailyGoalChange,
                )
            }
        }

        // ---- Accessibility ----
        SettingsSection(stringResource(R.string.settings_accessibility)) {
            SwitchRow(
                title = stringResource(R.string.settings_reduce_motion),
                description = stringResource(R.string.settings_reduce_motion_desc),
                checked = state.reducedMotion,
                onCheckedChange = viewModel::onReducedMotionChange,
            )
        }

        // ---- About ----
        SettingsSection(stringResource(R.string.settings_about)) {
            AboutLine(stringResource(R.string.settings_about_offline))
            AboutLine(stringResource(R.string.settings_about_no_account))
            AboutLine(stringResource(R.string.settings_about_no_upload))
            if (state.appVersion.isNotEmpty()) {
                Text(
                    stringResource(R.string.settings_version, state.appVersion),
                    fontSize = 12.sp,
                    color = colors.faint,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    val colors = DhikrTheme.colors
    Column(modifier = Modifier.padding(bottom = 18.dp)) {
        Text(
            text = title.uppercase(),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.dim,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .headingSemantics(),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ListRowShape)
                .background(colors.card)
                .border(1.dp, colors.line, ListRowShape)
                .padding(16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val colors = DhikrTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(if (isSelected) colors.sage else colors.surface)
                    .clickable(role = Role.RadioButton) { if (!isSelected) onSelect(value) }
                    .minTapTarget()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) colors.onSage else colors.text,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = DhikrTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // No .clip(ListRowShape) here: this Row is shorter than twice the
            // shape's 22dp corner radius, so the clamped corner curve bit into
            // the top-left of the title text ("Haptic feedback" rendered as
            // "|aptic feedback"). The Row has no background/border of its own —
            // the only thing the clip bounded was the click ripple, and a
            // rectangular ripple inside the section card is fine.
            .clickable(role = Role.Switch) { onCheckedChange(!checked) },
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 14.sp, color = colors.text)
            Text(
                description,
                fontSize = 12.sp,
                color = colors.faint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = checked,
            // Toggling is handled by the whole row's clickable above; the
            // Switch itself is a non-interactive indicator so screen readers
            // announce a single Switch node rather than two overlapping targets.
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onSage,
                checkedTrackColor = colors.sage,
                uncheckedThumbColor = colors.faint,
                uncheckedTrackColor = colors.surface,
                uncheckedBorderColor = colors.line,
            ),
        )
    }
}

@Composable
private fun AboutLine(text: String) {
    val colors = DhikrTheme.colors
    Text(
        text = text,
        fontSize = 13.sp,
        color = colors.dim,
        modifier = Modifier.padding(vertical = 3.dp),
    )
}
