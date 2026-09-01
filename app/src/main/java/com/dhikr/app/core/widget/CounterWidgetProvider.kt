package com.dhikr.app.core.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.dhikr.app.DhikrApplication
import com.dhikr.app.core.database.RoutineRepository
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
                // For a routine session the engine target is the current step's
                // user-editable targetCount (CounterViewModel builds the step as
                // TasbihCounter(step.targetCount, 1)), NOT the Tasbih's lapTarget.
                val routineStepTarget = if (session?.routineId != null) {
                    val routineRepository = RoutineRepository(
                        app.database.routineDao(),
                        app.database.routineCompletionDao(),
                        app.database.routineProgressDao(),
                    )
                    routineRepository.getWithSteps(session.routineId)
                        ?.steps
                        ?.sortedBy { it.stepOrder }
                        ?.getOrNull(session.routineStep)
                        ?.targetCount
                } else {
                    null
                }
                val target = when {
                    session == null -> 0
                    session.routineId != null -> routineStepTarget ?: 0
                    else -> tasbih?.lapTarget ?: 0
                }
                // A routine session whose routine/step can't be resolved falls
                // back to the no-session state (null name -> "no session" body).
                val resolvedName = if (session?.routineId != null && routineStepTarget == null) {
                    null
                } else {
                    tasbih?.name
                }
                val views = WidgetRenders.buildCounter(
                    context = context,
                    session = session,
                    tasbihName = resolvedName,
                    target = target,
                )
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }
}
