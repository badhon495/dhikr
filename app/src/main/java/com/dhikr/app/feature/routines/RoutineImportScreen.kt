package com.dhikr.app.feature.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.core.share.PreviewRoutine
import com.dhikr.app.ui.headingSemantics
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.PillShape

@Composable
fun RoutineImportScreen(
    viewModel: RoutineImportViewModel,
    onClose: () -> Unit,
) {
    val colors = DhikrTheme.colors
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            stringResource(R.string.routines_import_title),
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.text,
            modifier = Modifier
                .padding(top = 16.dp, bottom = 12.dp)
                .headingSemantics(),
        )

        when (val s = state) {
            RoutineImportViewModel.State.Loading ->
                CenteredMessage(stringResource(R.string.routines_import_loading))

            RoutineImportViewModel.State.Working ->
                CenteredMessage(stringResource(R.string.routines_import_working))

            is RoutineImportViewModel.State.Error -> {
                CenteredMessage(s.message)
                PrimaryButton(stringResource(R.string.routines_import_cancel), onClick = onClose)
            }

            is RoutineImportViewModel.State.Done -> {
                CenteredMessage(
                    stringResource(
                        R.string.routines_import_done,
                        s.result.routinesImported,
                        s.result.tasbihAdded,
                    ),
                )
                PrimaryButton(stringResource(R.string.routines_import_done_button), onClick = onClose)
            }

            is RoutineImportViewModel.State.Preview -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(s.preview.routines) { routine -> PreviewCard(routine) }
                    item {
                        Text(
                            text = if (s.preview.newTasbihCount > 0) {
                                stringResource(R.string.routines_import_adds_tasbih, s.preview.newTasbihCount)
                            } else {
                                stringResource(R.string.routines_import_adds_no_tasbih)
                            },
                            fontSize = 12.5.sp,
                            color = colors.dim,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SecondaryButton(
                        stringResource(R.string.routines_import_cancel),
                        Modifier.weight(1f),
                        onClick = onClose,
                    )
                    PrimaryButton(
                        stringResource(R.string.routines_import_confirm),
                        Modifier.weight(1f),
                        onClick = viewModel::confirm,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(routine: PreviewRoutine) {
    val colors = DhikrTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .padding(16.dp),
    ) {
        Text(routine.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.text)
        routine.steps.forEachIndexed { index, step ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    fontSize = 12.sp,
                    color = colors.faint,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Text(step.tasbihName, fontSize = 13.5.sp, color = colors.text, modifier = Modifier.weight(1f))
                Text(
                    "${step.targetCount}",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.terra,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    val colors = DhikrTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, color = colors.dim, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = DhikrTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(PillShape)
            .background(colors.sage)
            .clickable(role = Role.Button, onClick = onClick)
            .minTapTarget()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.onSage)
    }
}

@Composable
private fun SecondaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = DhikrTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(PillShape)
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .minTapTarget()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
    }
}
