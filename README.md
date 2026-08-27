# Dhikr

Native Android Tasbih/Dhikr counter app. Fast, offline-first, ad-free, no account needed.

Kotlin + Jetpack Compose + Material 3 + Room + DataStore.

## Progress

### Done
- Core counter engine (tap-to-count, session tracking)
- Room database: Tasbih, Routine, RoutineStep, Session entities + DAOs
- Custom Tasbih create/edit/delete
- Routines: preset seed data, list, start, step auto-advance, delete
- Home screen: goal ring, continue session, favorites, routines shortcuts
- Insights screen: totals, 7-day bar chart, consistency calendar, history log
- Bottom navigation wired across Home / Counter / Routines / Insights
- Local day-bucketing for history/stats (reactive, no stale data)
- Crash guards and edge-case fixes from review passes (cold start, uninitialized engine, session precedence)
- UI responsiveness and theme (light/dark) consistency pass

### Not yet done
- Notifications/reminders (Android notification APIs)
- Home screen widget
- WorkManager/AlarmManager scheduling
- Baseline Profiles + Macrobenchmark performance testing
- Release build hardening (R8/shrinking verification)

## Tech stack
- Kotlin, Jetpack Compose, Material 3
- Kotlin Coroutines, ViewModel
- Room (structured data), DataStore (preferences)

## Project layout
```
app/src/main/java/com/dhikr/app/
  core/       # database (entities, DAOs, seed), datastore, counter engine, models
  feature/    # home, counter, tasbih, routines, insights
  ui/         # shared UI
```

## Build
```
./gradlew assembleDebug
```

See [plan.md](plan.md) for full spec/requirements.
