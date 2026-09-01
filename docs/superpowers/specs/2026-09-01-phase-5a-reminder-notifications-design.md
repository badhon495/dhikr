# Phase 5A — Reminder Notifications — Design

Date: 2026-09-01
Status: approved for planning

## Context

Phase 5 of `plan.md` is "Notifications and Widget". It is split into
sub-projects:

- **5A (this spec)** — per-routine reminder notifications.
- **5B** — home-screen widget (separate spec/plan/implement cycle).
- **5C** — prayer-linked reminders (deferred).

The app is native Android (Kotlin, Compose, Room, DataStore). No DI
framework: repositories are constructed by hand in the `DhikrApp`
composable; `DhikrApplication` owns the Room database. Deep-link route
strings already exist (`counter?routineId={routineId}`) and
`CounterViewModel` already accepts a `startingRoutineId`. `MainActivity`
does not currently read its launch `Intent`.

## Goal

Let the user attach a single recurring local reminder to a routine:
a time of day, a set of weekdays, and an on/off switch. When it fires,
a notification says "Time for {routine name}"; tapping it opens that
routine's counter; a Snooze action re-fires it 15 minutes later.

Everything is local. No network, no account, no new dependency.

## Non-goals

- More than one reminder per routine.
- Reminders on individual tasbih or a global app reminder.
- Exact-to-the-minute delivery (inexact alarms are acceptable for a
  dhikr nudge).
- Prayer-time calculation (5C).
- "Mark done" notification action.
- Localization of the new strings beyond the existing English resource
  file (Phase 4 localization is deferred project-wide).
- Test infrastructure (project remains test-free for now).

## Data model

Add three columns to `RoutineEntity`
(`core/database/entity/RoutineEntity.kt`):

| column                | type      | default | meaning |
|-----------------------|-----------|---------|---------|
| `reminderEnabled`     | `Boolean` | `false` | reminder on/off |
| `reminderMinuteOfDay` | `Int`     | `0`     | 0–1439, local wall-clock minute |
| `reminderDays`        | `Int`     | `0`     | 7-bit weekday mask; bit 0 = Sunday … bit 6 = Saturday; `0` means every day |

Bump `AppDatabase` version 6 → 7. **No hand migration** — the existing
`fallbackToDestructiveMigration(dropAllTables = true)` rebuilds and
reseeds, consistent with every prior schema change in this project
(see the version history comment in `AppDatabase.kt`).

`SeedData` preset routines keep the defaults (reminder off).

### Repository surface (`RoutineRepository`)

- `setReminder(routineId: String, enabled: Boolean, minuteOfDay: Int, daysMask: Int)`
  — updates the row (`updatedAt` bumped).
- `routinesWithReminders(): List<RoutineWithSteps>` — one-shot read of
  every routine where `reminderEnabled = 1`, for `rescheduleAll()`.
- `getWithSteps(id)` already exists — used by the receiver to resolve
  the routine name on fire.

`RoutineDao` gains the matching `@Query` methods. `createRoutine` /
`updateRoutine` signatures are left unchanged; the editor ViewModel
persists reminder fields with a separate `setReminder(...)` call right
after the create/update returns.

## Scheduling layer — new package `core/notifications/`

### `NextReminderTime` (pure Kotlin, object with one function)

```
fun next(
    nowMillis: Long,
    minuteOfDay: Int,
    daysMask: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): Long
```

Returns the epoch-millis of the next occurrence:

1. Normalise `daysMask`: `0` → all 7 bits set.
2. Starting from today in `zone`, walk forward up to 8 days.
3. For each day whose weekday bit is set, compute that day's
   `minuteOfDay` instant. Return the first such instant strictly after
   `nowMillis`.
4. Day 0 (today) is only a candidate if its instant is still in the
   future.

Uses `java.time` (`ZonedDateTime`, `LocalTime`, `DayOfWeek`). No
Android imports. `DayOfWeek.SUNDAY` maps to bit 0 (Java's
`DayOfWeek` is MON=1..SUN=7, so bit index = `dayOfWeek.value % 7`).

### `ReminderScheduler` (constructed with application `Context`)

- `schedule(routine: RoutineEntity)` — no-op if `!reminderEnabled`;
  otherwise compute `NextReminderTime.next(...)` and set one inexact
  alarm:
  `alarmManager.setAndAllowWhileIdle(RTC_WAKEUP, triggerAt, pendingIntent)`.
  (`setAndAllowWhileIdle` is inexact — it does not need
  `SCHEDULE_EXACT_ALARM` — but still fires in Doze windows.)
- `cancel(routineId: String)` — cancel the `PendingIntent`.
- `rescheduleAll()` — `suspend`; reads `routinesWithReminders()` and
  `schedule(...)`s each. Called from `BootReceiver`.
- `scheduleSnooze(routineId, routineName)` — one-off alarm 15 min out
  (reuses the same receiver with an `EXTRA_IS_SNOOZE` flag so it does
  not chain another occurrence).

`PendingIntent`:
- request code = `routineId.hashCode()` (stable per routine).
- `Intent` targets `ReminderReceiver`, carries `EXTRA_ROUTINE_ID`.
- flags `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`.

### `ReminderReceiver : BroadcastReceiver` (manifest-registered)

`onReceive`:
1. `val pending = goAsync()`.
2. On a background coroutine (`CoroutineScope(Dispatchers.Default)`):
   - Resolve `EXTRA_ROUTINE_ID` → `RoutineRepository.getWithSteps(id)`.
     If the routine is gone (deleted), do nothing further.
   - If action is `ACTION_SNOOZE`: cancel the shown notification and
     `scheduler.scheduleSnooze(...)`; done.
   - Otherwise: post the notification (below), then — unless
     `EXTRA_IS_SNOOZE` — `scheduler.schedule(routine)` to arm the next
     occurrence.
3. `pending.finish()`.

Repositories are built ad-hoc here from
`context.applicationContext as DhikrApplication` → `database`, matching
how `DhikrApp` builds them. (A small `DhikrApplication` accessor —
`fun routineRepository(): RoutineRepository` — is acceptable if it
reduces duplication.)

### `BootReceiver : BroadcastReceiver` (manifest-registered)

Listens for `ACTION_BOOT_COMPLETED` and `ACTION_MY_PACKAGE_REPLACED`
(alarms are cleared on reboot and on app update). `goAsync()` →
`scheduler.rescheduleAll()` → `finish()`.

## Notification

- Channel `reminders` (id `"reminders"`), importance
  `IMPORTANCE_DEFAULT`, created in `DhikrApplication.onCreate()` via
  `NotificationManagerCompat` (guard for API < 26 is automatic with
  the compat API; the create call is cheap and idempotent).
- New drawable `res/drawable/ic_stat_reminder.xml` — a simple
  monochrome vector (white on transparent), reusing a glyph consistent
  with `ui/NavIcons.kt` treatment.
- Notification content:
  - small icon `ic_stat_reminder`
  - title: `getString(R.string.reminder_notification_title, routineName)`
    → "Time for Morning Dhikr"
  - `setAutoCancel(true)`, `setCategory(CATEGORY_REMINDER)`
  - content `PendingIntent` → `MainActivity` with
    `EXTRA_ROUTINE_ID`, `FLAG_ACTIVITY_SINGLE_TOP`, immutable.
  - action: "Snooze" → broadcast to `ReminderReceiver` with
    `ACTION_SNOOZE` + `EXTRA_ROUTINE_ID`.
- Notification id = `routineId.hashCode()` so a routine's reminder
  replaces its own previous notification rather than stacking.

### POST_NOTIFICATIONS permission (API 33+)

- Declared in the manifest.
- Requested from the routine editor the first time the user toggles a
  reminder **on**, using
  `rememberLauncherForActivityResult(RequestPermission())`.
- A short rationale line is shown above the toggle before the system
  dialog.
- If denied: the reminder setting is still saved and the alarm still
  scheduled (it simply won't surface a notification until the user
  grants the permission in system settings). The editor shows an
  inline "Notifications are turned off for Dhikr" hint with the
  existing muted style.
- API < 33: no runtime request; treated as granted.

## Deep link into the routine counter

- `MainActivity`: add `android:launchMode="singleTop"` in the
  manifest. Override `onNewIntent` and also read `intent` in
  `onCreate`. Extract `EXTRA_ROUTINE_ID` into a
  `mutableStateOf<String?>` hoisted in `setContent`, passed to
  `DhikrApp` as `pendingRoutineId`.
- `DhikrApp(pendingRoutineId: String?, ...)`: a `LaunchedEffect` keyed
  on the value navigates `navController.navigate("counter?routineId=$id")`
  then calls a `onPendingRoutineConsumed()` callback so it fires once.
- No change to `CounterViewModel` / `CounterScreen` — the existing
  `startingRoutineId` path handles it.

## UI — routine editor

`RoutineEditorScreen` gains a "Reminder" section below the steps list,
above Save:

- A toggle row ("Daily reminder", switch on the right).
- When on:
  - Time row — shows the formatted time, opens a Compose `TimePicker`
    inside an `AlertDialog` on tap.
  - A row of 7 small weekday toggle chips (S M T W T F S). All-off is
    coerced to all-on at save (matches `daysMask = 0` semantics).
  - The permission hint line when applicable.

`RoutineEditorViewModel`:
- `RoutineEditorUiState` gains `reminderEnabled`, `reminderMinuteOfDay`,
  `reminderDays`.
- Loads them from the existing routine in `init` (edit mode);
  defaults for new routines.
- `onReminderEnabledChange`, `onReminderTimeChange`,
  `onReminderDaysToggle` handlers.
- `onSave`: after `createRoutine` / `updateRoutine`, call
  `RoutineRepository.setReminder(id, ...)` then
  `reminderScheduler.schedule(savedRoutineEntity)` (or `cancel(id)` if
  disabled).
- New `Factory` param: `ReminderScheduler` (built in `DhikrApp` with
  `context.applicationContext`).

Routine deletion (`RoutinesViewModel` / wherever `deleteRoutine` is
called): also call `reminderScheduler.cancel(routine.id)`.

## Manifest changes

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

```xml
<activity android:name=".MainActivity" android:launchMode="singleTop" ... />

<receiver android:name=".core.notifications.ReminderReceiver" android:exported="false" />
<receiver
    android:name=".core.notifications.BootReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
    </intent-filter>
</receiver>
```

No `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` — inexact alarms only.

## New string resources (`values/strings.xml`)

- `reminder_section_title` — "Reminder"
- `reminder_toggle_label` — "Daily reminder"
- `reminder_time_label` — "Time"
- `reminder_days_label` — "Repeat"
- `reminder_notification_title` — "Time for %1$s"
- `reminder_snooze_action` — "Snooze 15 min"
- `reminder_permission_hint` — "Notifications are turned off for Dhikr"
- `reminder_permission_rationale` — short sentence shown before the
  system prompt
- weekday initials — reuse a `string-array` or single-letter strings

## Error handling (plan §57)

- Routine deleted between alarm set and fire → receiver finds no row,
  posts nothing.
- Permission denied → alarm still armed, no crash, inline hint.
- `NextReminderTime` with `minuteOfDay` out of range → coerced into
  `0..1439`.
- Reboot / app update → `BootReceiver` re-arms everything.
- Snooze on a routine whose notification was already dismissed → still
  schedules the +15 min alarm; harmless.

## Files touched

New:
- `core/notifications/NextReminderTime.kt`
- `core/notifications/ReminderScheduler.kt`
- `core/notifications/ReminderReceiver.kt`
- `core/notifications/BootReceiver.kt`
- `core/notifications/ReminderNotifications.kt` (channel + builder helpers)
- `res/drawable/ic_stat_reminder.xml`

Changed:
- `core/database/entity/RoutineEntity.kt` (+3 columns)
- `core/database/AppDatabase.kt` (version 7)
- `core/database/dao/RoutineDao.kt` (+queries)
- `core/database/RoutineRepository.kt` (+`setReminder`, +`routinesWithReminders`)
- `DhikrApplication.kt` (notification channel; optional repo accessor)
- `MainActivity.kt` (intent handling)
- `DhikrApp.kt` (build `ReminderScheduler`; `pendingRoutineId` plumbing)
- `feature/routines/RoutineEditorScreen.kt` (Reminder section)
- `feature/routines/RoutineEditorViewModel.kt` (reminder state + save)
- `feature/routines/RoutinesViewModel.kt` (cancel on delete)
- `AndroidManifest.xml`
- `res/values/strings.xml`

## Verification

Per project convention (`verify-via-build-not-emulator` memory):
`./gradlew :app:assembleDebug` after edits. Manual reasoning for the
`NextReminderTime` cases. Emulator run only if the user asks.
