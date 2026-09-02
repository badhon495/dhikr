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
- Baseline Profile + Macrobenchmark modules (`:baselineprofile`, `:benchmark`); Phase 8 code/build performance pass (see [Performance](#performance))

### Not yet done
- Localization: only English strings exist (`values/`); Bengali translations pending (Arabic is content-script only, not a supported UI language — no Arabic translations or RTL planned)
- Phase 8 device measurement pass: the before/after benchmark deltas that gate the Workstream B/C optimizations (see [Performance](#performance))

### Cut from scope
- QR code routine sharing — JSON/file-based routine sharing is the only sharing mechanism
- Bundled audio pronunciation

## Tech stack
- Kotlin, Jetpack Compose, Material 3
- Kotlin Coroutines, ViewModel
- Room (structured data), DataStore (preferences)
- `androidx.profileinstaller` + Baseline Profile; `androidx.benchmark` (Macrobenchmark) in the `:benchmark` module

## Project layout
```
app/            # the application
  src/main/java/com/dhikr/app/
    core/       # database, datastore, counter engine (+ auto-counter detector), notifications, widget, backup, share, ai, haptics
    feature/    # home, counter, tasbih, routines, insights, settings
    ui/         # shared UI, icons
baselineprofile/ # com.android.test module — generates app/src/main/baseline-prof.txt
benchmark/       # com.android.test module — Macrobenchmarks (startup, counter, scroll, navigation)
```

## Build
```
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:assembleRelease      # R8 + resource-shrunk release APK
./gradlew :app:bundleRelease        # release AAB
./gradlew :app:testDebugUnitTest    # JVM unit tests
```

Release signing reads `keystore.properties` at the repo root (git-ignored);
without it the release build is unsigned but otherwise identical.

## Database

Room, single `AppDatabase` (`dhikr.db`), schema version 10. All data is
seeded or derived (built-in dhikr, preset routines, session history) and
fully rebuildable, so schema changes use
`fallbackToDestructiveMigration(dropAllTables = true)` — no hand-written
migrations. Entities: `tasbih`, `routine`, `routine_step`, `session`,
`routine_completion`, `routine_progress`, `tasbih_progress`. Day-scoped
progress rows are stamped with local midnight and filtered on read, which
is the "reset at midnight" behaviour.

## Backup format

JSON, exported/imported from Settings. Round-trips Tasbih, routines +
steps, session history, and preferences. `kotlinx.serialization` (codegen,
not reflection).

## AI configuration

Optional. The user supplies their own Google Gemini API key in Settings;
it is stored with `EncryptedSharedPreferences` (`androidx.security-crypto`
/ Tink) and never leaves the device except in the request to Gemini.
Generated per-tasbih benefits text is cached in the `tasbih` table.

## Widget

Classic `RemoteViews` (no Glance dependency). A counter widget increments
the persisted session without opening the app (`core/counter/WidgetCounter`
+ `core/widget/`), and an insights widget shows today / week / all-time
totals.

## Performance

Performance is treated as a first-class requirement (plan.md §45–§55,
§66), not end-of-project cleanup.

### Modules

| Module | Plugin | What it produces |
|---|---|---|
| `:baselineprofile` | `com.android.test` + `androidx.baselineprofile` | `app/src/main/baseline-prof.txt` from the plan.md §46 journey (cold start → Home → Tasbih → 50-tap counter session → undo → Insights → Monthly history → Home) |
| `:benchmark` | `com.android.test` + `androidx.benchmark` | Macrobenchmarks: `StartupBenchmark` (cold/warm, None/Partial/Full compilation), `CounterBenchmark` (100/1000 taps + idle-with-timer), `ScrollBenchmark` (Tasbih list, Insights), `NavigationBenchmark` (tab hops) |

`:app` has an `androidx.profileinstaller` dependency, the
`baselineProfile(project(":baselineprofile"))` consumer wiring, and a
`benchmark` build type (release-like, debug-signed, `isDebuggable = false`,
R8 on).

### Commands

Run on a physical device (Macrobenchmark needs API 29+, Baseline Profile
generation needs API 33+ non-rooted). The device locale must be **English**
— the profile generator and benchmarks select nav tabs by their visible
label text.

```
./gradlew :app:generateBaselineProfile              # regenerate baseline-prof.txt
./gradlew :benchmark:connectedBenchmarkAndroidTest  # full Macrobenchmark suite
./gradlew :benchmark:connectedBenchmarkAndroidTest --tests "com.dhikr.app.benchmark.StartupBenchmark"
```

Benchmark JSON lands in
`benchmark/build/outputs/connected_android_test_additional_output/`.

### Measure-before / measure-after discipline (§66)

Every Workstream B (code) and C (build) change is gated on a before/after
benchmark: measure, change, measure, keep only on a meaningful benefit or
a clear architectural improvement with no regression; otherwise revert.
The full record — every candidate, its rationale, and its measured delta —
lives in
[`docs/superpowers/specs/2026-09-02-phase-8-performance-design.md`](docs/superpowers/specs/2026-09-02-phase-8-performance-design.md).

Implemented so far: routine-step caching (B1), the counter timer flow
split (B2), Compose `@Immutable` stability (B3), destination-scoped
repositories (B4), `material-icons-extended` → core (B5), a
`session.routineId` index (B6), and IO-dispatched DB seeding (B7). The
device measurement pass that confirms/reverts each is pending.

See [plan.md](plan.md) for the full spec/requirements.
