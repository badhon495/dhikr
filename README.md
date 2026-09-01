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
- AI benefits: user-supplied Gemini API key (encrypted on-device), per-tasbih virtues/benefits generation cached in Room
- Notifications/reminders (scheduled, boot-persistent, local only)
- Home screen widget (small/medium, direct counting)
- Backup/export + import (JSON), routine sharing
- Session summary: tap the counter screen's elapsed-time label for started-at time, duration, counts, pace
- Auto counter (experimental, off by default): accelerometer-based wrist-flick tap, Settings toggle, hidden on devices without an accelerometer
- Onboarding tutorial: 5-page overlay shown once before Home, skippable anytime

### Not yet done
- Localization: only English strings exist (`values/`); Bengali translations pending (Arabic is content-script only, not a supported UI language — no Arabic translations or RTL planned)
- Baseline Profiles + Macrobenchmark performance testing
- Full performance/battery/memory audit (plan.md Phase 8)

### Cut from scope
- QR code routine sharing — JSON/file-based routine sharing is the only sharing mechanism
- Bundled audio pronunciation

## Tech stack
- Kotlin, Jetpack Compose, Material 3
- Kotlin Coroutines, ViewModel
- Room (structured data), DataStore (preferences)

## Project layout
```
app/src/main/java/com/dhikr/app/
  core/       # database, datastore, counter engine (+ auto-counter detector), notifications, widget, backup, share, ai, haptics
  feature/    # home, counter, tasbih, routines, insights, settings
  ui/         # shared UI
```

## Build
```
./gradlew assembleDebug
```

See [plan.md](plan.md) for full spec/requirements.
