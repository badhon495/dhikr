package com.dhikr.app.core.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.dhikr.app.DhikrApplication
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.datastore.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CounterWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as DhikrApplication
        val sessionRepository = SessionRepository(context.applicationContext)
        val tasbihRepository = TasbihRepository(
            app.database.tasbihDao(),
            app.database.routineDao(),
            app.database.tasbihProgressDao(),
            app.database.sessionDao(),
        )
        val pending = goAsync()
        scope.launch {
            try {
                val session = sessionRepository.sessionFlow.first()
                val tasbih = session?.let { tasbihRepository.getById(it.activeDhikrId) }
                val views = WidgetRenders.buildCounter(
                    context = context,
                    session = session,
                    tasbihName = tasbih?.name,
                    lapTarget = tasbih?.lapTarget ?: 0,
                )
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }
}
