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
 * Alarms are cleared on reboot and on app update, so re-arm every enabled
 * routine reminder when the device finishes booting or the package is
 * replaced.
 */
class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
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
                scheduler.rescheduleAll(repository)
            } finally {
                pending.finish()
            }
        }
    }
}
