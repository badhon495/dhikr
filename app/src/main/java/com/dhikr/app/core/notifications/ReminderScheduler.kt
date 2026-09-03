package com.dhikr.app.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dhikr.app.core.database.RoutineRepository
import com.dhikr.app.core.database.TasbihRepository

/**
 * One inexact AlarmManager alarm per enabled routine / tasbih reminder.
 * `setAndAllowWhileIdle` is inexact (no SCHEDULE_EXACT_ALARM permission) but
 * still fires in Doze. After each fire, [ReminderReceiver] re-arms the next
 * occurrence.
 */
class ReminderScheduler(private val appContext: Context) {

    private val alarmManager =
        appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(extraKey: String, targetId: String, requestCode: Int, isSnooze: Boolean): PendingIntent {
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            putExtra(extraKey, targetId)
            if (isSnooze) putExtra(ReminderNotifications.EXTRA_IS_SNOOZE, true)
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun routinePi(routineId: String, isSnooze: Boolean) = pendingIntent(
        ReminderNotifications.EXTRA_ROUTINE_ID, routineId,
        ReminderNotifications.routineNotifId(routineId), isSnooze,
    )

    private fun tasbihPi(tasbihId: String, isSnooze: Boolean) = pendingIntent(
        ReminderNotifications.EXTRA_TASBIH_ID, tasbihId,
        ReminderNotifications.tasbihNotifId(tasbihId), isSnooze,
    )

    fun schedule(routineId: String, minuteOfDay: Int, daysMask: Int) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            NextReminderTime.next(System.currentTimeMillis(), minuteOfDay, daysMask),
            routinePi(routineId, isSnooze = false),
        )
    }

    fun scheduleSnooze(routineId: String) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 15L * 60 * 1000,
            routinePi(routineId, isSnooze = true),
        )
    }

    fun cancel(routineId: String) {
        alarmManager.cancel(routinePi(routineId, isSnooze = false))
        ReminderNotifications.cancel(appContext, routineId)
    }

    fun scheduleTasbih(tasbihId: String, minuteOfDay: Int, daysMask: Int) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            NextReminderTime.next(System.currentTimeMillis(), minuteOfDay, daysMask),
            tasbihPi(tasbihId, isSnooze = false),
        )
    }

    fun scheduleSnoozeTasbih(tasbihId: String) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 15L * 60 * 1000,
            tasbihPi(tasbihId, isSnooze = true),
        )
    }

    fun cancelTasbih(tasbihId: String) {
        alarmManager.cancel(tasbihPi(tasbihId, isSnooze = false))
        ReminderNotifications.cancelTasbih(appContext, tasbihId)
    }

    suspend fun rescheduleAll(routineRepository: RoutineRepository, tasbihRepository: TasbihRepository) {
        routineRepository.routinesWithReminders().forEach { r ->
            schedule(r.id, r.reminderMinuteOfDay, r.reminderDays)
        }
        tasbihRepository.tasbihWithReminders().forEach { t ->
            scheduleTasbih(t.id, t.reminderMinuteOfDay, t.reminderDays)
        }
    }
}
