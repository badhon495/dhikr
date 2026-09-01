# Build a High-Performance Android Tasbih / Dhikr Application

## 1. Role

You are a senior Android engineer, UI/UX engineer, and performance engineer.

Build a production-quality Android application for Muslims to count and manage Tasbih/Dhikr.

The application must be:

* Extremely fast
* Smooth on low-end Android devices
* Lightweight
* Offline-first
* Privacy-focused
* Completely free
* Completely ad-free
* Usable without an account
* Beautiful but minimal
* Accessible
* Reliable
* Easy to maintain
* Designed for long-term extensibility

Do not blindly add libraries or features. Every dependency and architectural decision must have a clear justification.

The primary goal is:

> Make counting Dhikr feel instant while keeping the entire application lightweight and reliable.

---

# 2. Technology Requirements

Build this as a native Android application.

Use:

* Kotlin
* Jetpack Compose
* Material 3 where appropriate
* Kotlin Coroutines
* ViewModel
* Room for structured local data
* DataStore for lightweight preferences/settings
* Android notification APIs for reminders
* Android App Widgets for the counter widget
* WorkManager or AlarmManager where appropriate
* Android's native lifecycle APIs
* Baseline Profiles
* Macrobenchmark for performance testing
* R8/shrinking for release builds

Use the latest stable versions of Android/Kotlin/AndroidX libraries that are compatible with the project.

Do not use React Native, Flutter, WebView, or another cross-platform framework.

The application is Android-only.

---

# 3. Core Design Principles

## Performance comes first

The app must be designed for:

* Low-end Android devices
* Devices with limited RAM
* Slow CPUs
* Small screens
* Poor/no internet connectivity
* Older supported Android versions

Do not optimize only for a modern flagship phone.

Avoid:

* unnecessary recompositions
* unnecessary object allocations
* unnecessary database writes
* unnecessary network calls
* unnecessary background work
* unnecessarily large dependencies
* huge images
* excessive animations
* unnecessary services
* polling
* loading all application data at startup

The main counting screen must feel instantaneous.

A tap should result in an immediate visual response and optional haptic feedback.

---

# 4. Offline-First Requirement

The core application MUST work without an internet connection.

The following must work completely offline:

* Counting
* Tasbih creation
* Tasbih editing
* Tasbih deletion
* Goals
* Laps
* History
* Statistics
* Routines
* Favorites
* Search
* Notifications/reminders
* Themes
* Import/export
* Widget
* Built-in Tasbih content
* Audio that is bundled locally

Internet must NOT be required for normal usage.

Do not create a backend unless absolutely necessary.

The default architecture should be:

Android App
→ Local database
→ Local preferences
→ Local notification scheduling
→ Local widgets

Optional internet functionality should be isolated from the core application.

---

# 5. No Account

Do not require:

* login
* registration
* email
* phone number
* cloud account

The user should be able to install the application and immediately start counting.

---

# 6. No Ads

The application must contain:

* No advertisements
* No ad SDK
* No tracking SDK
* No intrusive analytics

Do not introduce advertising libraries.

---

# 7. Privacy

Make privacy a major selling point.

The application should store user data locally by default.

Do not collect unnecessary personal information.

Do not send Tasbih history or personal routines to a server.

The user should be able to use the entire core application without an internet connection.

---

# 8. Application Structure

Use clean and maintainable architecture.

Prefer a simple modern architecture rather than over-engineering.

Recommended structure:

app
├── core
│   ├── database
│   ├── datastore
│   ├── model
│   ├── notifications
│   ├── haptics
│   ├── backup
│   └── utilities
│
├── feature
│   ├── home
│   ├── tasbih
│   ├── routines
│   ├── history
│   ├── statistics
│   ├── settings
│   ├── onboarding
│   └── ai
│
└── widget

Keep modules/components logically separated.

Do not create unnecessary abstraction layers.

---

# 9. Core Data Model

Design appropriate Room entities.

At minimum, support concepts such as:

## Tasbih

Fields should include something similar to:

* id
* name
* Arabic text
* transliteration
* translation
* description
* source/reference
* target count
* lap count
* createdAt
* updatedAt
* isFavorite
* isBuiltIn
* custom metadata where necessary

Do not blindly copy this exact schema if a better normalized design is appropriate.

---

# 10. Tasbih Counter Engine

Create a dedicated counter/domain component independent of the UI.

It should support:

* increment
* decrement/undo
* reset
* pause
* resume
* target
* lap target
* lap completion
* total goal
* progress
* session tracking

Conceptually:

TasbihCounter

* increment()
* undo()
* reset()
* pause()
* resume()
* completeLap()
* getCurrentCount()
* getCurrentLap()
* getProgress()

The UI must not contain the core counting logic.

The same counter logic should eventually be usable by:

* main application
* widget
* notification controls
* future Wear OS support
* external controls

---

# 11. Counter Screen

The counter screen is the most important screen in the application.

It must be extremely responsive.

Example:

SubhanAllah

67 / 100

```
    [ LARGE COUNTER ]

        67

    [ TAP AREA ]
```

Lap 2 / 3

Progress
████████░░ 67%

Provide:

* Large count
* Large touch target
* Current target
* Current lap
* Total progress
* Undo
* Reset
* Pause/resume
* Optional vibration
* Optional sound
* Optional screen-awake mode

Allow the user to tap a large portion of the screen to count.

Do not require the user to precisely hit a tiny + icon.

---

# 12. Counting Interaction

When the user taps:

1. Increment the counter immediately.
2. Update the visible UI immediately.
3. Trigger optional haptic feedback.
4. Detect lap completion.
5. Detect target completion.
6. Persist data efficiently.

Do not perform expensive work synchronously during the tap.

Do not write large database transactions on every tap.

Use an efficient persistence strategy while maintaining crash safety.

---

# 13. Undo

Provide an easy undo mechanism.

Example:

67
↓
68
↓
Undo
↓
67

At minimum, allow the user to undo the most recent count.

Do not make accidental resets easy.

---

# 14. Lap System

Support:

Example:

Tasbih:

SubhanAllah

Lap target:

33

Total target:

99

Display:

Lap 1
33/33 ✓

Lap 2
33/33 ✓

Lap 3
12/33

When the lap reaches its target:

* mark lap complete
* provide subtle feedback
* automatically move to the next lap
* reset the current lap counter

Do not lose the total count.

---

# 15. Goal System

Support:

### Daily goal

Example:

100/day

### Weekly goal

700/week

### Total goal

10,000

### Routine goal

Morning Dhikr
100 counts

Display clear progress.

Example:

72 / 100

72%

Avoid overly gamified or stressful design.

---

# 16. History

Store useful counting history.

Show:

* Today
* Yesterday
* Previous days
* Sessions
* Total counts
* Tasbih-specific history

Example:

Today

09:15
SubhanAllah
100

09:42
Alhamdulillah
100

21:10
Astaghfirullah
300

Allow the user to inspect historical activity.

---

# 17. Daily Statistics

Provide:

* Today's total
* This week's total
* This month's total
* All-time total
* Tasbih-specific totals

Also provide a calendar/history visualization.

Avoid expensive charts if they negatively affect performance.

Use lightweight Compose UI where possible.

---

# 18. Streak / Consistency

Support consistency milestones such as:

* 7 days
* 15 days
* 23 days
* 30 days
* 100 days

Do not make this feel like a competitive game.

Use positive language such as:

"7 days of consistency"

rather than aggressive streak-loss mechanics.

If the user misses a day, do not shame them.

---

# 19. Built-in Tasbih Library

Provide a collection of commonly used Dhikr/Tasbih.

Each entry should support:

* Arabic
* Transliteration
* Translation
* Meaning
* Recommended/common count where appropriate
* Source/reference

IMPORTANT:

Do not invent Islamic rulings, hadith, benefits, or religious claims.

Any religious information included in the built-in library must be sourced from reliable Islamic references.

Clearly distinguish sourced information from general explanations.

Do not allow AI-generated religious claims to silently become authoritative content.

---

# 20. Custom Tasbih

Users must be able to create their own Tasbih.

Allow:

* Name
* Arabic text
* Transliteration
* Translation
* Description
* Why they are reciting it
* Personal notes
* Target
* Lap target
* Total goal

Custom Tasbih should work completely offline.

---

# 21. Routines

Allow users to create routines.

Example:

Morning Dhikr

1. SubhanAllah — 33
2. Alhamdulillah — 33
3. Allahu Akbar — 34

The application should automatically move to the next item after completion.

Support:

* Create routine
* Edit routine
* Delete routine
* Reorder items
* Set counts
* Start routine
* Pause routine
* Resume routine
* Track routine progress

---

# 22. Preset Routines

Provide useful presets such as:

* Morning Dhikr
* Evening Dhikr
* After Salah
* Before Sleep
* Custom daily routine

Religious content must be properly sourced.

Do not claim that a specific count is religiously required unless supported by a reliable source.

---

# 23. Favorites

Users can mark Tasbih and routines as favorites.

Provide a quick-access favorites section.

---

# 24. Search

Add local search for:

* Tasbih name
* Arabic
* Transliteration
* Translation
* Routine name

Search must work offline.

Do not add a heavyweight search engine.

---

# 25. Tutorial / Onboarding

Create a very short onboarding experience.

Do not make onboarding long.

Explain:

1. How to create/select a Tasbih
2. How to count
3. How laps work
4. How goals work
5. How history works
6. Privacy/offline nature of the app

Allow users to skip onboarding.

---

# 26. Resume Previous Session

If the user leaves while counting:

Example:

SubhanAllah
437 / 1000

When they return:

"Continue where you left off?"

Continue

Start New

Do not lose active sessions.

---

# 27. Notifications / Nudges

Allow optional reminders.

Examples:

"Time for your Dhikr"

Allow users to select:

* Time
* Days
* Frequency
* Which Tasbih/routine
* Enable/disable

Do not send notifications without permission.

Do not spam the user.

Notifications should be fully local.

---

# 28. Prayer-linked Reminders

Where technically and legally appropriate, consider:

* After Fajr
* After Dhuhr
* After Asr
* After Maghrib
* After Isha
* Before sleep

Keep this optional.

Do not require location permission unless absolutely necessary.

If prayer-time calculation is introduced, prefer an offline calculation approach rather than sending location data to a server.

---

# 29. Widget

Create an Android home-screen widget.

At minimum:

Small widget:

SubhanAllah
67 / 100

[ + ]

Medium widget:

SubhanAllah

67 / 100

[ + ] [ Continue ]

If Android supports direct interaction appropriately, allow counting directly from the widget.

The widget must remain lightweight.

Do not refresh it excessively.

---

# 30. Themes

Support:

* System default
* Light
* Dark

Design should be calm and minimal.

Do not make the application excessively decorative.

The UI should feel peaceful and focused.

---

# 31. Accessibility

Support:

* Screen readers
* Large text
* Large counter mode
* High contrast
* Content descriptions
* Proper touch targets
* Reduced motion where possible
* Haptic toggle
* Sound toggle

Ensure Arabic and Bengali text render correctly.

---

# 32. Languages

Architecture must support localization from the beginning.

Initial languages:

* English
* Bengali

Use Android localization properly.

Do not hardcode UI strings.

Arabic text still appears as content (the Dhikr script itself, §9/§19/§20)
and must keep rendering correctly, but Arabic is not a supported UI
language — no Arabic-language string translations, no RTL layout support.

---

# 33. Audio — REMOVED

Out of scope. Bundled audio pronunciation will not be implemented.

---

# 34. Keep Screen Awake

Provide:

"Keep screen awake while counting"

Do not enable it globally.

Only activate it when the user explicitly enables it or while actively counting if appropriate.

---

# 35. Accidental Touch Protection

Provide an optional counter lock.

When enabled:

* prevent accidental reset
* prevent accidental navigation
* require deliberate action for destructive operations

Avoid tiny destructive buttons.

---

# 36. Import / Export

Users must be able to export their:

* Tasbih
* Routines
* Goals
* Preferences where appropriate
* History if practical

Use a documented JSON-based backup format.

Example:

MyTasbihBackup.json

Allow importing the backup later.

Validate imported data before inserting it.

Handle:

* invalid files
* duplicate IDs
* incompatible versions
* corrupted data
* missing fields

Gracefully.

---

# 37. Routine Sharing

Allow users to share routines.

Potential format:

.tasbih

or JSON.

A user should be able to:

Export Routine
→ Share
→ Another user
→ Import

No server should be necessary.

---

# 38. QR Sharing — REMOVED

Out of scope. Routine sharing (§37, JSON/file-based) is the only sharing
mechanism; QR-code generation/scanning will not be implemented.

---

# 39. AI Feature

AI is OPTIONAL.

The application must remain fully useful without AI.

Do not make users provide an API key just to use basic functionality.

If AI is implemented:

* isolate it behind a separate feature
* do not initialize it at startup
* do not make it a dependency of the counter
* do not send private history unnecessarily
* clearly indicate when information is AI-generated
* never present AI as a religious authority

Potential features:

* Explain a Tasbih
* Translate text
* Explain meaning
* Help organize a routine
* Answer general questions about the provided text

For religious claims, prefer sourced information.

If the user supplies their own API key, store it securely.

Do not hardcode API keys.

Do not ship an application-wide secret API key inside the APK.

---

# 40. Auto Counter

Implement only as an optional advanced feature.

If it means detecting physical repetitions using sensors/camera/microphone:

* keep it disabled by default
* explain its limitations
* minimize battery usage
* avoid unnecessary permissions
* do not let it affect normal counter performance

The standard manual counter must remain the primary mode.

---

# 41. Timer

Support an optional session timer.

Example:

Session started:
09:30

Duration:
12:43

Counts:
500

Counts/minute:
39

Do not make the timer consume unnecessary CPU in the background.

---

# 42. Sharing Progress

Allow users to share a simple progress summary.

Example:

Today's Dhikr

SubhanAllah — 100
Alhamdulillah — 100
Allahhu Akbar — 100

Total: 300

Do not expose private information unless the user explicitly chooses to share it.

---

# 43. Home Screen

Keep the home screen simple.

Suggested structure:

Greeting

Continue previous session

Favorite Tasbih

Today's progress

Recent routines

Quick Start

Do not load the entire database into the home screen unnecessarily.

Use lazy lists where appropriate.

---

# 44. Navigation

Keep navigation simple.

Suggested navigation:

Home
Tasbih
History
Statistics
Settings

Avoid having dozens of navigation destinations loaded eagerly.

Lazy-load or initialize features only when needed.

---

# 45. Performance Requirements

Treat performance as a first-class feature.

The following must be benchmarked:

## Startup

Measure:

* cold startup
* warm startup
* time to first frame
* time to interactive UI

## Counter

Measure:

* 100 taps
* 1,000 taps
* lap completion
* undo
* reset

## Navigation

Measure:

* Home → Tasbih
* Tasbih → History
* History → Statistics
* Home → Settings

## Lists

Measure:

* 100 Tasbih entries
* 1,000 history entries
* large routine

---

# 46. Baseline Profiles

Implement Baseline Profiles for important user journeys.

At minimum profile:

1. Application startup
2. Home screen
3. Opening a Tasbih
4. Starting a counting session
5. Counting
6. Opening History
7. Opening Statistics

Verify that the Baseline Profile actually covers the intended code paths.

---

# 47. Macrobenchmark

Create Macrobenchmark tests.

Measure:

* Startup
* Counter interaction
* Navigation
* Scrolling
* Important animations

Do not claim performance improvements without measurements.

---

# 48. Compose Performance

Avoid unnecessary recomposition.

Use:

* stable state where appropriate
* immutable UI state where appropriate
* derived state where appropriate
* keys in lazy lists
* remember only where it provides value
* lazy layouts for potentially large collections

Do not use `remember` everywhere blindly.

Do not introduce unnecessary state hoisting complexity.

Profile before optimizing.

---

# 49. Database Performance

Room database should:

* use appropriate indexes
* avoid unnecessary queries
* avoid N+1 query patterns
* use Flow only where reactive updates are actually required
* perform database operations off the main thread
* batch writes where appropriate

Do not continuously observe huge tables when the UI only needs a small subset.

---

# 50. Startup Performance

Do not initialize every feature during application startup.

Avoid:

* initializing AI clients
* reading entire history
* loading all Tasbih
* scheduling unnecessary work
* initializing heavy libraries

at startup.

Only initialize what is required for the first screen.

---

# 51. Memory

Keep memory usage low.

Avoid:

* storing huge collections in memory
* duplicate data models
* unnecessarily large images
* memory-heavy caches
* retaining Activity/Context references
* long-lived coroutine scopes that leak

Check for memory leaks.

---

# 52. Battery

The application should consume almost no battery when idle.

Avoid:

* polling
* unnecessary foreground services
* frequent background tasks
* sensor usage unless explicitly enabled
* continuous network requests

Notifications should be scheduled rather than constantly checked.

---

# 53. Permissions

Request the absolute minimum permissions.

Do not request:

* location
* contacts
* storage
* microphone
* camera

unless a feature genuinely needs it.

Explain permissions before requesting them.

---

# 54. Security

Protect:

* API keys
* imported data
* local sensitive information

Validate imported files.

Do not trust external JSON blindly.

Do not expose internal database details.

Do not hardcode secrets.

---

# 55. Build Size

Keep the release APK/AAB as small as reasonably possible.

Use:

* R8
* resource shrinking
* optimized images
* compressed assets
* minimal dependencies

Do not sacrifice usability simply to save a few MB.

Measure release size.

---

# 56. UI/UX Philosophy

The UI should feel:

* peaceful
* clean
* minimal
* modern
* respectful
* fast
* focused

Avoid:

* excessive animations
* gamification
* aggressive streak mechanics
* unnecessary badges
* clutter
* excessive gradients
* huge decorative images

The primary interaction should always remain obvious:

> Count Dhikr.

---

# 57. Error Handling

The application should never crash because of:

* invalid imported backup
* corrupted data
* notification permission denial
* missing widget
* unavailable AI
* offline mode
* database migration issues

Provide graceful fallback behavior.

---

# 58. Testing

Create:

## Unit tests

For:

* Counter
* Lap calculation
* Goal calculation
* Progress calculation
* Streak calculation
* Import/export
* Routine ordering
* Data validation

## UI tests

For:

* Start counting
* Increment
* Undo
* Complete lap
* Complete goal
* Pause/resume
* Create Tasbih
* Create routine
* Import/export

## Integration tests

For:

* Room
* DataStore
* Notifications
* Widget
* Backup/restore

## Performance tests

Use Macrobenchmark.

---

# 59. Test Important Edge Cases

Test:

* 0 target
* target = 1
* very large target
* lap target > total target
* total target not divisible by lap target
* rapid tapping
* accidental double taps
* app killed during session
* device rotated
* app backgrounded
* device restarted
* dark mode
* Bengali
* low memory
* no internet
* notification permission denied
* importing malformed backup
* duplicate imported data

---

# 60. Android Lifecycle

Correctly handle:

* Activity recreation
* configuration changes
* backgrounding
* process death
* device restart

Do not rely solely on in-memory state.

---

# 61. Small Screen Support

The application must work well on small Android screens.

Do not assume:

* large displays
* large RAM
* high DPI
* flagship performance

Ensure:

* buttons remain accessible
* counter is readable
* text doesn't overflow
* Arabic doesn't clip
* Bengali doesn't clip
* navigation remains usable

---

# 62. Responsive Design

Support different screen sizes.

Do not hardcode everything in pixels.

Use appropriate Compose dimensions and responsive layouts.

---

# 63. Release Configuration

Configure:

* debug build
* release build
* R8
* resource shrinking
* signing-ready configuration
* ProGuard/R8 rules where necessary
* Baseline Profile
* Macrobenchmark

Do not commit signing keys or secrets.

---

# 64. Documentation

Create a README explaining:

* architecture
* project structure
* setup
* build commands
* testing
* release build
* performance benchmarking
* Baseline Profiles
* database structure
* backup format
* AI configuration
* widget implementation

Document important architectural decisions.

---

# 65. Development Process

Do NOT attempt to implement everything in one giant step.

Work in phases.

## Phase 1 — Architecture

First:

* create project
* configure Kotlin
* configure Compose
* configure Room
* configure DataStore
* configure navigation
* establish architecture
* establish theme
* create models
* create counter engine

Then verify that the project builds.

---

## Phase 2 — Core Counter

Implement:

* Tasbih
* Counter
* Target
* Laps
* Undo
* Pause/resume
* Automatic persistence
* Haptic feedback
* Reset
* Session recovery

Benchmark this phase.

The counter must feel extremely responsive before moving on.

---

## Phase 3 — Data

Implement:

* Room
* History
* Statistics
* Goals
* Favorites
* Search
* Custom Tasbih
* Routines

Benchmark database operations.

---

## Phase 4 — UX

Implement:

* onboarding
* animations
* themes
* accessibility
* localization
* Bengali
* improved navigation

Do not allow animations to hurt responsiveness.

---

## Phase 5 — Notifications and Widget

Implement:

* reminders
* notification actions
* home-screen widget

Test across Android versions.

---

## Phase 6 — Backup and Sharing

Implement:

* JSON backup
* import
* export
* routine sharing

---

## Phase 7 — Advanced Features

Only after the core app is stable:

* timer
* auto-counter
* AI
* advanced statistics
* advanced customization

---

## Phase 8 — Performance

Perform a full performance audit.

Check:

* startup
* frame rendering
* memory
* battery
* database
* widget
* APK/AAB size
* recomposition
* unnecessary dependencies
* network activity

Add/fix Baseline Profiles.

Create Macrobenchmarks.

---

# 66. Important Development Rule

Do not make assumptions such as:

"Kotlin is fast, therefore the application is fast."

Measure actual performance.

Whenever you make a performance optimization:

1. Measure before.
2. Make the change.
3. Measure after.
4. Keep the change only if it provides a meaningful benefit or improves architecture without hurting performance.

Avoid premature optimization.

---

# 67. Religious Content Safety

This is a religious application, so accuracy is important.

Do not invent:

* hadith
* Quran references
* benefits
* recommended counts
* religious rulings
* claims about rewards

If a claim cannot be reliably sourced, do not present it as established religious fact.

Where appropriate, show:

* Source
* Hadith reference
* Quran reference
* Scholarly reference

Keep AI-generated explanations clearly separate from verified source material.

---

# 68. Final Quality Requirements

Before considering the application complete, verify:

* App starts quickly.
* Counter responds instantly.
* App works offline.
* No ads exist.
* No unnecessary permissions exist.
* No account is required.
* Data is persisted reliably.
* User can recover previous sessions.
* Import/export works.
* Widget works.
* Notifications work.
* Dark/light modes work.
* Bengali works.
* Small screens work.
* Low-end devices remain usable.
* No obvious memory leaks exist.
* No unnecessary network calls exist.
* Release build is optimized.
* R8 works.
* Baseline Profile works.
* Macrobenchmarks exist.
* Unit/UI/integration tests exist.
* Documentation exists.

---

# 69. Critical Instruction to the Coding Agent

Do not simply generate code until the feature list is exhausted.

Before implementing each major feature:

1. Explain the architectural approach briefly.
2. Identify potential performance implications.
3. Implement it.
4. Test it.
5. Check for regressions.
6. Keep the implementation as simple as possible.

If there are two reasonable approaches, prefer the one that:

1. Has lower runtime overhead.
2. Has fewer dependencies.
3. Has lower memory usage.
4. Has simpler maintenance.
5. Works offline.
6. Is easier to test.

Do not add a library merely because it makes implementation slightly easier.

---

# 70. Definition of Success

The finished application should feel like:

> "I tap the screen and the number changes immediately."

It should NOT feel like:

> "I tapped the screen and the application is doing something."

The user should never have to think about the application's technology.

The application should simply feel:

**fast, calm, reliable, private, and effortless.**

Build the application with performance and simplicity as first-class requirements, not as optimization work to be done at the end.
