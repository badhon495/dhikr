package com.dhikr.app.feature.tasbih

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.ListRowShape
import com.dhikr.app.ui.theme.NotoNaskhArabic
import com.dhikr.app.ui.theme.PillShape

@Composable
fun TasbihEditorScreen(viewModel: TasbihEditorViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors

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
                    if (state.isEditingExisting) R.string.tasbih_editor_title_edit
                    else R.string.tasbih_editor_title_new,
                ),
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
            )
        }

        LabeledField(stringResource(R.string.tasbih_editor_name_label)) {
            PillTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                placeholder = stringResource(R.string.tasbih_editor_name_placeholder),
            )
        }

        LabeledField(
            maybeRequired(
                stringResource(R.string.tasbih_editor_arabic_label),
                state.arabicRequired,
            ),
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                PillTextField(
                    value = state.arabic,
                    onValueChange = viewModel::onArabicChange,
                    placeholder = stringResource(R.string.tasbih_editor_arabic_placeholder),
                    textStyle = TextStyle(
                        fontFamily = NotoNaskhArabic,
                        fontSize = 18.sp,
                        color = colors.text,
                    ),
                )
            }
        }

        LabeledField(
            maybeRequired(
                stringResource(R.string.tasbih_editor_pronunciation_label),
                state.pronunciationRequired,
            ),
        ) {
            PillTextField(
                value = state.pronunciation,
                onValueChange = viewModel::onPronunciationChange,
                placeholder = stringResource(R.string.tasbih_editor_pronunciation_placeholder),
            )
        }

        LabeledField(stringResource(R.string.tasbih_editor_translation_label)) {
            PillTextField(
                value = state.translation,
                onValueChange = viewModel::onTranslationChange,
                placeholder = stringResource(R.string.tasbih_editor_translation_placeholder),
            )
        }

        LabeledField(stringResource(R.string.tasbih_editor_note_label)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ListRowShape)
                    .background(colors.card)
                    .border(1.dp, colors.line, ListRowShape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (state.note.isEmpty()) {
                    Text(
                        text = stringResource(R.string.tasbih_editor_note_placeholder),
                        fontSize = 14.sp,
                        color = colors.faint,
                    )
                }
                BasicTextField(
                    value = state.note,
                    onValueChange = viewModel::onNoteChange,
                    minLines = 3,
                    textStyle = TextStyle(fontSize = 14.sp, color = colors.text),
                    cursorBrush = SolidColor(colors.text),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        LabeledField(stringResource(R.string.tasbih_editor_lap_target_label)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                NumberField(
                    value = state.lapTarget,
                    onValueChange = viewModel::onLapTargetChange,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepperButton(
                        label = "−",
                        bg = colors.surface,
                        fg = colors.text,
                        onClick = { viewModel.onLapTargetChange(state.lapTarget - 1) },
                    )
                    StepperButton(
                        label = "+",
                        bg = colors.sage,
                        fg = colors.onSage,
                        onClick = { viewModel.onLapTargetChange(state.lapTarget + 1) },
                    )
                }
            }
        }

        LabeledField(stringResource(R.string.tasbih_editor_daily_goal_label)) {
            DailyGoalPicker(
                selected = state.dailyGoal,
                onSelect = viewModel::onDailyGoalChange,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .heightIn(min = 52.dp)
                .clip(PillShape)
                .background(if (state.canSave) colors.terra else colors.track)
                .clickable(enabled = state.canSave, role = Role.Button) { viewModel.onSave(onBack) }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.tasbih_editor_save),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.card,
            )
        }
        Text(
            text = stringResource(R.string.tasbih_editor_footer),
            fontSize = 11.5.sp,
            color = colors.faint,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 16.dp),
        )
    }
}

/** Appends a " · required" suffix to [label] when [required], for the one of
 *  Arabic / Pronunciation that the current counter-script setting demands. */
@Composable
private fun maybeRequired(label: String, required: Boolean): String =
    if (required) stringResource(R.string.tasbih_editor_required_suffix, label) else label

@Composable
private fun LabeledField(label: String, content: @Composable () -> Unit) {
    val colors = DhikrTheme.colors
    Column(modifier = Modifier.padding(bottom = 15.dp)) {
        Text(
            text = label.uppercase(),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.dim,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        content()
    }
}

/** 48dp pill input: `card` background, 1px `line` border, manual placeholder. */
@Composable
private fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle? = null,
) {
    val colors = DhikrTheme.colors
    val style = textStyle ?: TextStyle(fontSize = 14.sp, color = colors.text)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(ListRowShape)
            .background(colors.card)
            .border(1.dp, colors.line, ListRowShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, fontSize = 14.sp, color = colors.faint)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = false,
            textStyle = style,
            cursorBrush = SolidColor(colors.text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StepperButton(label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(role = Role.Button) { onClick() }
            .minTapTarget(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

/**
 * Inline number pill the user can type into directly. Digits-only, coerced by
 * the caller's [onValueChange]; a blank field commits nothing so the last value
 * stands. Stays in sync when the value changes elsewhere (e.g. stepper buttons).
 */
@Composable
private fun NumberField(value: Int, onValueChange: (Int) -> Unit) {
    val colors = DhikrTheme.colors
    var text by rememberSaveable { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (text.toIntOrNull() != value) text = value.toString()
    }
    Box(
        modifier = Modifier
            .widthIn(min = 64.dp)
            .heightIn(min = 36.dp)
            .clip(PillShape)
            .background(colors.surface)
            .border(1.dp, colors.line, PillShape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
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
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(colors.text),
        )
    }
}

/**
 * Preset daily-goal pills (33 / 100 / 500) plus a "Custom" pill that reveals an
 * inline number field. Tapping the active pill again clears the goal (null).
 */
@Composable
private fun DailyGoalPicker(selected: Int?, onSelect: (Int?) -> Unit) {
    val colors = DhikrTheme.colors
    val presets = listOf(33, 100, 500)
    var customActive by rememberSaveable { mutableStateOf(false) }
    var customText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(selected) {
        if (selected != null && selected !in presets && !customActive) {
            customActive = true
            customText = selected.toString()
        }
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { option ->
            val isSel = !customActive && selected == option
            GoalPill(option.toString(), isSel) {
                customActive = false
                onSelect(if (isSel) null else option)
            }
        }
        GoalPill(stringResource(R.string.settings_daily_goal_custom), customActive) {
            if (customActive) {
                customActive = false
                onSelect(null)
            } else {
                customActive = true
                onSelect(customText.toIntOrNull())
            }
        }
    }

    if (customActive) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .heightIn(min = 48.dp)
                .clip(PillShape)
                .background(colors.card)
                .border(1.dp, colors.line, PillShape)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (customText.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_daily_goal_custom_placeholder),
                    fontSize = 14.sp,
                    color = colors.faint,
                )
            }
            BasicTextField(
                value = customText,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(5)
                    customText = digits
                    onSelect(digits.toIntOrNull())
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(fontSize = 14.sp, color = colors.text),
                cursorBrush = SolidColor(colors.text),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GoalPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = DhikrTheme.colors
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(if (selected) colors.sage else colors.surface)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .minTapTarget()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) colors.onSage else colors.text,
        )
    }
}
