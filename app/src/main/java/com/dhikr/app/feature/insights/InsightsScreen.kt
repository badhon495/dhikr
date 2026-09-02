package com.dhikr.app.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.ui.INSIGHTS_SCREEN_TEST_TAG
import com.dhikr.app.ui.ScheduleIcon
import com.dhikr.app.ui.headingSemantics
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.Caprasimo
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.PillShape
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel,
    onStartCounting: () -> Unit,
    onSeeAllMonths: () -> Unit,
    scrollToTopSignal: Int = 0,
) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors
    val scrollState = rememberScrollState()

    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) scrollState.animateScrollTo(0)
    }

    if (state.isEmpty) {
        Column(
            modifier = Modifier.fillMaxSize().background(colors.bg).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(colors.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ScheduleIcon,
                    contentDescription = stringResource(R.string.insights_empty_icon_content_description),
                    tint = colors.dim,
                    modifier = Modifier.size(30.dp),
                )
            }
            Text(
                stringResource(R.string.insights_empty_title),
                fontSize = 18.sp,
                color = colors.text,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                stringResource(R.string.insights_empty_body),
                fontSize = 13.sp,
                color = colors.faint,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
            )
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(colors.sage)
                    .clickable(role = Role.Button) { onStartCounting() }
                    .minTapTarget()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.insights_empty_cta), color = colors.onSage)
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(scrollState)
            .testTag(INSIGHTS_SCREEN_TEST_TAG)
            .padding(16.dp),
    ) {
        val monthName = remember {
            SimpleDateFormat("MMMM", Locale.getDefault()).format(Calendar.getInstance().time)
        }
        Text(
            stringResource(R.string.insights_title),
            fontSize = 23.sp,
            color = colors.text,
            modifier = Modifier.headingSemantics(),
        )
        Text(monthName, fontSize = 13.sp, color = colors.dim, modifier = Modifier.padding(top = 2.dp))

        // Totals 2x2 grid
        Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TotalTile(stringResource(R.string.insights_today), state.today, Modifier.weight(1f))
            TotalTile(stringResource(R.string.insights_this_week), state.week, Modifier.weight(1f))
        }
        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TotalTile(stringResource(R.string.insights_this_month), state.month, Modifier.weight(1f))
            TotalTile(stringResource(R.string.insights_all_time), state.allTime, Modifier.weight(1f))
        }

        // Last 7 days
        Text(
            stringResource(R.string.insights_last_7_days),
            fontSize = 11.5.sp,
            color = colors.dim,
            modifier = Modifier
                .padding(top = 20.dp, bottom = 8.dp)
                .headingSemantics(),
        )
        val barChartDescription = stringResource(
            R.string.insights_bar_chart_description,
            state.last7Days.joinToString(", ") { (label, value) -> "$label $value" },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.card)
                .padding(12.dp)
                .clearAndSetSemantics { contentDescription = barChartDescription },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val maxValue = (state.last7Days.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
            state.last7Days.forEachIndexed { index, (label, value) ->
                val isToday = index == state.last7Days.lastIndex
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(value.toString(), fontSize = 10.5.sp, color = colors.dim)
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .width(18.dp)
                            .height((value.toFloat() / maxValue * 70).dp.coerceAtLeast(4.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isToday) colors.terra else colors.sage),
                    )
                    Text(label, fontSize = 10.5.sp, color = colors.faint, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        // Consistency calendar
        Text(
            stringResource(R.string.insights_consistency),
            fontSize = 11.5.sp,
            color = colors.dim,
            modifier = Modifier
                .padding(top = 20.dp, bottom = 2.dp)
                .headingSemantics(),
        )
        val activeDaysThisMonth = state.calendarIntensity.values.count { it > 0 }
        Text(
            stringResource(R.string.insights_consistency_meta, activeDaysThisMonth),
            fontSize = 10.5.sp,
            color = colors.faint,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.card)
                .padding(12.dp),
        ) {
            val sortedEntries = state.calendarIntensity.entries.sortedBy { it.key }
            sortedEntries.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { (day, count) ->
                        val intensity = when {
                            count == 0 -> colors.track
                            count < 33 -> colors.sageSoft
                            count < 100 -> colors.sageMid
                            else -> colors.sage
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(intensity),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(day.toString(), fontSize = 12.sp, color = colors.text)
                        }
                    }
                    repeat(7 - week.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.insights_legend_less), fontSize = 10.sp, color = colors.faint)
                Row(modifier = Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(colors.track, colors.sageSoft, colors.sageMid, colors.sage).forEach { c ->
                        Box(modifier = Modifier.width(10.dp).height(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
                    }
                }
                Text(stringResource(R.string.insights_legend_more), fontSize = 10.sp, color = colors.faint)
            }
        }

        // Previous month summary + link to the full month-by-month history
        val previousMonth = state.previousMonth
        if (previousMonth != null && previousMonth.total > 0) {
            val prevMonthName = remember(previousMonth.monthStartMillis) {
                SimpleDateFormat("MMMM", Locale.getDefault()).format(Date(previousMonth.monthStartMillis))
            }
            Text(
                stringResource(R.string.insights_last_month),
                fontSize = 11.5.sp,
                color = colors.dim,
                modifier = Modifier
                    .padding(top = 20.dp, bottom = 8.dp)
                    .headingSemantics(),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.card)
                    .padding(16.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(prevMonthName, fontSize = 14.5.sp, color = colors.text)
                    Text(
                        previousMonth.total.toString(),
                        fontSize = 20.sp,
                        fontFamily = Caprasimo,
                        color = colors.terra,
                    )
                }
                Text(
                    stringResource(
                        R.string.insights_month_active_days,
                        previousMonth.consistentDays,
                        previousMonth.daysInMonth,
                    ),
                    fontSize = 11.sp,
                    color = colors.faint,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .clip(PillShape)
                .clickable(role = Role.Button) { onSeeAllMonths() }
                .minTapTarget()
                .padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            Text(
                stringResource(R.string.insights_see_all_months),
                fontSize = 12.sp,
                color = colors.sage,
            )
        }

        // History grouped by Dhikr
        val todayLabel = stringResource(R.string.insights_day_today)
        val yesterdayLabel = stringResource(R.string.insights_day_yesterday)
        Row(modifier = Modifier.padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.insights_history_title),
                fontSize = 11.5.sp,
                color = colors.dim,
                modifier = Modifier.headingSemantics(),
            )
            Text(
                stringResource(R.string.insights_history_grouped),
                fontSize = 10.5.sp,
                color = colors.faint,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        state.historyByTasbih.forEach { group ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.card)
                    .padding(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(group.tasbihName, fontSize = 14.5.sp, color = colors.text)
                    Text(group.lifetimeTotal.toString(), fontSize = 14.5.sp, color = colors.terra)
                }
                group.dailyTotals.forEach { (dayStartMillis, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text(
                            formatDayLabel(dayStartMillis, todayLabel, yesterdayLabel),
                            fontSize = 11.sp,
                            color = colors.faint,
                            modifier = Modifier.width(72.dp),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(7.dp)
                                .clip(PillShape)
                                .background(colors.track),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((count.toFloat() / 200f).coerceIn(0f, 1f))
                                    .height(7.dp)
                                    .clip(PillShape)
                                    .background(colors.sage),
                            )
                        }
                        Text(count.toString(), fontSize = 12.sp, color = colors.dim, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

private fun formatDayLabel(dayStartMillis: Long, todayLabel: String, yesterdayLabel: String): String {
    val target = Calendar.getInstance().apply { timeInMillis = dayStartMillis }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        isSameDay(target, today) -> todayLabel
        isSameDay(target, yesterday) -> yesterdayLabel
        else -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(dayStartMillis))
    }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

@Composable
private fun TotalTile(label: String, value: Int, modifier: Modifier = Modifier) {
    val colors = DhikrTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .padding(14.dp),
    ) {
        Text(label.uppercase(), fontSize = 10.5.sp, color = colors.dim)
        Text(
            value.toString(),
            fontSize = 24.sp,
            fontFamily = Caprasimo,
            color = colors.text,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
