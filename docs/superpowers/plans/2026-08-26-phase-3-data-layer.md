# Phase 3 Data Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Room database (Tasbih, Routine/RoutineStep, Session) and build the
Tasbih library, Custom Tasbih editor, Routines, Insights, and real Home screens on
top of it, plus wire the Counter screen's already-reserved routine fields and
session-history logging.

**Architecture:** Room replaces `BuiltInDhikr`'s in-memory list as the runtime
source of truth for Tasbih (seeded once on first launch, stable IDs preserved).
Routines are normalized (`RoutineEntity` + ordered `RoutineStepEntity`). Sessions
are a single append-only table; Insights computes aggregates via one-shot `@Query`
calls rather than a maintained aggregate table. Four new feature packages
(`feature/tasbih`, `feature/routines`, `feature/insights`, `feature/home`) each
follow the existing `feature/counter` pattern (ViewModel + Screen composable).

**Tech Stack:** Room 2.8.4 (KSP, not KAPT), KSP Gradle plugin 2.3.11, everything
else unchanged from Phase 1+2 (Kotlin 2.3.20, AGP 9.3.0, Compose BOM 2026.08.00,
Navigation Compose, DataStore, Coroutines).

**Spec:** `docs/superpowers/specs/2026-08-26-phase-3-data-layer-design.md`

## Global Constraints

- Room 2.8.4, package `androidx.room` (NOT the alpha `androidx.room3` — that line
  is KSP-only-multiplatform-alpha and not appropriate for this project)
- KSP Gradle plugin `com.google.devtools.ksp`, version `2.3.11` (KSP2, versioned
  independently of Kotlin; compatible with Kotlin 2.3.20)
- Room's own minSdk floor is API 23 — no conflict with this project's minSdk 24
- No Room migrations infrastructure needed yet (schema version 1, first release —
  add `Migration` objects only when the schema actually changes after this phase
  ships)
- Verification for this whole plan is **build-only**: a successful Gradle build is
  the completion criterion. Do not write unit tests, Room migration tests, DAO
  tests, or Compose UI tests — the user tests manually.
- If an implementation question arises that isn't answered by this plan, the spec,
  `plan.md`, or `design/README.md`, ask the user — do not guess or silently pick a
  default.
- All UI strings go through `res/values/strings.xml` — nothing hardcoded.
- Built-in Tasbih IDs (`kursi`, `subhan`, `hamd`, `akbar`, `istighfar`,
  `bihamdihi`, `hawla`) must be preserved exactly when seeded into Room — any
  already-persisted `CounterSessionState.activeDhikrId` (DataStore, unrelated
  table) must keep resolving via `TasbihDao.getById(id)`.
- `core.datastore.SessionRepository` (existing, DataStore-backed, live in-progress
  session) and the new `core.database.HistoryRepository` (Room-backed, permanent
  session log) are deliberately separate components with different names — do not
  conflate or merge them.
- Session history logging fires on natural completion and on genuinely leaving an
  active session (composable actually leaving composition) — never on a bare
  app-background/foreground cycle. See spec's "Session logging" section for the
  exact mechanism.
- Deleting a Tasbih referenced by a routine step must not crash (FK is
  `RESTRICT` — check before deleting, catch `SQLiteConstraintException`
  defensively).

---

## Task 1: Room dependency setup + AppDatabase skeleton

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/dhikr/app/core/database/AppDatabase.kt`
- Modify: `app/src/main/java/com/dhikr/app/DhikrApplication.kt`

**Interfaces:**
- Produces: `AppDatabase` (abstract Room database class, no entities/DAOs wired
  yet — this task only proves Room + KSP compile and a database instance can be
  built), plus a `DhikrApplication.database: AppDatabase` lazily-constructed
  singleton other tasks' repositories will consume via `context.applicationContext`.

- [ ] **Step 1: Add Room + KSP to the version catalog**

Edit `gradle/libs.versions.toml`:

```toml
[versions]
# ... existing entries unchanged ...
room = "2.8.4"
ksp = "2.3.11"

[libraries]
# ... existing entries unchanged ...
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

[plugins]
# ... existing entries unchanged ...
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: Apply the KSP plugin at the root**

Edit `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 3: Apply KSP in the app module and add Room dependencies**

Edit `app/build.gradle.kts` — add to the `plugins { }` block:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}
```

Add to the `dependencies { }` block (alongside the existing entries, don't
reorder what's already there):

```kotlin
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
```

- [ ] **Step 4: Write the `AppDatabase` skeleton**

```kotlin
package com.dhikr.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase()
```

`exportSchema = false` for now — this project has no `schemas/` directory
committed and no migration-testing infrastructure (out of scope this phase per
Global Constraints); revisit if a later phase adds real schema-migration tests.
Entities list is empty here on purpose — Tasks 2/6/9 each add their own
entity(ies) to this annotation as they're introduced, so this file's `@Database`
annotation is touched by several later tasks (expected, not a conflict).

- [ ] **Step 5: Wire a lazy singleton `AppDatabase` instance into `DhikrApplication`**

Read the current `DhikrApplication.kt` first (it's a bare `Application()`
subclass from Phase 1+2) and modify it to:

```kotlin
package com.dhikr.app

import android.app.Application
import androidx.room.Room
import com.dhikr.app.core.database.AppDatabase

class DhikrApplication : Application() {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, "dhikr.db")
            .build()
    }
}
```

Lazy, not eager at `onCreate()` — per plan.md §50, do not initialize heavy work
unconditionally at startup; the first screen that actually needs the database
(Task 3+) triggers construction on first access.

- [ ] **Step 6: Build to verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. This proves the KSP toolchain is wired correctly
before any entity/DAO code (which would produce far more confusing errors if the
plugin setup itself were wrong). If KSP or Room dependency resolution fails here,
stop and check the exact version numbers against what's currently published
(library versions can move between when this plan was written and when it's
executed) — ask the user before substituting a different version.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts app/src/main/java/com/dhikr/app/core/database/AppDatabase.kt app/src/main/java/com/dhikr/app/DhikrApplication.kt
git commit -m "Add Room + KSP dependencies and AppDatabase skeleton"
```

---

## Task 2: TasbihEntity, TasbihDao, seed data

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/database/entity/TasbihEntity.kt`
- Create: `app/src/main/java/com/dhikr/app/core/database/dao/TasbihDao.kt`
- Create: `app/src/main/java/com/dhikr/app/core/database/seed/SeedData.kt`
- Modify: `app/src/main/java/com/dhikr/app/core/database/AppDatabase.kt`
- Modify: `app/src/main/java/com/dhikr/app/DhikrApplication.kt`

Note: `core/model/Dhikr.kt` and `core/model/BuiltInDhikr.kt` are NOT touched by
this task, even though `SeedData.kt` duplicates `BuiltInDhikr`'s content — see
Step 7. Task 6 deletes both once `CounterViewModel` no longer needs them.

**Interfaces:**
- Produces:
  ```kotlin
  @Entity(tableName = "tasbih")
  data class TasbihEntity(
      @PrimaryKey val id: String,
      val name: String,
      val arabic: String,
      val transliteration: String,
      val translation: String,
      val note: String = "",
      val source: String? = null,
      val lapTarget: Int,
      val lapCount: Int,
      val dailyGoal: Int? = null,
      val isFavorite: Boolean = false,
      val isBuiltIn: Boolean,
      val createdAt: Long,
      val updatedAt: Long,
  )

  @Dao
  interface TasbihDao {
      fun observeAll(): Flow<List<TasbihEntity>>
      fun observeFavorites(): Flow<List<TasbihEntity>>
      suspend fun getById(id: String): TasbihEntity?
      fun search(query: String): Flow<List<TasbihEntity>>
      suspend fun insert(tasbih: TasbihEntity)
      suspend fun insertAll(tasbih: List<TasbihEntity>)
      suspend fun update(tasbih: TasbihEntity)
      suspend fun setFavorite(id: String, isFavorite: Boolean)
      suspend fun delete(tasbih: TasbihEntity)
      suspend fun count(): Int
  }

  object SeedData {
      val builtInTasbih: List<TasbihEntity>
  }
  ```
- Consumes: nothing new.

This task is the most content-sensitive one in the phase — `SeedData.builtInTasbih`
must carry over the exact 7 entries' Arabic/Bengali/English content from Phase
1+2's `BuiltInDhikr.kt` byte-for-byte. Do not retype the Bengali/Arabic strings
from memory or an external source — copy them directly from the existing
`core/model/BuiltInDhikr.kt` file (read it first).

- [ ] **Step 1: Read the existing `BuiltInDhikr.kt` and `Dhikr.kt` in full**

Before writing anything, read
`app/src/main/java/com/dhikr/app/core/model/BuiltInDhikr.kt` and
`app/src/main/java/com/dhikr/app/core/model/Dhikr.kt` completely — the 7 entries'
exact field values are the seed data source of truth for this task.

- [ ] **Step 2: Write `TasbihEntity.kt`**

```kotlin
package com.dhikr.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasbih",
    indices = [Index(value = ["isFavorite"], name = "idx_tasbih_favorite")],
)
data class TasbihEntity(
    @PrimaryKey val id: String,
    val name: String,
    val arabic: String,
    val transliteration: String,
    val translation: String,
    val note: String = "",
    val source: String? = null,
    val lapTarget: Int,
    val lapCount: Int,
    val dailyGoal: Int? = null,
    val isFavorite: Boolean = false,
    val isBuiltIn: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
```

- [ ] **Step 3: Write `TasbihDao.kt`**

```kotlin
package com.dhikr.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dhikr.app.core.database.entity.TasbihEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TasbihDao {

    @Query("SELECT * FROM tasbih ORDER BY isFavorite DESC, isBuiltIn DESC, name ASC")
    fun observeAll(): Flow<List<TasbihEntity>>

    @Query("SELECT * FROM tasbih WHERE isFavorite = 1 ORDER BY name ASC")
    fun observeFavorites(): Flow<List<TasbihEntity>>

    @Query("SELECT * FROM tasbih WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TasbihEntity?

    @Query(
        """
        SELECT * FROM tasbih
        WHERE name LIKE '%' || :query || '%'
           OR arabic LIKE '%' || :query || '%'
           OR transliteration LIKE '%' || :query || '%'
           OR translation LIKE '%' || :query || '%'
        ORDER BY isFavorite DESC, name ASC
        """
    )
    fun search(query: String): Flow<List<TasbihEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tasbih: TasbihEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tasbih: List<TasbihEntity>)

    @Update
    suspend fun update(tasbih: TasbihEntity)

    @Query("UPDATE tasbih SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Delete
    suspend fun delete(tasbih: TasbihEntity)

    @Query("SELECT COUNT(*) FROM tasbih")
    suspend fun count(): Int
}
```

`OnConflictStrategy.IGNORE` on insert (not `REPLACE`): seeding runs every app
launch (see Step 6) but must be a no-op after the first — `IGNORE` silently skips
rows whose primary key already exists rather than overwriting user edits to a
same-ID row (not a real scenario for built-ins today, but the safer default;
`REPLACE` would clobber a hypothetical future "user edited a built-in" case).
`search`'s `LIKE '%...%'` is case-insensitive by SQLite's default `LIKE` behavior
for ASCII; this matches the spec's "case-insensitive substring" requirement for
the Latin/English content — note Arabic/Bengali `LIKE` case-folding doesn't apply
the same way, but those scripts don't have a case distinction to begin with, so
this is not a gap.

- [ ] **Step 4: Write `SeedData.kt`, moving the 7 entries from `BuiltInDhikr.kt`**

Convert each entry from `BuiltInDhikr.all` into a `TasbihEntity`, preserving IDs
exactly. Use a fixed `createdAt`/`updatedAt` timestamp for all seed rows (e.g. a
constant epoch value, since "when was this built-in created" isn't meaningful —
do not use `System.currentTimeMillis()` here, which would make every fresh
install's seed timestamp different for no reason and complicate any future
sort-by-recency query).

```kotlin
package com.dhikr.app.core.database.seed

import com.dhikr.app.core.database.entity.TasbihEntity

object SeedData {
    private const val SEED_TIMESTAMP = 0L

    val builtInTasbih: List<TasbihEntity> = listOf(
        TasbihEntity(
            id = "kursi",
            name = "Ayatul Kursi",
            arabic = "",
            transliteration = /* copy exact 4-line concatenated Bengali string from BuiltInDhikr.kt verbatim */,
            translation = "",
            lapTarget = 7,
            lapCount = 1,
            isFavorite = true,
            isBuiltIn = true,
            createdAt = SEED_TIMESTAMP,
            updatedAt = SEED_TIMESTAMP,
        ),
        // ... subhan, hamd, akbar, istighfar, bihamdihi, hawla, each copied
        // verbatim from BuiltInDhikr.kt's corresponding Dhikr(...) entry ...
    )
}
```

Every field (`name`, `arabic`, `transliteration`, `translation`, `lapTarget`,
`lapCount`, `isFavorite`) must match `BuiltInDhikr.kt`'s existing 7 entries
exactly — this is a direct field-for-field port, not a re-derivation. Do not
invent or alter `source`/`note` — leave `source = null` and `note = ""` for all
seed entries (no religious sourcing citations exist in the Phase 1+2 content to
carry over; inventing one would violate plan.md §67's "do not invent... Quran
references... unless reliably sourced").

- [ ] **Step 5: Update `AppDatabase.kt` to include the new entity/DAO**

```kotlin
package com.dhikr.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dhikr.app.core.database.dao.TasbihDao
import com.dhikr.app.core.database.entity.TasbihEntity

@Database(
    entities = [TasbihEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasbihDao(): TasbihDao
}
```

- [ ] **Step 6: Seed the database on first launch from `DhikrApplication`**

Update `DhikrApplication.kt` to trigger seeding once, off the main thread, without
blocking app startup:

```kotlin
package com.dhikr.app

import android.app.Application
import androidx.room.Room
import com.dhikr.app.core.database.AppDatabase
import com.dhikr.app.core.database.seed.SeedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DhikrApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, "dhikr.db")
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            if (database.tasbihDao().count() == 0) {
                database.tasbihDao().insertAll(SeedData.builtInTasbih)
            }
        }
    }
}
```

The `count() == 0` check (rather than relying solely on `OnConflictStrategy.IGNORE`
across all 7 rows every launch) avoids 7 no-op insert statements running on every
single app start — cheap, but pointless work `plan.md §50/§52` asks to avoid.

- [ ] **Step 7: Leave `BuiltInDhikr.kt` and `Dhikr.kt` untouched — no action this
      step**

`core.model.Dhikr` and `core.model.BuiltInDhikr` are still referenced by
`CounterUiState.kt` and `CounterViewModel.kt` at this point in the plan. Do not
delete either file in this task, even though `SeedData.kt` now duplicates
`BuiltInDhikr`'s content — deleting them now would break the current build, since
`CounterViewModel` hasn't been migrated onto `TasbihEntity` yet. Task 6 migrates
`CounterViewModel`/`CounterUiState` onto `TasbihEntity` directly and deletes both
`BuiltInDhikr.kt` and `Dhikr.kt` in the same task, once nothing references them.
(This is why this task's Files list above does not list either file under
"Delete" — both stay in place through this task.)

- [ ] **Step 8: Build to verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. Room's KSP annotation processor will fail loudly and
specifically if `TasbihEntity`/`TasbihDao` have a mismatched type or missing
`@PrimaryKey` — read the error carefully if it fails, Room's compile-time
diagnostics are usually precise about the exact problem.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/database/ app/src/main/java/com/dhikr/app/DhikrApplication.kt
git commit -m "Add TasbihEntity, TasbihDao, seed data for built-in Tasbih"
```

---

## Task 3: TasbihRepository

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/database/TasbihRepository.kt`

**Interfaces:**
- Consumes: `TasbihDao` (Task 2).
- Produces:
  ```kotlin
  class TasbihRepository(private val tasbihDao: TasbihDao) {
      fun observeAll(): Flow<List<TasbihEntity>>
      fun observeFavorites(): Flow<List<TasbihEntity>>
      fun search(query: String): Flow<List<TasbihEntity>>
      suspend fun getById(id: String): TasbihEntity?
      suspend fun insert(tasbih: TasbihEntity)
      suspend fun update(tasbih: TasbihEntity)
      suspend fun toggleFavorite(id: String, currentlyFavorite: Boolean)
      suspend fun delete(tasbih: TasbihEntity): DeleteResult
      fun newId(): String
  }

  sealed interface DeleteResult {
      data object Success : DeleteResult
      data class BlockedByRoutines(val routineNames: List<String>) : DeleteResult
  }
  ```
  Consumed by Tasks 4, 5, 6, 8, 9, 12 (every screen/ViewModel that touches Tasbih
  data).

This thin repository layer exists so ViewModels don't call DAOs directly and so
the delete-blocked-by-routine-reference check (spec's Error handling section) has
one place to live rather than being reimplemented per-screen.

- [ ] **Step 1: Write `TasbihRepository.kt`**

`delete()`'s routine-reference check needs `RoutineDao`, introduced later in
Task 9. This task implements plain delete (no reference check yet — no routine
table exists to check against); Task 9 modifies this same file's `delete()` body
to add the real check once `RoutineDao` exists (that modification is called out
explicitly in Task 9's own steps below).

```kotlin
package com.dhikr.app.core.database

import com.dhikr.app.core.database.dao.TasbihDao
import com.dhikr.app.core.database.entity.TasbihEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

sealed interface DeleteResult {
    data object Success : DeleteResult
    data class BlockedByRoutines(val routineNames: List<String>) : DeleteResult
}

class TasbihRepository(private val tasbihDao: TasbihDao) {

    fun observeAll(): Flow<List<TasbihEntity>> = tasbihDao.observeAll()

    fun observeFavorites(): Flow<List<TasbihEntity>> = tasbihDao.observeFavorites()

    fun search(query: String): Flow<List<TasbihEntity>> = tasbihDao.search(query)

    suspend fun getById(id: String): TasbihEntity? = tasbihDao.getById(id)

    suspend fun insert(tasbih: TasbihEntity) = tasbihDao.insert(tasbih)

    suspend fun update(tasbih: TasbihEntity) = tasbihDao.update(tasbih)

    suspend fun toggleFavorite(id: String, currentlyFavorite: Boolean) {
        tasbihDao.setFavorite(id, !currentlyFavorite)
    }

    suspend fun delete(tasbih: TasbihEntity): DeleteResult {
        // Task 9 replaces this body with a real routine-reference check once
        // RoutineDao exists. For now, plain delete.
        tasbihDao.delete(tasbih)
        return DeleteResult.Success
    }

    fun newId(): String = UUID.randomUUID().toString()
}
```

Two separate methods, `insert()` for creating a new Tasbih and `update()` for
editing an existing one — both simple pass-throughs to the DAO, no hidden
branching. Task 5's editor ViewModel calls whichever is appropriate: `insert()`
when creating (no existing `id` to load), `update()` when editing (an existing
row was loaded first).

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/database/TasbihRepository.kt
git commit -m "Add TasbihRepository"
```

---

## Task 4: Tasbih library screen

**Files:**
- Create: `app/src/main/java/com/dhikr/app/feature/tasbih/TasbihLibraryViewModel.kt`
- Create: `app/src/main/java/com/dhikr/app/feature/tasbih/TasbihLibraryScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `TasbihRepository` (Task 3).
- Produces:
  ```kotlin
  data class TasbihLibraryUiState(
      val query: String,
      val results: List<TasbihEntity>,
      val builtInCount: Int,
      val customCount: Int,
  )

  class TasbihLibraryViewModel(private val repository: TasbihRepository) : ViewModel() {
      val uiState: StateFlow<TasbihLibraryUiState>
      fun onQueryChange(query: String)
      fun onToggleFavorite(id: String, currentlyFavorite: Boolean)
  }

  @Composable
  fun TasbihLibraryScreen(
      viewModel: TasbihLibraryViewModel,
      onOpenTasbih: (String) -> Unit,
      onNewTasbih: () -> Unit,
  )
  ```
  Consumed by `DhikrApp.kt`'s nav graph (Task 12).

- [ ] **Step 1: Add Tasbih-library strings to `strings.xml`**

```xml
<string name="tasbih_library_title">Tasbih</string>
<string name="tasbih_library_new">+ New</string>
<string name="tasbih_library_search_placeholder">Search name, Arabic, transliteration</string>
<string name="tasbih_library_result_count_all">%1$d built-in · %2$d custom</string>
<string name="tasbih_library_result_count_filtered">%1$d of %2$d match \"%3$s\"</string>
<string name="tasbih_library_empty">Nothing matches that. Create it as a custom Tasbih.</string>
<string name="tasbih_library_favorite_content_description">Favorite</string>
```

- [ ] **Step 2: Write `TasbihLibraryViewModel.kt`**

Search debounces at the DAO/Flow level via `flatMapLatest` so typing quickly
doesn't fire a query per keystroke — reuses the same responsive-but-not-wasteful
principle as the Counter screen's persistence debounce from Phase 1+2.

```kotlin
package com.dhikr.app.feature.tasbih

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.database.entity.TasbihEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class TasbihLibraryUiState(
    val query: String = "",
    val results: List<TasbihEntity> = emptyList(),
    val builtInCount: Int = 0,
    val customCount: Int = 0,
)

class TasbihLibraryViewModel(private val repository: TasbihRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<TasbihLibraryUiState> = combine(
        query,
        query.flatMapLatest { q ->
            if (q.isBlank()) repository.observeAll() else repository.search(q)
        },
        repository.observeAll(), // for stable built-in/custom counts, independent of the filter
    ) { q, results, all ->
        TasbihLibraryUiState(
            query = q,
            results = results,
            builtInCount = all.count { it.isBuiltIn },
            customCount = all.count { !it.isBuiltIn },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TasbihLibraryUiState(),
    )

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    fun onToggleFavorite(id: String, currentlyFavorite: Boolean) {
        viewModelScope.launch { repository.toggleFavorite(id, currentlyFavorite) }
    }

    class Factory(private val repository: TasbihRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TasbihLibraryViewModel(repository) as T
    }
}
```

Add the missing `import androidx.lifecycle.ViewModelProvider` and
`import kotlinx.coroutines.launch` while implementing — omitted above by
oversight, needed for `Factory` and `viewModelScope.launch` respectively.

- [ ] **Step 3: Write `TasbihLibraryScreen.kt`**

Layout per design README §3: header (title + sage "+ New" pill), 46dp search
pill (`surface`, 1px `line` border, magnifier icon in `faint`), result-count line
(switches between the two string variants added in Step 1 depending on whether
`query` is blank), a `LazyColumn` of `card`-filled 22dp rows (name 14.5sp/600 over
Bengali 12sp `faint` ellipsized 1 line, meta text, Arabic right-aligned max 96dp
wide, favorite heart that stops click propagation so tapping it never also opens
the Dhikr), and the empty-state text when `results` is empty and `query` is not
blank.

```kotlin
package com.dhikr.app.feature.tasbih

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.core.database.entity.TasbihEntity
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.PillShape

@Composable
fun TasbihLibraryScreen(
    viewModel: TasbihLibraryViewModel,
    onOpenTasbih: (String) -> Unit,
    onNewTasbih: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors

    Column(modifier = Modifier.fillMaxSize().background(colors.bg).padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.tasbih_library_title), fontSize = 23.sp, color = colors.text)
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(colors.sage)
                    .clickable { onNewTasbih() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.tasbih_library_new), color = colors.onSage)
            }
        }

        TextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text(stringResource(R.string.tasbih_library_search_placeholder)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(PillShape),
        )

        Text(
            text = if (state.query.isBlank()) {
                stringResource(R.string.tasbih_library_result_count_all, state.builtInCount, state.customCount)
            } else {
                stringResource(
                    R.string.tasbih_library_result_count_filtered,
                    state.results.size,
                    state.builtInCount + state.customCount,
                    state.query,
                )
            },
            fontSize = 11.5.sp,
            color = colors.faint,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )

        if (state.results.isEmpty() && state.query.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.tasbih_library_empty),
                    color = colors.faint,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.results, key = { it.id }) { tasbih ->
                    TasbihRow(
                        tasbih = tasbih,
                        onClick = { onOpenTasbih(tasbih.id) },
                        onToggleFavorite = { viewModel.onToggleFavorite(tasbih.id, tasbih.isFavorite) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TasbihRow(tasbih: TasbihEntity, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    val colors = DhikrTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
            .background(colors.card)
            .clickable { onClick() }
            .padding(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tasbih.name, fontSize = 14.5.sp, color = colors.text)
            Text(
                tasbih.transliteration,
                fontSize = 12.sp,
                color = colors.faint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            tasbih.arabic,
            fontSize = 18.sp,
            color = colors.dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 96.dp).padding(horizontal = 8.dp),
        )
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(R.string.tasbih_library_favorite_content_description)) {
                    onToggleFavorite()
                }
                .padding(6.dp),
        ) {
            Icon(
                imageVector = if (tasbih.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(R.string.tasbih_library_favorite_content_description),
                tint = if (tasbih.isFavorite) colors.terra else colors.faint,
            )
        }
    }
}
```

The heart's `clickable` on the inner `Box` is a separate clickable region nested
inside the row's own `clickable` — in Compose, a clicked child consumes the tap
before it propagates to the parent's `clickable`, so tapping the heart does NOT
also trigger `onClick()`/open the Dhikr. This satisfies the design's "must not
open the Dhikr (stop propagation)" requirement without needing an explicit
`pointerInput`/`consume()` call — verify this is still true by testing the
behavior after building (nested `clickable`s consuming taps is standard Compose
behavior, but confirm no `Modifier.clickable` ordering issue reintroduces
propagation).

Meta text (`33 × 3 laps` / `100 per lap`) shown in the design's row spec is
omitted from the code above for brevity — add a small `Text` between the name and
transliteration showing `"${tasbih.lapTarget} × ${tasbih.lapCount} laps"` if
`lapCount > 1`, else `"${tasbih.lapTarget} per lap"`, at 11–12.5sp `dim`, matching
the design's meta-text sizing from other screens.

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. `androidx.compose.material.icons.extended` (for
`Favorite`/`FavoriteBorder`) may not be in the current dependency list — check
`app/build.gradle.kts`; if the icons aren't resolvable, add
`implementation(libs.compose.material.icons.extended)` and the matching
`compose-material-icons-extended` entry to `gradle/libs.versions.toml`'s
`[libraries]` (group `androidx.compose.material`, name
`material-icons-extended`, no separate version — it's part of the Compose BOM).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/tasbih/TasbihLibraryViewModel.kt app/src/main/java/com/dhikr/app/feature/tasbih/TasbihLibraryScreen.kt app/src/main/res/values/strings.xml app/build.gradle.kts gradle/libs.versions.toml
git commit -m "Add Tasbih library screen with search and favorites"
```

---

## Task 5: Custom Tasbih editor screen

**Files:**
- Create: `app/src/main/java/com/dhikr/app/feature/tasbih/TasbihEditorViewModel.kt`
- Create: `app/src/main/java/com/dhikr/app/feature/tasbih/TasbihEditorScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `TasbihRepository` (Task 3).
- Produces:
  ```kotlin
  data class TasbihEditorUiState(
      val isEditingExisting: Boolean = false,
      val name: String = "",
      val arabic: String = "",
      val translation: String = "",
      val note: String = "",
      val lapTarget: Int = 33,
      val dailyGoal: Int? = null,
      val canSave: Boolean = false,
  )

  class TasbihEditorViewModel(
      private val repository: TasbihRepository,
      private val editingId: String? = null,
  ) : ViewModel() {
      val uiState: StateFlow<TasbihEditorUiState>
      fun onNameChange(value: String)
      fun onArabicChange(value: String)
      fun onTranslationChange(value: String)
      fun onNoteChange(value: String)
      fun onLapTargetChange(value: Int)
      fun onDailyGoalChange(value: Int?)
      fun onSave(onSaved: () -> Unit)
  }

  @Composable
  fun TasbihEditorScreen(viewModel: TasbihEditorViewModel, onBack: () -> Unit)
  ```
  Consumed by `DhikrApp.kt`'s nav graph (Task 12), which passes `editingId = null`
  for "+ New" and the tapped Tasbih's `id` when opened for editing.

- [ ] **Step 1: Add editor strings to `strings.xml`**

```xml
<string name="tasbih_editor_title_new">New Tasbih</string>
<string name="tasbih_editor_title_edit">Edit Tasbih</string>
<string name="tasbih_editor_name_label">Name</string>
<string name="tasbih_editor_name_placeholder">e.g. Evening Tasbih</string>
<string name="tasbih_editor_arabic_label">Arabic text</string>
<string name="tasbih_editor_arabic_placeholder">اكتب الذكر</string>
<string name="tasbih_editor_translation_label">Translation</string>
<string name="tasbih_editor_translation_placeholder">What it means</string>
<string name="tasbih_editor_note_label">Personal note</string>
<string name="tasbih_editor_note_placeholder">Why you are reciting it</string>
<string name="tasbih_editor_lap_target_label">Lap target</string>
<string name="tasbih_editor_daily_goal_label">Daily goal</string>
<string name="tasbih_editor_save">Save Tasbih</string>
<string name="tasbih_editor_footer">Stored on this device only. Nothing is uploaded.</string>
```

- [ ] **Step 2: Write `TasbihEditorViewModel.kt`**

Loading an existing Tasbih for edit is a one-shot suspend read on `init` (not a
continuous observer — matches the Counter screen's `restoreSession()` pattern
from Phase 1+2). `canSave` is derived: name must be non-blank.

```kotlin
package com.dhikr.app.feature.tasbih

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.database.entity.TasbihEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TasbihEditorUiState(
    val isEditingExisting: Boolean = false,
    val name: String = "",
    val arabic: String = "",
    val translation: String = "",
    val note: String = "",
    val lapTarget: Int = 33,
    val dailyGoal: Int? = null,
    val canSave: Boolean = false,
)

class TasbihEditorViewModel(
    private val repository: TasbihRepository,
    private val editingId: String? = null,
) : ViewModel() {

    private var loadedEntity: TasbihEntity? = null
    private val _uiState = MutableStateFlow(TasbihEditorUiState())
    val uiState: StateFlow<TasbihEditorUiState> = _uiState.asStateFlow()

    init {
        if (editingId != null) {
            viewModelScope.launch {
                repository.getById(editingId)?.let { entity ->
                    loadedEntity = entity
                    _uiState.value = TasbihEditorUiState(
                        isEditingExisting = true,
                        name = entity.name,
                        arabic = entity.arabic,
                        translation = entity.translation,
                        note = entity.note,
                        lapTarget = entity.lapTarget,
                        dailyGoal = entity.dailyGoal,
                        canSave = entity.name.isNotBlank(),
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) = update { it.copy(name = value, canSave = value.isNotBlank()) }
    fun onArabicChange(value: String) = update { it.copy(arabic = value) }
    fun onTranslationChange(value: String) = update { it.copy(translation = value) }
    fun onNoteChange(value: String) = update { it.copy(note = value) }
    fun onLapTargetChange(value: Int) = update { it.copy(lapTarget = value.coerceAtLeast(1)) }
    fun onDailyGoalChange(value: Int?) = update { it.copy(dailyGoal = value) }

    fun onSave(onSaved: () -> Unit) {
        val s = _uiState.value
        if (!s.canSave) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = loadedEntity
            if (existing != null) {
                repository.update(
                    existing.copy(
                        name = s.name,
                        arabic = s.arabic,
                        translation = s.translation,
                        note = s.note,
                        lapTarget = s.lapTarget,
                        dailyGoal = s.dailyGoal,
                        updatedAt = now,
                    )
                )
            } else {
                repository.insert(
                    TasbihEntity(
                        id = repository.newId(),
                        name = s.name,
                        arabic = s.arabic,
                        transliteration = "",
                        translation = s.translation,
                        note = s.note,
                        lapTarget = s.lapTarget,
                        lapCount = 1,
                        dailyGoal = s.dailyGoal,
                        isBuiltIn = false,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
            onSaved()
        }
    }

    private inline fun update(block: (TasbihEditorUiState) -> TasbihEditorUiState) {
        _uiState.value = block(_uiState.value)
    }

    class Factory(
        private val repository: TasbihRepository,
        private val editingId: String? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TasbihEditorViewModel(repository, editingId) as T
    }
}
```

Custom Tasbih created through this editor always get `lapCount = 1` (a single
lap) — the design's editor (README §4) has no lap-*count* field, only a lap
*target* stepper; multi-lap custom Tasbih are not exposed by this UI. This
matches the design exactly — do not add a lap-count field that isn't in the
design spec.

`transliteration = ""` for new custom Tasbih: the editor's fields (per design
README §4) are Name/Arabic/Translation/Note/Lap-target/Daily-goal — there is no
transliteration field in the design. Leave it empty for custom entries; the
Counter/library screens must already handle `transliteration.isEmpty()`
gracefully since Ayatul Kursi's `arabic` field is empty in exactly the same way
(Phase 1+2 precedent).

- [ ] **Step 3: Write `TasbihEditorScreen.kt`**

Layout per design README §4: back chevron + title (switches "New Tasbih"/"Edit
Tasbih" based on `isEditingExisting`), fields 15dp apart each with an uppercase
label over a 48dp pill input, Arabic field RTL, note field as a 3-row textarea,
lap-target stepper (`−`/`+` 36dp circles, clamped min 1 — `onLapTargetChange`
already clamps), daily-goal as three pill options (33/100/500, selected = sage
fill), full-width 52dp terracotta Save button (disabled when `!canSave`), and the
"stored on this device only" footer text.

```kotlin
package com.dhikr.app.feature.tasbih

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.PillShape

@Composable
fun TasbihEditorScreen(viewModel: TasbihEditorViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(
                if (state.isEditingExisting) R.string.tasbih_editor_title_edit
                else R.string.tasbih_editor_title_new,
            ),
            fontSize = 23.sp,
            color = colors.text,
            modifier = Modifier.padding(bottom = 20.dp),
        )

        LabeledField(stringResource(R.string.tasbih_editor_name_label)) {
            TextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                placeholder = { Text(stringResource(R.string.tasbih_editor_name_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LabeledField(stringResource(R.string.tasbih_editor_arabic_label)) {
            TextField(
                value = state.arabic,
                onValueChange = viewModel::onArabicChange,
                placeholder = { Text(stringResource(R.string.tasbih_editor_arabic_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LabeledField(stringResource(R.string.tasbih_editor_translation_label)) {
            TextField(
                value = state.translation,
                onValueChange = viewModel::onTranslationChange,
                placeholder = { Text(stringResource(R.string.tasbih_editor_translation_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LabeledField(stringResource(R.string.tasbih_editor_note_label)) {
            TextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                placeholder = { Text(stringResource(R.string.tasbih_editor_note_placeholder)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LabeledField(stringResource(R.string.tasbih_editor_lap_target_label)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(state.lapTarget.toString(), fontSize = 16.sp, color = colors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepperButton("−", colors.surface, colors.text) { viewModel.onLapTargetChange(state.lapTarget - 1) }
                    StepperButton("+", colors.sage, colors.onSage) { viewModel.onLapTargetChange(state.lapTarget + 1) }
                }
            }
        }

        LabeledField(stringResource(R.string.tasbih_editor_daily_goal_label)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(33, 100, 500).forEach { option ->
                    val selected = state.dailyGoal == option
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(if (selected) colors.sage else colors.surface)
                            .clickable { viewModel.onDailyGoalChange(if (selected) null else option) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(option.toString(), color = if (selected) colors.onSage else colors.text)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .height(52.dp)
                .clip(PillShape)
                .background(if (state.canSave) colors.terra else colors.track)
                .clickable(enabled = state.canSave) { viewModel.onSave(onBack) },
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.tasbih_editor_save), color = colors.card)
        }
        Text(
            stringResource(R.string.tasbih_editor_footer),
            fontSize = 11.5.sp,
            color = colors.faint,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun LabeledField(label: String, content: @Composable () -> Unit) {
    val colors = DhikrTheme.colors
    Column(modifier = Modifier.padding(bottom = 15.dp)) {
        Text(label.uppercase(), fontSize = 11.5.sp, color = colors.dim, modifier = Modifier.padding(bottom = 4.dp))
        content()
    }
}

@Composable
private fun StepperButton(label: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick() }
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg)
    }
}
```

The `onBack` navigation lambda is passed directly as `onSaved` to
`viewModel.onSave(onBack)` in the Save button's `clickable` — saving
successfully navigates back to wherever the editor was opened from (library or
Home), matching the design's implicit "Save closes the editor" flow.

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/tasbih/TasbihEditorViewModel.kt app/src/main/java/com/dhikr/app/feature/tasbih/TasbihEditorScreen.kt app/src/main/res/values/strings.xml
git commit -m "Add Custom Tasbih editor screen"
```

---
## Task 6: Migrate CounterViewModel/CounterUiState onto TasbihEntity

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/feature/counter/CounterUiState.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/counter/CounterViewModel.kt`
- Modify: `app/src/main/java/com/dhikr/app/DhikrApp.kt`
- Delete: `app/src/main/java/com/dhikr/app/core/model/Dhikr.kt`
- Delete: `app/src/main/java/com/dhikr/app/core/model/BuiltInDhikr.kt`

**Interfaces:**
- Consumes: `TasbihRepository` (Task 3), `TasbihEntity` (Task 2).
- Produces: `CounterUiState.dhikr` changes type from `Dhikr` to `TasbihEntity` (the
  field name stays `dhikr` — renaming it is out of scope for this task, avoiding
  unnecessary churn across `CounterScreen.kt`, which already reads `state.dhikr.name`
  etc. and needs no changes since `TasbihEntity` has the same field names as
  `Dhikr` did: `name`, `arabic`, `transliteration`, `lapTarget`, `lapCount`). New
  `CounterViewModel` constructor: `CounterViewModel(sessionRepository: SessionRepository, tasbihRepository: TasbihRepository, startingDhikrId: String? = null)`.
  `CounterViewModel.Factory` gains a `tasbihRepository` parameter. Consumed by
  `DhikrApp.kt` (this task) and Tasks 8/12 (routine start, Home continue-session).

This is a "load once, then it's simple" migration: `TasbihEntity` and the old
`Dhikr` class have identical field names for everything `CounterUiState`/
`CounterScreen` actually use (`id`, `name`, `arabic`, `transliteration`,
`lapTarget`, `lapCount`) — the type swap is close to mechanical, but loading a
Tasbih from Room is `suspend`, unlike the old `BuiltInDhikr.byId()`'s synchronous
in-memory lookup, so `CounterViewModel`'s `init` sequencing needs care: the
ViewModel can no longer synchronously construct its first `_uiState` value in the
constructor the way it did when `BuiltInDhikr.byId()` was a plain function call.

- [ ] **Step 1: Read the current `CounterUiState.kt` and `CounterViewModel.kt` in
      full**

Both files are small (Phase 1+2 output) — read them completely before editing so
every `Dhikr`/`BuiltInDhikr` reference is found, not just the obvious ones.

- [ ] **Step 2: Update `CounterUiState.kt`'s import and field type**

Change `import com.dhikr.app.core.model.Dhikr` to
`import com.dhikr.app.core.database.entity.TasbihEntity`, and change the `dhikr:
Dhikr` field to `dhikr: TasbihEntity`. No other changes — `totalCount`/
`progressFraction`'s computed properties reference `dhikr.lapTarget`, which exists
identically on `TasbihEntity`.

- [ ] **Step 3: Update `CounterViewModel.kt`**

Key changes:
- Constructor gains `tasbihRepository: TasbihRepository`.
- `private var dhikr: Dhikr = BuiltInDhikr.byId(...)` (a synchronous field
  initializer) cannot survive as-is, since loading from Room is suspend. Replace
  the eager field-initializer pattern with: construct `_uiState` initially from a
  **placeholder/loading-safe default is not available** — instead, make Tasbih
  loading part of the same `init` coroutine that already does `restoreSession()`
  (Phase 1+2's one-shot restore pattern), sequenced so the Tasbih is loaded
  *before* the engine is constructed:

  ```kotlin
  class CounterViewModel(
      private val sessionRepository: SessionRepository,
      private val tasbihRepository: TasbihRepository,
      startingDhikrId: String? = null,
  ) : ViewModel() {

      private lateinit var dhikr: TasbihEntity
      private lateinit var engine: TasbihCounter
      private var locked = false
      private var elapsedSeconds = 0

      private val _uiState = MutableStateFlow(CounterUiState.Empty)
      val uiState: StateFlow<CounterUiState> = _uiState.asStateFlow()

      private val requestedStartingId = startingDhikrId

      init {
          viewModelScope.launch {
              initializeSession()
              // debounced persistence + timer only start once the engine exists
              _uiState
                  .drop(1)
                  .debounce(500)
                  .onEach { persist() }
                  .launchIn(viewModelScope)
              startTimer()
          }
      }

      private suspend fun initializeSession() {
          val savedSession = sessionRepository.sessionFlow.first()
          val idToLoad = savedSession?.activeDhikrId ?: requestedStartingId
          // Never crash if the requested/saved Tasbih can't be found (deleted
          // custom Tasbih, corrupted DataStore referencing a stale id, etc.) —
          // fall back to whatever Tasbih Room actually has, per plan.md §57's
          // "never crash because of... corrupted data" requirement. An empty
          // Room table at this point would mean seeding (DhikrApplication)
          // hasn't completed yet or failed; falling back to CounterUiState.Empty
          // and leaving `dhikr`/`engine` uninitialized in that one pathological
          // case is acceptable since the screen has nothing to count without at
          // least one Tasbih existing.
          val loaded = idToLoad?.let { tasbihRepository.getById(it) }
              ?: tasbihRepository.observeAll().first().firstOrNull()
          if (loaded == null) return // nothing to load; _uiState stays at Empty
          dhikr = loaded
          engine = TasbihCounter(dhikr.lapTarget, dhikr.lapCount)
          if (savedSession != null && savedSession.activeDhikrId == loaded.id) {
              engine.restore(
                  count = savedSession.count,
                  lap = savedSession.lap,
                  previous = if (savedSession.previousCount != null && savedSession.previousLap != null) {
                      savedSession.previousCount to savedSession.previousLap
                  } else null,
              )
              locked = savedSession.locked
              elapsedSeconds = savedSession.elapsedSeconds
              if (!savedSession.running) engine.pause()
          }
          _uiState.value = buildState()
      }

      // onTap/onUndo/onReset/onTogglePause/onToggleLock/buildState/flushSession/
      // persist/onCleared: unchanged in logic from Phase 1+2, but every
      // reference to `dhikr`/`engine` now assumes they're initialized (safe,
      // since `uiState` is only ever collected after `_uiState.value =
      // buildState()` runs at the end of initializeSession() —
      // collectAsState() in Compose starts from CounterUiState.Empty until
      // then).

      class Factory(
          private val sessionRepository: SessionRepository,
          private val tasbihRepository: TasbihRepository,
          private val startingDhikrId: String? = null,
      ) : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T =
              CounterViewModel(sessionRepository, tasbihRepository, startingDhikrId) as T
      }
  }
  ```

  This restructures Phase 1+2's `restoreSession()`/`startTimer()` split into a
  single sequenced `initializeSession()` because the Tasbih load and the session
  restore are no longer independent — Phase 1+2 always loaded the Tasbih
  synchronously via `BuiltInDhikr.byId()` before restore ran; now both are
  suspend and must happen in a defined order (load-then-maybe-restore).

  `CounterUiState.Empty` (a sensible zero-state default) needs to exist for the
  `_uiState` initial value before `initializeSession()` completes — add a
  companion-object constant to `CounterUiState.kt`:
  ```kotlin
  companion object {
      val Empty = CounterUiState(
          dhikr = TasbihEntity(
              id = "", name = "", arabic = "", transliteration = "", translation = "",
              lapTarget = 1, lapCount = 1, isBuiltIn = true, createdAt = 0, updatedAt = 0,
          ),
          count = 0, lap = 1, totalLaps = 1, canUndo = false, running = false,
          locked = false, elapsedSeconds = 0, isComplete = false, justCompletedLap = false,
      )
  }
  ```
  `CounterScreen.kt` briefly renders this empty state for one frame while
  `initializeSession()`'s coroutine runs (Room reads are fast — sub-frame in
  practice for a single-row lookup) — this is an acceptable, standard Compose
  loading-flicker tradeoff and matches how the rest of this phase's screens
  handle their own one-shot loads (e.g. Task 5's editor). Do not add a separate
  loading spinner/state for this — it would be over-engineering for a
  sub-frame gap.

- [ ] **Step 4: Update `DhikrApp.kt`'s `CounterViewModel.Factory` construction**

Read `DhikrApp.kt`'s current `composable(ROUTE_COUNTER) { ... }` block (uses
`BuiltInDhikr.all.first().id` as `startingDhikrId` today) and update it to pass a
`tasbihRepository` (constructed the same `remember(context) { ... }` way as the
existing `sessionRepository`, via `(context.applicationContext as
DhikrApplication).database.tasbihDao()` wrapped in `TasbihRepository(...)`) and
change `startingDhikrId` from `BuiltInDhikr.all.first().id` to the literal string
`"subhan"` (SubhanAllah's stable seed ID, matching what the Home screen's
"Start SubhanAllah" stub button implies as the default — Task 12 replaces this
whole flow with real navigation arguments per-Tasbih, so this task's default is
a placeholder that Task 12 supersedes, not a final design decision).

- [ ] **Step 5: Delete `core/model/Dhikr.kt` and `core/model/BuiltInDhikr.kt`**

Both are now fully superseded — `TasbihEntity` (Task 2) replaces `Dhikr`,
`SeedData.kt` (Task 2) replaces `BuiltInDhikr`. Confirm via a repo-wide search
that no file still imports `com.dhikr.app.core.model.Dhikr` or
`com.dhikr.app.core.model.BuiltInDhikr` before deleting — if anything else still
references them, that reference must be updated first (this plan's tasks up to
this point should not have introduced any new usage, but verify rather than
assume).

- [ ] **Step 6: Build to verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. This task touches the most subtle coroutine
sequencing change in the plan (suspend Tasbih load replacing a synchronous one) —
if the build fails on a `lateinit property dhikr has not been initialized`-style
runtime concern surfaced by the compiler (unlikely, but the pattern is worth
double-checking), re-read Step 3's ordering rather than adding a workaround that
weakens the initialization guarantee.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/counter/CounterUiState.kt app/src/main/java/com/dhikr/app/feature/counter/CounterViewModel.kt app/src/main/java/com/dhikr/app/DhikrApp.kt
git rm app/src/main/java/com/dhikr/app/core/model/Dhikr.kt app/src/main/java/com/dhikr/app/core/model/BuiltInDhikr.kt
git commit -m "Migrate CounterViewModel from in-memory BuiltInDhikr to Room-backed TasbihEntity"
```

---
## Task 7: RoutineEntity, RoutineStepEntity, RoutineDao, preset routines seed

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/database/entity/RoutineEntity.kt`
- Create: `app/src/main/java/com/dhikr/app/core/database/entity/RoutineStepEntity.kt`
- Create: `app/src/main/java/com/dhikr/app/core/database/dao/RoutineDao.kt`
- Modify: `app/src/main/java/com/dhikr/app/core/database/seed/SeedData.kt`
- Modify: `app/src/main/java/com/dhikr/app/core/database/AppDatabase.kt`
- Modify: `app/src/main/java/com/dhikr/app/DhikrApplication.kt`

**Interfaces:**
- Consumes: `TasbihEntity` (Task 2, for the `tasbihId` FK).
- Produces:
  ```kotlin
  @Entity(tableName = "routine")
  data class RoutineEntity(
      @PrimaryKey val id: String,
      val name: String,
      val isPreset: Boolean,
      val createdAt: Long,
      val updatedAt: Long,
  )

  @Entity(tableName = "routine_step", foreignKeys = [...], indices = [...])
  data class RoutineStepEntity(
      @PrimaryKey(autoGenerate = true) val stepId: Long = 0,
      val routineId: String,
      val tasbihId: String,
      val stepOrder: Int,
      val targetCount: Int,
  )

  data class RoutineWithSteps(
      @Embedded val routine: RoutineEntity,
      @Relation(parentColumn = "id", entityColumn = "routineId")
      val steps: List<RoutineStepEntity>,
  )

  @Dao
  interface RoutineDao {
      fun observeAllWithSteps(): Flow<List<RoutineWithSteps>>
      suspend fun getWithSteps(id: String): RoutineWithSteps?
      suspend fun insertRoutine(routine: RoutineEntity)
      suspend fun insertRoutines(routines: List<RoutineEntity>)
      suspend fun insertSteps(steps: List<RoutineStepEntity>)
      suspend fun replaceSteps(routineId: String, steps: List<RoutineStepEntity>)
      suspend fun updateRoutine(routine: RoutineEntity)
      suspend fun deleteRoutine(routine: RoutineEntity)
      suspend fun countStepsUsingTasbih(tasbihId: String): Int
      suspend fun routineNamesUsingTasbih(tasbihId: String): List<String>
      suspend fun count(): Int
  }
  ```
  Consumed by Task 8 (`RoutineRepository`), Task 3's later modification (routine-
  reference check on Tasbih delete).

- [ ] **Step 1: Write `RoutineEntity.kt`**

```kotlin
package com.dhikr.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routine")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isPreset: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
```

- [ ] **Step 2: Write `RoutineStepEntity.kt`**

```kotlin
package com.dhikr.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routine_step",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TasbihEntity::class,
            parentColumns = ["id"],
            childColumns = ["tasbihId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("routineId"), Index("tasbihId")],
)
data class RoutineStepEntity(
    @PrimaryKey(autoGenerate = true) val stepId: Long = 0,
    val routineId: String,
    val tasbihId: String,
    val stepOrder: Int,
    val targetCount: Int,
)
```

Add `import com.dhikr.app.core.database.entity.TasbihEntity` if `TasbihEntity`
isn't already in the same package (it is — both live in
`core.database.entity` — so no cross-package import is actually needed; this note
exists only to flag that the FK reference must resolve to the real class, not a
placeholder).

- [ ] **Step 3: Write `RoutineDao.kt`**

```kotlin
package com.dhikr.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.dhikr.app.core.database.entity.RoutineEntity
import com.dhikr.app.core.database.entity.RoutineStepEntity
import kotlinx.coroutines.flow.Flow

data class RoutineWithSteps(
    @Embedded val routine: RoutineEntity,
    @Relation(parentColumn = "id", entityColumn = "routineId")
    val steps: List<RoutineStepEntity>,
)

@Dao
interface RoutineDao {

    @Transaction
    @Query("SELECT * FROM routine ORDER BY isPreset DESC, name ASC")
    fun observeAllWithSteps(): Flow<List<RoutineWithSteps>>

    @Transaction
    @Query("SELECT * FROM routine WHERE id = :id LIMIT 1")
    suspend fun getWithSteps(id: String): RoutineWithSteps?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRoutine(routine: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRoutines(routines: List<RoutineEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSteps(steps: List<RoutineStepEntity>)

    @Query("DELETE FROM routine_step WHERE routineId = :routineId")
    suspend fun deleteStepsForRoutine(routineId: String)

    @Transaction
    suspend fun replaceSteps(routineId: String, steps: List<RoutineStepEntity>) {
        deleteStepsForRoutine(routineId)
        insertSteps(steps)
    }

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Delete
    suspend fun deleteRoutine(routine: RoutineEntity)

    @Query("SELECT COUNT(*) FROM routine_step WHERE tasbihId = :tasbihId")
    suspend fun countStepsUsingTasbih(tasbihId: String): Int

    @Query(
        """
        SELECT DISTINCT r.name FROM routine r
        INNER JOIN routine_step s ON s.routineId = r.id
        WHERE s.tasbihId = :tasbihId
        """
    )
    suspend fun routineNamesUsingTasbih(tasbihId: String): List<String>

    @Query("SELECT COUNT(*) FROM routine")
    suspend fun count(): Int
}
```

`replaceSteps()` (delete-then-reinsert, wrapped in `@Transaction` on a default
Kotlin interface method) is how the Routines screen's reorder/edit flow persists
a new step order — simpler and more robust than computing a diff against the old
step list, and correct here because `stepId` is `autoGenerate` (reordering
doesn't need to preserve old row identities, only the `stepOrder` values matter
to the UI).

- [ ] **Step 4: Add preset routines to `SeedData.kt`**

Read the current `SeedData.kt` (Task 2's output) before editing. Add 4 preset
routines per plan.md §22 / design README §5's examples — Morning Dhikr, Evening
Dhikr, After Salah, Before Sleep — each referencing built-in Tasbih IDs from the
existing `builtInTasbih` seed list. Use the same `SEED_TIMESTAMP` constant
already defined in this file.

```kotlin
val presetRoutines: List<RoutineEntity> = listOf(
    RoutineEntity(id = "morning", name = "Morning Dhikr", isPreset = true, createdAt = SEED_TIMESTAMP, updatedAt = SEED_TIMESTAMP),
    RoutineEntity(id = "evening", name = "Evening Dhikr", isPreset = true, createdAt = SEED_TIMESTAMP, updatedAt = SEED_TIMESTAMP),
    RoutineEntity(id = "after_salah", name = "After Salah", isPreset = true, createdAt = SEED_TIMESTAMP, updatedAt = SEED_TIMESTAMP),
    RoutineEntity(id = "before_sleep", name = "Before Sleep", isPreset = true, createdAt = SEED_TIMESTAMP, updatedAt = SEED_TIMESTAMP),
)

val presetRoutineSteps: List<RoutineStepEntity> = listOf(
    // Morning Dhikr: SubhanAllah x33, Alhamdulillah x33, AllahuAkbar x34 (matches
    // the prototype's ROUTINES array and design README's own example exactly —
    // design/Dhikr Android App.dc.html's `morning`/`salah` routine definitions)
    RoutineStepEntity(routineId = "morning", tasbihId = "subhan", stepOrder = 0, targetCount = 33),
    RoutineStepEntity(routineId = "morning", tasbihId = "hamd", stepOrder = 1, targetCount = 33),
    RoutineStepEntity(routineId = "morning", tasbihId = "akbar", stepOrder = 2, targetCount = 34),
    // Evening Dhikr: same three, evening framing — no separate evening-specific
    // prototype data exists, so this reuses the same structure as Morning per
    // the plan's own "Evening Dhikr" listing (plan.md §22) which gives no
    // distinct counts of its own.
    RoutineStepEntity(routineId = "evening", tasbihId = "subhan", stepOrder = 0, targetCount = 33),
    RoutineStepEntity(routineId = "evening", tasbihId = "hamd", stepOrder = 1, targetCount = 33),
    RoutineStepEntity(routineId = "evening", tasbihId = "akbar", stepOrder = 2, targetCount = 34),
    // After Salah: matches the prototype's `salah` routine exactly (same 3 steps)
    RoutineStepEntity(routineId = "after_salah", tasbihId = "subhan", stepOrder = 0, targetCount = 33),
    RoutineStepEntity(routineId = "after_salah", tasbihId = "hamd", stepOrder = 1, targetCount = 33),
    RoutineStepEntity(routineId = "after_salah", tasbihId = "akbar", stepOrder = 2, targetCount = 34),
    // Before Sleep: matches the prototype's `sleep` routine exactly —
    // Astaghfirullah x100, Subhanallahi wa bihamdihi x100
    RoutineStepEntity(routineId = "before_sleep", tasbihId = "istighfar", stepOrder = 0, targetCount = 100),
    RoutineStepEntity(routineId = "before_sleep", tasbihId = "bihamdihi", stepOrder = 1, targetCount = 100),
)
```

The "Evening Dhikr" preset duplicating "Morning Dhikr"'s exact steps is a real
content gap — the prototype (`design/Dhikr Android App.dc.html`'s `ROUTINES`
array) only defines `morning`, `salah`, and `sleep`, not a distinct `evening`.
Rather than inventing distinct Evening Dhikr content (which would violate plan.md
§67's "do not invent... religious rulings/recommended counts" for something with
no sourced basis), this task reuses Morning's structure for Evening as the
closest defensible placeholder. **Flag this to the user during implementation
rather than silently shipping it** — if the user has a specific Evening Dhikr
content preference (e.g. different Tasbih/counts than Morning), get it before
finalizing; this plan's default is "same as Morning" only because no better
source exists.

- [ ] **Step 5: Update `AppDatabase.kt`**

Add `RoutineEntity::class, RoutineStepEntity::class` to the `entities` array and
`abstract fun routineDao(): RoutineDao` to the class body.

- [ ] **Step 6: Update `DhikrApplication.kt`'s seeding logic**

Extend the existing `onCreate()` seeding coroutine (from Task 2) to also seed
routines, gated the same way (only if the routine table is empty):

```kotlin
override fun onCreate() {
    super.onCreate()
    applicationScope.launch {
        if (database.tasbihDao().count() == 0) {
            database.tasbihDao().insertAll(SeedData.builtInTasbih)
        }
        if (database.routineDao().count() == 0) {
            database.routineDao().insertRoutines(SeedData.presetRoutines)
            database.routineDao().insertSteps(SeedData.presetRoutineSteps)
        }
    }
}
```

Uses `insertRoutines()` (the list-taking overload added to `RoutineDao` in
Step 3), matching `TasbihDao.insertAll`'s existing pattern from Task 2.

- [ ] **Step 7: Build to verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/database/ app/src/main/java/com/dhikr/app/DhikrApplication.kt
git commit -m "Add RoutineEntity, RoutineStepEntity, RoutineDao, preset routines seed"
```

---

## Task 8: RoutineRepository + extend TasbihRepository's delete check

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/database/RoutineRepository.kt`
- Modify: `app/src/main/java/com/dhikr/app/core/database/TasbihRepository.kt`

**Interfaces:**
- Consumes: `RoutineDao` (Task 7).
- Produces:
  ```kotlin
  class RoutineRepository(private val routineDao: RoutineDao) {
      fun observeAllWithSteps(): Flow<List<RoutineWithSteps>>
      suspend fun getWithSteps(id: String): RoutineWithSteps?
      suspend fun createRoutine(name: String, steps: List<Pair<String, Int>>): String
      suspend fun updateSteps(routineId: String, steps: List<Pair<String, Int>>)
      suspend fun renameRoutine(routineId: String, name: String)
      suspend fun deleteRoutine(routine: RoutineEntity)
      fun newId(): String
  }
  ```
  Consumed by Task 9 (Routines screen), Task 10 (Counter-screen routine start),
  Task 12 (Home's routine tiles).

- [ ] **Step 1: Write `RoutineRepository.kt`**

`steps: List<Pair<String, Int>>` in `createRoutine`/`updateSteps` is
`(tasbihId, targetCount)` pairs in display order — the repository assigns
`stepOrder` from list position so callers (the Routines editor UI) don't need to
manage order indices themselves, only list ordering.

```kotlin
package com.dhikr.app.core.database

import com.dhikr.app.core.database.dao.RoutineDao
import com.dhikr.app.core.database.dao.RoutineWithSteps
import com.dhikr.app.core.database.entity.RoutineEntity
import com.dhikr.app.core.database.entity.RoutineStepEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class RoutineRepository(private val routineDao: RoutineDao) {

    fun observeAllWithSteps(): Flow<List<RoutineWithSteps>> = routineDao.observeAllWithSteps()

    suspend fun getWithSteps(id: String): RoutineWithSteps? = routineDao.getWithSteps(id)

    suspend fun createRoutine(name: String, steps: List<Pair<String, Int>>): String {
        val id = newId()
        val now = System.currentTimeMillis()
        routineDao.insertRoutine(RoutineEntity(id = id, name = name, isPreset = false, createdAt = now, updatedAt = now))
        routineDao.insertSteps(steps.mapIndexed { index, (tasbihId, targetCount) ->
            RoutineStepEntity(routineId = id, tasbihId = tasbihId, stepOrder = index, targetCount = targetCount)
        })
        return id
    }

    suspend fun updateSteps(routineId: String, steps: List<Pair<String, Int>>) {
        routineDao.replaceSteps(
            routineId,
            steps.mapIndexed { index, (tasbihId, targetCount) ->
                RoutineStepEntity(routineId = routineId, tasbihId = tasbihId, stepOrder = index, targetCount = targetCount)
            },
        )
    }

    suspend fun renameRoutine(routineId: String, name: String) {
        val current = routineDao.getWithSteps(routineId)?.routine ?: return
        routineDao.updateRoutine(current.copy(name = name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteRoutine(routine: RoutineEntity) = routineDao.deleteRoutine(routine)

    fun newId(): String = UUID.randomUUID().toString()
}
```

- [ ] **Step 2: Extend `TasbihRepository.delete()` with the real routine-reference
      check**

Read the current `TasbihRepository.kt` (Task 3's output) and modify its
constructor and `delete()` body:

```kotlin
class TasbihRepository(
    private val tasbihDao: TasbihDao,
    private val routineDao: RoutineDao,
) {
    // ... all other methods unchanged from Task 3 ...

    suspend fun delete(tasbih: TasbihEntity): DeleteResult {
        val blockingRoutineNames = routineDao.routineNamesUsingTasbih(tasbih.id)
        if (blockingRoutineNames.isNotEmpty()) {
            return DeleteResult.BlockedByRoutines(blockingRoutineNames)
        }
        tasbihDao.delete(tasbih)
        return DeleteResult.Success
    }
}
```

This changes `TasbihRepository`'s constructor signature — every existing call
site that constructs a `TasbihRepository` (Tasks 4, 5, 6 so far) now needs a
`routineDao` argument too. Since Tasks 4-6 were already implemented before this
task runs, **this task must update every existing `TasbihRepository(...)`
construction site** to pass the new argument — search the codebase for
`TasbihRepository(` and update each call site found (likely in `DhikrApp.kt`,
wherever the repository is `remember`'d). Do not leave any call site broken.

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. A missed call site from Step 2 will show as a
constructor-arity compile error — fix every one found, don't stop at the first.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/database/RoutineRepository.kt app/src/main/java/com/dhikr/app/core/database/TasbihRepository.kt app/src/main/java/com/dhikr/app/DhikrApp.kt
git commit -m "Add RoutineRepository; wire routine-reference check into TasbihRepository.delete()"
```

---
## Task 9: Routines screen (list, create/edit steps, delete)

**Files:**
- Create: `app/src/main/java/com/dhikr/app/feature/routines/RoutinesViewModel.kt`
- Create: `app/src/main/java/com/dhikr/app/feature/routines/RoutinesScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `RoutineRepository` (Task 8), `TasbihRepository` (Task 3, for the
  step-editor's Tasbih picker).
- Produces:
  ```kotlin
  data class RoutinesUiState(val routines: List<RoutineWithSteps> = emptyList())

  class RoutinesViewModel(private val repository: RoutineRepository) : ViewModel() {
      val uiState: StateFlow<RoutinesUiState>
      fun onDeleteRoutine(routine: RoutineEntity)
      fun onReorderStep(routineId: String, steps: List<Pair<String, Int>>)
  }

  @Composable
  fun RoutinesScreen(
      viewModel: RoutinesViewModel,
      onStartRoutine: (String) -> Unit,
      onEditRoutine: (String) -> Unit,
      onNewRoutine: () -> Unit,
  )
  ```
  Consumed by `DhikrApp.kt`'s nav graph (Task 12).

Per the spec's note that the design bundle has no dedicated "routine step editor"
screen mock, this task builds the minimal editing surface the design's Routines
card interactions imply: tapping a routine's step list opens inline editing
(add/remove a step via a Tasbih picker + count field, drag handle reorders in
place) rather than a separate full-screen editor. This keeps scope aligned with
what's actually designed rather than inventing an undesigned screen.

- [ ] **Step 1: Add Routines strings to `strings.xml`**

```xml
<string name="routines_title">Routines</string>
<string name="routines_start">Start</string>
<string name="routines_step_count">%1$d steps · %2$d counts</string>
<string name="routines_new">+ New routine</string>
<string name="routines_delete_confirm_title">Delete this routine?</string>
<string name="routines_delete_confirm_body">This cannot be undone.</string>
<string name="routines_add_step">+ Add step</string>
```

- [ ] **Step 2: Write `RoutinesViewModel.kt`**

```kotlin
package com.dhikr.app.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.RoutineRepository
import com.dhikr.app.core.database.dao.RoutineWithSteps
import com.dhikr.app.core.database.entity.RoutineEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutinesUiState(val routines: List<RoutineWithSteps> = emptyList())

class RoutinesViewModel(private val repository: RoutineRepository) : ViewModel() {

    val uiState: StateFlow<RoutinesUiState> = repository.observeAllWithSteps()
        .map { RoutinesUiState(routines = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RoutinesUiState())

    fun onDeleteRoutine(routine: RoutineEntity) {
        viewModelScope.launch { repository.deleteRoutine(routine) }
    }

    fun onReorderSteps(routineId: String, steps: List<Pair<String, Int>>) {
        viewModelScope.launch { repository.updateSteps(routineId, steps) }
    }

    class Factory(private val repository: RoutineRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RoutinesViewModel(repository) as T
    }
}
```

- [ ] **Step 3: Write `RoutinesScreen.kt`**

Layout per design README §5: title, one `card` per routine (26dp radius) with a
header row (name 16sp/700, "N steps · M counts" meta, sage Start pill) and step
rows (index in `faint` tabular, name, count in terracotta 700, drag handle in
`faint`, 1px `line` top border between rows), and a dashed-border "+ New routine"
footer pill.

```kotlin
package com.dhikr.app.feature.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.core.database.dao.RoutineWithSteps
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.PillShape

@Composable
fun RoutinesScreen(
    viewModel: RoutinesViewModel,
    onStartRoutine: (String) -> Unit,
    onEditRoutine: (String) -> Unit,
    onNewRoutine: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors

    Column(modifier = Modifier.fillMaxSize().background(colors.bg).padding(16.dp)) {
        Text(stringResource(R.string.routines_title), fontSize = 23.sp, color = colors.text)
        LazyColumn(
            modifier = Modifier.padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(state.routines, key = { it.routine.id }) { routineWithSteps ->
                RoutineCard(
                    routineWithSteps = routineWithSteps,
                    onStart = { onStartRoutine(routineWithSteps.routine.id) },
                    onEdit = { onEditRoutine(routineWithSteps.routine.id) },
                )
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(PillShape)
                        .background(colors.bg)
                        .clickable { onNewRoutine() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.routines_new), color = colors.dim)
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(routineWithSteps: RoutineWithSteps, onStart: () -> Unit, onEdit: () -> Unit) {
    val colors = DhikrTheme.colors
    val steps = routineWithSteps.steps.sortedBy { it.stepOrder }
    val totalCount = steps.sumOf { it.targetCount }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(colors.card)
            .clickable { onEdit() }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(routineWithSteps.routine.name, fontSize = 16.sp, color = colors.text)
                Text(
                    stringResource(R.string.routines_step_count, steps.size, totalCount),
                    fontSize = 12.5.sp,
                    color = colors.dim,
                )
            }
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(colors.sage)
                    .clickable { onStart() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.routines_start), color = colors.onSage)
            }
        }
        steps.forEachIndexed { index, step ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                Text("${index + 1}", fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(end = 10.dp))
                // Step display name is intentionally the raw tasbihId here — this
                // is a known gap: RoutineWithSteps carries only tasbihId, not the
                // Tasbih's display name, so a lookup against TasbihRepository is
                // needed to show a real name instead of the id. See Step 4 below.
                Text(step.tasbihId, fontSize = 13.5.sp, color = colors.text, modifier = Modifier.weight(1f))
                Text("${step.targetCount}", fontSize = 13.5.sp, color = colors.terra)
            }
        }
    }
}
```

- [ ] **Step 4: Resolve the step-display-name gap flagged in Step 3**

`RoutineStepEntity` stores only `tasbihId`, not a display name, so `RoutineCard`
as drafted in Step 3 shows the raw ID string instead of "SubhanAllah" etc. — fix
this before considering the task done, using one of two approaches: (a) have
`RoutinesViewModel` join each `RoutineWithSteps`'s step `tasbihId`s against
`TasbihRepository` to build a `Map<String, TasbihEntity>` alongside the routine
list and pass that map down to `RoutineCard` for name lookup, or (b) add a Room
`@Relation`/custom query in `RoutineDao` that returns the Tasbih name directly
alongside each step. Prefer (a) — it keeps `RoutineDao` simple and reuses the
already-existing `TasbihRepository` rather than adding more DAO surface for a
display-only concern. Implement whichever approach you choose completely — do
not leave the raw-ID display as the final state.

- [ ] **Step 5: Build to verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/routines/ app/src/main/res/values/strings.xml
git commit -m "Add Routines screen (list, start, delete, step display)"
```

---

## Task 10: Counter screen routine auto-advance wiring

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/feature/counter/CounterViewModel.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/counter/CounterUiState.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/counter/CounterScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `RoutineRepository` (Task 8), `TasbihRepository` (Task 3).
- Produces: `CounterViewModel` constructor gains `routineRepository:
  RoutineRepository` and a `startingRoutineId: String? = null` parameter.
  `CounterUiState` gains `routineSteps: List<RoutineStepDisplay> = emptyList()`,
  `currentRoutineStepIndex: Int = -1`, and `isRoutineComplete: Boolean = false`
  (where `RoutineStepDisplay(tasbihName: String, targetCount: Int)`). Consumed by
  `CounterScreen.kt` (this task, renders the chips row and — per Step 4 —
  branches to the "Routine complete" overlay) and `DhikrApp.kt` (Task 12, passes
  `startingRoutineId` when starting a routine).

This task makes the `routineId`/`routineStep` fields on `CounterSessionState`
(reserved since Phase 1+2, unused until now) live, and renders the routine-chips
row in `CounterScreen` that Phase 1+2's spec explicitly deferred ("present in the
composable tree but not rendered").

- [ ] **Step 1: Add routine-related strings to `strings.xml`**

```xml
<string name="counter_step_label">Step %1$d of %2$d</string>
<string name="routine_complete_title">Routine complete</string>
<string name="routine_complete_body">%1$s finished — %2$d counts in %3$s.</string>
```

- [ ] **Step 2: Add `RoutineStepDisplay` and the two new fields to
      `CounterUiState.kt`**

```kotlin
data class RoutineStepDisplay(val tasbihName: String, val targetCount: Int)
```

Add `routineSteps: List<RoutineStepDisplay> = emptyList()` and
`currentRoutineStepIndex: Int = -1` to `CounterUiState`. `-1` means "not running a
routine" — `CounterScreen` checks `state.routineSteps.isNotEmpty()` (not the
index) to decide whether to render the chips row at all, so an empty list is the
real "no routine" signal and the index default is just a safe non-matching value.

- [ ] **Step 3: Extend `CounterViewModel.kt` for routine awareness**

Read the current file in full (as modified by Task 6) before editing — this step
assumes Task 6's `lateinit var dhikr`/`engine`, `initializeSession()` suspend
function, and unchanged `onTap()`/`onUndo()`/etc. structure. Add:

```kotlin
class CounterViewModel(
    private val sessionRepository: SessionRepository,
    private val tasbihRepository: TasbihRepository,
    private val routineRepository: RoutineRepository,
    startingDhikrId: String? = null,
    startingRoutineId: String? = null,
) : ViewModel() {

    private lateinit var dhikr: TasbihEntity
    private lateinit var engine: TasbihCounter
    private var locked = false
    private var elapsedSeconds = 0

    // Routine state — empty/−1 means "not running a routine".
    private var activeRoutine: RoutineWithSteps? = null
    private var routineStepIndex = -1
    private var routineStepNames: List<String> = emptyList()

    private val _uiState = MutableStateFlow(CounterUiState.Empty)
    val uiState: StateFlow<CounterUiState> = _uiState.asStateFlow()

    private val requestedStartingId = startingDhikrId
    private val requestedRoutineId = startingRoutineId

    // init { ... } block: unchanged from Task 6 — still calls
    // initializeSession() then wires the debounce pipeline and startTimer().

    private suspend fun initializeSession() {
        val savedSession = sessionRepository.sessionFlow.first()
        val routineIdToLoad = requestedRoutineId ?: savedSession?.routineId
        if (routineIdToLoad != null) {
            val routine = routineRepository.getWithSteps(routineIdToLoad)
            if (routine != null && routine.steps.isNotEmpty()) {
                activeRoutine = routine
                val sortedSteps = routine.steps.sortedBy { it.stepOrder }
                routineStepNames = sortedSteps.map { step ->
                    tasbihRepository.getById(step.tasbihId)?.name ?: step.tasbihId
                }
                routineStepIndex = (savedSession?.routineStep ?: 0).coerceIn(0, sortedSteps.lastIndex)
                val currentStep = sortedSteps[routineStepIndex]
                val stepTasbih = tasbihRepository.getById(currentStep.tasbihId)
                if (stepTasbih != null) {
                    dhikr = stepTasbih
                    engine = TasbihCounter(currentStep.targetCount, 1)
                    applyRestoredCountIfMatching(savedSession, currentStep.tasbihId)
                    _uiState.value = buildState()
                    return
                }
            }
        }
        // Not a routine (or the routine/step Tasbih couldn't be resolved —
        // fall through to plain single-Tasbih behavior rather than crashing).
        val idToLoad = savedSession?.activeDhikrId ?: requestedStartingId
        val loaded = idToLoad?.let { tasbihRepository.getById(it) }
            ?: tasbihRepository.observeAll().first().firstOrNull()
        if (loaded == null) return
        dhikr = loaded
        engine = TasbihCounter(dhikr.lapTarget, dhikr.lapCount)
        applyRestoredCountIfMatching(savedSession, loaded.id)
        _uiState.value = buildState()
    }

    private fun applyRestoredCountIfMatching(savedSession: CounterSessionState?, loadedTasbihId: String) {
        if (savedSession != null && savedSession.activeDhikrId == loadedTasbihId) {
            engine.restore(
                count = savedSession.count,
                lap = savedSession.lap,
                previous = if (savedSession.previousCount != null && savedSession.previousLap != null) {
                    savedSession.previousCount to savedSession.previousLap
                } else null,
            )
            locked = savedSession.locked
            elapsedSeconds = savedSession.elapsedSeconds
            if (!savedSession.running) engine.pause()
        }
    }

    fun onTap() {
        val snap = engine.increment()
        if (snap.isComplete && activeRoutine != null) {
            advanceRoutineStep()
            return
        }
        _uiState.value = buildState(justCompletedLap = snap.justCompletedLap)
    }

    private fun advanceRoutineStep() {
        val routine = activeRoutine ?: return
        val sortedSteps = routine.steps.sortedBy { it.stepOrder }
        val nextIndex = routineStepIndex + 1
        if (nextIndex > sortedSteps.lastIndex) {
            // Last step just completed — signal routine completion, no
            // interruption to the current display (the completion overlay is
            // a Compose-level dialog in CounterScreen, not a state reset here).
            _uiState.value = buildState().copy(isRoutineComplete = true)
            return
        }
        viewModelScope.launch {
            val nextStep = sortedSteps[nextIndex]
            val nextTasbih = tasbihRepository.getById(nextStep.tasbihId) ?: return@launch
            routineStepIndex = nextIndex
            dhikr = nextTasbih
            engine = TasbihCounter(nextStep.targetCount, 1)
            // elapsedSeconds is intentionally left as-is — the session timer
            // runs continuously across routine steps, it does not reset per step.
            _uiState.value = buildState()
        }
    }

    // onUndo()/onReset()/onTogglePause()/onToggleLock()/flushSession()/persist()/
    // onCleared(): unchanged from Task 6.

    class Factory(
        private val sessionRepository: SessionRepository,
        private val tasbihRepository: TasbihRepository,
        private val routineRepository: RoutineRepository,
        private val startingDhikrId: String? = null,
        private val startingRoutineId: String? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CounterViewModel(sessionRepository, tasbihRepository, routineRepository, startingDhikrId, startingRoutineId) as T
    }
}
```

This replaces Task 6's 3-parameter `Factory` entirely — `Factory` is redeclared
here with all 5 constructor parameters now that `routineRepository` and
`startingRoutineId` exist. (Task 11, below, extends this `Factory` once more to
add `historyRepository` — see that task's own note.)

Add `routineSteps`/`currentRoutineStepIndex`/`isRoutineComplete` to
`buildState()`'s `CounterUiState(...)` construction (all three added to
`CounterUiState` in Step 2 above), sourcing them from
`routineStepNames.mapIndexed { i, name -> RoutineStepDisplay(name,
sortedSteps[i].targetCount) }` (only when `activeRoutine != null`, else the empty
default) and `routineStepIndex`. Since `sortedSteps` is recomputed from
`activeRoutine?.steps?.sortedBy { it.stepOrder }` in more than one place above,
consider hoisting it to a small private property/function if the duplication
feels awkward — use your judgment, this is a minor internal-organization choice
that doesn't affect the public interface.

Each step's completion (both a normal `advanceRoutineStep()` call and the final
`isRoutineComplete = true` case) should also log a `SessionEntity` for that
step — Task 11 adds this call once `HistoryRepository` exists; this task's job is
only to get the state transitions correct.

Write this extension carefully against the actual current file content — do not
guess at exact current line numbers or field orderings; read the file, make the
minimal correct change preserving Task 6's structure (the `lateinit var
dhikr`/`engine` pattern, the `initializeSession()` suspend function, the
unchanged `onUndo()`/`onReset()`/etc. methods).

- [ ] **Step 4: Render the routine-chips row in `CounterScreen.kt`**

Read the current file (as modified by Task 6, unchanged structurally from Phase
1+2 otherwise) and add the chips row between the top bar and the tap area, per
design README §1.2: "horizontal row of pills, one per step, label `'<first word>
<count>'`. Current step is a sage fill with on-sage text; completed steps are
`surface` + `faint`; upcoming are `surface` + `dim`." Only render this row when
`state.routineSteps.isNotEmpty()`.

```kotlin
if (state.routineSteps.isNotEmpty()) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 8.dp),
    ) {
        state.routineSteps.forEachIndexed { index, step ->
            val isCurrent = index == state.currentRoutineStepIndex
            val isCompleted = index < state.currentRoutineStepIndex
            val bg = if (isCurrent) colors.sage else colors.surface
            val fg = when {
                isCurrent -> colors.onSage
                isCompleted -> colors.faint
                else -> colors.dim
            }
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(bg)
                    .padding(horizontal = 11.dp, vertical = 5.dp),
            ) {
                Text(
                    "${step.tasbihName.substringBefore(' ')} ${step.targetCount}",
                    fontSize = 11.5.sp,
                    color = fg,
                )
            }
        }
    }
}
```

Place this `if` block immediately after the top-bar `Row` and before the tap
area's `BoxWithConstraints`, matching the design's layout order (top bar → routine
chips → tap area).

- [ ] **Step 5: Add the "Routine complete" overlay dialog**

Per the design's Overlays table: "Routine complete | Last routine step completed
| Same dialog [as Goal reached], `Routine complete`, `Morning Dhikr finished — 100
counts in 08:12.`" Read the existing reset-confirmation `AlertDialog` usage in
`CounterScreen.kt` (from Phase 1+2) for the established dialog pattern in this
file, and add a second dialog gated on `state.isRoutineComplete`:

```kotlin
if (state.isRoutineComplete) {
    AlertDialog(
        onDismissRequest = { /* no-op: dismissal happens via the Done button only */ },
        title = { Text(stringResource(R.string.routine_complete_title)) },
        text = {
            Text(
                stringResource(
                    R.string.routine_complete_body,
                    state.dhikr.name, // the routine's own name isn't on CounterUiState yet — see note below
                    state.totalCount,
                    formatSessionLabel(state.elapsedSeconds, state.totalCount),
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { viewModel.onRoutineCompleteAcknowledged() }) {
                Text("Done")
            }
        },
    )
}
```

Two follow-ups this step must resolve, not leave open:
1. `routine_complete_body`'s first `%1$s` argument should be the routine's
   *name* ("Morning Dhikr"), not `state.dhikr.name` (which is the last step's
   Tasbih name, e.g. "Allahu Akbar") — `CounterUiState` needs a
   `routineName: String? = null` field (add it in Step 2 above, or here) sourced
   from `activeRoutine?.routine?.name` in `CounterViewModel.buildState()`. Use
   that field here instead of `state.dhikr.name`.
2. `viewModel.onRoutineCompleteAcknowledged()` doesn't exist yet in
   `CounterViewModel` — add it (in Step 3 above) as a method that resets
   `isRoutineComplete` to `false` in the UI state (dismissing the dialog) without
   otherwise touching the counter's current state, since the routine's steps are
   already all complete at this point — there's nothing left to reset within this
   routine run. This mirrors the "Goal reached" dialog's Phase-1+2-era Done
   button behavior conceptually but does not need to reset count/lap/timer the
   way a single-Tasbih goal-reached dismissal does, since a routine's last step
   simply stopped counting rather than needing a fresh restart.

Add `import androidx.compose.material3.AlertDialog` and
`androidx.compose.material3.TextButton` if not already imported in this file.

- [ ] **Step 6: Build to verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/counter/ app/src/main/res/values/strings.xml
git commit -m "Wire routine auto-advance and chips row into Counter screen"
```

---
## Task 11: SessionEntity, SessionDao, HistoryRepository, session logging wiring

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/database/entity/SessionEntity.kt`
- Create: `app/src/main/java/com/dhikr/app/core/database/dao/SessionDao.kt`
- Create: `app/src/main/java/com/dhikr/app/core/database/HistoryRepository.kt`
- Modify: `app/src/main/java/com/dhikr/app/core/database/AppDatabase.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/counter/CounterViewModel.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/counter/CounterScreen.kt`

**Interfaces:**
- Consumes: `TasbihEntity`, `RoutineEntity` (for FKs).
- Produces:
  ```kotlin
  @Entity(tableName = "session")
  data class SessionEntity(
      @PrimaryKey(autoGenerate = true) val id: Long = 0,
      val tasbihId: String,
      val routineId: String? = null,
      val count: Int,
      val startedAt: Long,
      val endedAt: Long,
  )

  class HistoryRepository(
      private val sessionDao: SessionDao,
      private val tasbihRepository: TasbihRepository,
  ) {
      suspend fun logSession(tasbihId: String, routineId: String?, count: Int, startedAt: Long, endedAt: Long)
      suspend fun todayTotal(): Int
      suspend fun weekTotal(): Int
      suspend fun monthTotal(): Int
      suspend fun allTimeTotal(): Int
      suspend fun last7DaysTotals(): List<Pair<String, Int>>
      suspend fun calendarIntensity(year: Int, month: Int): Map<Int, Int>
      suspend fun historyByTasbih(): List<TasbihHistoryGroup>
  }
  ```
  Consumed by Task 13 (Insights screen), Task 14 (Home's goal ring).

- [ ] **Step 1: Write `SessionEntity.kt`**

```kotlin
package com.dhikr.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "session",
    foreignKeys = [
        ForeignKey(
            entity = TasbihEntity::class,
            parentColumns = ["id"],
            childColumns = ["tasbihId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("tasbihId"), Index("startedAt")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tasbihId: String,
    val routineId: String? = null,
    val count: Int,
    val startedAt: Long,
    val endedAt: Long,
)
```

- [ ] **Step 2: Write `SessionDao.kt`**

Date-range queries use epoch millis boundaries computed by the caller
(`HistoryRepository`, Step 3) rather than SQL date functions, keeping the DAO's
SQL simple and the actual "what counts as today/this week" logic in Kotlin where
it's easier to reason about and adjust (e.g. for timezone handling).

```kotlin
package com.dhikr.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dhikr.app.core.database.entity.SessionEntity

data class TasbihDailyTotal(val tasbihId: String, val dayStartMillis: Long, val total: Int)

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity)

    @Query("SELECT COALESCE(SUM(count), 0) FROM session WHERE startedAt >= :sinceMillis")
    suspend fun totalSince(sinceMillis: Long): Int

    @Query(
        """
        SELECT tasbihId, (startedAt / :dayMillis) * :dayMillis AS dayStartMillis, SUM(count) AS total
        FROM session
        WHERE startedAt >= :sinceMillis
        GROUP BY tasbihId, dayStartMillis
        """
    )
    suspend fun dailyTotalsSince(sinceMillis: Long, dayMillis: Long): List<TasbihDailyTotal>

    @Query(
        """
        SELECT (startedAt / :dayMillis) * :dayMillis AS dayStartMillis, SUM(count) AS total
        FROM session
        WHERE startedAt >= :sinceMillis
        GROUP BY dayStartMillis
        """
    )
    suspend fun allTasbihDailyTotalsSince(sinceMillis: Long, dayMillis: Long): List<DayTotal>

    @Query("SELECT DISTINCT tasbihId FROM session")
    suspend fun distinctTasbihIds(): List<String>
}

data class DayTotal(val dayStartMillis: Long, val total: Int)
```

`(startedAt / :dayMillis) * :dayMillis` buckets each session's start time into
its containing day boundary using integer division — this is UTC-day bucketing,
not the device's local calendar day. This is a known simplification: for a
counting-habit app (not a scheduling app), a session logged at 11:58pm bucketing
into "today" vs "tomorrow" by UTC rather than local time is a minor edge case,
not a correctness-critical one. If precise local-calendar-day bucketing turns out
to matter, that's a straightforward follow-up (compute day boundaries in Kotlin
using `java.time.LocalDate`/`ZoneId.systemDefault()` instead of SQL integer
division) — not attempted here to keep this task's SQL simple, per the "prefer
lower complexity" guidance in plan.md §69.

- [ ] **Step 3: Write `HistoryRepository.kt`**

```kotlin
package com.dhikr.app.core.database

import com.dhikr.app.core.database.dao.SessionDao
import com.dhikr.app.core.database.entity.SessionEntity
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class TasbihHistoryGroup(val tasbihId: String, val tasbihName: String, val lifetimeTotal: Int, val dailyTotals: List<Pair<Long, Int>>)

class HistoryRepository(
    private val sessionDao: SessionDao,
    private val tasbihRepository: TasbihRepository,
) {
    private val dayMillis = TimeUnit.DAYS.toMillis(1)

    suspend fun logSession(tasbihId: String, routineId: String?, count: Int, startedAt: Long, endedAt: Long) {
        if (count <= 0) return // nothing to record — matches the spec's "resetting to 0 writes nothing"
        sessionDao.insert(
            SessionEntity(tasbihId = tasbihId, routineId = routineId, count = count, startedAt = startedAt, endedAt = endedAt)
        )
    }

    suspend fun todayTotal(): Int = sessionDao.totalSince(startOfTodayMillis())

    suspend fun weekTotal(): Int = sessionDao.totalSince(startOfTodayMillis() - 6 * dayMillis)

    suspend fun monthTotal(): Int = sessionDao.totalSince(startOfMonthMillis())

    suspend fun allTimeTotal(): Int = sessionDao.totalSince(0L)

    suspend fun last7DaysTotals(): List<Pair<String, Int>> {
        val since = startOfTodayMillis() - 6 * dayMillis
        val totals = sessionDao.allTasbihDailyTotalsSince(since, dayMillis).associateBy { it.dayStartMillis }
        val calendar = Calendar.getInstance().apply { timeInMillis = since }
        return (0..6).map { offset ->
            val dayStart = since + offset * dayMillis
            val label = SimpleDateFormatCache.weekdayFormat.format(java.util.Date(dayStart))
            label to (totals[dayStart]?.total ?: 0)
        }
    }

    suspend fun calendarIntensity(year: Int, month: Int): Map<Int, Int> {
        val calendar = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val monthStart = calendar.timeInMillis
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val totals = sessionDao.dailyTotalsSince(monthStart, dayMillis)
        val byDay = totals.groupBy { ((it.dayStartMillis - monthStart) / dayMillis).toInt() + 1 }
            .mapValues { (_, rows) -> rows.sumOf { it.total } }
        return (1..daysInMonth).associateWith { day -> byDay[day] ?: 0 }
    }

    suspend fun historyByTasbih(): List<TasbihHistoryGroup> {
        val since = startOfTodayMillis() - 6 * dayMillis
        val tasbihIds = sessionDao.distinctTasbihIds()
        return tasbihIds.mapNotNull { id ->
            val tasbih = tasbihRepository.getById(id) ?: return@mapNotNull null
            val lifetimeTotal = sessionDao.totalSince(0L) // NOTE: see fix note below
            val daily = sessionDao.dailyTotalsSince(since, dayMillis)
                .filter { it.tasbihId == id }
                .map { it.dayStartMillis to it.total }
            TasbihHistoryGroup(tasbihId = id, tasbihName = tasbih.name, lifetimeTotal = lifetimeTotal, dailyTotals = daily)
        }
    }

    private fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfMonthMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private object SimpleDateFormatCache {
    val weekdayFormat = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
}
```

**Real bug to fix before this task is done**: `historyByTasbih()`'s
`lifetimeTotal` is computed as `sessionDao.totalSince(0L)` inside the `mapNotNull`
loop — this sums **all Tasbih's** sessions, not just the current `id`'s, because
`totalSince()` has no `tasbihId` filter. Add a `tasbihId`-filtered variant to
`SessionDao` (Step 2) — e.g.
`@Query("SELECT COALESCE(SUM(count), 0) FROM session WHERE tasbihId = :tasbihId") suspend fun totalForTasbih(tasbihId: String): Int` —
and call that instead: `val lifetimeTotal = sessionDao.totalForTasbih(id)`. Do
not ship the draft above as written; this is a real correctness defect (every
Tasbih's "lifetime total" in the History view would show the same, wrong,
grand-total number), not a stylistic note.

`startOfTodayMillis()`/`startOfMonthMillis()` use `Calendar.getInstance()`, which
respects the device's default timezone/locale — this is intentionally different
from `SessionDao`'s UTC-bucketed daily-totals queries (Step 2's note); the
"today"/"this month" boundary the user sees on Insights should match their local
calendar day, even though the day-bucketing inside `last7DaysTotals()`/
`calendarIntensity()` uses UTC bucketing for SQL simplicity. This inconsistency
is a known, accepted simplification for this phase (not silently — flagging it
here) — a session near midnight could appear to fall on the "wrong" day in the
calendar/bar-chart views relative to the Today/This-week total cards. Acceptable
for a personal habit-tracking app at this phase; revisit only if it becomes a
real reported issue.

- [ ] **Step 4: Update `AppDatabase.kt`**

Add `SessionEntity::class` to `entities` and `abstract fun sessionDao():
SessionDao`.

- [ ] **Step 5: Wire session logging into `CounterViewModel.kt`**

Read the current file (as modified by Tasks 6 and 10) before editing. Add:

- Constructor: `historyRepository: HistoryRepository` (as the last parameter,
  after `startingRoutineId`). Update `Factory` (added by Task 10) to also take
  and forward `historyRepository` — it now takes 6 parameters total
  (`sessionRepository`, `tasbihRepository`, `routineRepository`,
  `startingDhikrId`, `startingRoutineId`, `historyRepository`) and passes all six
  through to the `CounterViewModel(...)` constructor call in `create()`.
- A `sessionStartedAtMillis: Long` field, set whenever a fresh session begins —
  i.e. inside `initializeSession()`, right after `dhikr`/`engine` are assigned (in
  both the routine and non-routine branches), set
  `sessionStartedAtMillis = System.currentTimeMillis()` UNLESS this is a restore
  of an already-in-progress session (in which case reuse
  `System.currentTimeMillis() - elapsedSeconds * 1000L` as an approximation of
  the original start time — exact original start time isn't tracked in
  `CounterSessionState`, and this approximation is good enough for a History
  row's `startedAt` field, which is display-only).
- A private suspend function:
  ```kotlin
  private suspend fun logCurrentSessionIfNonZero() {
      if (!::engine.isInitialized) return
      val snap = engine.snapshot()
      if (snap.count > 0) {
          historyRepository.logSession(
              tasbihId = dhikr.id,
              routineId = activeRoutine?.routine?.id,
              count = snap.count,
              startedAt = sessionStartedAtMillis,
              endedAt = System.currentTimeMillis(),
          )
      }
  }
  ```
- Call `logCurrentSessionIfNonZero()` from three places:
  1. In `onTap()`'s completion branch, for a plain (non-routine) Tasbih —
     immediately when `snap.isComplete` becomes true (before returning), since
     goal-reached is an unambiguous session-end signal per the spec.
  2. In `advanceRoutineStep()` (Task 10's addition), right before advancing to
     the next step or setting `isRoutineComplete = true` — each step's completion
     logs its own row with that step's `routineId`, per the spec.
  3. A new public method `fun logAndClearOnLeave()` that `CounterScreen.kt`
     calls when the composable leaves composition (Step 6 below) — this is the
     "navigated away with count > 0" path from the spec, distinct from the two
     completion-triggered calls above (which fire mid-session, before
     navigation).

  For call sites 1 and 2, after logging, reset `sessionStartedAtMillis =
  System.currentTimeMillis()` so a subsequent lap/step within the same screen
  visit doesn't get double-counted into the next logged session's duration.

- [ ] **Step 6: Call `logAndClearOnLeave()` from `CounterScreen.kt`'s navigation
      exit**

Read the current file (as modified by Task 10) before editing. The existing
`DisposableEffect(lifecycleOwner, viewModel) { ... }` block (from Phase 1+2) only
handles `ON_STOP`-triggered `flushSession()` — per this spec's "Session logging"
section, history logging must fire on the composable actually leaving
composition, which is a *different* trigger than `ON_STOP`. Add a second,
separate `DisposableEffect`:

```kotlin
DisposableEffect(viewModel) {
    onDispose {
        viewModel.logAndClearOnLeave()
    }
}
```

This fires when `CounterScreen` leaves composition (back navigation, navigating
to another destination) — not on `ON_STOP` from mere backgrounding, since the
composable stays in composition through an app-background/foreground cycle. Do
not merge this into the existing `ON_STOP` `DisposableEffect` — they trigger on
genuinely different lifecycle events and conflating them would reintroduce the
exact bug the spec calls out (logging a session every time the user glances
away).

- [ ] **Step 7: Build to verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/database/ app/src/main/java/com/dhikr/app/feature/counter/
git commit -m "Add SessionEntity, SessionDao, HistoryRepository, and session logging wiring"
```

---
## Task 12: Insights screen

**Files:**
- Create: `app/src/main/java/com/dhikr/app/feature/insights/InsightsViewModel.kt`
- Create: `app/src/main/java/com/dhikr/app/feature/insights/InsightsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `HistoryRepository` (Task 11).
- Produces:
  ```kotlin
  data class InsightsUiState(
      val today: Int = 0,
      val week: Int = 0,
      val month: Int = 0,
      val allTime: Int = 0,
      val last7Days: List<Pair<String, Int>> = emptyList(),
      val calendarIntensity: Map<Int, Int> = emptyMap(),
      val historyByTasbih: List<TasbihHistoryGroup> = emptyList(),
      val isEmpty: Boolean = true,
  )

  class InsightsViewModel(private val repository: HistoryRepository) : ViewModel() {
      val uiState: StateFlow<InsightsUiState>
  }

  @Composable
  fun InsightsScreen(viewModel: InsightsViewModel, onStartCounting: () -> Unit)
  ```
  Consumed by `DhikrApp.kt`'s nav graph (Task 14).

Per plan.md §49/spec's "Session logging" section, Insights reads are one-shot —
loaded once when the ViewModel is created, not continuously observed. If the user
wants a refresh after logging a new session, navigating back to Insights creates
a fresh ViewModel (default Compose Navigation behavior unless the destination is
explicitly retained), which re-triggers the one-shot load naturally.

- [ ] **Step 1: Add Insights strings to `strings.xml`**

```xml
<string name="insights_title">Insights</string>
<string name="insights_today">Today</string>
<string name="insights_this_week">This week</string>
<string name="insights_this_month">This month</string>
<string name="insights_all_time">All time</string>
<string name="insights_last_7_days">Last 7 days</string>
<string name="insights_consistency">Consistency</string>
<string name="insights_consistency_meta">%1$d days this month</string>
<string name="insights_legend_less">less</string>
<string name="insights_legend_more">more</string>
<string name="insights_history_title">History</string>
<string name="insights_history_grouped">grouped by Dhikr</string>
<string name="insights_empty_title">No sessions yet</string>
<string name="insights_empty_body">Counts appear here as soon as you finish your first session.</string>
<string name="insights_empty_cta">Start counting</string>
```

- [ ] **Step 2: Write `InsightsViewModel.kt`**

```kotlin
package com.dhikr.app.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.HistoryRepository
import com.dhikr.app.core.database.TasbihHistoryGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class InsightsUiState(
    val today: Int = 0,
    val week: Int = 0,
    val month: Int = 0,
    val allTime: Int = 0,
    val last7Days: List<Pair<String, Int>> = emptyList(),
    val calendarIntensity: Map<Int, Int> = emptyMap(),
    val historyByTasbih: List<TasbihHistoryGroup> = emptyList(),
    val isEmpty: Boolean = true,
)

class InsightsViewModel(private val repository: HistoryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val now = Calendar.getInstance()
            val today = repository.todayTotal()
            val week = repository.weekTotal()
            val month = repository.monthTotal()
            val allTime = repository.allTimeTotal()
            val last7Days = repository.last7DaysTotals()
            val calendar = repository.calendarIntensity(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
            val history = repository.historyByTasbih()
            _uiState.value = InsightsUiState(
                today = today,
                week = week,
                month = month,
                allTime = allTime,
                last7Days = last7Days,
                calendarIntensity = calendar,
                historyByTasbih = history,
                isEmpty = allTime == 0,
            )
        }
    }

    class Factory(private val repository: HistoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            InsightsViewModel(repository) as T
    }
}
```

- [ ] **Step 3: Write `InsightsScreen.kt`**

Layout per design README §6: header, 2×2 totals grid (`surface` tiles, uppercase
label over Caprasimo 24sp figure), last-7-days bar row (112dp tall `card`, 10dp-
radius sage bars with today in terracotta), consistency calendar (`card`, 7-column
grid of 9dp-radius square cells using a 4-step intensity ramp, legend row), history
grouped by Dhikr (one `card` per Dhikr with day rows and progress bars scaled
against 200 counts), and the empty state when `state.isEmpty`.

```kotlin
package com.dhikr.app.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.PillShape

@Composable
fun InsightsScreen(viewModel: InsightsViewModel, onStartCounting: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors

    if (state.isEmpty) {
        Column(
            modifier = Modifier.fillMaxSize().background(colors.bg).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.insights_empty_title), fontSize = 18.sp, color = colors.text)
            Text(
                stringResource(R.string.insights_empty_body),
                fontSize = 13.sp,
                color = colors.faint,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
            )
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(colors.sage)
                    .clickable { onStartCounting() }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.insights_empty_cta), color = colors.onSage)
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.insights_title), fontSize = 23.sp, color = colors.text)

        // Totals 2x2 grid
        Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TotalTile(stringResource(R.string.insights_today), state.today, Modifier.weight(1f))
            TotalTile(stringResource(R.string.insights_this_week), state.week, Modifier.weight(1f))
        }
        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TotalTile(stringResource(R.string.insights_this_month), state.month, Modifier.weight(1f))
            TotalTile(stringResource(R.string.insights_all_time), state.allTime, Modifier.weight(1f))
        }

        // Last 7 days
        Text(stringResource(R.string.insights_last_7_days), fontSize = 11.5.sp, color = colors.dim, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.card)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val maxValue = (state.last7Days.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
            state.last7Days.forEachIndexed { index, (label, value) ->
                val isToday = index == state.last7Days.lastIndex
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(value.toString(), fontSize = 10.5.sp, color = colors.dim)
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .width(18.dp)
                            .height((value.toFloat() / maxValue * 70).dp.coerceAtLeast(4.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isToday) colors.terra else colors.sage),
                    )
                    Text(label, fontSize = 10.5.sp, color = colors.faint, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        // Consistency calendar
        Text(stringResource(R.string.insights_consistency), fontSize = 11.5.sp, color = colors.dim, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.card)
                .padding(12.dp),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.height(160.dp),
            ) {
                items(state.calendarIntensity.entries.sortedBy { it.key }.size) { index ->
                    val (day, count) = state.calendarIntensity.entries.sortedBy { it.key }.toList()[index]
                    val intensity = when {
                        count == 0 -> colors.track
                        count < 33 -> colors.sageSoft
                        count < 100 -> colors.sage
                        else -> colors.sage // 4th ramp step reuses sage — see note below
                    }
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(intensity),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(day.toString(), fontSize = 10.sp, color = colors.text)
                    }
                }
            }
            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.insights_legend_less), fontSize = 10.sp, color = colors.faint)
                Row(modifier = Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(colors.track, colors.sageSoft, colors.sage, colors.sage).forEach { c ->
                        Box(modifier = Modifier.width(10.dp).height(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
                    }
                }
                Text(stringResource(R.string.insights_legend_more), fontSize = 10.sp, color = colors.faint)
            }
        }

        // History grouped by Dhikr
        Text(stringResource(R.string.insights_history_title), fontSize = 11.5.sp, color = colors.dim, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        state.historyByTasbih.forEach { group ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.card)
                    .padding(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(group.tasbihName, fontSize = 14.5.sp, color = colors.text)
                    Text(group.lifetimeTotal.toString(), fontSize = 14.5.sp, color = colors.terra)
                }
                group.dailyTotals.forEach { (_, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(7.dp)
                                .clip(PillShape)
                                .background(colors.track),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((count.toFloat() / 200f).coerceIn(0f, 1f))
                                    .height(7.dp)
                                    .clip(PillShape)
                                    .background(colors.sage),
                            )
                        }
                        Text(count.toString(), fontSize = 12sp, color = colors.dim, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalTile(label: String, value: Int, modifier: Modifier = Modifier) {
    val colors = DhikrTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .padding(14.dp),
    ) {
        Text(label.uppercase(), fontSize = 10.5.sp, color = colors.dim)
        Text(value.toString(), fontSize = 24.sp, color = colors.text, modifier = Modifier.padding(top = 4.dp))
    }
}
```

Fix the typo `12sp` → `12.sp` in the history day-row `Text` (a missing dot before
`sp` — this would fail to compile as written; correct it while implementing, it's
a straightforward typo, not a design decision). The 4-step intensity ramp's 3rd
and 4th steps both resolving to `colors.sage` in the draft above is also a real
gap — the design specifies 4 distinct visual steps (`track → sage-soft → sage →
terra` per the light-theme ramp described in `design/README.md`'s "Calendar
intensity ramp" line). Use `colors.terra` for the top intensity band (e.g.
`count >= 100`) instead of reusing `colors.sage` twice, so all 4 ramp steps are
visually distinct, matching the design's documented 4-color ramp exactly.

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/insights/ app/src/main/res/values/strings.xml
git commit -m "Add Insights screen (totals, 7-day bars, consistency calendar, history)"
```

---

## Task 13: Home screen for real

**Files:**
- Create: `app/src/main/java/com/dhikr/app/feature/home/HomeViewModel.kt`
- Create: `app/src/main/java/com/dhikr/app/feature/home/HomeScreen.kt`
- Delete: the `HomeStub` composable inside `app/src/main/java/com/dhikr/app/DhikrApp.kt`
  (moves to `HomeScreen.kt`; `DhikrApp.kt`'s nav graph wiring itself is updated in
  Task 14, not this task — this task only builds the replacement screen)
- Modify: `app/src/main/res/values/strings.xml`
- Modify: DataStore preferences — add `dailyGoalTarget` (see Step 2)

**Interfaces:**
- Consumes: `TasbihRepository` (Task 3), `RoutineRepository` (Task 8),
  `HistoryRepository` (Task 11), `SessionRepository` (existing, for the
  continue-session card).
- Produces:
  ```kotlin
  data class HomeUiState(
      val greeting: String = "",
      val dateLabel: String = "",
      val dailyGoalTarget: Int = 100,
      val todayTotal: Int = 0,
      val continueSession: ContinueSessionInfo? = null,
      val favorites: List<TasbihEntity> = emptyList(),
      val routines: List<RoutineWithSteps> = emptyList(),
  )

  data class ContinueSessionInfo(val tasbihName: String, val count: Int, val target: Int)

  class HomeViewModel(
      private val tasbihRepository: TasbihRepository,
      private val routineRepository: RoutineRepository,
      private val historyRepository: HistoryRepository,
      private val sessionRepository: SessionRepository,
  ) : ViewModel() {
      val uiState: StateFlow<HomeUiState>
  }

  @Composable
  fun HomeScreen(
      viewModel: HomeViewModel,
      onContinueSession: () -> Unit,
      onOpenTasbih: (String) -> Unit,
      onOpenLibrary: () -> Unit,
      onStartRoutine: (String) -> Unit,
      onOpenRoutines: () -> Unit,
  )
  ```
  Consumed by `DhikrApp.kt`'s nav graph (Task 14).

- [ ] **Step 1: Add Home strings to `strings.xml`**

```xml
<string name="home_greeting">Assalamu alaikum</string>
<string name="home_continue_session_kicker">CONTINUE SESSION</string>
<string name="home_favorites_title">Favourites</string>
<string name="home_favorites_all">All</string>
<string name="home_routines_title">Routines</string>
<string name="home_routines_manage">Manage</string>
```

(This task replaces the earlier Phase-1+2 `home_title`/`home_start_counter`
strings' *usage* — leave those two string entries in `strings.xml` even if
unused after this task, rather than hunting down and removing them; deleting
unused string resources is a cleanup task, not required here.)

- [ ] **Step 2: Add the `dailyGoalTarget` DataStore preference**

Per the spec, this is a single global preference with no UI to set it yet
(Settings is still a stub) — defaults to 100. Add this to wherever Phase 1+2's
existing preferences live; if no general-purpose "app preferences" DataStore
exists yet distinct from `core.datastore.SessionRepository` (which is specifically
for the live counter session, not general app preferences), create a small new
one:

```kotlin
package com.dhikr.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.preferencesDataStore by preferencesDataStore(name = "preferences")

class AppPreferencesRepository(private val context: Context) {
    private val dailyGoalKey = intPreferencesKey("daily_goal_target")

    val dailyGoalTarget = context.preferencesDataStore.data.map { it[dailyGoalKey] ?: 100 }

    suspend fun setDailyGoalTarget(value: Int) {
        context.preferencesDataStore.edit { it[dailyGoalKey] = value }
    }
}
```

Read-only consumption for this task (`HomeViewModel` reads `dailyGoalTarget` via
`.first()`, a one-shot read matching the rest of this phase's one-shot-load
pattern); `setDailyGoalTarget()` exists for a later Settings-screen phase to call
— not exercised by any UI in this phase.

- [ ] **Step 3: Write `HomeViewModel.kt`**

```kotlin
package com.dhikr.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.HistoryRepository
import com.dhikr.app.core.database.RoutineRepository
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.database.dao.RoutineWithSteps
import com.dhikr.app.core.database.entity.TasbihEntity
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.datastore.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ContinueSessionInfo(val tasbihName: String, val count: Int, val target: Int)

data class HomeUiState(
    val greeting: String = "",
    val dateLabel: String = "",
    val dailyGoalTarget: Int = 100,
    val todayTotal: Int = 0,
    val continueSession: ContinueSessionInfo? = null,
    val favorites: List<TasbihEntity> = emptyList(),
    val routines: List<RoutineWithSteps> = emptyList(),
)

class HomeViewModel(
    private val tasbihRepository: TasbihRepository,
    private val routineRepository: RoutineRepository,
    private val historyRepository: HistoryRepository,
    private val sessionRepository: SessionRepository,
    private val preferencesRepository: AppPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val dailyGoal = preferencesRepository.dailyGoalTarget.first()
            val todayTotal = historyRepository.todayTotal()
            val favorites = tasbihRepository.observeFavorites().first()
            val routines = routineRepository.observeAllWithSteps().first().take(3)
            val session = sessionRepository.sessionFlow.first()
            val continueInfo = session?.let { s ->
                tasbihRepository.getById(s.activeDhikrId)?.let { tasbih ->
                    ContinueSessionInfo(tasbihName = tasbih.name, count = s.count, target = tasbih.lapTarget)
                }
            }
            _uiState.value = HomeUiState(
                greeting = "Assalamu alaikum",
                dateLabel = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()),
                dailyGoalTarget = dailyGoal,
                todayTotal = todayTotal,
                continueSession = continueInfo,
                favorites = favorites,
                routines = routines,
            )
        }
    }

    class Factory(
        private val tasbihRepository: TasbihRepository,
        private val routineRepository: RoutineRepository,
        private val historyRepository: HistoryRepository,
        private val sessionRepository: SessionRepository,
        private val preferencesRepository: AppPreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(tasbihRepository, routineRepository, historyRepository, sessionRepository, preferencesRepository) as T
    }
}
```

The `greeting` field hardcodes `"Assalamu alaikum"` in Kotlin rather than reading
it from `strings.xml`'s `home_greeting` — this is a real inconsistency with this
plan's own "all UI strings go through resources" Global Constraint. Fix it: read
via `stringResource(R.string.home_greeting)` in `HomeScreen.kt` (Step 4) instead
of embedding the literal in the ViewModel — remove the `greeting` field from
`HomeUiState`/`HomeViewModel` entirely, since a ViewModel shouldn't hold a
resource-derived string anyway (no `Context`/`Resources` access in a plain
`ViewModel` without extra plumbing); let `HomeScreen` call `stringResource(...)`
directly where the greeting is displayed. `dateLabel`, by contrast, is genuinely
computed data (today's date), not a translatable UI label, so it's fine for the
ViewModel to compute it via `SimpleDateFormat` as shown.

- [ ] **Step 4: Write `HomeScreen.kt`**

Layout per design README §2: greeting block (Caprasimo 24sp greeting +
12.5sp dim date) with a 74dp day-goal ring on the right; Continue-session card
(`sage-soft` fill, 28dp radius) shown only when `state.continueSession != null`;
Favourites section (section label + terracotta "All" link, `card`-filled 22dp
pill rows); Routines section (section label + "Manage" link, three equal-width
`surface` tiles).

```kotlin
package com.dhikr.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.core.database.entity.TasbihEntity
import com.dhikr.app.ui.theme.DhikrTheme
import kotlin.math.min

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onContinueSession: () -> Unit,
    onOpenTasbih: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onStartRoutine: (String) -> Unit,
    onOpenRoutines: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Greeting + goal ring
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(stringResource(R.string.home_greeting), fontSize = 24.sp, color = colors.text)
                Text(state.dateLabel, fontSize = 12.5.sp, color = colors.dim)
            }
            GoalRing(progress = if (state.dailyGoalTarget > 0) state.todayTotal.toFloat() / state.dailyGoalTarget else 0f)
        }

        // Continue session
        state.continueSession?.let { info ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.sageSoft)
                    .clickable { onContinueSession() }
                    .padding(16.dp),
            ) {
                Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(colors.sage))
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("CONTINUE SESSION", fontSize = 10.sp, color = colors.dim)
                    Text(info.tasbihName, fontSize = 14.5.sp, color = colors.text)
                }
                Text("${info.count}/${info.target}", fontSize = 13.sp, color = colors.dim)
            }
        }

        // Favourites
        Column {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_favorites_title), fontSize = 11.5.sp, color = colors.dim)
                Text(
                    stringResource(R.string.home_favorites_all),
                    fontSize = 11.5.sp,
                    color = colors.terra,
                    modifier = Modifier.clickable { onOpenLibrary() },
                )
            }
            state.favorites.forEach { tasbih ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.card)
                        .clickable { onOpenTasbih(tasbih.id) }
                        .padding(14.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tasbih.name, fontSize = 14.5.sp, color = colors.text)
                        Text(tasbih.transliteration, fontSize = 12.sp, color = colors.faint, maxLines = 1)
                    }
                    Text(tasbih.arabic, fontSize = 14.sp, color = colors.dim)
                }
            }
        }

        // Routines
        Column {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_routines_title), fontSize = 11.5.sp, color = colors.dim)
                Text(
                    stringResource(R.string.home_routines_manage),
                    fontSize = 11.5.sp,
                    color = colors.terra,
                    modifier = Modifier.clickable { onOpenRoutines() },
                )
            }
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.routines.forEach { routineWithSteps ->
                    val totalCount = routineWithSteps.steps.sumOf { it.targetCount }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .clickable { onStartRoutine(routineWithSteps.routine.id) }
                            .padding(12.dp),
                    ) {
                        Text(routineWithSteps.routine.name, fontSize = 13.sp, color = colors.text, maxLines = 2)
                        Text(
                            "${routineWithSteps.steps.size} steps · $totalCount counts",
                            fontSize = 10.5.sp,
                            color = colors.dim,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalRing(progress: Float) {
    val colors = DhikrTheme.colors
    Box(modifier = Modifier.size(74.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 7.dp.toPx()
            val inset = strokeWidth / 2
            drawArc(
                color = colors.track,
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = colors.terra,
                startAngle = -90f, sweepAngle = 360f * min(1f, progress), useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Text("${(min(1f, progress) * 100).toInt()}%", fontSize = 15.sp, color = colors.text)
    }
}
```

- [ ] **Step 5: Build to verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/home/ app/src/main/java/com/dhikr/app/core/datastore/AppPreferencesRepository.kt app/src/main/res/values/strings.xml
git commit -m "Add real Home screen (goal ring, continue session, favorites, routines)"
```

---

## Task 14: Wire all screens + bottom navigation into DhikrApp.kt

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/DhikrApp.kt` (substantial rewrite)

**Interfaces:**
- Consumes: every ViewModel/Screen produced by Tasks 4, 5, 6, 9, 10, 12, 13.
- Produces: the final nav graph — `DhikrApp()`'s public signature is unchanged
  (`@Composable fun DhikrApp()`, no parameters).

This is the integration task that ties every previous task's screen into one
navigable app with the persistent 5-item bottom nav (Home · Tasbih · Count ·
Insights · Settings) per design README's "Bottom navigation" section. Settings
remains a stub destination (its content is out of scope this phase).

- [ ] **Step 1: Read the current `DhikrApp.kt` in full**

This file has been touched by Tasks 6 and 8 already (repository construction,
`TasbihRepository`'s constructor gaining `routineDao`) — read its current state
before rewriting, don't assume Phase 1+2's original shape.

- [ ] **Step 2: Rewrite `DhikrApp.kt`**

Structure: a `Scaffold` with a `bottomBar` (per design's persistent nav) wrapping
a `NavHost`. Repositories are constructed once at the top level via `remember`
(reusing the pattern already established for `SessionRepository`/
`TasbihRepository` from earlier tasks) and passed down to each destination's
ViewModel factory.

```kotlin
package com.dhikr.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dhikr.app.core.database.HistoryRepository
import com.dhikr.app.core.database.RoutineRepository
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.datastore.SessionRepository
import com.dhikr.app.feature.counter.CounterScreen
import com.dhikr.app.feature.counter.CounterViewModel
import com.dhikr.app.feature.home.HomeScreen
import com.dhikr.app.feature.home.HomeViewModel
import com.dhikr.app.feature.insights.InsightsScreen
import com.dhikr.app.feature.insights.InsightsViewModel
import com.dhikr.app.feature.routines.RoutinesScreen
import com.dhikr.app.feature.routines.RoutinesViewModel
import com.dhikr.app.feature.tasbih.TasbihEditorScreen
import com.dhikr.app.feature.tasbih.TasbihEditorViewModel
import com.dhikr.app.feature.tasbih.TasbihLibraryScreen
import com.dhikr.app.feature.tasbih.TasbihLibraryViewModel
import com.dhikr.app.ui.theme.DhikrTheme

private const val ROUTE_HOME = "home"
private const val ROUTE_TASBIH_LIBRARY = "tasbih"
private const val ROUTE_TASBIH_EDITOR = "tasbih/editor?id={id}"
private const val ROUTE_COUNTER = "counter?dhikrId={dhikrId}&routineId={routineId}"
private const val ROUTE_INSIGHTS = "insights"
private const val ROUTE_ROUTINES = "routines"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun DhikrApp() {
    DhikrTheme {
        val navController = rememberNavController()
        val context = LocalContext.current
        val app = context.applicationContext as DhikrApplication

        val sessionRepository = remember { SessionRepository(context.applicationContext) }
        val tasbihRepository = remember { TasbihRepository(app.database.tasbihDao(), app.database.routineDao()) }
        val routineRepository = remember { RoutineRepository(app.database.routineDao()) }
        val historyRepository = remember { HistoryRepository(app.database.sessionDao(), tasbihRepository) }
        val preferencesRepository = remember { AppPreferencesRepository(context.applicationContext) }

        Scaffold(
            bottomBar = { DhikrBottomNav(navController) },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = ROUTE_HOME,
                modifier = Modifier.padding(padding),
            ) {
                composable(ROUTE_HOME) {
                    val viewModel: HomeViewModel = viewModel(
                        factory = HomeViewModel.Factory(tasbihRepository, routineRepository, historyRepository, sessionRepository, preferencesRepository),
                    )
                    HomeScreen(
                        viewModel = viewModel,
                        onContinueSession = { navController.navigate("counter") },
                        onOpenTasbih = { id -> navController.navigate("counter?dhikrId=$id") },
                        onOpenLibrary = { navController.navigate(ROUTE_TASBIH_LIBRARY) },
                        onStartRoutine = { id -> navController.navigate("counter?routineId=$id") },
                        onOpenRoutines = { navController.navigate(ROUTE_ROUTINES) },
                    )
                }
                composable(ROUTE_TASBIH_LIBRARY) {
                    val viewModel: TasbihLibraryViewModel = viewModel(
                        factory = TasbihLibraryViewModel.Factory(tasbihRepository),
                    )
                    TasbihLibraryScreen(
                        viewModel = viewModel,
                        onOpenTasbih = { id -> navController.navigate("counter?dhikrId=$id") },
                        onNewTasbih = { navController.navigate("tasbih/editor") },
                    )
                }
                composable(
                    ROUTE_TASBIH_EDITOR,
                    arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { backStackEntry ->
                    val editingId = backStackEntry.arguments?.getString("id")
                    val viewModel: TasbihEditorViewModel = viewModel(
                        factory = TasbihEditorViewModel.Factory(tasbihRepository, editingId),
                    )
                    TasbihEditorScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
                composable(
                    ROUTE_COUNTER,
                    arguments = listOf(
                        navArgument("dhikrId") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("routineId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { backStackEntry ->
                    val dhikrId = backStackEntry.arguments?.getString("dhikrId")
                    val routineId = backStackEntry.arguments?.getString("routineId")
                    val viewModel: CounterViewModel = viewModel(
                        factory = CounterViewModel.Factory(
                            sessionRepository, tasbihRepository, routineRepository,
                            dhikrId, routineId, historyRepository,
                        ),
                    )
                    CounterScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
                composable(ROUTE_INSIGHTS) {
                    val viewModel: InsightsViewModel = viewModel(
                        factory = InsightsViewModel.Factory(historyRepository),
                    )
                    InsightsScreen(viewModel = viewModel, onStartCounting = { navController.navigate("counter") })
                }
                composable(ROUTE_ROUTINES) {
                    val viewModel: RoutinesViewModel = viewModel(
                        factory = RoutinesViewModel.Factory(routineRepository),
                    )
                    RoutinesScreen(
                        viewModel = viewModel,
                        onStartRoutine = { id -> navController.navigate("counter?routineId=$id") },
                        onEditRoutine = { /* Step 3 note: no dedicated route yet — see below */ },
                        onNewRoutine = { /* Step 3 note: no dedicated route yet — see below */ },
                    )
                }
                composable(ROUTE_SETTINGS) {
                    SettingsStub()
                }
            }
        }
    }
}

@Composable
private fun SettingsStub() {
    val colors = DhikrTheme.colors
    Text("Settings", modifier = Modifier.background(colors.bg).padding(24.dp), color = colors.text)
}
```

**Real gap to resolve, not leave as a stub comment**: Task 9's `RoutinesScreen`
signature includes `onEditRoutine: (String) -> Unit` and `onNewRoutine: () ->
Unit`, but this draft's `RoutinesScreen(...)` call wires both to empty lambdas
with a "no dedicated route yet" comment — that's incomplete. Task 9's spec
described inline step-editing (add/remove/reorder within the routine card
itself, not a separate full-screen editor) — reconcile this: either (a)
`RoutinesScreen`'s "edit" interaction is actually handled entirely within
`RoutinesScreen.kt` itself via local composable state (an expanded/editing mode
per-card), in which case `onEditRoutine`/`onNewRoutine` as separate navigation
callbacks shouldn't exist on the signature at all — revisit Task 9's screen
signature and remove them if editing is fully in-screen; or (b) a genuine
lightweight editor destination is needed, in which case add a
`ROUTE_ROUTINE_EDITOR` route here wired properly. Decide and implement one of
these completely in this task — do not leave `onEditRoutine`/`onNewRoutine`
wired to no-op lambdas as the final state.

`onContinueSession`/`onStartCounting`/`onStartRoutine` all navigate to
`"counter"` variants without clearing the back stack — tapping back from the
Counter screen returns to Home/Insights/Routines respectively, which matches
normal Android back-navigation expectations and needs no special `popUpTo`
handling.

- [ ] **Step 3: Write `DhikrBottomNav`**

Per design README's "Bottom navigation" section: `surface` background, 1px `line`
top border, 5 items (Home · Tasbih · Count · Insights · Settings), active item
gets a 52×28dp `sage-soft` pill behind the icon, inactive items are `faint`.

```kotlin
@Composable
private fun DhikrBottomNav(navController: androidx.navigation.NavController) {
    val colors = DhikrTheme.colors
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val items = listOf(
        Triple(ROUTE_HOME, "Home", android.R.drawable.ic_menu_myplaces), // placeholder icons — see note
        Triple(ROUTE_TASBIH_LIBRARY, "Tasbih", android.R.drawable.ic_menu_agenda),
        Triple(ROUTE_COUNTER, "Count", android.R.drawable.ic_menu_add),
        Triple(ROUTE_INSIGHTS, "Insights", android.R.drawable.ic_menu_sort_by_size),
        Triple(ROUTE_SETTINGS, "Settings", android.R.drawable.ic_menu_preferences),
    )

    androidx.compose.material3.NavigationBar(containerColor = colors.surface) {
        items.forEach { (route, label, iconRes) ->
            val selected = currentRoute?.startsWith(route.substringBefore("?")) == true
            androidx.compose.material3.NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) navController.navigate(route.substringBefore("?")) },
                icon = { androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(iconRes), contentDescription = label) },
                label = { Text(label, fontSize = 10.5.sp) },
                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.text,
                    selectedTextColor = colors.text,
                    indicatorColor = colors.sageSoft,
                    unselectedIconColor = colors.faint,
                    unselectedTextColor = colors.faint,
                ),
            )
        }
    }
}
```

Using Android's built-in placeholder drawables (`android.R.drawable.*`) for the 5
nav icons rather than building 5 more custom `ImageVector`s (per Phase 1+2's
`CounterIcons.kt` pattern) — the design specifies Lucide-style stroke icons for
these, matching Phase 1+2's approach used for the Counter screen's icons. Given
this task's scope is already large, using placeholder system icons here is an
explicit, flagged simplification, not a silent shortcut — a future pass should
replace these 5 with proper stroke-style vectors matching `CounterIcons.kt`'s
pattern (back chevron/undo/reset/lock) for visual consistency. Do not spend this
task's effort hand-deriving 5 new icon paths; flag this instead and move on.

`selected = currentRoute?.startsWith(route.substringBefore("?")) == true` handles
the Counter route's query-parameter suffix (`counter?dhikrId=...&routineId=...`)
matching against the base `"counter"` nav item.

Also import `androidx.compose.ui.unit.sp` if not already present in this file
from earlier edits.

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. This is the largest integration point in the plan —
expect several import/constructor-argument mismatches surfacing here as each
earlier task's exact final signature gets consumed together for the first time;
resolve them by checking each referenced file's actual current signature, not by
guessing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dhikr/app/DhikrApp.kt
git commit -m "Wire all Phase 3 screens and bottom navigation into DhikrApp"
```

---

## Task 15: Final full-project build verification

**Files:** none (verification-only task)

**Interfaces:** none

- [ ] **Step 1: Clean build**

Run: `./gradlew clean assembleDebug`
Expected: `BUILD SUCCESSFUL`, with an APK produced at
`app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 2: Lint check (non-blocking informational)**

Run: `./gradlew lintDebug`
Expected: completes; only stop and report to the user if lint reports an actual
ERROR, not a warning.

- [ ] **Step 3: Report status to the user**

State plainly whether the build succeeded. Do not run the app, take screenshots,
or attempt any behavioral verification — per this plan's Global Constraints,
verification is build-only and the user tests manually.

No commit needed for this task (verification only, no file changes).
