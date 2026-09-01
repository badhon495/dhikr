package com.dhikr.app.core.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Single entry point for refreshing the home-screen widgets. Broadcasts an
 * APPWIDGET_UPDATE to each provider with its current widget ids; no-ops for a
 * provider that has no widget placed, so callers never need to check.
 */
object DhikrWidgets {

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val providers = listOf(
            CounterWidgetProvider::class.java,
            InsightsWidgetProvider::class.java,
        )
        for (cls in providers) {
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isEmpty()) continue
            context.sendBroadcast(
                Intent(context, cls).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }
    }
}
