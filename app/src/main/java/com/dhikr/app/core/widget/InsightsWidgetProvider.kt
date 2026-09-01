package com.dhikr.app.core.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.dhikr.app.DhikrApplication
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.utilities.DayBounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class InsightsWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as DhikrApplication
        val sessionDao = app.database.sessionDao()
        val preferences = AppPreferencesRepository(context.applicationContext)
        val pending = goAsync()
        scope.launch {
            try {
                val startOfToday = DayBounds.startOfTodayMillis()
                val dayMillis = TimeUnit.DAYS.toMillis(1)
                val today = sessionDao.totalSince(startOfToday).first()
                val week = sessionDao.totalSince(startOfToday - 6 * dayMillis).first()
                val allTime = sessionDao.totalSince(0L).first()
                val goal = preferences.dailyGoalTarget.first()
                val views = WidgetRenders.buildInsights(context, today, goal, week, allTime)
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }
}
