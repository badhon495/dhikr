# Phase 5A — Reminder Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user attach one recurring local reminder (time + weekdays + on/off) to a routine; when it fires, a notification opens that routine's counter, with a Snooze action.

**Architecture:** Three new nullable-defaulted columns on `RoutineEntity` hold the reminder. A new `core/notifications/` package schedules a single inexact `AlarmManager` alarm per enabled routine; a `BroadcastReceiver` posts the notification and re-arms the next occurrence; a boot receiver re-arms everything after reboot/update. The routine editor gains a Reminder section. `MainActivity` learns to read a `routineId` extra and deep-link into the existing `counter?routineId=` route.

**Tech Stack:** Kotlin, Jetpack Compose, Room, DataStore, AndroidX core, `AlarmManager`, `NotificationManagerCompat`, `java.time`. No new Gradle dependency.

**Spec:** `docs/superpowers/specs/2026-09-01-phase-5a-reminder-notifications-design.md`

## Global Constraints

- Native Android only. Kotlin + Compose. `minSdk = 24`, `targetSdk = 37`, `compileSdk = 37`.
- No new Gradle dependency.
- No DI framework — repositories are built by hand in `DhikrApp.kt`; `DhikrApplication` owns the Room database (`app.database`).
- No test infrastructure exists and none is added here. Verify every task with `./gradlew :app:assembleDebug` (per the `verify-via-build-not-emulator` convention). Run the app only if the user asks.
- Schema changes bump `AppDatabase.version` and rely on the existing `fallbackToDestructiveMigration(dropAllTables = true)` — no hand-written `Migration`.
- Inexact alarms only. Do **not** add `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`.
- All reminder logic is local. No network, no analytics.
- New user-facing copy goes in `res/values/strings.xml` only (localization is deferred project-wide). English strings, sentence case, matching the file's existing "Favourite"/British-ish spelling style.
- Weekday bitmask: bit 0 = Sunday … bit 6 = Saturday. `daysMask == 0` means "every day". Java `DayOfWeek.value` (MON=1..SUN=7) maps to bit index `value % 7`.
- `PendingIntent` request code AND notification id for a routine's reminder are both `routineId.hashCode()`.
- `PendingIntent` flags: `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`.

---

### Task 1: Reminder columns on RoutineEntity + repository surface

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/core/database/entity/RoutineEntity.kt`
- Modify: `app/src/main/java/com/dhikr/app/core/database/AppDatabase.kt`
- Modify: `app/src/main/java/com/dhikr/app/core/database/dao/RoutineDao.kt`
- Modify: `app/src/main/java/com/dhikr/app/core/database/RoutineRepository.kt`

**Interfaces:**
- Consumes: nothing (first task).
- Produces:
  - `RoutineEntity` gains `val reminderEnabled: Boolean = false`, `val reminderMinuteOfDay: Int = 0`, `val reminderDays: Int = 0`.
  - `RoutineDao.setReminder(id: String, enabled: Boolean, minuteOfDay: Int, days: Int)` — suspend, `@Query` UPDATE, also sets `updatedAt`.
  - `RoutineDao.routinesWithRemindersRaw(): List<RoutineEntity>` — suspend, `SELECT * FROM routine WHERE reminderEnabled = 1`.
  - `RoutineRepository.setReminder(routineId: String, enabled: Boolean, minuteOfDay: Int, daysMask: Int)` — suspend.
  - `RoutineRepository.routinesWithReminders(): List<RoutineEntity>` — suspend.
  - `RoutineRepository.getRoutine(id: String): RoutineEntity?` — suspend (thin wrapper over `getWithSteps(id)?.routine`), used by the receiver.

- [ ] **Step 1: Add the three columns to `RoutineEntity`**

In `RoutineEntity.kt`, add to the `data class` body (after `updatedAt`):

```kotlin
    val reminderEnabled: Boolean = false,
    /** Local wall-clock minute of day, 0..1439. */
    val reminderMinuteOfDay: Int = 0,
    /** 7-bit weekday mask; bit 0 = Sunday .. bit 6 = Saturday. 0 = every day. */
    val reminderDays: Int = 0,
```

- [ ] **Step 2: Bump the database version**

In `AppDatabase.kt`, change `version = 6` to `version = 7` and add a line to the version-history comment block:

```kotlin
    // v7: added RoutineEntity.reminderEnabled / reminderMinuteOfDay /
    // reminderDays (per-routine local reminder). No hand migration —
    // fallbackToDestructiveMigration rebuilds + reseeds.
```

- [ ] **Step 3: Add DAO methods**

In `RoutineDao.kt`, add inside the interface:

```kotlin
    @Query(
        "UPDATE routine SET reminderEnabled = :enabled, reminderMinuteOfDay = :minuteOfDay, " +
            "reminderDays = :days, updatedAt = :now WHERE id = :id"
    )
    suspend fun setReminder(id: String, enabled: Boolean, minuteOfDay: Int, days: Int, now: Long)

    @Query("SELECT * FROM routine WHERE reminderEnabled = 1")
    suspend fun routinesWithRemindersRaw(): List<RoutineEntity>

    @Query("SELECT * FROM routine WHERE id = :id LIMIT 1")
    suspend fun getRoutineRaw(id: String): RoutineEntity?
```

(Add `import com.dhikr.app.core.database.entity.RoutineEntity` — it is already imported.)

- [ ] **Step 4: Add repository methods**

In `RoutineRepository.kt`, add:

```kotlin
    suspend fun setReminder(routineId: String, enabled: Boolean, minuteOfDay: Int, daysMask: Int) {
        routineDao.setReminder(routineId, enabled, minuteOfDay, daysMask, System.currentTimeMillis())
    }

    suspend fun routinesWithReminders(): List<RoutineEntity> = routineDao.routinesWithRemindersRaw()

    suspend fun getRoutine(id: String): RoutineEntity? = routineDao.getRoutineRaw(id)
```

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Room regenerates the schema for v7; KSP does not complain about the new columns or queries.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/database/
git commit -m "Add per-routine reminder columns and repository methods"
```

---

### Task 2: NextReminderTime pure scheduling calculation

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/notifications/NextReminderTime.kt`

**Interfaces:**
- Consumes: nothing (pure Kotlin / `java.time`).
- Produces:
  - `object NextReminderTime`
  - `fun NextReminderTime.next(nowMillis: Long, minuteOfDay: Int, daysMask: Int, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long` — epoch millis of the next occurrence strictly after `nowMillis`.

- [ ] **Step 1: Write the file**

```kotlin
package com.dhikr.app.core.notifications

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Computes the next epoch-millis a routine reminder should fire, given a
 * wall-clock minute of day and a 7-bit weekday mask (bit 0 = Sunday ..
 * bit 6 = Saturday; 0 = every day). Pure — no Android, no AlarmManager.
 */
object NextReminderTime {

    fun next(
        nowMillis: Long,
        minuteOfDay: Int,
        daysMask: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val minute = minuteOfDay.coerceIn(0, 24 * 60 - 1)
        val effectiveMask = if (daysMask and 0x7F == 0) 0x7F else daysMask and 0x7F
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)

        // Walk today + the next 7 days; the first matching, still-future slot wins.
        for (dayOffset in 0..7) {
            val day = now.toLocalDate().plusDays(dayOffset.toLong())
            val bit = day.dayOfWeek.value % 7 // Mon=1..Sun=7 -> Sun=0..Sat=6
            if (effectiveMask and (1 shl bit) == 0) continue
            val candidate = day.atStartOfDay(zone)
                .plusMinutes(minute.toLong())
            if (candidate.toInstant().toEpochMilli() > nowMillis) {
                return candidate.toInstant().toEpochMilli()
            }
        }
        // Unreachable with a non-zero mask, but return a safe far-future value.
        return nowMillis + 7L * 24 * 60 * 60 * 1000
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual reasoning check (no test infra)**

Confirm by hand, writing the trace into the commit message body:
- `minuteOfDay = 540` (09:00), `daysMask = 0`, now = today 08:00 → returns today 09:00.
- Same, now = today 10:00 → returns tomorrow 09:00.
- `daysMask = 0b0000010` (Monday only, bit 1), now = Monday 12:00 → returns next Monday 09:00.
- `minuteOfDay = 5000` → coerced to 1439 (23:59).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/notifications/NextReminderTime.kt
git commit -m "Add NextReminderTime next-occurrence calculation"
```

---

### Task 3: Notification channel, icon, strings, builder helper

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/notifications/ReminderNotifications.kt`
- Create: `app/src/main/res/drawable/ic_stat_reminder.xml`
- Modify: `app/src/main/java/com/dhikr/app/DhikrApplication.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `object ReminderNotifications`
  - `const val ReminderNotifications.CHANNEL_ID = "reminders"`
  - `const val ReminderNotifications.EXTRA_ROUTINE_ID = "com.dhikr.app.extra.ROUTINE_ID"`
  - `const val ReminderNotifications.ACTION_SNOOZE = "com.dhikr.app.action.SNOOZE_REMINDER"`
  - `const val ReminderNotifications.EXTRA_IS_SNOOZE = "com.dhikr.app.extra.IS_SNOOZE"`
  - `fun ReminderNotifications.ensureChannel(context: Context)`
  - `fun ReminderNotifications.post(context: Context, routineId: String, routineName: String)` — builds + posts the notification (checks `POST_NOTIFICATIONS` on API 33+, no-ops if not granted).
  - `fun ReminderNotifications.cancel(context: Context, routineId: String)`

- [ ] **Step 1: Add the notification icon**

Create `ic_stat_reminder.xml` — a simple monochrome bell, white fill so Android tints it for the status bar:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#FFFFFFFF">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,22c1.1,0 2,-0.9 2,-2h-4C10,21.1 10.9,22 12,22zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5S10.5,3.17 10.5,4v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1L18,16z" />
</vector>
```

- [ ] **Step 2: Add strings**

In `res/values/strings.xml`, before the closing `</resources>`, add a section:

```xml
    <!-- Reminders (Phase 5A) -->
    <string name="reminder_section_title">Reminder</string>
    <string name="reminder_toggle_label">Daily reminder</string>
    <string name="reminder_toggle_desc">A local nudge to start this routine</string>
    <string name="reminder_time_label">Time</string>
    <string name="reminder_days_label">Repeat</string>
    <string name="reminder_notification_title">Time for %1$s</string>
    <string name="reminder_notification_body">Tap to start counting.</string>
    <string name="reminder_snooze_action">Snooze 15 min</string>
    <string name="reminder_permission_hint">Notifications are turned off for Dhikr. Turn them on in system settings to get reminders.</string>
    <string name="reminder_permission_rationale">Dhikr needs notification access to remind you at the time you pick.</string>
    <string name="reminder_day_sun">S</string>
    <string name="reminder_day_mon">M</string>
    <string name="reminder_day_tue">T</string>
    <string name="reminder_day_wed">W</string>
    <string name="reminder_day_thu">T</string>
    <string name="reminder_day_fri">F</string>
    <string name="reminder_day_sat">S</string>
```

- [ ] **Step 3: Write `ReminderNotifications.kt`**

```kotlin
package com.dhikr.app.core.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dhikr.app.MainActivity
import com.dhikr.app.R

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
        val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_ROUTINE_ID, routineId)
        }
        val openPending = PendingIntent.getActivity(context, id, openIntent, immutable)

        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_ROUTINE_ID, routineId)
        }
        val snoozePending = PendingIntent.getBroadcast(context, id + 1, snoozeIntent, immutable)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
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
}
```

- [ ] **Step 4: Create the channel at app start**

In `DhikrApplication.kt` `onCreate()`, after `super.onCreate()` and before the `applicationScope.launch { ... }` seeding block, add:

```kotlin
        com.dhikr.app.core.notifications.ReminderNotifications.ensureChannel(this)
```

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (`ReminderReceiver` is referenced but not yet created — **this step will fail to compile until Task 4**. Reorder: do Step 5 verification at the end of Task 4, or stub `ReminderReceiver` now.) To keep this task independently buildable, also create the stub in the next step.

- [ ] **Step 6: Stub `ReminderReceiver` so this task compiles**

Create `app/src/main/java/com/dhikr/app/core/notifications/ReminderReceiver.kt`:

```kotlin
package com.dhikr.app.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Filled in by Task 4. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
```

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/notifications/ app/src/main/res/drawable/ic_stat_reminder.xml app/src/main/res/values/strings.xml app/src/main/java/com/dhikr/app/DhikrApplication.kt
git commit -m "Add reminder notification channel, icon, strings, builder"
```

---

### Task 4: ReminderScheduler + receivers + manifest

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/notifications/ReminderScheduler.kt`
- Modify: `app/src/main/java/com/dhikr/app/core/notifications/ReminderReceiver.kt` (replace stub)
- Create: `app/src/main/java/com/dhikr/app/core/notifications/BootReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes:
  - `NextReminderTime.next(nowMillis, minuteOfDay, daysMask, zone)` (Task 2)
  - `ReminderNotifications.post(context, routineId, routineName)`, `.cancel(...)`, `.ACTION_SNOOZE`, `.EXTRA_ROUTINE_ID`, `.EXTRA_IS_SNOOZE` (Task 3)
  - `RoutineRepository.routinesWithReminders()`, `.getRoutine(id)` (Task 1)
- Produces:
  - `class ReminderScheduler(private val appContext: Context)`
  - `fun ReminderScheduler.schedule(routineId: String, minuteOfDay: Int, daysMask: Int)`
  - `fun ReminderScheduler.cancel(routineId: String)`
  - `fun ReminderScheduler.scheduleSnooze(routineId: String)`
  - `suspend fun ReminderScheduler.rescheduleAll(repository: RoutineRepository)`

- [ ] **Step 1: Write `ReminderScheduler.kt`**

```kotlin
package com.dhikr.app.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dhikr.app.core.database.RoutineRepository

/**
 * One inexact AlarmManager alarm per enabled routine. `setAndAllowWhileIdle`
 * is inexact (no SCHEDULE_EXACT_ALARM permission) but still fires in Doze.
 * After each fire, ReminderReceiver re-arms the next occurrence.
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
```

- [ ] **Step 2: Replace the `ReminderReceiver` stub with the real implementation**

```kotlin
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
                // Re-arm the next recurring occurrence (a snoozed fire does not chain).
                if (!isSnooze && routine.reminderEnabled) {
                    scheduler.schedule(routineId, routine.reminderMinuteOfDay, routine.reminderDays)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
```

- [ ] **Step 3: Write `BootReceiver.kt`**

```kotlin
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
```

- [ ] **Step 4: Manifest — permissions and receivers**

In `AndroidManifest.xml`, add after the existing `VIBRATE` permission:

```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

Inside `<application>`, after the `<activity>` element, add:

```xml
        <receiver
            android:name=".core.notifications.ReminderReceiver"
            android:exported="false" />

        <receiver
            android:name=".core.notifications.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Lint may warn about `setAndAllowWhileIdle` battery cost — that is expected and acceptable (inexact, one alarm per routine).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/notifications/ app/src/main/AndroidManifest.xml
git commit -m "Add ReminderScheduler, reminder + boot receivers, manifest entries"
```

---

### Task 5: MainActivity deep link + DhikrApp scheduler wiring

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/dhikr/app/MainActivity.kt`
- Modify: `app/src/main/java/com/dhikr/app/DhikrApp.kt`

**Interfaces:**
- Consumes: `ReminderNotifications.EXTRA_ROUTINE_ID` (Task 3); `ReminderScheduler` (Task 4); existing nav route `"counter?routineId=$id"`.
- Produces:
  - `DhikrApp(themeMode, dynamicColor, pendingRoutineId: String?, onPendingRoutineConsumed: () -> Unit)` — two new params (both with defaults so no other caller breaks).
  - A `remember { ReminderScheduler(context.applicationContext) }` available in `DhikrApp` for later tasks via a `ReminderScheduler` passed into `RoutineEditorViewModel.Factory` and `RoutinesViewModel.Factory`.

- [ ] **Step 1: `launchMode` on MainActivity**

In `AndroidManifest.xml`, add `android:launchMode="singleTop"` to the `<activity android:name=".MainActivity" ...>` element.

- [ ] **Step 2: Read the intent in `MainActivity`**

Replace `MainActivity.kt` body with intent-aware version:

```kotlin
package com.dhikr.app

import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.datastore.ThemeMode
import com.dhikr.app.core.notifications.ReminderNotifications
import com.dhikr.app.ui.theme.resolveIsDark

class MainActivity : ComponentActivity() {

    private var pendingRoutineId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingRoutineId = intent?.getStringExtra(ReminderNotifications.EXTRA_ROUTINE_ID)
        setContent {
            val context = LocalContext.current
            val preferencesRepository = remember {
                AppPreferencesRepository(context.applicationContext)
            }
            val themeMode by preferencesRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val dynamicColor by preferencesRepository.dynamicColorEnabled.collectAsState(initial = false)

            val darkTheme = themeMode.resolveIsDark()
            SideEffect {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(TRANSPARENT)
                } else {
                    SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            DhikrApp(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                pendingRoutineId = pendingRoutineId,
                onPendingRoutineConsumed = { pendingRoutineId = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoutineId = intent.getStringExtra(ReminderNotifications.EXTRA_ROUTINE_ID)
    }
}
```

- [ ] **Step 3: Consume it in `DhikrApp`**

In `DhikrApp.kt`:

1. Change the signature:

```kotlin
@Composable
fun DhikrApp(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    pendingRoutineId: String? = null,
    onPendingRoutineConsumed: () -> Unit = {},
) {
```

2. After `val navController = rememberNavController()` and the repository `remember` blocks, add:

```kotlin
        val reminderScheduler = remember {
            com.dhikr.app.core.notifications.ReminderScheduler(context.applicationContext)
        }

        LaunchedEffect(pendingRoutineId) {
            val id = pendingRoutineId ?: return@LaunchedEffect
            navController.navigate("counter?routineId=$id")
            onPendingRoutineConsumed()
        }
```

(Add `import androidx.compose.runtime.LaunchedEffect`.)

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. `reminderScheduler` is currently unused (wired in Task 6) — a warning is acceptable, or suppress by using it in Task 6 within the same branch before final commit.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/dhikr/app/MainActivity.kt app/src/main/java/com/dhikr/app/DhikrApp.kt
git commit -m "Handle reminder-notification deep link into routine counter"
```

---

### Task 6: Routine editor Reminder section + save/cancel wiring

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/feature/routines/RoutineEditorViewModel.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/routines/RoutineEditorScreen.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/routines/RoutinesViewModel.kt`
- Modify: `app/src/main/java/com/dhikr/app/DhikrApp.kt`

**Interfaces:**
- Consumes: `ReminderScheduler` (Task 4/5); `RoutineRepository.setReminder(...)` (Task 1); strings from Task 3.
- Produces: nothing downstream (last task).

- [ ] **Step 1: Extend `RoutineEditorViewModel`**

In `RoutineEditorUiState` add:

```kotlin
    val reminderEnabled: Boolean = false,
    val reminderMinuteOfDay: Int = 8 * 60, // default 08:00
    val reminderDays: Int = 0, // 0 = every day
```

Add constructor param `private val reminderScheduler: com.dhikr.app.core.notifications.ReminderScheduler` (after `editingRoutineId`), and thread it through `Factory`.

In `init`, when loading an existing routine, also copy the reminder fields:

```kotlin
                        state.copy(
                            name = existing.routine.name,
                            steps = existing.steps
                                .sortedBy { s -> s.stepOrder }
                                .map { s -> StepDraft(s.tasbihId, s.targetCount) },
                            isEditing = true,
                            reminderEnabled = existing.routine.reminderEnabled,
                            reminderMinuteOfDay = existing.routine.reminderMinuteOfDay,
                            reminderDays = existing.routine.reminderDays,
                        ).withCanSave()
```

Add handlers:

```kotlin
    fun onReminderEnabledChange(value: Boolean) = update { it.copy(reminderEnabled = value) }

    fun onReminderTimeChange(minuteOfDay: Int) =
        update { it.copy(reminderMinuteOfDay = minuteOfDay.coerceIn(0, 24 * 60 - 1)) }

    fun onReminderDayToggle(dayBit: Int) = update { state ->
        state.copy(reminderDays = state.reminderDays xor (1 shl dayBit))
    }
```

In `onSave`, after obtaining `id` and before `onSaved(id)`:

```kotlin
            val daysMask = s.reminderDays and 0x7F
            routineRepository.setReminder(id, s.reminderEnabled, s.reminderMinuteOfDay, daysMask)
            if (s.reminderEnabled) {
                reminderScheduler.schedule(id, s.reminderMinuteOfDay, daysMask)
            } else {
                reminderScheduler.cancel(id)
            }
```

- [ ] **Step 2: Update the `Factory` in `RoutineEditorViewModel`**

```kotlin
    class Factory(
        private val routineRepository: RoutineRepository,
        private val tasbihRepository: TasbihRepository,
        private val editingRoutineId: String? = null,
        private val reminderScheduler: com.dhikr.app.core.notifications.ReminderScheduler,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RoutineEditorViewModel(routineRepository, tasbihRepository, editingRoutineId, reminderScheduler) as T
    }
```

- [ ] **Step 3: Pass the scheduler from `DhikrApp`**

In `DhikrApp.kt`, the `ROUTE_ROUTINE_EDITOR` composable — update the factory call:

```kotlin
                    val viewModel: RoutineEditorViewModel = viewModel(
                        factory = RoutineEditorViewModel.Factory(routineRepository, tasbihRepository, editingId, reminderScheduler),
                    )
```

Also update the `ROUTE_ROUTINES` composable's `RoutinesViewModel.Factory` (see Step 5).

- [ ] **Step 4: Add the Reminder section UI in `RoutineEditorScreen`**

Add these imports:

```kotlin
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Switch
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.platform.LocalContext
import com.dhikr.app.core.notifications.ReminderNotifications
```

Insert, between the "+ Add step" `Box` and the Save `Box`:

```kotlin
        FieldLabel(
            text = stringResource(R.string.reminder_section_title),
            modifier = Modifier.padding(top = 24.dp),
        )

        val context = LocalContext.current
        var showTimePicker by remember { mutableStateOf(false) }
        var permissionDenied by remember { mutableStateOf(false) }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> permissionDenied = !granted }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(ListRowShape)
                .background(colors.card)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reminder_toggle_label),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                )
                Text(
                    text = stringResource(R.string.reminder_toggle_desc),
                    fontSize = 11.5.sp,
                    color = colors.faint,
                )
            }
            Switch(
                checked = state.reminderEnabled,
                onCheckedChange = { checked ->
                    viewModel.onReminderEnabledChange(checked)
                    if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !ReminderNotifications.hasPermission(context)
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }

        if (state.reminderEnabled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .heightIn(min = 48.dp)
                    .clip(ListRowShape)
                    .background(colors.card)
                    .clickable(role = Role.Button) { showTimePicker = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.reminder_time_label),
                    fontSize = 13.sp,
                    color = colors.dim,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "%02d:%02d".format(
                        state.reminderMinuteOfDay / 60,
                        state.reminderMinuteOfDay % 60,
                    ),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                )
            }

            Text(
                text = stringResource(R.string.reminder_days_label).uppercase(),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.dim,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            )
            val dayLabels = listOf(
                R.string.reminder_day_sun, R.string.reminder_day_mon, R.string.reminder_day_tue,
                R.string.reminder_day_wed, R.string.reminder_day_thu, R.string.reminder_day_fri,
                R.string.reminder_day_sat,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                dayLabels.forEachIndexed { bit, labelRes ->
                    val selected = state.reminderDays == 0 || (state.reminderDays and (1 shl bit)) != 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 3.dp)
                            .heightIn(min = 40.dp)
                            .clip(CircleShape)
                            .background(if (selected) colors.sage else colors.surface)
                            .clickable(role = Role.Button) { viewModel.onReminderDayToggle(bit) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) colors.onSage else colors.dim,
                        )
                    }
                }
            }

            if (permissionDenied) {
                Text(
                    text = stringResource(R.string.reminder_permission_hint),
                    fontSize = 11.5.sp,
                    color = colors.faint,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        if (showTimePicker) {
            val picker = rememberTimePickerState(
                initialHour = state.reminderMinuteOfDay / 60,
                initialMinute = state.reminderMinuteOfDay % 60,
                is24Hour = true,
            )
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                containerColor = colors.card,
                shape = DialogShape,
                text = { TimePicker(state = picker) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onReminderTimeChange(picker.hour * 60 + picker.minute)
                        showTimePicker = false
                    }) { Text(stringResource(R.string.routine_complete_done), color = colors.text) }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) {
                        Text(stringResource(R.string.routines_delete_cancel_action), color = colors.dim)
                    }
                },
            )
        }
```

- [ ] **Step 5: Cancel the alarm on routine delete**

In `RoutinesViewModel.kt`, add a `reminderScheduler` constructor param + `Factory` param, and update `onDeleteRoutine`:

```kotlin
    fun onDeleteRoutine(routine: RoutineEntity) {
        viewModelScope.launch {
            repository.deleteRoutine(routine)
            reminderScheduler.cancel(routine.id)
        }
    }
```

`Factory`:

```kotlin
    class Factory(
        private val repository: RoutineRepository,
        private val tasbihRepository: TasbihRepository,
        private val reminderScheduler: com.dhikr.app.core.notifications.ReminderScheduler,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RoutinesViewModel(repository, tasbihRepository, reminderScheduler) as T
    }
```

Constructor:

```kotlin
class RoutinesViewModel(
    private val repository: RoutineRepository,
    private val tasbihRepository: TasbihRepository,
    private val reminderScheduler: com.dhikr.app.core.notifications.ReminderScheduler,
) : ViewModel() {
```

In `DhikrApp.kt` `ROUTE_ROUTINES` composable, update the factory call:

```kotlin
                    val viewModel: RoutinesViewModel = viewModel(
                        factory = RoutinesViewModel.Factory(routineRepository, tasbihRepository, reminderScheduler),
                    )
```

- [ ] **Step 6: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. `reminderScheduler` in `DhikrApp` is now used; no unused warning.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/routines/ app/src/main/java/com/dhikr/app/DhikrApp.kt
git commit -m "Add Reminder section to routine editor; wire scheduling"
```

---

## Self-Review

**Spec coverage:**
- Data model (3 columns, v7, destructive) → Task 1. ✓
- `NextReminderTime` → Task 2. ✓
- `ReminderScheduler` (schedule/cancel/rescheduleAll/scheduleSnooze) → Task 4. ✓
- `ReminderReceiver` (fire → post + re-arm; snooze action) → Task 4. ✓
- `BootReceiver` (BOOT_COMPLETED + MY_PACKAGE_REPLACED) → Task 4. ✓
- Notification (channel, icon, title/tap/Snooze, id = hashCode) → Task 3. ✓
- POST_NOTIFICATIONS request on first enable + denied hint → Task 6. ✓
- Deep link (singleTop, onNewIntent, LaunchedEffect nav) → Task 5. ✓
- Routine editor Reminder section → Task 6. ✓
- Cancel on delete → Task 6 Step 5. ✓
- Manifest (perms + receivers) → Task 4 Step 4, Task 5 Step 1. ✓
- Strings → Task 3 Step 2. ✓
- Error handling (routine deleted, permission denied, coerced minute, reboot) → covered across Tasks 2/4/6. ✓

**Known limitation (acceptable, not a gap):** reminders are not written to / read from JSON backups (`BackupModels.BackupRoutine`) — the new `RoutineEntity` fields keep their defaults on restore. Backup format is Phase 6; reminders can be added to it there.

**Placeholder scan:** No TBD/TODO. The Task 3 stub `ReminderReceiver` is intentional and replaced in Task 4 Step 2.

**Type consistency:**
- `routineId.hashCode()` used for both PendingIntent request code and notification id, consistently (Tasks 3, 4).
- `schedule(routineId: String, minuteOfDay: Int, daysMask: Int)` signature identical in Tasks 4, 5, 6.
- `daysMask`/`reminderDays` bit semantics (bit 0 = Sunday, `value % 7`) consistent between Task 2 and Task 6.
- `ReminderNotifications.hasPermission` / `.post` / `.cancel` names match between Task 3 defn and Tasks 4/6 use.
- `DhikrApp` new params `pendingRoutineId` / `onPendingRoutineConsumed` match between Task 5 defn and MainActivity call.
