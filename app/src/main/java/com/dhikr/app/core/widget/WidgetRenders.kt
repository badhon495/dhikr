package com.dhikr.app.core.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dhikr.app.MainActivity
import com.dhikr.app.R
import com.dhikr.app.core.notifications.ReminderNotifications
import java.text.NumberFormat
import java.util.Locale

/**
 * Pure formatting/layout helpers for both widgets. The RemoteViews builders
 * (added alongside the providers) call these; the helpers are split out so they
 * unit-test without an Android runtime.
 */
object WidgetRenders {

    data class Progress(val progress: Int, val max: Int)

    fun formatCountOfTarget(count: Int, target: Int): String = "$count / $target"

    /**
     * A ProgressBar needs max >= 1 and progress in 0..max. Clamp so goal <= 0
     * (or a count that ran past target after a goal change) still renders a
     * sane bar instead of crashing or showing a full/negative one.
     */
    fun clampProgress(value: Int, target: Int): Progress {
        val max = target.coerceAtLeast(1)
        return Progress(progress = value.coerceIn(0, max), max = max)
    }

    fun formatGrouped(value: Int): String =
        NumberFormat.getIntegerInstance(Locale.US).format(value.toLong())

    // Values MainActivity reads to route a widget body tap. Kept in sync with
    // MainActivity.EXTRA_OPEN / its accepted values.
    private const val EXTRA_OPEN = "com.dhikr.app.extra.OPEN"
    private const val OPEN_COUNTER = "counter"
    private const val OPEN_INSIGHTS = "insights"

    private fun openActivityIntent(context: Context, open: String, routineId: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_OPEN, open)
            if (routineId != null) putExtra(ReminderNotifications.EXTRA_ROUTINE_ID, routineId)
        }
        // Unique request code per (open,routineId) so distinct extras aren't
        // coalesced by PendingIntent's equality (which ignores extras).
        val reqCode = (open + (routineId ?: "")).hashCode()
        return PendingIntent.getActivity(
            context, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * @param session the active session, or null for the "no session" state.
     * @param tasbihName resolved name for [session] (null when no session or
     *   the Tasbih was deleted).
     * @param lapTarget the active Tasbih's per-lap target (0 when unknown).
     */
    fun buildCounter(
        context: Context,
        session: com.dhikr.app.core.model.CounterSessionState?,
        tasbihName: String?,
        lapTarget: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_counter)
        val isRoutine = session?.routineId != null
        val hasCountable = session != null && tasbihName != null

        if (!hasCountable) {
            views.setTextViewText(R.id.widget_counter_name, context.getString(R.string.widget_no_session_title))
            views.setTextViewText(R.id.widget_counter_value, context.getString(R.string.widget_no_session_body))
            val p = clampProgress(0, 1)
            views.setProgressBar(R.id.widget_counter_progress, p.max, p.progress, false)
            views.setViewVisibility(R.id.widget_counter_plus, android.view.View.GONE)
            val open = openActivityIntent(context, OPEN_COUNTER)
            views.setOnClickPendingIntent(R.id.widget_counter_root, open)
            return views
        }

        views.setTextViewText(R.id.widget_counter_name, tasbihName)
        views.setTextViewText(
            R.id.widget_counter_value,
            formatCountOfTarget(session!!.count, lapTarget),
        )
        val p = clampProgress(session.count, lapTarget)
        views.setProgressBar(R.id.widget_counter_progress, p.max, p.progress, false)

        val bodyIntent = if (isRoutine) {
            openActivityIntent(context, OPEN_COUNTER, session.routineId)
        } else {
            openActivityIntent(context, OPEN_COUNTER)
        }
        views.setOnClickPendingIntent(R.id.widget_counter_root, bodyIntent)

        if (isRoutine) {
            // Routine sessions render but [+] opens the app (guided flow).
            views.setViewVisibility(R.id.widget_counter_plus, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_counter_plus, context.getString(R.string.widget_routine_hint))
            views.setOnClickPendingIntent(
                R.id.widget_counter_plus,
                openActivityIntent(context, OPEN_COUNTER, session.routineId),
            )
        } else {
            views.setViewVisibility(R.id.widget_counter_plus, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_counter_plus, "+")
            val incIntent = Intent(context, CounterWidgetReceiver::class.java)
                .setAction(CounterWidgetReceiver.ACTION_INCREMENT)
            val incPending = PendingIntent.getBroadcast(
                context, 0, incIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_counter_plus, incPending)
        }
        return views
    }

    fun buildInsights(
        context: Context,
        today: Int,
        goal: Int,
        week: Int,
        allTime: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_insights)
        views.setTextViewText(R.id.widget_insights_today_value, formatCountOfTarget(today, goal))
        val p = clampProgress(today, goal)
        views.setProgressBar(R.id.widget_insights_progress, p.max, p.progress, false)
        views.setTextViewText(R.id.widget_insights_week_value, formatGrouped(week))
        views.setTextViewText(R.id.widget_insights_all_time_value, formatGrouped(allTime))
        views.setOnClickPendingIntent(R.id.widget_insights_root, openActivityIntent(context, OPEN_INSIGHTS))
        return views
    }
}
