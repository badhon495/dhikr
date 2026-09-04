# Dhikr

Native Android Tasbih / Dhikr counter. Fast, offline-first, ad-free, no account required.

Kotlin · Jetpack Compose · Material 3 · Room · DataStore. minSdk 24, targetSdk 37.

## Features

- Tap-to-count engine with session tracking and undo
- Built-in dhikr library plus custom Tasbih (create / edit / delete)
- Routines: multi-step dhikr sequences with auto-advance; preset seeds included
- Home screen: daily goal ring, resume session, favorites, routine shortcuts
- Insights: daily / weekly / all-time totals, 7-day chart, consistency calendar, history log
- Reminders: scheduled local notifications, persist across reboot
- Home screen widgets: a counter widget (count without opening the app) and an insights widget
- Backup: JSON export / import from Settings; routine sharing via share code
- Optional AI benefits: bring your own Google Gemini API key (encrypted on-device), per-tasbih virtues cached locally
- Auto counter (experimental, off by default): accelerometer wrist-flick detection
- English and Bengali UI; light / dark themes; onboarding tutorial

## Project layout

```
app/
  src/main/java/com/dhikr/app/
    core/       # database, datastore, counter engine, notifications, widget, backup, share, ai, haptics
    feature/    # home, counter, tasbih, routines, insights, settings
    ui/         # shared UI, theme, icons
baselineprofile/  # com.android.test module — generates app/src/main/baseline-prof.txt
benchmark/        # com.android.test module — Macrobenchmarks (startup, counter, scroll, navigation)
```

## Build

```
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:assembleRelease      # R8 + resource-shrunk release APK
./gradlew :app:bundleRelease        # release AAB
./gradlew :app:testDebugUnitTest    # JVM unit tests
```

Release signing reads `keystore.properties` at the repo root (git-ignored). Without it
the release build is unsigned but otherwise identical.

## Database

Single Room `AppDatabase` (`dhikr.db`). All data is seeded or derived and fully
rebuildable, so schema changes use `fallbackToDestructiveMigration(dropAllTables = true)`
rather than hand-written migrations. Day-scoped progress rows are stamped with local
midnight and filtered on read — this is the "reset at midnight" behaviour.

## AI configuration

Optional. The user supplies their own Google Gemini API key in Settings. It is stored
with `EncryptedSharedPreferences` (`androidx.security-crypto` / Tink) and never leaves
the device except in the request to Gemini. Generated benefits text is cached in the
`tasbih` table.

## Performance

`:baselineprofile` generates `app/src/main/baseline-prof.txt`; `:benchmark` holds the
Macrobenchmark suite. Run both on a physical device (Macrobenchmark API 29+, Baseline
Profile generation API 33+) with the device locale set to English — tabs are selected
by visible label text.

```
./gradlew :app:generateBaselineProfile
./gradlew :benchmark:connectedBenchmarkAndroidTest
```
