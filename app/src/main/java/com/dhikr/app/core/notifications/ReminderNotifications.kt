package com.dhikr.app.core.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dhikr.app.MainActivity
import com.dhikr.app.R

/**
 * Builds and posts the per-routine reminder notification, and owns the
 * notification channel plus the intent-extra / action constants shared by
 * [ReminderScheduler] and [ReminderReceiver].
 */
object ReminderNotifications {

    const val CHANNEL_ID = "reminders"
    const val EXTRA_ROUTINE_ID = "com.dhikr.app.extra.ROUTINE_ID"
    const val EXTRA_IS_SNOOZE = "com.dhikr.app.extra.IS_SNOOZE"
    const val ACTION_SNOOZE = "com.dhikr.app.action.SNOOZE_REMINDER"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT)
            .setName("Reminders")
            .setDescription("Routine reminders you set in the app")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun post(context: Context, routineId: String, routineName: String) {
        if (!hasPermission(context)) return
        ensureChannel(context)

        val id = routineId.hashCode()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_ROUTINE_ID, routineId)
        }
        val openPending = PendingIntent.getActivity(context, id, openIntent, flags)

        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_ROUTINE_ID, routineId)
        }
        val snoozePending = PendingIntent.getBroadcast(context, id + 1, snoozeIntent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setLargeIcon(launcherBitmap(context))
            .setContentTitle(context.getString(R.string.reminder_notification_title, routineName))
            .setContentText(context.getString(R.string.reminder_notification_body))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPending)
            .addAction(0, context.getString(R.string.reminder_snooze_action), snoozePending)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun cancel(context: Context, routineId: String) {
        NotificationManagerCompat.from(context).cancel(routineId.hashCode())
    }

    /**
     * The full-colour launcher icon as a bitmap, for [NotificationCompat.Builder.setLargeIcon]
     * — shown in the expanded notification body. (The status-bar small icon
     * can only ever be a white silhouette.)
     */
    private fun launcherBitmap(context: Context): android.graphics.Bitmap? {
        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) ?: return null
        if (drawable is BitmapDrawable) return drawable.bitmap
        val size = drawable.intrinsicWidth.takeIf { it > 0 } ?: 108
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
