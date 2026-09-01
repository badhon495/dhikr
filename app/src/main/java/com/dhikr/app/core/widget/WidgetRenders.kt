package com.dhikr.app.core.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import com.dhikr.app.MainActivity
import com.dhikr.app.R
import com.dhikr.app.core.model.CounterSessionState
import com.dhikr.app.core.notifications.ReminderNotifications
import java.text.NumberFormat
import java.util.Locale

/**
 * Widget rendering for both home-screen widgets. The pure formatting helpers
 * ([formatCountOfTarget], [formatGroupedCountOfTarget], [clampProgress],
 * [formatGrouped]) are unit-tested without an Android runtime; the
 * RemoteViews/PendingIntent builders ([buildCounter], [buildInsights]) touch
 * the Android framework and are manual-test-only.
 */
object WidgetRenders {

    data class Progress(val progress: Int, val max: Int)

    fun formatCountOfTarget(count: Int, target: Int): String = "$count / $target"

    /**
     * Like [formatCountOfTarget] but with thousands grouping on both numbers —
     * used by the insights widget, whose "today" value routinely runs past 1000.
     */
    fun formatGroupedCountOfTarget(count: Int, target: Int): String =
        "${formatGrouped(count)} / ${formatGrouped(target)}"

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

    private fun openActivityIntent(context: Context, open: String, routineId: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN, open)
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
     * @param target the engine target for [session]: the active Tasbih's
     *   per-lap target for a plain session, or the current routine step's
     *   `targetCount` for a routine session (0 when unknown).
     */
    fun buildCounter(
        context: Context,
        session: CounterSessionState?,
        tasbihName: String?,
        target: Int,
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
            val open = openActivityIntent(context, MainActivity.OPEN_COUNTER)
            views.setOnClickPendingIntent(R.id.widget_counter_root, open)
            return views
        }

        views.setTextViewText(R.id.widget_counter_name, tasbihName)
        views.setTextViewText(
            R.id.widget_counter_value,
            formatCountOfTarget(session.count, target),
        )
        val p = clampProgress(session.count, target)
        views.setProgressBar(R.id.widget_counter_progress, p.max, p.progress, false)

        val bodyIntent = if (isRoutine) {
            openActivityIntent(context, MainActivity.OPEN_COUNTER, session.routineId)
        } else {
            openActivityIntent(context, MainActivity.OPEN_COUNTER)
        }
        views.setOnClickPendingIntent(R.id.widget_counter_root, bodyIntent)

        if (isRoutine) {
            // Routine sessions render but [+] opens the app (guided flow).
            views.setViewVisibility(R.id.widget_counter_plus, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_counter_plus, context.getString(R.string.widget_routine_hint))
            // Smaller text so "Open to continue" fits the full-width button
            // without clipping (the layout sizes "+" at 22sp).
            views.setTextViewTextSize(R.id.widget_counter_plus, TypedValue.COMPLEX_UNIT_SP, 13f)
            // The layout hardcodes the "Add one count" description; this button
            // opens the app instead, so override it per branch.
            views.setContentDescription(
                R.id.widget_counter_plus,
                context.getString(R.string.widget_routine_hint),
            )
            views.setOnClickPendingIntent(
                R.id.widget_counter_plus,
                openActivityIntent(context, MainActivity.OPEN_COUNTER, session.routineId),
            )
        } else {
            views.setViewVisibility(R.id.widget_counter_plus, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_counter_plus, "+")
            views.setTextViewTextSize(R.id.widget_counter_plus, TypedValue.COMPLEX_UNIT_SP, 22f)
            views.setContentDescription(
                R.id.widget_counter_plus,
                context.getString(R.string.widget_increment_content_description),
            )
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
        views.setTextViewText(R.id.widget_insights_today_value, formatGroupedCountOfTarget(today, goal))
        val p = clampProgress(today, goal)
        views.setProgressBar(R.id.widget_insights_progress, p.max, p.progress, false)
        views.setTextViewText(R.id.widget_insights_week_value, formatGrouped(week))
        views.setTextViewText(R.id.widget_insights_all_time_value, formatGrouped(allTime))
        views.setOnClickPendingIntent(R.id.widget_insights_root, openActivityIntent(context, MainActivity.OPEN_INSIGHTS))
        return views
    }
}
