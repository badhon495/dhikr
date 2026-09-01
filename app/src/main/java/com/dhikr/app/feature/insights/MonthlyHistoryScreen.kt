package com.dhikr.app.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.core.database.MonthSummary
import com.dhikr.app.ui.headingSemantics
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.Caprasimo
import com.dhikr.app.ui.theme.DhikrTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MonthlyHistoryScreen(viewModel: MonthlyHistoryViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors
    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }

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
                text = stringResource(R.string.insights_all_months_title),
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .headingSemantics(),
            )
        }

        if (!state.isLoading && state.months.isEmpty()) {
            Text(
                stringResource(R.string.insights_all_months_empty),
                fontSize = 13.sp,
                color = colors.faint,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        state.months.forEach { month ->
            MonthCard(month, monthYearFormat.format(Date(month.monthStartMillis)))
        }
    }
}

@Composable
private fun MonthCard(month: MonthSummary, title: String) {
    val colors = DhikrTheme.colors
    val activeLabel = stringResource(
        R.string.insights_month_active_days,
        month.consistentDays,
        month.daysInMonth,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card)
            .padding(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(title, fontSize = 14.5.sp, color = colors.text)
            Text(
                month.total.toString(),
                fontSize = 20.sp,
                fontFamily = Caprasimo,
                color = colors.terra,
            )
        }
        Text(
            activeLabel,
            fontSize = 11.sp,
            color = colors.faint,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
