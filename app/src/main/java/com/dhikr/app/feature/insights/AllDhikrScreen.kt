package com.dhikr.app.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.ui.headingSemantics
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.PillShape

@Composable
fun AllDhikrScreen(viewModel: AllDhikrViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors
    val todayLabel = stringResource(R.string.insights_day_today)
    val yesterdayLabel = stringResource(R.string.insights_day_yesterday)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 16.dp),
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
                text = stringResource(R.string.insights_all_dhikr_title),
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .headingSemantics(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .heightIn(min = 48.dp)
                .clip(PillShape)
                .background(colors.card)
                .border(1.dp, colors.line, PillShape)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (state.query.isEmpty()) {
                Text(
                    stringResource(R.string.insights_all_dhikr_search_hint),
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

        if (!state.isLoading && state.groups.isEmpty()) {
            Text(
                stringResource(R.string.insights_all_dhikr_no_match),
                fontSize = 13.sp,
                color = colors.faint,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        state.groups.forEach { group ->
            DhikrHistoryCard(group, todayLabel, yesterdayLabel)
        }
    }
}
