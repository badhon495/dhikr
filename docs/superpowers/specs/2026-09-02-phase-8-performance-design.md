# Phase 8 — Performance & Optimization — Design

Date: 2026-09-02
Status: Approved for implementation

## Goal

Complete plan.md Phase 8 (§65) and the performance requirements it
depends on (§45–§55, §66). The app has shipped Phases 1–6; it has no
benchmark infrastructure, no Baseline Profile, and no measured
performance baseline. This phase adds the measurement tooling first,
then makes only the code and build changes that a before/after
measurement shows to be worthwhile.

The governing rule for every change in Workstreams B and C is plan.md
§66: measure before, make the change, measure after, keep the change
only if it gives a meaningful benefit or improves architecture without
hurting performance.

## Scope

In scope:

- A `:baselineprofile` Gradle module that generates
  `app/src/main/baseline-prof.txt` covering the plan.md §46 journeys.
- A `:benchmark` Gradle module with Macrobenchmark tests for startup,
  counter interaction, scrolling, and navigation (plan.md §47).
- `androidx.profileinstaller` in `:app`, a `baselineProfile` consumer
  wiring, and a `benchmark` build type.
- A measured before/after table for every candidate optimization,
  committed into this spec.
- Code-level changes in `CounterViewModel` / `CounterUiState` /
  `DhikrApp`, and the `material-icons-extended` → core dependency swap,
  each gated on its benchmark delta.
- A release APK/AAB size measurement and an R8 / dependency audit.
- Filling in the README performance section (plan.md §64).

Out of scope:

- Wear OS support and any new user-facing feature.
- Replacing `androidx.security-crypto` / Tink (audit and note only —
  changing it needs its own justification per plan.md §25).
- Any change whose benchmark shows no measurable benefit — such
  findings are dropped or recorded as "no measurable effect", not
  merged speculatively.
- Microbenchmark (`androidx.benchmark.junit4`) — Macrobenchmark covers
  the journeys that matter here; a microbenchmark module is not
  justified by any current question.

## Device and environment

Benchmarks run on the connected device: Xiaomi POCO F3 (`alioth`),
Android 15, API 35, `adb` over TCP at `192.168.31.252:5555`. API 35
satisfies Macrobenchmark (API 29+) and Baseline Profile generation
(API 33+, non-rooted, via the `androidx.benchmark` broadcast path).

All benchmark and profile-generation runs are executed by the user on
this device. Claude scaffolds the modules and writes the test code so
it compiles; Claude does not assume it can run instrumented tests.

## Workstream A — Benchmark infrastructure

This workstream is a hard prerequisite for B and C. No optimization in
B or C is implemented until A4 has produced a committed baseline table.

### A1. `:baselineprofile` module

- New module `baselineprofile/`, plugin `com.android.test`, plus
  `androidx.baselineprofile`.
- `targetProjectPath = ":app"`.
- One `BaselineProfileGenerator` (JUnit4, `BaselineProfileRule`)
  running the plan.md §46 journeys in a single flow:
  1. cold start to the Home screen
  2. Home fully rendered (wait for a stable Home marker)
  3. open the Tasbih library, open a Tasbih into the Counter
  4. start a counting session — 50 taps on the tap area
  5. undo once, reset once
  6. open Insights, open Monthly history
  7. back to Home
- Uses UIAutomator with `res`-id / content-description selectors. The
  Counter tap area already has a content description
  (`counter_tap_action_label`); other targets get testTags or rely on
  existing content descriptions. New testTags added to production code
  are kept minimal and named `*_TEST_TAG` constants.
- Output committed at `app/src/main/baseline-prof.txt` (and its
  `baseline-prof.txt` startup-profile companion if generated).

### A2. `:benchmark` module

- New module `benchmark/`, plugin `com.android.test`, plus
  `androidx.benchmark.macro.junit4`.
- `targetProjectPath = ":app"`, tests run against the `benchmark`
  build type (see A3).
- Test classes:
  - `StartupBenchmark` — `StartupTimingMetric`,
    `CompilationMode.None()` / `Partial()` (Baseline Profile) /
    `Full()`, `startupMode` COLD and WARM, 5+ iterations.
  - `CounterBenchmark` — navigates into a Counter session, then a
    measured block of 100 taps; a second test with 1000 taps;
    plus an idle-with-timer variant (session running, no taps, 10s
    window) that measures the B2 optimization. `FrameTimingMetric`.
    Dedicated undo / reset latency benchmarks were considered and
    **waived** (SDD Task 4 ruling): both are single interactions on
    an already-composed screen, the tap benchmarks exercise the same
    recomposition path, and no Workstream B item targets undo/reset
    latency specifically.
  - `ScrollBenchmark` — `FrameTimingMetric` over a fling on the Tasbih
    library list and on the Insights screen.
  - `NavigationBenchmark` — `FrameTimingMetric` over
    Home→Tasbih→Insights→Settings→Home tab switches.
- Each test uses `CompilationMode.Partial()` as the primary mode so
  the Baseline Profile's effect is what is measured; `None()` is kept
  for the startup comparison table only.

### A3. `:app` wiring

- Add `implementation(libs.androidx.profileinstaller)` — also fixes
  first-run cold start independent of benchmarking.
- Add `androidx.baselineprofile` plugin to `:app` and
  `baselineProfile(project(":baselineprofile"))`.
- New build type `benchmark`:
  - `initWith(buildTypes.release)`
  - `signingConfig = signingConfigs.getByName("debug")` (so it installs
    without the release keystore)
  - `matchingFallbacks += listOf("release")`
  - `isDebuggable = false`
  - `proguardFiles(...)` same as release
  - `isMinifyEnabled = true`, `isShrinkResources = true`
- `baselineProfile { }` block on `:app` if any non-default config is
  needed (automatic generation off by default; profile is regenerated
  only on the explicit `generateBaselineProfile` task).
- Version catalog: add `androidx-profileinstaller`,
  `androidx-benchmark-macro-junit4`, `androidx-baselineprofile`
  (plugin), `androidx-uiautomator`. Use the latest stable versions
  compatible with AGP 9.3.0 / `compileSdk 37`.

### A4. Baseline measurement run (user)

The user runs, on the POCO F3:

```
./gradlew :baselineprofile:generateBaselineProfile
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

Results are pasted back and recorded in the "Baseline (before)" table
in this spec. B and C proceed only after this table exists and is
committed.

## Workstream B — Code-level performance audit

Every item below is a *candidate*. It is implemented, measured against
the A4 baseline, and kept only if the relevant benchmark improves
meaningfully (or the change is a clear architectural improvement with
no regression). Items showing no measurable effect are reverted and
recorded as such.

### B1. `CounterViewModel.buildState()` re-sorts routine steps every tap

`buildState()` runs `routine.steps.sortedBy { it.stepOrder }` and
rebuilds the `routineSteps` list on every call — i.e. every tap during
a routine. The sorted step list and the `List<RoutineStepDisplay>` are
invariant for the life of a loaded routine.

Change: compute `sortedSteps` and the `List<RoutineStepDisplay>` once
when the routine (or routine step) is loaded, store them in fields,
and have `buildState()` read the cached list. The non-routine path is
already allocation-light and is left alone.

Benchmark: `CounterBenchmark` 1000-tap `FrameTimingMetric`, run with a
routine session.

### B2. Timer tick emits a full `CounterUiState` every second

`startTimer()` calls `_uiState.value = buildState()` once per second
while running. That is a new `CounterUiState` (new `count`, `lap`,
`routineSteps` reference, etc.) for a one-field change
(`elapsedSeconds`), forcing the whole Counter screen to recompose
every second even when the user is not tapping.

Change: remove `elapsedSeconds` from `CounterUiState`. Expose it as a
separate `val elapsedSeconds: StateFlow<Int>` on the ViewModel. The
timer updates only that flow. `CounterScreen` collects it separately
and passes it only into the two sub-nodes that display it (the top-bar
session label and the session-summary dialog), so a tick recomposes
those nodes and nothing else.

`CounterUiState.totalCount` / `progressFraction` do not depend on
`elapsedSeconds`, so they are unaffected. Persistence already reads
`elapsedSeconds` from a ViewModel field, not from `_uiState` — that
field stays; only the UI-state copy of it moves.

Call sites to update: `CounterScreen` reads `state.elapsedSeconds` in
the top-bar label, the routine-complete dialog body, and the
session-summary dialog — all switch to the separately collected value.
`CounterUiState.Empty` drops the field.

Benchmark: `CounterBenchmark` idle-with-timer `FrameTimingMetric` (add
a variant that starts a session and waits 10s without tapping) — frame
production should drop to near zero between ticks.

### B3. `CounterUiState` / `CounterScreenState` stability

Confirm `CounterUiState` is Compose-stable after B2. It holds
`dhikr: TasbihEntity` (a Room `@Entity data class` of primitives +
Strings — stable) and `routineSteps: List<RoutineStepDisplay>` (the
`List` interface is unstable to the compiler).

Change (only if the Compose compiler metrics or a benchmark flags it):
mark `RoutineStepDisplay` and `CounterUiState` `@Immutable`, or switch
`routineSteps` to `kotlinx.collections.immutable.ImmutableList`. Prefer
`@Immutable` annotations (no new dependency) unless the list is
genuinely mutated in place anywhere (it is not).

Verification: enable Compose compiler reports
(`-P plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination`)
for one build, inspect `CounterUiState`'s stability entry, then remove
the flag.

### B4. `DhikrApp` builds every repository eagerly at first composition

`DhikrApp` `remember`s ~10 repositories plus `GeminiClient` and
`BenefitsRepository` at the top of the composable tree. Home needs
`tasbihRepository`, `routineRepository`, `historyRepository`,
`sessionRepository`, `preferencesRepository`. AI (`GeminiClient`,
`BenefitsRepository`, `SecureKeyStore`), backup (`BackupRepository`),
and routine-sharing (`RoutineShareCodec`,
`RoutineShareRepository`) are only reached from the Tasbih editor,
Settings, and Routines destinations respectively.

`remember { ... }` runs its initializer during the first composition
regardless of whether the value is read this frame, so all of these
are constructed at startup (plan.md §50).

Change: move the AI, backup, and routine-sharing repositories out of
the top-level `DhikrApp` body and construct them inside the
`composable(...)` blocks that use them (`ROUTE_TASBIH_EDITOR`,
`ROUTE_SETTINGS`, `ROUTE_ROUTINES` / `ROUTE_ROUTINES_IMPORT`). Each is
already only passed into one or two ViewModels' factories.
`reminderScheduler` is used by both `ROUTE_ROUTINES` and
`ROUTE_ROUTINE_EDITOR` — move it to those two, or keep it hoisted if
its construction is trivially cheap (a `Context` wrapper — measure).

`GeminiClient()` construction cost, `BenefitsRepository.create()`, and
`SecureKeyStore` (which touches `EncryptedSharedPreferences` / Tink —
potentially the most expensive) are the specific startup costs this
targets.

Risk: this is the largest structural change in the phase — it moves
object lifetimes from "whole app session" to "destination on the back
stack". ViewModels already outlive individual recompositions via
`viewModel(factory = ...)`; the repositories they close over just need
to be stable across that ViewModel's life, which a `remember` at the
destination gives. No behavior change is intended.

Benchmark: `StartupBenchmark` COLD, `timeToInitialDisplay` and
`timeToFullDisplay`. Keep only if startup improves; if
`SecureKeyStore` / Tink is the dominant cost, that alone justifies the
move.

### B5. `material-icons-extended` → `material-icons-core`

Only six Material icons are used in production
(`Search`, `PlayArrow`, `ArrowBack` (AutoMirrored), `Schedule`,
`Favorite`, `FavoriteBorder`) — all present in `material-icons-core`,
which is already transitively available via Material3. The nav / counter
/ onboarding icons are hand-authored `ImageVector`s and are unaffected.

Change: drop `libs.compose.material.icons.extended`, add explicit
`libs.compose.material.icons.core` (or rely on the Material3
transitive), fix the six import sites. If `Schedule` proves awkward,
hand-author it alongside the existing `ui/*Icons.kt` vectors.

Benchmark: release AAB size before/after (Workstream C1); build-time
`:app:kaptKotlin` / indexing is a secondary, non-shipped benefit.

### B6. Room index audit

`session` has indices on `startedAt` and `tasbihId` (good). Audit the
other tables for missing indices on columns used in `WHERE` / `JOIN` /
`ORDER BY`:

- `tasbih_progress`, `routine_progress`, `routine_completion` —
  primary-key / lookup-column coverage.
- `routine_step` — the `stepOrder` sort and the `routineId` FK.
- FK columns without an index trigger a Room compile warning already;
  check the build log.

The `GROUP BY ((startedAt + offset)/day)*day` aggregates in
`SessionDao` compute the bucket key, so no index can serve them
directly. This is acceptable for the current data volume; B6 verifies
it with a benchmark rather than assuming.

Any index change is a schema change → `version` bump in `AppDatabase`.
`fallbackToDestructiveMigration(dropAllTables = true)` is already
configured, so no hand-written migration is required (consistent with
every prior version bump, per the `AppDatabase` history comment).

Benchmark: a `ScrollBenchmark` / list-render variant seeded with 1000
`session` rows and 100 `tasbih` rows (plan.md §45 "Lists"). Seeding is
done in the benchmark's setup via a debug-only content path or by
pre-populating the database file.

### B7. Seed I/O dispatcher

`DhikrApplication.onCreate` runs the seed check/insert on
`Dispatchers.Default`. Room's own executors handle the actual SQLite
I/O, but the `count()` / `insertAll` suspend calls should be dispatched
on `Dispatchers.IO`. Small, low-risk; measured via `StartupBenchmark`
COLD (seeding races first composition).

## Workstream C — Build / release optimization

### C1. Release size baseline

`./gradlew :app:bundleRelease`, then `bundletool build-apks` /
`get-size total` (or the AGP APK analyzer) for the download and install
size. Recorded before B5 and re-measured after. Target: a documented
number, not a fixed budget (plan.md §55 — "as small as reasonably
possible", "do not sacrifice usability").

### C2. R8 audit

Release already sets `isMinifyEnabled` + `isShrinkResources`. One-off
diagnostic run with `-printusage build/r8-usage.txt` and
`-printseeds build/r8-seeds.txt` (added to `proguard-rules.pro`
temporarily) to confirm:

- no large unreachable graph is being kept,
- the only `-keep` rules needed are the Tink `-dontwarn`s already
  present plus whatever Compose / Room / `kotlinx.serialization` AARs
  contribute (those ship their own consumer rules — verify none are
  duplicated or over-broad locally).

Consider `android.enableR8.fullMode=true` in `gradle.properties` —
measure APK size and run the full benchmark + a manual smoke pass
(serialization and Room reflection are the usual fullMode casualties;
this app uses `kotlinx.serialization` codegen, not reflection, and Room
codegen, so fullMode is likely safe). Keep only if it shrinks output
and all benchmarks + a smoke test pass.

### C3. Dependency audit

`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`.
Flag anything large or unexpected. Known items:

- `androidx.security-crypto` → Tink (~1 MB) for a single stored API
  key string. **Audit only** — record the size cost and a note that a
  lighter approach (e.g. `EncryptedFile` is also Tink; a plain
  KeyStore-wrapped AES of one short string would avoid Tink entirely)
  exists, but do not implement it in this phase (plan.md §25, §54 —
  the key must stay encrypted at rest; changing the mechanism needs
  its own spec and security review).
- `material-icons-extended` — addressed by B5.
- `appcompat` — required for the per-app locale backport
  (`AppLocalesMetadataHolderService`), justified, keep.

### C4. Baseline Profile embedded in release

After A3, `./gradlew :app:bundleRelease`, unzip the AAB, confirm
`BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof` (and
`baseline.profm`) are present. If absent, fix the `baselineProfile`
consumer wiring. This is the check that the profile actually ships,
not just that it generates.

### C5. Dev-loop config

Not shipped, but plan.md §64 covers the development process. Add to
`gradle.properties` if not already implied by defaults:

- `org.gradle.caching=true`
- `org.gradle.configuration-cache=true` (verify the build is
  configuration-cache compatible; the `keystore.properties` read in
  `app/build.gradle.kts` at configuration time may need
  `providers.fileContents`).
- `org.gradle.parallel=true`

Each verified with a clean build + an incremental build; reverted if
it breaks the build.

## Deliverables

- This spec, with the "Baseline (before)" and "After" measurement
  tables filled in and committed.
- Implementation plan at
  `docs/superpowers/plans/2026-09-02-phase-8-performance.md`.
- New modules `:baselineprofile` and `:benchmark`.
- `app/src/main/baseline-prof.txt` committed.
- `:app` build changes: `profileinstaller`, `baselineProfile` consumer,
  `benchmark` build type, version-catalog additions, `material-icons`
  swap.
- Code changes in `CounterViewModel`, `CounterUiState`, `CounterScreen`,
  `DhikrApp`, `DhikrApplication`, and any `AppDatabase` version bump
  from B6.
- README performance section (benchmarking, Baseline Profiles, how to
  run the `:benchmark` tasks, the measured numbers).

## Testing

- Existing unit tests (`app/src/test`) must still pass unchanged. B1
  (routine step caching) and B2 (`CounterUiState` shape) touch
  `CounterViewModel` — its existing tests are updated for the new
  `elapsedSeconds` flow and the cached step list, keeping the same
  behavioral assertions.
- New instrumented tests are the Macrobenchmarks themselves (`:benchmark`)
  and the profile generator (`:baselineprofile`).
- A manual smoke pass on the device after B4 (all destinations open,
  AI / backup / share flows still work) and after C2 fullMode (if
  adopted): serialization round-trip (backup export+import), Room
  reads on every screen.
- Every B/C item records its before/after benchmark numbers in this
  spec. An item with no measurable benefit is reverted and its row
  reads "no measurable effect — reverted".

## Baseline (before)

_To be filled in after Workstream A4._

| Journey | Metric | CompilationMode | Value |
|---|---|---|---|
| Cold startup | timeToInitialDisplay | None | TBD |
| Cold startup | timeToInitialDisplay | Partial (Baseline Profile) | TBD |
| Warm startup | timeToInitialDisplay | Partial | TBD |
| Counter 100 taps | frameDurationCpu P50/P90/P99 | Partial | TBD |
| Counter 1000 taps | frameDurationCpu P50/P90/P99 | Partial | TBD |
| Counter idle 10s (timer) | frameOverrunCount | Partial | TBD |
| Tasbih list scroll | frameDurationCpu P90/P99 | Partial | TBD |
| Insights scroll | frameDurationCpu P90/P99 | Partial | TBD |
| Nav Home→Tasbih→Insights→Settings | frameDurationCpu P90 | Partial | TBD |
| Release AAB | download size / install size | — | TBD |

## After

_One row per B/C item, filled in as each is measured._

| Item | Metric | Before | After | Kept? |
|---|---|---|---|---|
| B1 routine step caching | Counter 1000-tap P90 | TBD | TBD | TBD |
| B2 timer flow split | Counter idle frameOverrunCount | TBD | TBD | TBD |
| B3 state stability | Counter 1000-tap P90 | TBD | TBD | TBD |
| B4 lazy repositories | Cold startup TTID | TBD | TBD | TBD |
| B5 icons-core swap | AAB size | TBD | TBD | TBD |
| B6 index audit | 1000-row list scroll P90 | TBD | TBD | TBD |
| B7 seed dispatcher | Cold startup TTID | TBD | TBD | TBD |
| C2 R8 fullMode | AAB size | TBD | TBD | TBD |
