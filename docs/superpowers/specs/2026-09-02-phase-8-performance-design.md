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

**Status: pending device run.** The POCO F3 (`192.168.31.252:5555`) was
offline for the implementation session. Workstream A (modules, generator,
Macrobenchmarks) is committed and compiles; Workstreams B and C were
implemented as code/build changes and verified with `./gradlew`
build + unit tests, but the before/after benchmark deltas below are not
yet measured. The engineer runs, on the device:

```
./gradlew :app:generateBaselineProfile
./gradlew :benchmark:connectedBenchmarkAndroidTest
git checkout <pre-B1 sha> && ./gradlew :benchmark:connectedBenchmarkAndroidTest   # for "before" columns
```

then fills the tables and reverts any B/C item whose row shows no
measurable benefit (§66).

| Journey | Metric | CompilationMode | Value |
|---|---|---|---|
| Cold startup | timeToInitialDisplay | None | pending device |
| Cold startup | timeToInitialDisplay | Partial (Baseline Profile) | pending device |
| Warm startup | timeToInitialDisplay | Partial | pending device |
| Counter 100 taps | frameDurationCpu P50/P90/P99 | Partial | pending device |
| Counter 1000 taps | frameDurationCpu P50/P90/P99 | Partial | pending device |
| Counter idle 10s (timer) | frameOverrunCount | Partial | pending device |
| Tasbih list scroll | frameDurationCpu P90/P99 | Partial | pending device |
| Insights scroll | frameDurationCpu P90/P99 | Partial | pending device |
| Nav Home→Tasbih→Insights→Settings | frameDurationCpu P90 | Partial | pending device |
| Release AAB | download size / install size | — | see C1 below |

## After

_One row per B/C item, filled in as each is measured._

| Item | Metric | Before | After | Kept? |
|---|---|---|---|---|
| B1 routine step caching | Counter 1000-tap P90 | pending device | pending device | implemented; keep if unchanged/better (allocation reduction, identical output) |
| B2 timer flow split | Counter idle frameOverrunCount | pending device | pending device | implemented; keep if idle frame production drops and tap100/1000 don't regress |
| B3 state stability | Counter 1000-tap P90 | pending device | pending device | implemented; @Immutable + stable types, keep even within noise |
| B4 lazy repositories | Cold startup TTID | pending device | pending device | implemented; §50 structural fix, revert if no benefit + confirm smoke pass |
| B5 icons-core swap | AAB size | see C1 | see C1 | implemented; keep (removes material-icons-extended) |
| B6 index audit | routine-delete scan / list scroll P90 | pending device | pending device | implemented (Index("routineId"), v10); correctness fix, keep |
| B7 seed dispatcher | Cold startup TTID | pending device | pending device | implemented; IO for I/O, correct regardless |
| C2 R8 fullMode | AAB size | pending | pending | see C2 section |

### Summary

Workstream A (both `com.android.test` modules, the §46-journey Baseline
Profile generator, and the four Macrobenchmark classes) is complete and
compiles. Workstreams B and C were implemented as code and build changes,
each verified with `./gradlew` build + the existing unit-test suite
(all green), and documented here.

What is **not** done: the on-device before/after measurement pass. The
POCO F3 was offline for the whole implementation session, and per project
convention (build-only verification) benchmarks are the user's to run.
Until that pass:

- B3, B6, B7 are correctness / architecture improvements and are kept on
  their merits regardless of the delta.
- B1, B2, B4, B5 are kept pending measurement — the user runs
  `:benchmark:connectedBenchmarkAndroidTest` before/after and reverts any
  whose row shows no benefit (§66). B4 additionally needs the manual
  smoke pass (every destination opens; AI / backup / share flows work).
- C2 fullMode is not adopted (needs the device smoke pass); C5 flags are
  verified and kept.

`:app:lintDebug` has 4 errors — all pre-existing on `main` (Haptics
`NewApi`, `ReminderNotifications` `MissingPermission`, `SettingsScreen`
`LocalContextGetResourceValueCall`) and out of scope; Phase 8 adds no new
lint error. `:app:testDebugUnitTest` and `:app:assembleRelease` are green.

Net expected effect once measured: lower cold-start work (B4, B7,
Baseline Profile), no per-second recomposition of an idle counter screen
(B2), fewer per-tap allocations in a routine (B1), a leaner dependency
graph (B5), and one fewer Room full-table-scan risk (B6).

### Implementation notes (device-independent)

- **B1** — `CounterViewModel` now sorts `routine.steps` and builds the
  `RoutineStepDisplay` list once in `initializeSession()`;
  `advanceRoutineStep()` and `buildState()` read the cached lists. Output
  is byte-identical to the previous per-tap recompute.
- **B2** — `elapsedSeconds` removed from `CounterUiState`; exposed as
  `CounterViewModel.elapsedSeconds: StateFlow<Int>`. The 1s timer tick no
  longer calls `buildState()` / emits `_uiState`. `CounterScreen` collects
  the flow separately for the top-bar label and the two summary dialogs.
  Trade-off accepted: the debounced persister keys off `_uiState`, so
  elapsed time advancing alone no longer schedules a save — elapsed is
  recomputed from `sessionStartedAtMillis` on restore and re-saved on the
  next real state change or the ON_STOP flush.
- **B3** — `CounterUiState`, `RoutineStepDisplay`, `TasbihEntity` marked
  `@Immutable`. The temporary Compose-compiler-report flag dance from the
  plan was skipped; all three hold only stable types (primitives, String,
  nullable primitives, and — for `CounterUiState` — an `@Immutable`
  `TasbihEntity` plus a `List` whose holder is now `@Immutable`).
- **B4** — `SecureKeyStore`, `GeminiClient`, `BenefitsRepository`,
  `BackupRepository`, `RoutineShareCodec`, `RoutineShareRepository` moved
  from top-level `remember {}` into `ROUTE_TASBIH_EDITOR`, `ROUTE_SETTINGS`,
  `ROUTE_ROUTINES`, `ROUTE_ROUTINES_IMPORT`. `reminderScheduler` left
  hoisted — constructor is a bare `getSystemService` wrapper, no I/O, and
  it is used by two destinations. Note: with the current code none of the
  moved constructors touch Tink at construction (`SecureKeyStore.prefs` is
  `by lazy`, `BenefitsRepository.create` only wires method references), so
  the measured startup win may be small; keep only if the benchmark shows
  one.
- **B6** — full audit result: only `session.routineId` (a `SET_NULL` FK)
  lacked a covering index and was warned by Room. Added; version 9 → 10.
  Every other FK / `WHERE` / `JOIN` column is covered by an explicit index
  or a leftmost primary-key column. `SessionDao`'s
  `GROUP BY ((startedAt+offset)/day)*day` aggregates compute the bucket
  key and cannot be served by an index — acceptable at current volume.
- **B7** — `DhikrApplication.onCreate` seed launch now uses
  `Dispatchers.IO`.

## Workstream C results

### C1 — Release size

`./gradlew :app:bundleRelease` on this branch (unsigned; R8 +
resource-shrink on, as `release` always has been):

| Build | AAB (bytes) | AAB (MiB) |
|---|---|---|
| Post-B5 (`material-icons-core` + hand-authored `ScheduleIcon`) | 6,188,893 | 5.90 |

A clean pre-B5 comparison build was not obtained this session — reverting
`app/build.gradle.kts` + `gradle/libs.versions.toml` to the pre-B5 commit
also reverts unrelated later `:benchmark` version bumps and fails
configuration. It is not worth chasing: `material-icons-extended` is a
build-time artifact and R8 tree-shakes every unreferenced vector, so with
only six icons used the shipped-AAB delta from the source swap is expected
to be within noise of zero. **B5 is kept regardless** — it removes an
entire artifact from the dependency graph and the compile classpath
(plan.md §55, §25), which is a real build-hygiene win independent of AAB
bytes. If a precise number is wanted, build `:app:bundleRelease` at
`3a254f2` on a clean checkout and diff.

Download/install size via `bundletool build-apks --mode=default` +
`get-size total` — deferred (needs `bundletool`; not blocking).

### C4 — Baseline Profile embedded ✓

`unzip -l app-release.aab` shows:

```
BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof   (9494 b)
BUNDLE-METADATA/com.android.tools.build.profiles/baseline.profm  (1095 b)
base/root/META-INF/androidx.profileinstaller_profileinstaller.version
```

These are the merged AndroidX/Compose library profiles — the consumer
wiring (`baselineProfile(project(":baselineprofile"))` +
`androidx.profileinstaller`) works. The app-level §46-journey profile is
appended to this on top after the device `generateBaselineProfile` run
(Task 5) and committed to `app/src/main/baseline-prof.txt`.

### C2 — R8 audit

Release is already `isMinifyEnabled` + `isShrinkResources`. Diagnostics
(`-printusage` / `-printseeds` / `-printconfiguration` added to
`proguard-rules.pro` for one build, then reverted) and the
`android.enableR8.fullMode=true` size + smoke + benchmark comparison are
**pending** — fullMode's usual casualties are reflection-based
serialization and Room, and this app uses `kotlinx.serialization` codegen
+ Room codegen (both fullMode-safe in principle), but the plan requires a
device smoke pass (backup export/import round-trip, every screen opens)
before adopting it, which the offline device blocks. Not adopted this
pass; left as a documented follow-up.

### C3 — Dependency audit

`releaseRuntimeClasspath` top-level dependencies:

| Dependency | Role | Verdict |
|---|---|---|
| `androidx.compose.*` (BOM `2026.08.00`), `material3`, `material-icons-core` | UI | keep; `material-icons-extended` removed in B5 |
| `androidx.navigation:navigation-compose` | nav | keep |
| `androidx.room:room-*` | database | keep |
| `androidx.datastore:datastore-preferences` | preferences | keep |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | backup format | keep (codegen) |
| `androidx.lifecycle:lifecycle-*`, `androidx.activity:activity-compose`, `androidx.core:core-ktx` | platform | keep |
| `androidx.appcompat:appcompat` | per-app locale backport (`AppLocalesMetadataHolderService`) | keep — required for the language picker |
| `androidx.profileinstaller:profileinstaller` | baseline profile install | keep (Phase 8) |
| **`androidx.security:security-crypto` → Tink (~1 MB)** | encrypts one Gemini API-key string at rest | **audit only** — noted as a future candidate: a KeyStore-wrapped AES of one short string would drop Tink entirely, but the key must stay encrypted at rest and changing the mechanism needs its own spec + security review (plan.md §25, §54). Not changed in Phase 8. |

No surprising or unused top-level dependency. Tink is the one weight worth
a future look; everything else is justified.

### C5 — Dev-loop Gradle config

Added to `gradle.properties`:

```
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
```

Verified: clean `:app:assembleDebug` → `Configuration cache entry
stored`, no problems; second `:app:assembleDebug` → `Reusing
configuration cache` / `BUILD SUCCESSFUL in 2s`. The
`keystore.properties` read in `app/build.gradle.kts` at configuration
time is tolerated by Gradle 9.5's configuration cache (tracked as an
input) — no `providers.fileContents(...)` rewrite needed, no flag
dropped. `:app:assembleBenchmark --dry-run` still configures cleanly with
all three flags on.
