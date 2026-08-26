# Phase 3 — Data Layer: Room, History, Statistics, Goals, Favorites, Search, Custom Tasbih, Routines

Date: 2026-08-26
Status: Approved for planning

## Context

Phase 1+2 (project scaffold, `TasbihCounter` engine, Counter screen) is complete and
merged to `main`. The counter works with an in-memory `BuiltInDhikr` list and a
DataStore-backed live-session (resume-after-kill) mechanism, but has no database, no
history, no favorites persistence, no routines, and only a Home stub.

This phase implements Phase 3 of `plan.md`'s roadmap (§65): Room, History,
Statistics, Goals, Favorites, Search, Custom Tasbih, Routines — plus the four
screens the design bundle (`design/README.md`) specifies to expose them (Tasbih
library §3, Custom Tasbih editor §4, Routines §5, Insights §6), a real Home screen
(§2), and wiring the Counter screen's already-reserved routine fields.

**Explicitly out of scope, deferred to later phases:** Settings screen (stays a
stub), notifications/reminders, home-screen widget, backup/import-export, QR
sharing, AI features, audio, auto-counter, timer, Baseline Profiles/Macrobenchmark.

## Goals

1. A Room database (`AppDatabase`) with `Tasbih`, `Routine`/`RoutineStep`, and
   `Session` entities, seeded with the 7 built-in Dhikr and 3 preset routines on
   first launch, replacing `BuiltInDhikr` as the runtime source of truth while
   preserving existing stable Dhikr IDs so Phase 1+2's persisted
   `CounterSessionState.activeDhikrId` values keep resolving correctly.
2. Tasbih library screen: search (offline, case-insensitive substring across
   name/Arabic/transliteration/translation), favorite toggle, built-in + custom
   Tasbih together.
3. Custom Tasbih editor screen: create/edit a Tasbih (name, Arabic, translation,
   personal note, lap target, daily goal).
4. Routines: Room-backed CRUD (create/edit/delete/reorder steps), a Routines
   screen, 4 preset routines seeded at first launch, and Counter screen wiring so
   starting a routine actually auto-advances through its steps using the
   already-reserved `routineId`/`routineStep` session fields from Phase 1+2.
5. History + Statistics: every completed or abandoned-with-progress session is
   logged; an Insights screen shows totals (today/week/month/all-time), a 7-day bar
   chart, a consistency calendar, and history grouped by Dhikr.
6. A real Home screen: greeting, daily-goal ring, continue-session card, favorites,
   routine tiles — replacing the Phase 1+2 stub.
7. Bottom navigation wired for real across Home/Tasbih/Count/Insights (Settings
   stays a stub destination).

## Non-goals

- Settings screen content (theme picker, toggles, reminders UI) — stub only.
- Notifications, widget, backup/import/export, QR sharing, AI, audio,
  auto-counter, session timer UI, Baseline Profiles, Macrobenchmark.
- Weekly/total/routine-specific goals beyond the single global daily-goal ring
  described below (per-Tasbih daily goals are stored, per the editor's field, but
  no screen yet aggregates or surfaces them beyond the editor itself).
- Editing built-in Tasbih's core content (name/Arabic/translation) — favoriting
  and reading are supported; whether built-ins are user-editable is left for a
  later pass, not decided here (the schema doesn't prevent it, but no UI exposes
  it this phase).

## Architecture

### Package structure additions

```
app/src/main/java/com/dhikr/app/
├── core/
│   ├── database/
│   │   ├── AppDatabase.kt
│   │   ├── entity/           TasbihEntity, RoutineEntity, RoutineStepEntity, SessionEntity
│   │   ├── dao/               TasbihDao, RoutineDao, SessionDao
│   │   └── seed/               SeedData.kt (built-in Tasbih + preset routines as entities)
│   ├── model/                 (existing) Dhikr.kt renamed/adapted — see Data model below
│   └── datastore/              (existing SessionRepository unchanged; new DailyGoalRepository)
├── feature/
│   ├── home/                   HomeScreen, HomeViewModel
│   ├── tasbih/                  TasbihLibraryScreen, TasbihLibraryViewModel,
│   │                            TasbihEditorScreen, TasbihEditorViewModel
│   ├── routines/                RoutinesScreen, RoutinesViewModel
│   ├── insights/                InsightsScreen, InsightsViewModel
│   └── counter/                 (existing, modified for routine auto-advance)
```

Room, not a hand-rolled persistence layer: plan.md §8 names Room explicitly for
structured local data, and the relational shape here (Tasbih ↔ Routine steps ↔
Sessions, all cross-referenced) is exactly what Room/SQL is for for.

### Data model

**`TasbihEntity`** (table `tasbih`):

```kotlin
@Entity(tableName = "tasbih")
data class TasbihEntity(
    @PrimaryKey val id: String,       // stable slug for built-ins (e.g. "subhan"), UUID for custom
    val name: String,
    val arabic: String,
    val transliteration: String,
    val translation: String,
    val note: String = "",             // personal note / "why reciting" (custom only, empty for built-in)
    val source: String? = null,        // reference, e.g. hadith/Quran citation — null unless sourced
    val lapTarget: Int,
    val lapCount: Int,
    val dailyGoal: Int? = null,        // per-Tasbih goal from the editor's 33/100/500 picker
    val isFavorite: Boolean = false,
    val isBuiltIn: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
```

This single table replaces `core.model.Dhikr`/`BuiltInDhikr` as the runtime source
of truth — both built-in and custom Tasbih are ordinary rows distinguished by
`isBuiltIn`. `id` stability matters: built-in IDs (`kursi`, `subhan`, `hamd`,
`akbar`, `istighfar`, `bihamdihi`, `hawla`) are preserved exactly as they exist in
Phase 1+2's `BuiltInDhikr.all`, so any already-persisted `CounterSessionState`
(DataStore, unrelated to Room) continues to resolve via
`TasbihDao.getById(activeDhikrId)` without a migration on that side.

Index: `CREATE INDEX idx_tasbih_favorite ON tasbih(isFavorite)` — Home's favorites
section and the library's default favorites-first ordering both filter on this.

**`RoutineEntity`** (table `routine`) + **`RoutineStepEntity`** (table
`routine_step`, FK `routineId` → `routine.id` with `CASCADE` delete, FK `tasbihId`
→ `tasbih.id`):

```kotlin
@Entity(tableName = "routine")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isPreset: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "routine_step",
    foreignKeys = [
        ForeignKey(RoutineEntity::class, ["id"], ["routineId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TasbihEntity::class, ["id"], ["tasbihId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("routineId"), Index("tasbihId")],
)
data class RoutineStepEntity(
    @PrimaryKey(autoGenerate = true) val stepId: Long = 0,
    val routineId: String,
    val tasbihId: String,
    val stepOrder: Int,     // 0-indexed, defines the sequence; reordering rewrites this column
    val targetCount: Int,   // the step's count target (may differ from tasbihId's own lapTarget)
)
```

Normalized rather than an embedded JSON list of steps: steps need independent
reordering (drag handle in the Routines screen) and a stable order column is the
straightforward way to persist that. `onDelete = RESTRICT` on the `tasbihId` FK
means a Tasbih referenced by any routine step cannot be deleted while the
reference exists — the Tasbih library/editor's delete action must check this and
surface a clear message rather than letting a raw FK constraint violation crash;
see Error handling below.

**`SessionEntity`** (table `session`):

```kotlin
@Entity(
    tableName = "session",
    foreignKeys = [
        ForeignKey(TasbihEntity::class, ["id"], ["tasbihId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(RoutineEntity::class, ["id"], ["routineId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("tasbihId"), Index("startedAt")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tasbihId: String,
    val routineId: String? = null,   // non-null if this session was part of a routine run
    val count: Int,                  // final count when the session ended
    val startedAt: Long,             // epoch millis
    val endedAt: Long,               // epoch millis
)
```

`onDelete = CASCADE` on `tasbihId`: deleting a custom Tasbih deletes its session
history too (a Tasbih's history is meaningless without the Tasbih; this matches
the RESTRICT-on-routine-steps choice above being about *active* references, not
historical ones). `onDelete = SET_NULL` on `routineId`: deleting a routine keeps
the session history of runs made through it, just detaches the routine reference.

This is the **sole History source** — no separate aggregate table. Insights reads
are one-shot `@Query` calls (SUM/COUNT with date-range `WHERE` and `GROUP BY`),
triggered once per Insights screen visit via `viewModelScope.launch`, not a
continuously observed `Flow` over the whole table — satisfying plan.md §49's "do
not continuously observe huge tables when the UI only needs a small subset." At
this app's realistic data volume (a personal counting habit, not analytics-scale
ingestion), computing aggregates on read is simpler and has no write-path
complexity versus maintaining a synchronized aggregate table.

### Session logging (when a `SessionEntity` row is written)

A row is written when an active counting session **ends with `count > 0`** —
either by completing the full target (goal-reached), or by the user navigating
away from the Counter screen entirely while `count > 0`. Merely backgrounding the
app (switching apps, locking the screen) while staying on the Counter screen does
NOT log a session — the mechanism below distinguishes the two. Resetting to 0
before leaving writes nothing (there is nothing to record). This captures real
usage where dhikr sessions are often partial, matching plan.md §16's "Sessions"
concept more faithfully than completion-only logging would.

Mechanically, this phase keeps two lifecycle triggers clearly separate rather than
conflating them (Phase 1+2's `ON_STOP` fires on ordinary backgrounding too — app
switcher, lock screen — which must NOT log a session every time the user glances
away and comes back):

- **`flushSession()`** (DataStore live-session write, from Phase 1+2's Task 8) —
  unchanged, still fires on every `ON_STOP`. This is what makes process-death
  recovery work and is orthogonal to History.
- **History logging** fires only when the Counter screen composable is actually
  leaving composition — a `DisposableEffect(Unit) { onDispose { ... } }` in
  `CounterScreen`, which runs on real navigation away (back press, or navigating
  to another destination) but not on a bare app-background/foreground cycle where
  the composable stays alive. In `onDispose`, compare the current `count` against
  the count recorded at session start; if `count > 0`, call
  `HistoryRepository.logSession(...)` with a `SessionEntity` built from the
  current `CounterUiState`, then clear the in-memory session-start marker so a
  subsequent session on the same screen instance doesn't double-count.

Goal-reached completion is a third, more direct path: `onTap()`'s completion
branch (where `isComplete` flips true) logs the session immediately at that point,
since completion is an unambiguous "this session is over" signal that doesn't
need to wait for navigation.

**Naming clarification** (flagged because Phase 1+2 already has a
`core.datastore.SessionRepository` for live-session DataStore state): the new
Room-backed component for History is named `core.database.HistoryRepository`, not
`SessionRepository`, to avoid confusion between "the live in-progress session"
(DataStore, ephemeral, one row) and "the permanent log of finished sessions"
(Room, append-only, many rows).

### Daily goal (Home's ring)

A single global preference, `dailyGoalTarget: Int` (DataStore, `core.datastore`,
alongside the existing preferences), representing a total-count target across all
Tasbih combined for the current day — not a per-Tasbih figure. Home's ring
computes `todayTotal (HistoryRepository, SUM of session.count WHERE date =
today) / dailyGoalTarget`. Per-Tasbih `dailyGoal` (stored on `TasbihEntity` from
the editor's 33/100/500 picker) is persisted this phase but not yet aggregated or
surfaced by any screen — reserved for a later phase's per-Tasbih progress view, per
this spec's non-goals.

No UI to set `dailyGoalTarget` exists yet (Settings is still a stub) — it defaults
to a reasonable value (100, matching the design prototype's example) and is
otherwise fixed this phase. A later Settings-screen phase adds the picker.

### Routine execution (Counter screen wiring)

`CounterSessionState.routineId`/`routineStep` (reserved, unused fields from Phase
1+2) become live. Starting a routine (from Home's routine tile or the Routines
screen's Start pill) initializes `CounterViewModel` with `routineId` set and
`routineStep = 0`, loading the routine's first step's Tasbih. On completing a
step's target: if more steps remain, auto-advance — load the next step's Tasbih,
reset count/lap to the step's fresh state, increment `routineStep`, no
interruption (per design README §5: "on step completion it auto-advances to the
next step with the count reset"); if it was the last step, show the "Routine
complete" overlay (already specified in the design's Overlays table, unbuilt until
now) instead of the single-Tasbih "Goal reached" overlay. The routine-chips row in
`CounterScreen` (built but hidden in Phase 1+2, per that phase's spec) now
renders: current step sage-filled, completed steps sage/`on-sage`, upcoming
`surface`/`dim`, per design README §1.2.

Each step's completion still logs its own `SessionEntity` (with `routineId` set),
so a 3-step routine run produces 3 history rows — this keeps History/Insights
per-Tasbih queries correct without special-casing routine runs.

### Screens

**Tasbih library** (`feature/tasbih/TasbihLibraryScreen.kt`) — per design README
§3: header with `+ New` → editor; 46dp search pill filtering live across
name/Arabic/transliteration/translation (`TasbihDao`'s `LIKE`-based query, offline,
case-insensitive); result count line; `card` rows with favorite heart (tap toggles
favorite via `TasbihDao`, stops propagation so it doesn't also open the Dhikr);
empty-result state.

**Custom Tasbih editor** (`feature/tasbih/TasbihEditorScreen.kt`) — per design
README §4: name/Arabic/translation/note fields, lap-target stepper (min 1), daily
goal (33/100/500 pill picker), full-width Save button, "stored on this device
only" footer. Handles both create (new UUID) and edit (existing custom Tasbih;
built-ins are not editable through this screen this phase, per Non-goals).

**Routines** (`feature/routines/RoutinesScreen.kt`) — per design README §5: one
card per routine with header (name, "N steps · M counts", Start pill) and step
rows (index, name, count, drag handle); dashed "+ New routine" footer. Create/edit
reuses a routine-editing flow (add/remove/reorder steps, each step picking a
Tasbih + count via a picker over the Tasbih library) — kept as simple as the
design allows; no separate "new routine editor screen" design exists in the
README, so this phase builds the minimal editing surface the design's Routines
card interactions imply (tap a routine to edit its steps; drag handles reorder in
place) rather than inventing an undesigned dedicated screen.

**Insights** (`feature/insights/InsightsScreen.kt`) — per design README §6: 2×2
totals grid (today/week/month/all-time via `SessionDao` aggregate queries),
last-7-days bar row, consistency calendar (4-step intensity ramp, positive
language per plan.md §18 — "23 days of consistency," never "streak lost"), history
grouped by Dhikr with per-day progress bars, empty state for fresh installs (zero
sessions).

**Home** (`feature/home/HomeScreen.kt`, replacing the Phase 1+2 stub) — per design
README §2: greeting + day-goal ring; continue-session card (reads the existing
DataStore `SessionRepository`'s live session, if any, and its associated
`TasbihEntity` for display — this card already had its data source from Phase
1+2, it just needs a real screen); favorites section (`TasbihDao`'s
favorites query, "All" link to library); routines section (3 most-recent-or-preset
routines, "Manage" link to Routines screen).

**Bottom navigation**: wired across Home/Tasbih/Count/Insights per design's
persistent 5-item nav (§Bottom navigation); Settings remains a stub destination
(exists in the nav graph, shows a placeholder screen) since Settings content is
out of scope this phase.

### Error handling

Per plan.md §57's "never crash" requirement, extended to this phase's new surface:

- Deleting a Tasbih referenced by a routine step (`RESTRICT` FK): the delete
  action checks `RoutineDao.countStepsUsingTasbih(id)` before attempting the
  delete; if > 0, show a message naming the routine(s) rather than letting the FK
  constraint throw. (Room throws `SQLiteConstraintException` on a RESTRICT
  violation — this must be caught defensively even with the pre-check, in case of
  a race.)
- Corrupted/missing Room database file: Room's own crash-safety (WAL mode,
  default) handles most cases; a completely unreadable/corrupt DB file is treated
  the same as "fresh install" — recreate the database and re-seed built-ins/presets
  rather than crash. (This mirrors Phase 1+2's DataStore corruption handling.)
- Search with no results: the design's own empty state, not an error.
- Insights with zero sessions: the design's own empty state, not an error.

## Testing

Per the user's standing instruction for this project (carried from Phase 1+2's
spec): verification is **build-only**. No unit tests, Room migration tests, DAO
tests, or Compose UI tests are required or expected this phase. A successful
Gradle build is the completion criterion; the user tests manually.

## Open questions / assumptions carried forward

If an implementation question arises that isn't answered by this spec, `plan.md`,
or `design/README.md`, ask the user directly rather than guessing — per the
standing instruction from Phase 1+2. Assumptions already confirmed as reasonable
to carry forward without re-asking:

- Built-in Tasbih are seeded into Room once (first launch / DB creation) and
  become the runtime source of truth; `BuiltInDhikr.kt`'s in-memory list becomes
  seed data only (moved into `core.database.seed.SeedData.kt`), not consumed
  directly by any screen after this phase.
- The daily-goal ring is a single global preference, not a per-Tasbih aggregate.
- Session logging fires on natural completion and on genuinely leaving an active
  session (not on every background/foreground cycle).
- Full Routines CRUD (not preset-only) is in scope, including a step-editing flow
  the design bundle doesn't explicitly mock as its own screen.
- Whether built-in Tasbih are ever user-editable (beyond favoriting) is left
  undecided — no UI exposes editing them this phase, and the schema doesn't
  prevent it later.
