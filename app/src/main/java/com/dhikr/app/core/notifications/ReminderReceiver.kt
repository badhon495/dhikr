package com.dhikr.app.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dhikr.app.DhikrApplication
import com.dhikr.app.core.database.RoutineRepository
import com.dhikr.app.core.database.TasbihRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires when a routine reminder alarm goes off (or when the notification's
 * Snooze action is tapped). Posts the notification and re-arms the next
 * recurring occurrence; a snoozed fire does not chain another occurrence.
 */
class ReminderReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val tasbihId = intent.getStringExtra(ReminderNotifications.EXTRA_TASBIH_ID)
        val routineId = intent.getStringExtra(ReminderNotifications.EXTRA_ROUTINE_ID)
        val isSnooze = intent.getBooleanExtra(ReminderNotifications.EXTRA_IS_SNOOZE, false)
        val isSnoozeRequest = intent.action == ReminderNotifications.ACTION_SNOOZE

        val app = context.applicationContext as DhikrApplication
        val scheduler = ReminderScheduler(context.applicationContext)

        val pending = goAsync()
        scope.launch {
            try {
                when {
                    tasbihId != null -> handleTasbih(context, app, scheduler, tasbihId, isSnooze, isSnoozeRequest)
                    routineId != null -> handleRoutine(context, app, scheduler, routineId, isSnooze, isSnoozeRequest)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleRoutine(
        context: Context,
        app: DhikrApplication,
        scheduler: ReminderScheduler,
        routineId: String,
        isSnooze: Boolean,
        isSnoozeRequest: Boolean,
    ) {
        val repository = RoutineRepository(
            app.database.routineDao(),
            app.database.routineCompletionDao(),
            app.database.routineProgressDao(),
        )
        if (isSnoozeRequest) {
            ReminderNotifications.cancel(context, routineId)
            scheduler.scheduleSnooze(routineId)
            return
        }
        val routine = repository.getRoutine(routineId) ?: return
        if (!routine.reminderEnabled && !isSnooze) return
        ReminderNotifications.post(context, routineId, routine.name)
        if (!isSnooze && routine.reminderEnabled) {
            scheduler.schedule(routineId, routine.reminderMinuteOfDay, routine.reminderDays)
        }
    }

    private suspend fun handleTasbih(
        context: Context,
        app: DhikrApplication,
        scheduler: ReminderScheduler,
        tasbihId: String,
        isSnooze: Boolean,
        isSnoozeRequest: Boolean,
    ) {
        val repository = TasbihRepository(
            app.database.tasbihDao(),
            app.database.routineDao(),
            app.database.tasbihProgressDao(),
            app.database.sessionDao(),
        )
        if (isSnoozeRequest) {
            ReminderNotifications.cancelTasbih(context, tasbihId)
            scheduler.scheduleSnoozeTasbih(tasbihId)
            return
        }
        val tasbih = repository.getById(tasbihId) ?: return
        if (!tasbih.reminderEnabled && !isSnooze) return
        ReminderNotifications.postTasbih(context, tasbihId, tasbih.name)
        if (!isSnooze && tasbih.reminderEnabled) {
            scheduler.scheduleTasbih(tasbihId, tasbih.reminderMinuteOfDay, tasbih.reminderDays)
        }
    }
}
