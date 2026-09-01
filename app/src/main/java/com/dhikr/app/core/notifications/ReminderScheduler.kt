package com.dhikr.app.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dhikr.app.core.database.RoutineRepository

/**
 * One inexact AlarmManager alarm per enabled routine. `setAndAllowWhileIdle`
 * is inexact (no SCHEDULE_EXACT_ALARM permission) but still fires in Doze.
 * After each fire, [ReminderReceiver] re-arms the next occurrence.
 */
class ReminderScheduler(private val appContext: Context) {

    private val alarmManager =
        appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(routineId: String, isSnooze: Boolean): PendingIntent {
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            putExtra(ReminderNotifications.EXTRA_ROUTINE_ID, routineId)
            if (isSnooze) putExtra(ReminderNotifications.EXTRA_IS_SNOOZE, true)
        }
        return PendingIntent.getBroadcast(
            appContext,
            routineId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun schedule(routineId: String, minuteOfDay: Int, daysMask: Int) {
        val triggerAt = NextReminderTime.next(System.currentTimeMillis(), minuteOfDay, daysMask)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent(routineId, isSnooze = false),
        )
    }

    fun scheduleSnooze(routineId: String) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 15L * 60 * 1000,
            pendingIntent(routineId, isSnooze = true),
        )
    }

    fun cancel(routineId: String) {
        alarmManager.cancel(pendingIntent(routineId, isSnooze = false))
        ReminderNotifications.cancel(appContext, routineId)
    }

    suspend fun rescheduleAll(repository: RoutineRepository) {
        repository.routinesWithReminders().forEach { r ->
            schedule(r.id, r.reminderMinuteOfDay, r.reminderDays)
        }
    }
}
