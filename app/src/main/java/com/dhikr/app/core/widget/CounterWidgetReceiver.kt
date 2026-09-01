package com.dhikr.app.core.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dhikr.app.core.counter.WidgetCounter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the counter widget's [+] tap: applies one increment to the persisted
 * session off the main thread, then refreshes both widgets. exported=false —
 * only our own PendingIntent fires it.
 */
class CounterWidgetReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INCREMENT) return
        val pending = goAsync()
        scope.launch {
            try {
                WidgetCounter.applyIncrement(context)
                DhikrWidgets.refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_INCREMENT = "com.dhikr.app.action.WIDGET_INCREMENT"
    }
}
