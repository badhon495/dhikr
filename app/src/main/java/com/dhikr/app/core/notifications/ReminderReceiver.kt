package com.dhikr.app.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dhikr.app.DhikrApplication
import com.dhikr.app.core.database.RoutineRepository
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
        val routineId = intent.getStringExtra(ReminderNotifications.EXTRA_ROUTINE_ID) ?: return
        val isSnooze = intent.getBooleanExtra(ReminderNotifications.EXTRA_IS_SNOOZE, false)
        val isSnoozeRequest = intent.action == ReminderNotifications.ACTION_SNOOZE

        val app = context.applicationContext as DhikrApplication
        val repository = RoutineRepository(
            app.database.routineDao(),
            app.database.routineCompletionDao(),
            app.database.routineProgressDao(),
        )
        val scheduler = ReminderScheduler(context.applicationContext)

        val pending = goAsync()
        scope.launch {
            try {
                if (isSnoozeRequest) {
                    ReminderNotifications.cancel(context, routineId)
                    scheduler.scheduleSnooze(routineId)
                    return@launch
                }
                val routine = repository.getRoutine(routineId) ?: return@launch
                if (!routine.reminderEnabled && !isSnooze) return@launch
                ReminderNotifications.post(context, routineId, routine.name)
                if (!isSnooze && routine.reminderEnabled) {
                    scheduler.schedule(routineId, routine.reminderMinuteOfDay, routine.reminderDays)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
