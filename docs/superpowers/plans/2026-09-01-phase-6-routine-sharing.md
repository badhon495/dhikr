# Phase 6 — Routine Sharing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user send routines to another user with no server — a `.dhikrroutine` file via the Android share sheet, or a one-line copy-paste text blob — and import them back as fresh custom routines.

**Architecture:** Pure/impure split mirroring `core/counter/WidgetCounter`. All format, validation, and merge-decision logic lives in pure Kotlin objects (`RoutineShareCodec`, `RoutineShareBuilder`, `RoutineImportPlanner`) with JVM unit tests. `RoutineShareRepository` is the one impure unit: it reads DAOs, calls the pure functions, applies the result in one `withTransaction`. Two ViewModels + one screen + Android share-sheet / intent-filter / FileProvider wiring sit on top.

**Tech Stack:** Kotlin, kotlinx.serialization, Room, Jetpack Compose, Navigation-Compose, `java.util.zip` (gzip), `android.util.Base64` (behind a port). No new Gradle dependency.

**Spec:** `docs/superpowers/specs/2026-09-01-phase-6-routine-sharing-design.md` — read it alongside this plan.

## Global Constraints

- **No new Gradle dependency.** gzip via `java.util.zip.GZIPOutputStream` / `GZIPInputStream`; base64 via `android.util.Base64` reached only through `Base64Port`; `FileProvider` from `androidx.core` (already present).
- **New package:** `com.dhikr.app.core.share` for all format/logic units; ViewModels + screen go in `com.dhikr.app.feature.routines`.
- **Payload `format` string is `"dhikr.routine"`, `version` is `1`, text prefix is exactly `"DHIKR-ROUTINE-v1:"`.** The share format is independent of the backup format — separate DTOs, separate parser, neither accepts the other's `format` string.
- **Never serialized:** routine `id` / `createdAt` / `updatedAt` / `isPreset` / `isFavorite` / all reminder fields; `RoutineStepEntity.stepId`; `TasbihEntity.isBuiltIn` / `createdAt` / `updatedAt` / `isFavorite`.
- **`tasbih[]` in the payload holds a definition for every custom (`isBuiltIn = 0`) tasbih referenced by any step, and nothing else.** Built-in tasbih are referenced by bare id (recipient has the same seed data). Bundled tasbih keep their **original id**.
- **JSON config:** `ignoreUnknownKeys = true`, `encodeDefaults = true`; `prettyPrint = true` for the file form only.
- **Imported routines are always fresh:** new `UUID` id, `isPreset = false`, `isFavorite = false`, reminders off, `createdAt = updatedAt = now`. Re-import intentionally makes a new copy — no routine-level merge, no dedupe.
- **Bundled tasbih apply insert-vs-reuse by id:** id already in the `tasbih` table → leave the existing row untouched (count reused); else insert custom with fresh timestamps (count added).
- **Nothing in the import path writes to the DB until `confirm()`, and that write is a single `database.withTransaction { }`.**
- **User-facing error strings (exact):**
  - `"This isn't a Dhikr routine file."` — prefix / base64 / gzip / JSON / `format` mismatch.
  - `"This routine was shared from a newer version of the app."` — `version` > `SHARE_VERSION`.
  - `"This shared file is incomplete."` — empty `routines`, blank name, `targetCount < 1`, or a step tasbih neither present nor bundled.
  - `"Couldn't read that file."` — `openInputStream` null / throws.
  - `"Import failed. Your routines haven't changed."` — exception inside the Room transaction.
  - `"Couldn't prepare the routines to share."` — share-side build / FileProvider failure.
- **Verify step is a Gradle build** (`./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest`), not an emulator run. Run the app only if explicitly asked.
- **Tests:** JVM unit tests only, in `app/src/test/`, JUnit 4 + `kotlinx-coroutines-test` (already on the classpath). No `androidTest` source set. `RoutineShareRepository` gets **no** automated test — it is covered by the pure tests beneath it plus a manual two-device smoke.

---

### Task 1: Share models

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/share/RoutineShareModels.kt`
- Test: `app/src/test/java/com/dhikr/app/core/share/RoutineShareModelsTest.kt`

**Interfaces:**
- Consumes: `RoutineEntity`, `RoutineStepEntity`, `TasbihEntity` from `com.dhikr.app.core.database.entity` (same cross-package use as `core/backup/BackupModels.kt`).
- Produces:
  - `const val SHARE_FORMAT = "dhikr.routine"`, `const val SHARE_VERSION = 1`, `const val SHARE_TEXT_PREFIX = "DHIKR-ROUTINE-v1:"`
  - `internal const val MSG_NOT_OURS`, `MSG_NEWER`, `MSG_INCOMPLETE` (exact strings from Global Constraints)
  - `class ShareFormatException(message: String) : Exception(message)`
  - `@Serializable data class RoutineShareFile(val format: String, val version: Int, val createdAt: Long = 0L, val appVersionName: String = "", val routines: List<ShareRoutine> = emptyList(), val tasbih: List<ShareTasbih> = emptyList())`
  - `@Serializable data class ShareRoutine(val name: String, val steps: List<ShareRoutineStep> = emptyList())`
  - `@Serializable data class ShareRoutineStep(val tasbihId: String, val stepOrder: Int, val targetCount: Int)`
  - `@Serializable data class ShareTasbih(val id: String, val name: String, val arabic: String, val pronunciation: String = "", val translation: String = "", val note: String = "", val source: String? = null, val lapTarget: Int, val lapCount: Int, val dailyGoal: Int? = null)`
  - `data class ShareImportResult(val routinesImported: Int, val tasbihAdded: Int, val tasbihReused: Int)`
  - `data class ImportPlan(val routineInserts: List<RoutineEntity>, val stepInserts: List<RoutineStepEntity>, val tasbihInserts: List<TasbihEntity>, val result: ShareImportResult)`
  - `data class ImportPreview(val routines: List<PreviewRoutine>, val newTasbihCount: Int)`
  - `data class PreviewRoutine(val name: String, val steps: List<PreviewStep>)`
  - `data class PreviewStep(val tasbihName: String, val targetCount: Int)`

Note: `format` and `version` have **no default** — a JSON object missing either fails to deserialize, which the codec turns into `ShareFormatException`. This is why `{}` is rejected.

- [x] **Step 1: Write the failing test**

`app/src/test/java/com/dhikr/app/core/share/RoutineShareModelsTest.kt`:

```kotlin
package com.dhikr.app.core.share

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.serialization.json.Json

class RoutineShareModelsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun sample() = RoutineShareFile(
        format = SHARE_FORMAT,
        version = SHARE_VERSION,
        createdAt = 1_726_000_000_000L,
        appVersionName = "1.0",
        routines = listOf(
            ShareRoutine(
                name = "Morning Dhikr",
                steps = listOf(
                    ShareRoutineStep(tasbihId = "subhan", stepOrder = 0, targetCount = 33),
                    ShareRoutineStep(tasbihId = "custom-1", stepOrder = 1, targetCount = 10),
                ),
            ),
        ),
        tasbih = listOf(
            ShareTasbih(
                id = "custom-1", name = "My Dhikr", arabic = "x", pronunciation = "y",
                translation = "z", note = "n", source = "s", lapTarget = 10, lapCount = 1,
                dailyGoal = 100,
            ),
        ),
    )

    @Test
    fun roundTrip_isLossless() {
        val text = json.encodeToString(RoutineShareFile.serializer(), sample())
        val back = json.decodeFromString(RoutineShareFile.serializer(), text)
        assertEquals(sample(), back)
    }

    @Test
    fun unknownKey_deserializesWithoutThrowing() {
        val text = """
            {"format":"dhikr.routine","version":1,"createdAt":0,"appVersionName":"",
             "routines":[{"name":"R","steps":[]}],"tasbih":[],"somethingNew":42}
        """.trimIndent()
        val back = json.decodeFromString(RoutineShareFile.serializer(), text)
        assertEquals("R", back.routines.single().name)
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.share.RoutineShareModelsTest"`
Expected: FAIL — `RoutineShareFile` unresolved.

- [x] **Step 3: Write the models file**

`app/src/main/java/com/dhikr/app/core/share/RoutineShareModels.kt`:

```kotlin
package com.dhikr.app.core.share

import com.dhikr.app.core.database.entity.RoutineEntity
import com.dhikr.app.core.database.entity.RoutineStepEntity
import com.dhikr.app.core.database.entity.TasbihEntity
import kotlinx.serialization.Serializable

/** The share format is independent of the backup format: separate DTOs, separate
 *  parser, and neither parser accepts the other's `format` string. Bump
 *  [SHARE_VERSION] when the payload shape changes incompatibly. */
const val SHARE_FORMAT = "dhikr.routine"
const val SHARE_VERSION = 1

/** Guards a future incompatible text envelope; the JSON `version` still guards
 *  the payload shape. Decode requires this exact prefix. */
const val SHARE_TEXT_PREFIX = "DHIKR-ROUTINE-v1:"

internal const val MSG_NOT_OURS = "This isn't a Dhikr routine file."
internal const val MSG_NEWER = "This routine was shared from a newer version of the app."
internal const val MSG_INCOMPLETE = "This shared file is incomplete."

/** Thrown by the codec / planner when a payload can't be read or applied. The
 *  message is safe to show to the user. */
class ShareFormatException(message: String) : Exception(message)

/** `format` / `version` have no default so a payload missing either fails to
 *  deserialize — the codec turns that into a [ShareFormatException]. */
@Serializable
data class RoutineShareFile(
    val format: String,
    val version: Int,
    val createdAt: Long = 0L,
    val appVersionName: String = "",
    val routines: List<ShareRoutine> = emptyList(),
    val tasbih: List<ShareTasbih> = emptyList(),
)

@Serializable
data class ShareRoutine(
    val name: String,
    val steps: List<ShareRoutineStep> = emptyList(),
)

@Serializable
data class ShareRoutineStep(
    val tasbihId: String,
    val stepOrder: Int,
    val targetCount: Int,
)

/** A bundled custom-tasbih definition. Keeps its original [id] so re-import, or
 *  two shares referencing the same custom tasbih, dedupe by identity. */
@Serializable
data class ShareTasbih(
    val id: String,
    val name: String,
    val arabic: String,
    val pronunciation: String = "",
    val translation: String = "",
    val note: String = "",
    val source: String? = null,
    val lapTarget: Int,
    val lapCount: Int,
    val dailyGoal: Int? = null,
)

/** Summary of what an import actually wrote, surfaced to the user. */
data class ShareImportResult(
    val routinesImported: Int,
    val tasbihAdded: Int,
    val tasbihReused: Int,
)

/** The planner's output: the exact rows to insert, plus the result summary.
 *  [RoutineShareRepository] applies these in one transaction. */
data class ImportPlan(
    val routineInserts: List<RoutineEntity>,
    val stepInserts: List<RoutineStepEntity>,
    val tasbihInserts: List<TasbihEntity>,
    val result: ShareImportResult,
)

/** What the import preview screen shows before the user confirms. */
data class ImportPreview(
    val routines: List<PreviewRoutine>,
    val newTasbihCount: Int,
)

data class PreviewRoutine(val name: String, val steps: List<PreviewStep>)

data class PreviewStep(val tasbihName: String, val targetCount: Int)
```

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.share.RoutineShareModelsTest"`
Expected: PASS (2 tests).

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/share/RoutineShareModels.kt app/src/test/java/com/dhikr/app/core/share/RoutineShareModelsTest.kt
git commit -m "feat: add routine-share payload models"
```

---

### Task 2: Base64 port

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/share/Base64Port.kt`

**Interfaces:**
- Produces:
  - `interface Base64Port { fun encode(bytes: ByteArray): String; fun decode(text: String): ByteArray }`
  - `object AndroidBase64 : Base64Port` — wraps `android.util.Base64` (`NO_WRAP` on encode, `DEFAULT` on decode).

No test: this file is a three-line platform wrapper. It is kept separate precisely so the codec (Task 3) can be tested with a `java.util.Base64`-backed double. `android.util.Base64` is stubbed to return-default-values in unit tests (`testOptions.unitTests.isReturnDefaultValues = true`), so calling `AndroidBase64` from a test would silently return junk — the codec test must inject its own port.

- [x] **Step 1: Write the file**

`app/src/main/java/com/dhikr/app/core/share/Base64Port.kt`:

```kotlin
package com.dhikr.app.core.share

import android.util.Base64

/** Base64 codec seam. The real app uses [AndroidBase64]; the codec's unit tests
 *  inject a `java.util.Base64`-backed double so no `android.util.*` is touched. */
interface Base64Port {
    fun encode(bytes: ByteArray): String
    fun decode(text: String): ByteArray
}

/** `NO_WRAP` on encode (single-line output for the text-share form); `DEFAULT`
 *  on decode (tolerates line wrapping a chat app may have inserted). */
object AndroidBase64 : Base64Port {
    override fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    override fun decode(text: String): ByteArray = Base64.decode(text, Base64.DEFAULT)
}
```

- [x] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [x] **Step 3: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/share/Base64Port.kt
git commit -m "feat: add Base64Port seam for the routine-share codec"
```

---

### Task 3: Share codec

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/share/RoutineShareCodec.kt`
- Test: `app/src/test/java/com/dhikr/app/core/share/RoutineShareCodecTest.kt`

**Interfaces:**
- Consumes: `Base64Port` (Task 2), `RoutineShareFile` + constants + `ShareFormatException` (Task 1).
- Produces: `class RoutineShareCodec(private val base64: Base64Port)` with
  - `fun encodeFile(file: RoutineShareFile): String` — pretty JSON, no prefix.
  - `fun encodeText(file: RoutineShareFile): String` — `SHARE_TEXT_PREFIX` + `base64.encode(gzip(minified JSON bytes))`, single line.
  - `fun decode(raw: String): RoutineShareFile` — trims; if it starts with `SHARE_TEXT_PREFIX`, strips + base64-decodes + gunzips then parses; if it starts with `"DHIKR-ROUTINE-"` but not the exact v1 prefix, throws; otherwise parses `raw` as JSON. Validates `format == SHARE_FORMAT` and `version <= SHARE_VERSION`. Every failure path throws `ShareFormatException`.

- [x] **Step 1: Write the failing test**

`app/src/test/java/com/dhikr/app/core/share/RoutineShareCodecTest.kt`:

```kotlin
package com.dhikr.app.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64 as JavaBase64

/** `android.util.Base64` is stubbed in unit tests, so the codec is driven with
 *  a real `java.util.Base64`-backed port. getMimeDecoder tolerates whitespace,
 *  matching `android.util.Base64.DEFAULT`. */
private object JavaBase64Port : Base64Port {
    override fun encode(bytes: ByteArray): String = JavaBase64.getEncoder().encodeToString(bytes)
    override fun decode(text: String): ByteArray = JavaBase64.getMimeDecoder().decode(text)
}

class RoutineShareCodecTest {

    private val codec = RoutineShareCodec(JavaBase64Port)

    private fun sample() = RoutineShareFile(
        format = SHARE_FORMAT,
        version = SHARE_VERSION,
        createdAt = 42L,
        appVersionName = "1.0",
        routines = listOf(
            ShareRoutine("Morning", listOf(ShareRoutineStep("subhan", 0, 33))),
        ),
        tasbih = listOf(
            ShareTasbih(id = "c1", name = "Mine", arabic = "a", lapTarget = 10, lapCount = 1),
        ),
    )

    @Test
    fun encodeFile_thenDecode_isEqual() {
        assertEquals(sample(), codec.decode(codec.encodeFile(sample())))
    }

    @Test
    fun encodeText_isSingleLine_withPrefix_andDecodesEqual() {
        val text = codec.encodeText(sample())
        assertTrue(text.startsWith(SHARE_TEXT_PREFIX))
        assertEquals(1, text.lines().size)
        assertEquals(sample(), codec.decode(text))
    }

    @Test
    fun decode_acceptsRawPrettyJson_noPrefix() {
        val pretty = codec.encodeFile(sample())
        assertTrue(pretty.contains("\n"))
        assertEquals(sample(), codec.decode(pretty))
    }

    @Test
    fun decode_rejectsWrongTextPrefix() {
        val body = codec.encodeText(sample()).removePrefix(SHARE_TEXT_PREFIX)
        assertThrows { codec.decode("DHIKR-ROUTINE-v2:$body") }
    }

    @Test
    fun decode_rejectsTruncatedBase64() {
        val text = codec.encodeText(sample())
        assertThrows { codec.decode(text.substring(0, text.length - 6)) }
    }

    @Test
    fun decode_rejectsValidBase64OfNonGzip() {
        val b64 = JavaBase64.getEncoder().encodeToString("not gzip at all".toByteArray())
        assertThrows { codec.decode(SHARE_TEXT_PREFIX + b64) }
    }

    @Test
    fun decode_rejectsEmptyObject() {
        assertThrows { codec.decode("{}") }
    }

    @Test
    fun decode_rejectsBackupFile() {
        assertThrows { codec.decode("""{"format":"dhikr.backup","version":1}""") }
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
            fail("expected ShareFormatException")
        } catch (e: ShareFormatException) {
            // expected
        }
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.share.RoutineShareCodecTest"`
Expected: FAIL — `RoutineShareCodec` unresolved.

- [x] **Step 3: Write the codec**

`app/src/main/java/com/dhikr/app/core/share/RoutineShareCodec.kt`:

```kotlin
package com.dhikr.app.core.share

import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Serializes / parses the routine-share payload in both delivery forms:
 * a pretty-JSON `.dhikrroutine` file, and a `DHIKR-ROUTINE-v1:` single-line
 * string (base64 of gzip of minified JSON). Pure apart from the injected
 * [Base64Port]. Every failure raises [ShareFormatException] and mutates nothing.
 */
class RoutineShareCodec(private val base64: Base64Port) {

    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val compactJson = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encodeFile(file: RoutineShareFile): String =
        prettyJson.encodeToString(RoutineShareFile.serializer(), file)

    fun encodeText(file: RoutineShareFile): String {
        val minified = compactJson.encodeToString(RoutineShareFile.serializer(), file)
        return SHARE_TEXT_PREFIX + base64.encode(gzip(minified))
    }

    fun decode(raw: String): RoutineShareFile {
        val text = raw.trim()
        val jsonText = when {
            text.startsWith(SHARE_TEXT_PREFIX) -> gunzip(text.removePrefix(SHARE_TEXT_PREFIX))
            text.startsWith("DHIKR-ROUTINE-") -> throw ShareFormatException(MSG_NOT_OURS)
            else -> text
        }
        val file = try {
            compactJson.decodeFromString(RoutineShareFile.serializer(), jsonText)
        } catch (e: Exception) {
            throw ShareFormatException(MSG_NOT_OURS)
        }
        if (file.format != SHARE_FORMAT) throw ShareFormatException(MSG_NOT_OURS)
        if (file.version > SHARE_VERSION) throw ShareFormatException(MSG_NEWER)
        return file
    }

    private fun gzip(s: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(s.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    private fun gunzip(b64: String): String {
        val bytes = try {
            base64.decode(b64)
        } catch (e: Exception) {
            throw ShareFormatException(MSG_NOT_OURS)
        }
        return try {
            GZIPInputStream(ByteArrayInputStream(bytes)).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            throw ShareFormatException(MSG_NOT_OURS)
        }
    }
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.share.RoutineShareCodecTest"`
Expected: PASS (8 tests).

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/share/RoutineShareCodec.kt app/src/test/java/com/dhikr/app/core/share/RoutineShareCodecTest.kt
git commit -m "feat: add routine-share codec (file + text forms)"
```

---

### Task 4: Share builder

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/share/RoutineShareBuilder.kt`
- Test: `app/src/test/java/com/dhikr/app/core/share/RoutineShareBuilderTest.kt`

**Interfaces:**
- Consumes: `RoutineWithSteps` from `com.dhikr.app.core.database.dao`, `TasbihEntity`, `RoutineEntity`, `RoutineStepEntity`, `RoutineShareFile` + DTOs (Task 1).
- Produces: `object RoutineShareBuilder` with
  `fun build(routines: List<RoutineWithSteps>, customTasbih: List<TasbihEntity>, appVersionName: String, now: Long): RoutineShareFile`
  — strips ids / per-user state, re-normalizes each routine's `stepOrder` to `0..n-1` in `stepOrder` order, bundles exactly the passed `customTasbih`.

- [x] **Step 1: Write the failing test**

`app/src/test/java/com/dhikr/app/core/share/RoutineShareBuilderTest.kt`:

```kotlin
package com.dhikr.app.core.share

import com.dhikr.app.core.database.dao.RoutineWithSteps
import com.dhikr.app.core.database.entity.RoutineEntity
import com.dhikr.app.core.database.entity.RoutineStepEntity
import com.dhikr.app.core.database.entity.TasbihEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineShareBuilderTest {

    private fun routine() = RoutineWithSteps(
        routine = RoutineEntity(
            id = "r1", name = "Evening", isPreset = true, isFavorite = true,
            createdAt = 100L, updatedAt = 200L, reminderEnabled = true,
            reminderMinuteOfDay = 600, reminderDays = 42,
        ),
        steps = listOf(
            RoutineStepEntity(stepId = 9, routineId = "r1", tasbihId = "subhan", stepOrder = 5, targetCount = 33),
            RoutineStepEntity(stepId = 4, routineId = "r1", tasbihId = "c1", stepOrder = 2, targetCount = 10),
        ),
    )

    private fun custom() = TasbihEntity(
        id = "c1", name = "Mine", arabic = "a", pronunciation = "p", translation = "t",
        note = "n", source = "s", lapTarget = 10, lapCount = 1, dailyGoal = 50,
        isFavorite = true, isBuiltIn = false, createdAt = 1L, updatedAt = 2L,
    )

    @Test
    fun build_stripsPerUserState_andNormalizesOrder() {
        val file = RoutineShareBuilder.build(listOf(routine()), listOf(custom()), "1.0", 999L)

        assertEquals(SHARE_FORMAT, file.format)
        assertEquals(SHARE_VERSION, file.version)
        assertEquals("1.0", file.appVersionName)
        assertEquals(999L, file.createdAt)

        val r = file.routines.single()
        assertEquals("Evening", r.name)
        // sorted by original stepOrder (2, then 5), re-normalized to 0,1
        assertEquals(listOf("c1", "subhan"), r.steps.map { it.tasbihId })
        assertEquals(listOf(0, 1), r.steps.map { it.stepOrder })
        assertEquals(listOf(10, 33), r.steps.map { it.targetCount })

        val t = file.tasbih.single()
        assertEquals("c1", t.id)
        assertEquals("Mine", t.name)
        assertEquals(50, t.dailyGoal)
    }

    @Test
    fun build_bundlesEveryPassedCustomTasbih_andNothingElse() {
        val file = RoutineShareBuilder.build(listOf(routine()), listOf(custom()), "1.0", 0L)
        assertEquals(setOf("c1"), file.tasbih.map { it.id }.toSet())
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.share.RoutineShareBuilderTest"`
Expected: FAIL — `RoutineShareBuilder` unresolved.

- [x] **Step 3: Write the builder**

`app/src/main/java/com/dhikr/app/core/share/RoutineShareBuilder.kt`:

```kotlin
package com.dhikr.app.core.share

import com.dhikr.app.core.database.dao.RoutineWithSteps
import com.dhikr.app.core.database.entity.TasbihEntity

/**
 * Turns DB rows into a shareable [RoutineShareFile]. A share is a *template*:
 * no ids, no per-user state (favourite, preset, reminders, timestamps). The
 * caller has already picked the custom tasbih to bundle — every referenced
 * built-in is left as a bare id the recipient resolves from seed data.
 */
object RoutineShareBuilder {

    fun build(
        routines: List<RoutineWithSteps>,
        customTasbih: List<TasbihEntity>,
        appVersionName: String,
        now: Long,
    ): RoutineShareFile = RoutineShareFile(
        format = SHARE_FORMAT,
        version = SHARE_VERSION,
        createdAt = now,
        appVersionName = appVersionName,
        routines = routines.map { rws ->
            ShareRoutine(
                name = rws.routine.name,
                steps = rws.steps
                    .sortedBy { it.stepOrder }
                    .mapIndexed { index, step ->
                        ShareRoutineStep(
                            tasbihId = step.tasbihId,
                            stepOrder = index,
                            targetCount = step.targetCount,
                        )
                    },
            )
        },
        tasbih = customTasbih.map { t ->
            ShareTasbih(
                id = t.id,
                name = t.name,
                arabic = t.arabic,
                pronunciation = t.pronunciation,
                translation = t.translation,
                note = t.note,
                source = t.source,
                lapTarget = t.lapTarget,
                lapCount = t.lapCount,
                dailyGoal = t.dailyGoal,
            )
        },
    )
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.share.RoutineShareBuilderTest"`
Expected: PASS (2 tests).

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/share/RoutineShareBuilder.kt app/src/test/java/com/dhikr/app/core/share/RoutineShareBuilderTest.kt
git commit -m "feat: add routine-share builder"
```

---

### Task 5: Import planner

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/share/RoutineImportPlanner.kt`
- Test: `app/src/test/java/com/dhikr/app/core/share/RoutineImportPlannerTest.kt`

**Interfaces:**
- Consumes: `RoutineShareFile` + DTOs + constants + `ShareFormatException` (Task 1), `RoutineEntity`, `RoutineStepEntity`, `TasbihEntity`.
- Produces: `object RoutineImportPlanner` with
  `fun plan(file: RoutineShareFile, existingTasbihIds: Set<String>, now: Long, newRoutineId: () -> String): ImportPlan`
  - Validates: `format == SHARE_FORMAT`; `version <= SHARE_VERSION`; `routines` non-empty; every routine `name` non-blank after trim; every step `targetCount >= 1`.
  - Resolves every step's `tasbihId` against `existingTasbihIds ∪ file.tasbih[].id`; any miss → `ShareFormatException(MSG_INCOMPLETE)`.
  - Per bundled tasbih: id already in `existingTasbihIds` → reuse (not in `tasbihInserts`, counted in `tasbihReused`); else insert as `TasbihEntity(isBuiltIn = false, isFavorite = false, createdAt = now, updatedAt = now, ...)`.
  - Per routine: `RoutineEntity` with `newRoutineId()`, `isPreset = false`, `isFavorite = false`, reminders default, `createdAt = updatedAt = now`, `name = name.trim()`. Steps become `RoutineStepEntity` (default `stepId`), `stepOrder` re-normalized to `0..n-1` **in payload array order**, `targetCount` from payload.

- [x] **Step 1: Write the failing test**

`app/src/test/java/com/dhikr/app/core/share/RoutineImportPlannerTest.kt`:

```kotlin
package com.dhikr.app.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RoutineImportPlannerTest {

    private fun idMinter(): () -> String {
        var n = 0
        return { "gen-${n++}" }
    }

    private fun customTasbih(id: String) = ShareTasbih(
        id = id, name = "Custom $id", arabic = "a", pronunciation = "p", translation = "t",
        lapTarget = 10, lapCount = 1,
    )

    private fun file(
        routines: List<ShareRoutine>,
        tasbih: List<ShareTasbih> = emptyList(),
        format: String = SHARE_FORMAT,
        version: Int = SHARE_VERSION,
    ) = RoutineShareFile(format, version, 0L, "1.0", routines, tasbih)

    @Test
    fun happyPath_oneBuiltInStep_oneCustomStep() {
        val f = file(
            routines = listOf(
                ShareRoutine(
                    "Morning",
                    listOf(
                        ShareRoutineStep("subhan", 0, 33),
                        ShareRoutineStep("c1", 1, 10),
                    ),
                ),
            ),
            tasbih = listOf(customTasbih("c1")),
        )
        val plan = RoutineImportPlanner.plan(f, setOf("subhan"), now = 5000L, newRoutineId = idMinter())

        val routine = plan.routineInserts.single()
        assertEquals("gen-0", routine.id)
        assertEquals("Morning", routine.name)
        assertEquals(false, routine.isPreset)
        assertEquals(false, routine.isFavorite)
        assertEquals(false, routine.reminderEnabled)
        assertEquals(5000L, routine.createdAt)
        assertEquals(5000L, routine.updatedAt)

        assertEquals(listOf("subhan", "c1"), plan.stepInserts.map { it.tasbihId })
        assertEquals(listOf(0, 1), plan.stepInserts.map { it.stepOrder })
        assertTrue(plan.stepInserts.all { it.routineId == "gen-0" })
        assertTrue(plan.stepInserts.all { it.stepId == 0L })

        val inserted = plan.tasbihInserts.single()
        assertEquals("c1", inserted.id)
        assertEquals(false, inserted.isBuiltIn)
        assertEquals(false, inserted.isFavorite)
        assertEquals(5000L, inserted.createdAt)

        assertEquals(ShareImportResult(1, 1, 0), plan.result)
    }

    @Test
    fun reuse_whenBundledTasbihIdAlreadyPresent() {
        val f = file(
            routines = listOf(ShareRoutine("R", listOf(ShareRoutineStep("c1", 0, 5)))),
            tasbih = listOf(customTasbih("c1")),
        )
        val plan = RoutineImportPlanner.plan(f, setOf("c1"), 0L, idMinter())
        assertTrue(plan.tasbihInserts.isEmpty())
        assertEquals(ShareImportResult(1, 0, 1), plan.result)
    }

    @Test
    fun builtInOnlyRoutine_emptyBundle() {
        val f = file(routines = listOf(ShareRoutine("R", listOf(ShareRoutineStep("subhan", 0, 33)))))
        val plan = RoutineImportPlanner.plan(f, setOf("subhan"), 0L, idMinter())
        assertTrue(plan.tasbihInserts.isEmpty())
        assertEquals(1, plan.routineInserts.size)
    }

    @Test
    fun multiRoutine_distinctIds_stepsAttributedCorrectly() {
        val f = file(
            routines = listOf(
                ShareRoutine("A", listOf(ShareRoutineStep("subhan", 0, 1))),
                ShareRoutine("B", listOf(ShareRoutineStep("subhan", 0, 2))),
            ),
        )
        val plan = RoutineImportPlanner.plan(f, setOf("subhan"), 0L, idMinter())
        assertEquals(listOf("gen-0", "gen-1"), plan.routineInserts.map { it.id })
        assertEquals("gen-0", plan.stepInserts.first { it.targetCount == 1 }.routineId)
        assertEquals("gen-1", plan.stepInserts.first { it.targetCount == 2 }.routineId)
    }

    @Test
    fun incomplete_whenStepTasbihNeitherPresentNorBundled() {
        val f = file(routines = listOf(ShareRoutine("R", listOf(ShareRoutineStep("ghost", 0, 1)))))
        assertThrows(MSG_INCOMPLETE) { RoutineImportPlanner.plan(f, setOf("subhan"), 0L, idMinter()) }
    }

    @Test
    fun validation_failures() {
        assertThrows(MSG_INCOMPLETE) {
            RoutineImportPlanner.plan(file(emptyList()), emptySet(), 0L, idMinter())
        }
        assertThrows(MSG_INCOMPLETE) {
            RoutineImportPlanner.plan(
                file(listOf(ShareRoutine("   ", listOf(ShareRoutineStep("subhan", 0, 1))))),
                setOf("subhan"), 0L, idMinter(),
            )
        }
        assertThrows(MSG_INCOMPLETE) {
            RoutineImportPlanner.plan(
                file(listOf(ShareRoutine("R", listOf(ShareRoutineStep("subhan", 0, 0))))),
                setOf("subhan"), 0L, idMinter(),
            )
        }
        assertThrows(MSG_NEWER) {
            RoutineImportPlanner.plan(
                file(listOf(ShareRoutine("R", listOf(ShareRoutineStep("subhan", 0, 1)))), version = 999),
                setOf("subhan"), 0L, idMinter(),
            )
        }
        assertThrows(MSG_NOT_OURS) {
            RoutineImportPlanner.plan(
                file(listOf(ShareRoutine("R", listOf(ShareRoutineStep("subhan", 0, 1)))), format = "dhikr.backup"),
                setOf("subhan"), 0L, idMinter(),
            )
        }
    }

    private fun assertThrows(expectedMessage: String, block: () -> Unit) {
        try {
            block()
            fail("expected ShareFormatException")
        } catch (e: ShareFormatException) {
            assertEquals(expectedMessage, e.message)
        }
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.share.RoutineImportPlannerTest"`
Expected: FAIL — `RoutineImportPlanner` unresolved.

- [x] **Step 3: Write the planner**

`app/src/main/java/com/dhikr/app/core/share/RoutineImportPlanner.kt`:

```kotlin
package com.dhikr.app.core.share

import com.dhikr.app.core.database.entity.RoutineEntity
import com.dhikr.app.core.database.entity.RoutineStepEntity
import com.dhikr.app.core.database.entity.TasbihEntity

/**
 * Pure import decision engine. Validates the payload, resolves every step's
 * tasbih, decides insert-vs-reuse per bundled tasbih, mints routine ids, and
 * returns the exact rows to write. Throws [ShareFormatException] on any invalid
 * payload — the caller never gets a partial [ImportPlan].
 */
object RoutineImportPlanner {

    fun plan(
        file: RoutineShareFile,
        existingTasbihIds: Set<String>,
        now: Long,
        newRoutineId: () -> String,
    ): ImportPlan {
        if (file.format != SHARE_FORMAT) throw ShareFormatException(MSG_NOT_OURS)
        if (file.version > SHARE_VERSION) throw ShareFormatException(MSG_NEWER)
        if (file.routines.isEmpty()) throw ShareFormatException(MSG_INCOMPLETE)
        file.routines.forEach { routine ->
            if (routine.name.trim().isEmpty()) throw ShareFormatException(MSG_INCOMPLETE)
            routine.steps.forEach { step ->
                if (step.targetCount < 1) throw ShareFormatException(MSG_INCOMPLETE)
            }
        }

        val bundledIds = file.tasbih.map { it.id }.toSet()
        val resolvable = existingTasbihIds + bundledIds
        file.routines.forEach { routine ->
            routine.steps.forEach { step ->
                if (step.tasbihId !in resolvable) throw ShareFormatException(MSG_INCOMPLETE)
            }
        }

        val tasbihInserts = file.tasbih
            .filter { it.id !in existingTasbihIds }
            .map { t ->
                TasbihEntity(
                    id = t.id,
                    name = t.name,
                    arabic = t.arabic,
                    pronunciation = t.pronunciation,
                    translation = t.translation,
                    note = t.note,
                    source = t.source,
                    lapTarget = t.lapTarget,
                    lapCount = t.lapCount,
                    dailyGoal = t.dailyGoal,
                    isFavorite = false,
                    isBuiltIn = false,
                    createdAt = now,
                    updatedAt = now,
                )
            }
        val tasbihReused = file.tasbih.count { it.id in existingTasbihIds }

        val routineInserts = mutableListOf<RoutineEntity>()
        val stepInserts = mutableListOf<RoutineStepEntity>()
        file.routines.forEach { routine ->
            val id = newRoutineId()
            routineInserts += RoutineEntity(
                id = id,
                name = routine.name.trim(),
                isPreset = false,
                isFavorite = false,
                createdAt = now,
                updatedAt = now,
            )
            // stepOrder re-normalized to 0..n-1 in payload array order.
            routine.steps.forEachIndexed { index, step ->
                stepInserts += RoutineStepEntity(
                    routineId = id,
                    tasbihId = step.tasbihId,
                    stepOrder = index,
                    targetCount = step.targetCount,
                )
            }
        }

        return ImportPlan(
            routineInserts = routineInserts,
            stepInserts = stepInserts,
            tasbihInserts = tasbihInserts,
            result = ShareImportResult(
                routinesImported = routineInserts.size,
                tasbihAdded = tasbihInserts.size,
                tasbihReused = tasbihReused,
            ),
        )
    }
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.share.RoutineImportPlannerTest"`
Expected: PASS (6 tests).

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/share/RoutineImportPlanner.kt app/src/test/java/com/dhikr/app/core/share/RoutineImportPlannerTest.kt
git commit -m "feat: add routine-import planner"
```

---

### Task 6: DAO methods + share repository

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/core/database/dao/RoutineDao.kt` (add one method)
- Modify: `app/src/main/java/com/dhikr/app/core/database/dao/TasbihDao.kt` (add one method)
- Create: `app/src/main/java/com/dhikr/app/core/share/RoutineShareRepository.kt`

**Interfaces:**
- Consumes: `AppDatabase`, `RoutineShareCodec` (Task 3), `RoutineShareBuilder` (Task 4), `RoutineImportPlanner` (Task 5), all Task 1 types.
- Produces: `class RoutineShareRepository(private val database: AppDatabase, private val codec: RoutineShareCodec)` with
  - `suspend fun buildShare(routineIds: List<String>, appVersionName: String): RoutineShareFile`
  - `suspend fun preview(payload: String): ImportPreview`
  - `suspend fun import(payload: String): ShareImportResult`
  - New DAO: `RoutineDao.getManyWithSteps(ids: List<String>): List<RoutineWithSteps>`, `TasbihDao.getByIds(ids: List<String>): List<TasbihEntity>`.

No automated test (Global Constraints). Verified by build + Task 12's manual smoke.

- [x] **Step 1: Add `RoutineDao.getManyWithSteps`**

In `RoutineDao.kt`, after the existing `getWithSteps` (around line 31):

```kotlin
    @Transaction
    @Query("SELECT * FROM routine WHERE id IN (:ids)")
    suspend fun getManyWithSteps(ids: List<String>): List<RoutineWithSteps>
```

- [x] **Step 2: Add `TasbihDao.getByIds`**

In `TasbihDao.kt`, after `getAllCustom` (around line 51):

```kotlin
    @Query("SELECT * FROM tasbih WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<TasbihEntity>
```

- [x] **Step 3: Write the repository**

`app/src/main/java/com/dhikr/app/core/share/RoutineShareRepository.kt`:

```kotlin
package com.dhikr.app.core.share

import androidx.room.withTransaction
import com.dhikr.app.core.database.AppDatabase
import java.util.UUID

/**
 * The one impure unit of routine sharing. Reads DAOs, delegates every decision
 * to the pure [RoutineShareBuilder] / [RoutineImportPlanner] / [RoutineShareCodec],
 * and applies an import in a single transaction. Not unit-tested directly — the
 * pure units beneath it are exhaustively covered, plus a manual two-device smoke.
 */
class RoutineShareRepository(
    private val database: AppDatabase,
    private val codec: RoutineShareCodec,
) {

    /** DAO reads -> [RoutineShareBuilder]. Bundles every custom tasbih any step
     *  references; built-ins are left as bare ids. */
    suspend fun buildShare(routineIds: List<String>, appVersionName: String): RoutineShareFile {
        val routineDao = database.routineDao()
        val tasbihDao = database.tasbihDao()

        val routines = routineDao.getManyWithSteps(routineIds)
        val referencedIds = routines.flatMap { it.steps }.map { it.tasbihId }.distinct()
        val customTasbih = tasbihDao.getByIds(referencedIds).filter { !it.isBuiltIn }

        return RoutineShareBuilder.build(
            routines = routines,
            customTasbih = customTasbih,
            appVersionName = appVersionName,
            now = System.currentTimeMillis(),
        )
    }

    /** Parse + validate only, no DB writes. Resolves step tasbih display names
     *  from the payload bundle first, then a DB lookup for built-ins. */
    suspend fun preview(payload: String): ImportPreview {
        val file = codec.decode(payload)
        val tasbihDao = database.tasbihDao()
        val existingIds = tasbihDao.getAllIds().toSet()

        // Runs the planner purely to surface "incomplete" errors here rather
        // than only at confirm(); its plan is discarded.
        val plan = RoutineImportPlanner.plan(file, existingIds, now = 0L) { "preview" }

        val bundleNames = file.tasbih.associate { it.id to it.name }
        val dbNames = tasbihDao
            .getByIds(file.routines.flatMap { it.steps }.map { it.tasbihId }.distinct())
            .associate { it.id to it.name }

        val previewRoutines = file.routines.map { routine ->
            PreviewRoutine(
                name = routine.name.trim(),
                steps = routine.steps.map { step ->
                    PreviewStep(
                        tasbihName = bundleNames[step.tasbihId]
                            ?: dbNames[step.tasbihId]
                            ?: step.tasbihId,
                        targetCount = step.targetCount,
                    )
                },
            )
        }
        return ImportPreview(previewRoutines, plan.result.tasbihAdded)
    }

    /** Decode -> plan -> apply in one transaction. Any failure leaves the DB
     *  untouched. */
    suspend fun import(payload: String): ShareImportResult {
        val file = codec.decode(payload)
        val routineDao = database.routineDao()
        val tasbihDao = database.tasbihDao()

        val existingIds = tasbihDao.getAllIds().toSet()
        val plan = RoutineImportPlanner.plan(file, existingIds, now = System.currentTimeMillis()) {
            UUID.randomUUID().toString()
        }

        database.withTransaction {
            // Planner only ever hands over tasbih whose ids are absent, so the
            // IGNORE on insertAll is a safe apply.
            tasbihDao.insertAll(plan.tasbihInserts)
            routineDao.insertRoutines(plan.routineInserts)
            routineDao.insertSteps(plan.stepInserts)
        }
        return plan.result
    }
}
```

- [x] **Step 4: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (Room KSP regenerates the DAOs).

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/share/RoutineShareRepository.kt app/src/main/java/com/dhikr/app/core/database/dao/RoutineDao.kt app/src/main/java/com/dhikr/app/core/database/dao/TasbihDao.kt
git commit -m "feat: add RoutineShareRepository + supporting DAO reads"
```

---

### Task 7: Share ViewModel

**Files:**
- Create: `app/src/main/java/com/dhikr/app/feature/routines/RoutineShareViewModel.kt`

**Interfaces:**
- Consumes: `RoutineShareRepository` (Task 6), `RoutineShareCodec` (Task 3), `RoutineRepository` (`observeAllWithSteps`).
- Produces: `class RoutineShareViewModel` with
  - `data class Selectable(val id: String, val name: String, val isPreset: Boolean, val checked: Boolean)`
  - `data class SharePayload(val fileText: String, val clipboardText: String, val suggestedFileName: String)`
  - `sealed interface Status { Idle; Working; data class Ready(val payload: SharePayload); data class Error(val message: String) }`
  - `val selectable: StateFlow<List<Selectable>>`, `val status: StateFlow<Status>`
  - `fun open(preselectId: String)`, `fun toggle(id: String)`, `fun setAll(checked: Boolean)`, `fun dismiss()`, `fun buildPayload()`
  - `class Factory(shareRepository, routineRepository, codec, appVersionName)`

No automated test (Android/ViewModel — verified by build, consistent with the repo of pure logic beneath it). Mirrors `BackupViewModel`'s Context-free split.

- [x] **Step 1: Write the ViewModel**

`app/src/main/java/com/dhikr/app/feature/routines/RoutineShareViewModel.kt`:

```kotlin
package com.dhikr.app.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.RoutineRepository
import com.dhikr.app.core.share.RoutineShareCodec
import com.dhikr.app.core.share.RoutineShareRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Drives the "Share routines" checklist + payload build. The screen owns every
 * Context / Uri / Intent / clipboard touch and this ViewModel emits plain
 * strings — same split as [com.dhikr.app.feature.settings.BackupViewModel].
 */
class RoutineShareViewModel(
    private val shareRepository: RoutineShareRepository,
    private val routineRepository: RoutineRepository,
    private val codec: RoutineShareCodec,
    private val appVersionName: String,
) : ViewModel() {

    data class Selectable(
        val id: String,
        val name: String,
        val isPreset: Boolean,
        val checked: Boolean,
    )

    data class SharePayload(
        val fileText: String,
        val clipboardText: String,
        val suggestedFileName: String,
    )

    sealed interface Status {
        data object Idle : Status
        data object Working : Status
        data class Ready(val payload: SharePayload) : Status
        data class Error(val message: String) : Status
    }

    private val _selectable = MutableStateFlow<List<Selectable>>(emptyList())
    val selectable: StateFlow<List<Selectable>> = _selectable.asStateFlow()

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Loads the routine list with [preselectId] pre-checked. Safe to call again
     *  when the dialog reopens. */
    fun open(preselectId: String) {
        _status.value = Status.Idle
        viewModelScope.launch {
            val routines = routineRepository.observeAllWithSteps().first()
            _selectable.value = routines
                .sortedBy { it.routine.name.lowercase(Locale.getDefault()) }
                .map {
                    Selectable(
                        id = it.routine.id,
                        name = it.routine.name,
                        isPreset = it.routine.isPreset,
                        checked = it.routine.id == preselectId,
                    )
                }
        }
    }

    fun toggle(id: String) {
        _selectable.update { list ->
            list.map { if (it.id == id) it.copy(checked = !it.checked) else it }
        }
    }

    fun setAll(checked: Boolean) {
        _selectable.update { list -> list.map { it.copy(checked = checked) } }
    }

    fun dismiss() {
        _selectable.value = emptyList()
        _status.value = Status.Idle
    }

    fun buildPayload() {
        val checked = _selectable.value.filter { it.checked }
        if (checked.isEmpty() || _status.value == Status.Working) return
        _status.value = Status.Working
        viewModelScope.launch {
            _status.value = try {
                withContext(Dispatchers.IO) {
                    val file = shareRepository.buildShare(checked.map { it.id }, appVersionName)
                    val name = if (file.routines.size == 1) {
                        slug(file.routines.first().name) + ".dhikrroutine"
                    } else {
                        "dhikr-routines-" +
                            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) +
                            ".dhikrroutine"
                    }
                    Status.Ready(
                        SharePayload(
                            fileText = codec.encodeFile(file),
                            clipboardText = codec.encodeText(file),
                            suggestedFileName = name,
                        ),
                    )
                }
            } catch (e: Exception) {
                Status.Error("Couldn't prepare the routines to share.")
            }
        }
    }

    private fun slug(raw: String): String {
        val cleaned = raw.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return cleaned.ifEmpty { "routine" }
    }

    class Factory(
        private val shareRepository: RoutineShareRepository,
        private val routineRepository: RoutineRepository,
        private val codec: RoutineShareCodec,
        private val appVersionName: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RoutineShareViewModel(shareRepository, routineRepository, codec, appVersionName) as T
    }
}
```

- [x] **Step 2: Verify build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [x] **Step 3: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/routines/RoutineShareViewModel.kt
git commit -m "feat: add RoutineShareViewModel"
```

---

### Task 8: Import ViewModel

**Files:**
- Create: `app/src/main/java/com/dhikr/app/feature/routines/RoutineImportViewModel.kt`

**Interfaces:**
- Consumes: `RoutineShareRepository` (Task 6), `ImportPreview` / `ShareImportResult` / `ShareFormatException` (Task 1).
- Produces: `class RoutineImportViewModel(private val repository: RoutineShareRepository)` with
  - `sealed interface State { Loading; data class Preview(val preview: ImportPreview); Working; data class Done(val result: ShareImportResult); data class Error(val message: String) }`
  - `val state: StateFlow<State>`
  - `fun load(readText: suspend () -> String)` — parse only, idempotent (guards a re-entry)
  - `fun confirm()` — runs the import
  - `class Factory(repository)`

No automated test (verified by build).

- [x] **Step 1: Write the ViewModel**

`app/src/main/java/com/dhikr/app/feature/routines/RoutineImportViewModel.kt`:

```kotlin
package com.dhikr.app.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.share.ImportPreview
import com.dhikr.app.core.share.RoutineShareRepository
import com.dhikr.app.core.share.ShareFormatException
import com.dhikr.app.core.share.ShareImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State machine for the import-preview screen:
 * `Loading -> Preview -> Working -> Done` / `Error`. [load] parses only (no DB
 * writes); [confirm] runs the single-transaction import.
 */
class RoutineImportViewModel(
    private val repository: RoutineShareRepository,
) : ViewModel() {

    sealed interface State {
        data object Loading : State
        data class Preview(val preview: ImportPreview) : State
        data object Working : State
        data class Done(val result: ShareImportResult) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private var payload: String? = null
    private var started = false

    fun load(readText: suspend () -> String) {
        if (started) return
        started = true
        viewModelScope.launch {
            _state.value = try {
                val raw = withContext(Dispatchers.IO) { readText() }
                payload = raw
                State.Preview(withContext(Dispatchers.IO) { repository.preview(raw) })
            } catch (e: ShareFormatException) {
                State.Error(e.message ?: "This isn't a Dhikr routine file.")
            } catch (e: Exception) {
                State.Error("Couldn't read that file.")
            }
        }
    }

    fun confirm() {
        val raw = payload ?: return
        if (_state.value == State.Working) return
        _state.value = State.Working
        viewModelScope.launch {
            _state.value = try {
                State.Done(withContext(Dispatchers.IO) { repository.import(raw) })
            } catch (e: ShareFormatException) {
                State.Error(e.message ?: "This shared file is incomplete.")
            } catch (e: Exception) {
                State.Error("Import failed. Your routines haven't changed.")
            }
        }
    }

    class Factory(
        private val repository: RoutineShareRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RoutineImportViewModel(repository) as T
    }
}
```

- [x] **Step 2: Verify build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [x] **Step 3: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/routines/RoutineImportViewModel.kt
git commit -m "feat: add RoutineImportViewModel"
```

---

### Task 9: Import screen + strings

**Files:**
- Create: `app/src/main/java/com/dhikr/app/feature/routines/RoutineImportScreen.kt`
- Modify: `app/src/main/res/values/strings.xml` (add the import block)

**Interfaces:**
- Consumes: `RoutineImportViewModel` (Task 8).
- Produces: `@Composable fun RoutineImportScreen(viewModel: RoutineImportViewModel, onClose: () -> Unit)` — renders the four states, calls `viewModel.confirm()` on Import, calls `onClose()` on Cancel and on the Done button.

- [x] **Step 1: Add strings**

In `app/src/main/res/values/strings.xml`, after the `routines_favorite_state_off` line (line 110):

```xml
    <string name="routines_share">Share</string>
    <string name="routines_share_dialog_title">Share routines</string>
    <string name="routines_share_select_all">Select all</string>
    <string name="routines_share_clear">Clear</string>
    <string name="routines_share_preset_tag">preset</string>
    <string name="routines_share_action">Share</string>
    <string name="routines_share_send_file">Send file</string>
    <string name="routines_share_copy_text">Copy as text</string>
    <string name="routines_share_copied">Routine text copied.</string>
    <string name="routines_share_error">Couldn\'t prepare the routines to share.</string>
    <string name="routines_share_file_error">Couldn\'t prepare the file to share. You can still copy it as text.</string>
    <string name="routines_import">Import</string>
    <string name="routines_import_menu_title">Import routine</string>
    <string name="routines_import_pick_file">Pick file</string>
    <string name="routines_import_paste_text">Paste text</string>
    <string name="routines_import_paste_title">Paste routine text</string>
    <string name="routines_import_paste_hint">Paste the shared DHIKR-ROUTINE text here</string>
    <string name="routines_import_paste_confirm">Preview</string>
    <string name="routines_import_title">Import routines</string>
    <string name="routines_import_loading">Reading…</string>
    <string name="routines_import_adds_tasbih">Adds %1$d new tasbih</string>
    <string name="routines_import_adds_no_tasbih">No new tasbih</string>
    <string name="routines_import_confirm">Import</string>
    <string name="routines_import_cancel">Cancel</string>
    <string name="routines_import_working">Importing…</string>
    <string name="routines_import_done">Added %1$d routines · %2$d new tasbih.</string>
    <string name="routines_import_done_button">Done</string>
```

- [x] **Step 2: Write the screen**

`app/src/main/java/com/dhikr/app/feature/routines/RoutineImportScreen.kt`:

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.core.share.PreviewRoutine
import com.dhikr.app.ui.headingSemantics
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.PillShape

@Composable
fun RoutineImportScreen(
    viewModel: RoutineImportViewModel,
    onClose: () -> Unit,
) {
    val colors = DhikrTheme.colors
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            stringResource(R.string.routines_import_title),
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.text,
            modifier = Modifier
                .padding(top = 16.dp, bottom = 12.dp)
                .headingSemantics(),
        )

        when (val s = state) {
            RoutineImportViewModel.State.Loading ->
                CenteredMessage(stringResource(R.string.routines_import_loading))

            RoutineImportViewModel.State.Working ->
                CenteredMessage(stringResource(R.string.routines_import_working))

            is RoutineImportViewModel.State.Error -> {
                CenteredMessage(s.message)
                PrimaryButton(stringResource(R.string.routines_import_cancel), onClick = onClose)
            }

            is RoutineImportViewModel.State.Done -> {
                CenteredMessage(
                    stringResource(
                        R.string.routines_import_done,
                        s.result.routinesImported,
                        s.result.tasbihAdded,
                    ),
                )
                PrimaryButton(stringResource(R.string.routines_import_done_button), onClick = onClose)
            }

            is RoutineImportViewModel.State.Preview -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(s.preview.routines) { routine -> PreviewCard(routine) }
                    item {
                        Text(
                            text = if (s.preview.newTasbihCount > 0) {
                                stringResource(R.string.routines_import_adds_tasbih, s.preview.newTasbihCount)
                            } else {
                                stringResource(R.string.routines_import_adds_no_tasbih)
                            },
                            fontSize = 12.5.sp,
                            color = colors.dim,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SecondaryButton(
                        stringResource(R.string.routines_import_cancel),
                        Modifier.weight(1f),
                        onClick = onClose,
                    )
                    PrimaryButton(
                        stringResource(R.string.routines_import_confirm),
                        Modifier.weight(1f),
                        onClick = viewModel::confirm,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(routine: PreviewRoutine) {
    val colors = DhikrTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .padding(16.dp),
    ) {
        Text(routine.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.text)
        routine.steps.forEachIndexed { index, step ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    fontSize = 12.sp,
                    color = colors.faint,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Text(step.tasbihName, fontSize = 13.5.sp, color = colors.text, modifier = Modifier.weight(1f))
                Text(
                    "${step.targetCount}",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.terra,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    val colors = DhikrTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, color = colors.dim, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = DhikrTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(PillShape)
            .background(colors.sage)
            .clickable(role = Role.Button, onClick = onClick)
            .minTapTarget()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.onSage)
    }
}

@Composable
private fun SecondaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = DhikrTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(PillShape)
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .minTapTarget()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
    }
}
```

Note: if `DhikrTheme.colors` has no `onSage` / `sage` / `surface` / `card` / `terra` / `dim` / `faint` member, check `app/src/main/java/com/dhikr/app/ui/theme/` and use the nearest equivalent the other screens use (`RoutinesScreen.kt` uses all of these names, so they exist). Same for `PillShape`, `headingSemantics()`, `minTapTarget()`.

- [x] **Step 3: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [x] **Step 4: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/routines/RoutineImportScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat: add routine import preview screen"
```

---

### Task 10: Routines screen — Share wiring

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/feature/routines/RoutinesScreen.kt`

**Interfaces:**
- Consumes: `RoutineShareViewModel` (Task 7).
- Produces: `RoutinesScreen` gains a `shareViewModel: RoutineShareViewModel` parameter. Adds a `Share` entry to `RoutineActionMenu` (`onShare: () -> Unit`), a checklist dialog bound to `shareViewModel.selectable`, and a result sheet with **Send file** / **Copy as text**. `RoutinesScreen` owns the `FileProvider` + `Intent.createChooser` + `ClipboardManager` calls.

The import entry is added in Task 11 to keep share and import independently reviewable.

- [x] **Step 1: Add the `shareViewModel` parameter and `onShare` to the action menu**

Change the signature:

```kotlin
@Composable
fun RoutinesScreen(
    viewModel: RoutinesViewModel,
    shareViewModel: RoutineShareViewModel,
    onStartRoutine: (String) -> Unit,
    onNewRoutine: () -> Unit,
    onEditRoutine: (String) -> Unit,
) {
```

Add `onShare` to `RoutineActionMenu`:

```kotlin
@Composable
private fun RoutineActionMenu(
    name: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
```

Inside its `Column`, between the Edit and Delete rows, add:

```kotlin
                Text(
                    text = stringResource(R.string.routines_share),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onShare() }
                        .minTapTarget()
                        .padding(vertical = 12.dp),
                )
```

- [x] **Step 2: Wire the action-menu `onShare` and add share dialog state**

In the `RoutinesScreen` body, add state near `actionMenuTarget`:

```kotlin
    val context = LocalContext.current
    var shareDialogOpen by remember { mutableStateOf(false) }
    var shareSnack by remember { mutableStateOf<String?>(null) }
    val shareStatus by shareViewModel.status.collectAsState()
    val shareSelectable by shareViewModel.selectable.collectAsState()
```

Add imports: `androidx.compose.ui.platform.LocalContext`, `androidx.compose.foundation.rememberScrollState`, `androidx.compose.foundation.verticalScroll`, `androidx.compose.foundation.lazy.items` is already there via `items`. Also `android.content.ClipData`, `android.content.ClipboardManager`, `android.content.Context`, `android.content.Intent`, `androidx.core.content.FileProvider`, `java.io.File`.

In the `actionMenuTarget?.let { ... RoutineActionMenu(...) }` call, add:

```kotlin
            onShare = {
                val id = routineWithSteps.routine.id
                actionMenuTarget = null
                shareViewModel.open(id)
                shareDialogOpen = true
            },
```

- [x] **Step 3: Add the checklist dialog + result sheet**

After the `deleteConfirmTarget?.let { ... }` block, add:

```kotlin
    if (shareDialogOpen) {
        when (val status = shareStatus) {
            is RoutineShareViewModel.Status.Ready -> SharePayloadSheet(
                payload = status.payload,
                onSendFile = {
                    val ok = sendRoutineFile(context, status.payload)
                    shareDialogOpen = false
                    shareViewModel.dismiss()
                    if (!ok) shareSnack = context.getString(R.string.routines_share_file_error)
                },
                onCopyText = {
                    copyToClipboard(context, status.payload.clipboardText)
                    shareDialogOpen = false
                    shareViewModel.dismiss()
                    shareSnack = context.getString(R.string.routines_share_copied)
                },
                onDismiss = {
                    shareDialogOpen = false
                    shareViewModel.dismiss()
                },
            )
            is RoutineShareViewModel.Status.Error -> AlertDialog(
                onDismissRequest = { shareDialogOpen = false; shareViewModel.dismiss() },
                title = { Text(stringResource(R.string.routines_share_dialog_title)) },
                text = { Text(status.message) },
                containerColor = colors.card,
                titleContentColor = colors.text,
                textContentColor = colors.dim,
                shape = DialogShape,
                confirmButton = {
                    TextButton(onClick = { shareDialogOpen = false; shareViewModel.dismiss() }) {
                        Text(stringResource(R.string.routines_delete_cancel_action), color = colors.dim)
                    }
                },
            )
            else -> ShareChecklistDialog(
                routines = shareSelectable,
                working = status is RoutineShareViewModel.Status.Working,
                onToggle = shareViewModel::toggle,
                onSelectAll = { shareViewModel.setAll(true) },
                onClear = { shareViewModel.setAll(false) },
                onShare = shareViewModel::buildPayload,
                onDismiss = { shareDialogOpen = false; shareViewModel.dismiss() },
            )
        }
    }

    shareSnack?.let { msg ->
        AlertDialog(
            onDismissRequest = { shareSnack = null },
            confirmButton = {
                TextButton(onClick = { shareSnack = null }) {
                    Text(stringResource(R.string.routines_import_done_button), color = colors.dim)
                }
            },
            text = { Text(msg) },
            containerColor = colors.card,
            textContentColor = colors.text,
            shape = DialogShape,
        )
    }
```

- [x] **Step 4: Add the dialog composables + Android helpers**

At the bottom of `RoutinesScreen.kt`:

```kotlin
@Composable
private fun ShareChecklistDialog(
    routines: List<RoutineShareViewModel.Selectable>,
    working: Boolean,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DhikrTheme.colors
    val anyChecked = routines.any { it.checked }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routines_share_dialog_title)) },
        containerColor = colors.card,
        titleContentColor = colors.text,
        shape = DialogShape,
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(R.string.routines_share_select_all),
                        fontSize = 12.5.sp,
                        color = colors.sage,
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = onSelectAll)
                            .padding(vertical = 6.dp),
                    )
                    Text(
                        stringResource(R.string.routines_share_clear),
                        fontSize = 12.5.sp,
                        color = colors.dim,
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = onClear)
                            .padding(vertical = 6.dp),
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(routines, key = { it.id }) { row ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Checkbox) { onToggle(row.id) }
                                .minTapTarget()
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                text = if (row.checked) "\u2611" else "\u2610",
                                fontSize = 16.sp,
                                color = if (row.checked) colors.sage else colors.faint,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                            Text(row.name, fontSize = 14.sp, color = colors.text, modifier = Modifier.weight(1f))
                            if (row.isPreset) {
                                Text(
                                    stringResource(R.string.routines_share_preset_tag),
                                    fontSize = 11.sp,
                                    color = colors.faint,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = anyChecked && !working, onClick = onShare) {
                Text(
                    stringResource(R.string.routines_share_action),
                    color = if (anyChecked && !working) colors.sage else colors.faint,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.routines_delete_cancel_action), color = colors.dim)
            }
        },
    )
}

@Composable
private fun SharePayloadSheet(
    payload: RoutineShareViewModel.SharePayload,
    onSendFile: () -> Unit,
    onCopyText: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DhikrTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routines_share_dialog_title)) },
        containerColor = colors.card,
        titleContentColor = colors.text,
        shape = DialogShape,
        text = {
            Column {
                Text(
                    stringResource(R.string.routines_share_send_file),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onSendFile)
                        .minTapTarget()
                        .padding(vertical = 12.dp),
                )
                Text(
                    stringResource(R.string.routines_share_copy_text),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onCopyText)
                        .minTapTarget()
                        .padding(vertical = 12.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.routines_delete_cancel_action), color = colors.dim)
            }
        },
    )
}

/** Writes the payload to the app cache and fires an ACTION_SEND chooser.
 *  Returns false if the file couldn't be prepared. */
private fun sendRoutineFile(
    context: Context,
    payload: RoutineShareViewModel.SharePayload,
): Boolean = try {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, payload.suggestedFileName)
    file.writeText(payload.fileText)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
    true
} catch (e: Exception) {
    false
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("dhikr routine", text))
}
```

Add the remaining imports to the file: `androidx.compose.material3.AlertDialog` (present), `androidx.compose.foundation.layout.heightIn` (present), `androidx.compose.foundation.lazy.LazyColumn` (present).

- [x] **Step 5: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (`DhikrApp.kt` will not compile yet because `RoutinesScreen` now needs `shareViewModel` — that wiring is Task 11. If splitting across sessions, do Step 5's full build at the end of Task 11 instead and only `compileDebugKotlin` the isolated screen file here is not possible; accept a red build between Task 10 and Task 11, or land them as one commit.)

**Recommendation:** land Task 10 + Task 11 as a single reviewed unit — the `RoutinesScreen` signature change forces `DhikrApp.kt` to change with it.

- [x] **Step 6: Commit (with Task 11) — see Task 11 Step 8.**

---

### Task 11: Import entry + intent-filter + navigation + manifest

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/feature/routines/RoutinesScreen.kt` (import entry in header)
- Modify: `app/src/main/java/com/dhikr/app/MainActivity.kt` (`pendingShareUri`)
- Modify: `app/src/main/java/com/dhikr/app/DhikrApp.kt` (repo wiring, `routines/import` route, `pendingShareUri` effect, pass `shareViewModel` + `onImportRequested` to `RoutinesScreen`)
- Modify: `app/src/main/AndroidManifest.xml` (`FileProvider` provider + `ACTION_VIEW` intent-filter)
- Create: `app/src/main/res/xml/file_paths.xml`

**Interfaces:**
- Consumes: `RoutineImportScreen` (Task 9), `RoutineImportViewModel` (Task 8), `RoutineShareViewModel` (Task 7), `RoutineShareRepository` + `RoutineShareCodec` + `AndroidBase64` (Tasks 2/3/6).
- Produces: `RoutinesScreen` gains `onImportRequested: (suspend () -> String) -> Unit`. `DhikrApp` gains `pendingShareUri: android.net.Uri? = null`, `onPendingShareConsumed: () -> Unit = {}`.

- [x] **Step 1: `file_paths.xml`**

`app/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="shared" path="shared/" />
</paths>
```

- [x] **Step 2: Manifest**

In `app/src/main/AndroidManifest.xml`, add to `MainActivity`'s element a second `intent-filter` (after the `MAIN`/`LAUNCHER` one, still inside `<activity>`):

```xml
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="content" android:mimeType="application/json" />
                <data android:scheme="file" android:mimeType="application/json" />
            </intent-filter>
```

And add a `<provider>` inside `<application>` (after the last `<receiver>`):

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [x] **Step 3: `MainActivity.pendingShareUri`**

In `MainActivity.kt`:

```kotlin
import android.net.Uri
```

Add the field (next to `pendingOpen`):

```kotlin
    // Set from an ACTION_VIEW launch (a tapped .dhikrroutine file) and consumed
    // once by DhikrApp, which navigates to the import preview.
    private var pendingShareUri by mutableStateOf<Uri?>(null)
```

In `onCreate`, after the `pendingOpen` line:

```kotlin
        pendingShareUri = if (intent?.action == Intent.ACTION_VIEW) intent?.data else null
```

In `onNewIntent`, after the `pendingOpen` line:

```kotlin
        pendingShareUri = if (intent.action == Intent.ACTION_VIEW) intent.data else null
```

In the `DhikrApp(...)` call, add:

```kotlin
                pendingShareUri = pendingShareUri,
                onPendingShareConsumed = { pendingShareUri = null },
```

- [x] **Step 4: `DhikrApp` — repo wiring + app-version helper**

In `DhikrApp.kt` add imports:

```kotlin
import android.net.Uri
import com.dhikr.app.core.database.RoutineRepository  // already imported
import com.dhikr.app.core.share.AndroidBase64
import com.dhikr.app.core.share.RoutineShareCodec
import com.dhikr.app.core.share.RoutineShareRepository
import com.dhikr.app.feature.routines.RoutineImportScreen
import com.dhikr.app.feature.routines.RoutineImportViewModel
import com.dhikr.app.feature.routines.RoutineShareViewModel
```

Change the signature:

```kotlin
@Composable
fun DhikrApp(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    pendingRoutineId: String? = null,
    onPendingRoutineConsumed: () -> Unit = {},
    pendingOpen: String? = null,
    onPendingOpenConsumed: () -> Unit = {},
    pendingShareUri: Uri? = null,
    onPendingShareConsumed: () -> Unit = {},
) {
```

Add a route constant near the others:

```kotlin
private const val ROUTE_ROUTINES_IMPORT = "routines/import"
```

After `backupRepository`:

```kotlin
        val appVersionName = remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull().orEmpty()
        }
        val routineShareCodec = remember { RoutineShareCodec(AndroidBase64) }
        val routineShareRepository = remember { RoutineShareRepository(app.database, routineShareCodec) }
        var importReader by remember { mutableStateOf<(suspend () -> String)?>(null) }
```

(Then in the `ROUTE_SETTINGS` composable, replace its local `appVersion` `remember { ... }` with the hoisted `appVersionName` to avoid duplication — pass `appVersionName` where it read `appVersion`.)

- [x] **Step 5: `DhikrApp` — pendingShareUri effect**

Add near the other `LaunchedEffect`s:

```kotlin
        // A tapped .dhikrroutine file: stash a reader over its content-uri and
        // route to the import preview. The parser is the real gate on whether
        // the file is ours (the MIME filter is broad).
        LaunchedEffect(pendingShareUri) {
            val uri = pendingShareUri ?: return@LaunchedEffect
            val resolver = context.contentResolver
            importReader = {
                resolver.openInputStream(uri)?.use { it.reader().readText() }
                    ?: error("no input stream")
            }
            navController.navigate(ROUTE_ROUTINES_IMPORT)
            onPendingShareConsumed()
        }
```

- [x] **Step 6: `DhikrApp` — routines composable + import route**

Replace the `composable(ROUTE_ROUTINES) { ... }` block:

```kotlin
                composable(ROUTE_ROUTINES) {
                    val viewModel: RoutinesViewModel = viewModel(
                        factory = RoutinesViewModel.Factory(routineRepository, tasbihRepository, reminderScheduler),
                    )
                    val shareVm: RoutineShareViewModel = viewModel(
                        factory = RoutineShareViewModel.Factory(
                            routineShareRepository, routineRepository, routineShareCodec, appVersionName,
                        ),
                    )
                    RoutinesScreen(
                        viewModel = viewModel,
                        shareViewModel = shareVm,
                        onStartRoutine = { id -> navController.navigate("counter?routineId=$id") },
                        onNewRoutine = { navController.navigate("routines/editor") },
                        onEditRoutine = { id -> navController.navigate("routines/editor?id=$id") },
                        onImportRequested = { reader ->
                            importReader = reader
                            navController.navigate(ROUTE_ROUTINES_IMPORT)
                        },
                    )
                }
                composable(ROUTE_ROUTINES_IMPORT) {
                    val reader = importReader
                    val importVm: RoutineImportViewModel = viewModel(
                        factory = RoutineImportViewModel.Factory(routineShareRepository),
                    )
                    LaunchedEffect(reader) {
                        if (reader == null) {
                            navController.popBackStack()
                        } else {
                            importVm.load(reader)
                        }
                    }
                    RoutineImportScreen(
                        viewModel = importVm,
                        onClose = {
                            importReader = null
                            navController.popBackStack()
                        },
                    )
                }
```

- [x] **Step 7: `RoutinesScreen` — import entry in the header**

Add the parameter:

```kotlin
fun RoutinesScreen(
    viewModel: RoutinesViewModel,
    shareViewModel: RoutineShareViewModel,
    onStartRoutine: (String) -> Unit,
    onNewRoutine: () -> Unit,
    onEditRoutine: (String) -> Unit,
    onImportRequested: (suspend () -> String) -> Unit,
) {
```

Add state in the body:

```kotlin
    var importMenuOpen by remember { mutableStateOf(false) }
    var pasteDialogOpen by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val resolver = context.contentResolver
            onImportRequested {
                resolver.openInputStream(uri)?.use { it.reader().readText() }
                    ?: error("no input stream")
            }
        }
    }
```

Imports: `androidx.activity.compose.rememberLauncherForActivityResult`, `androidx.activity.result.contract.ActivityResultContracts`, `androidx.compose.foundation.text.KeyboardOptions` is not needed.

In the header `Row` (the one with the title + "+ New" pill), wrap the right side so both controls sit together. Replace the `Box { ... "+ New" ... }` with:

```kotlin
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.routines_import),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.sage,
                    modifier = Modifier
                        .clickable(role = Role.Button) { importMenuOpen = true }
                        .minTapTarget()
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                )
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(colors.sage)
                        .clickable(role = Role.Button) { onNewRoutine() }
                        .minTapTarget()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.routines_new_short),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSage,
                    )
                }
            }
```

Add the import menu + paste dialog near the other dialogs:

```kotlin
    if (importMenuOpen) {
        AlertDialog(
            onDismissRequest = { importMenuOpen = false },
            title = { Text(stringResource(R.string.routines_import_menu_title)) },
            containerColor = colors.card,
            titleContentColor = colors.text,
            shape = DialogShape,
            text = {
                Column {
                    Text(
                        stringResource(R.string.routines_import_pick_file),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                importMenuOpen = false
                                pickFileLauncher.launch(arrayOf("application/json"))
                            }
                            .minTapTarget()
                            .padding(vertical = 12.dp),
                    )
                    Text(
                        stringResource(R.string.routines_import_paste_text),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                importMenuOpen = false
                                pasteText = ""
                                pasteDialogOpen = true
                            }
                            .minTapTarget()
                            .padding(vertical = 12.dp),
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { importMenuOpen = false }) {
                    Text(stringResource(R.string.routines_delete_cancel_action), color = colors.dim)
                }
            },
        )
    }

    if (pasteDialogOpen) {
        AlertDialog(
            onDismissRequest = { pasteDialogOpen = false },
            title = { Text(stringResource(R.string.routines_import_paste_title)) },
            containerColor = colors.card,
            titleContentColor = colors.text,
            shape = DialogShape,
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .padding(12.dp),
                ) {
                    if (pasteText.isEmpty()) {
                        Text(
                            stringResource(R.string.routines_import_paste_hint),
                            fontSize = 13.sp,
                            color = colors.faint,
                        )
                    }
                    BasicTextField(
                        value = pasteText,
                        onValueChange = { pasteText = it },
                        textStyle = TextStyle(fontSize = 13.sp, color = colors.text),
                        cursorBrush = SolidColor(colors.text),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pasteText.isNotBlank(),
                    onClick = {
                        val text = pasteText
                        pasteDialogOpen = false
                        onImportRequested { text }
                    },
                ) {
                    Text(
                        stringResource(R.string.routines_import_paste_confirm),
                        color = if (pasteText.isNotBlank()) colors.sage else colors.faint,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pasteDialogOpen = false }) {
                    Text(stringResource(R.string.routines_delete_cancel_action), color = colors.dim)
                }
            },
        )
    }
```

- [x] **Step 8: Verify full build + tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all share tests green (Tasks 1/3/4/5), existing tests still green.

- [x] **Step 9: Commit (Tasks 10 + 11 together)**

```bash
git add app/src/main/java/com/dhikr/app/feature/routines/RoutinesScreen.kt \
        app/src/main/java/com/dhikr/app/MainActivity.kt \
        app/src/main/java/com/dhikr/app/DhikrApp.kt \
        app/src/main/AndroidManifest.xml \
        app/src/main/res/xml/file_paths.xml
git commit -m "feat: wire routine share + import into the Routines screen"
```

---

### Task 12: Full verification + manual smoke + memory

**Files:** none (verification + docs)

- [x] **Step 1: Clean build + all tests + lint**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
Expected: BUILD SUCCESSFUL. Address any `lint` error introduced by the new manifest / xml / strings (unused-string warnings are acceptable if a string is genuinely referenced from code).

- [x] **Step 2: Confirm the "About" claims still hold**

Read `app/src/main/res/values/strings.xml` settings-About entries ("works offline", "no account", "never uploads"). Sharing writes a local cache file the user explicitly sends and contacts no server — the claims stay accurate. No change needed; note it in the task report.

- [x] **Step 3: Manual two-device smoke (record results in the task report)**

Two emulators, or one device sharing to itself via Files:
1. Create a routine with at least one custom-tasbih step.
2. Share it as a **file** → on the receiver, tap the `.dhikrroutine` → preview lists the right routine + steps + "Adds 1 new tasbih" → Import → the routine and the custom tasbih appear in the library.
3. Share the same routine via **Copy as text** → paste into the in-app *Import routine → Paste text* dialog → preview matches → Import.
4. Import the same payload a **second** time → a second routine is created, and no duplicate tasbih is added ("Adds 0 new tasbih" / `tasbihReused = 1`).
5. Feed the importer a non-routine `.json` → `"This isn't a Dhikr routine file."`, no DB change.

- [x] **Step 4: Update the plan checkboxes and memory**

Mark this plan's tasks complete. Update `C:\Users\azama\.claude\projects\d--Badhon-own-dhikr\memory\dhikr-project-status.md` to record Phase 6 (routine sharing) as implemented, QR sharing still deferred.

- [x] **Step 5: Final commit**

```bash
git add -A
git commit -m "docs: mark Phase 6 routine sharing complete"
```

---

## Self-Review

**1. Spec coverage**

| Spec section | Covered by |
|---|---|
| Payload format (JSON shape, never-serialized fields) | Task 1 models + Global Constraints |
| Text form (`DHIKR-ROUTINE-v1:` + base64(gzip)) | Task 3 codec + `RoutineShareCodecTest` |
| Import semantics steps 1–5 | Task 5 planner + Task 6 repo `import` |
| Relationship to backup format (independent DTOs/parser) | Task 1 (`SHARE_FORMAT`, no shared code) + codec format check |
| Components table — models | Task 1 |
| Components table — `Base64Port` / `AndroidBase64` | Task 2 |
| Components table — codec | Task 3 |
| Components table — builder | Task 4 |
| Components table — planner | Task 5 |
| Components table — repository | Task 6 |
| Components table — `RoutineShareViewModel` | Task 7 |
| Components table — `RoutineImportViewModel` | Task 8 |
| Components table — `RoutineImportScreen` | Task 9 |
| Components table — `RoutinesScreen` (Share row, checklist, Import entry, Intent/FileProvider/clipboard) | Tasks 10 + 11 |
| Components table — `MainActivity` `pendingShareUri` | Task 11 |
| Components table — `DhikrApp` route + `LaunchedEffect` + `readText` lambda | Task 11 |
| Components table — `AndroidManifest.xml` provider + intent-filter | Task 11 |
| Components table — `res/xml/file_paths.xml` | Task 11 |
| New DAO methods (`getManyWithSteps`, `getByIds`) | Task 6 |
| Share-sheet plumbing (cache file, FileProvider uri, `ACTION_SEND` chooser, clipboard) | Task 10 Step 4 |
| Import intent filter + "not ours" handling | Task 11 Step 2 + codec `ShareFormatException` |
| Error-handling table | Global Constraints + Tasks 8/10 error branches |
| Manifest / Gradle (no new dep) | Global Constraints + Task 11 |
| Testing — `RoutineShareModelsTest` | Task 1 |
| Testing — `RoutineShareCodecTest` (all 5 reject cases) | Task 3 |
| Testing — `RoutineShareBuilderTest` | Task 4 |
| Testing — `RoutineImportPlannerTest` (happy/reuse/built-in-only/multi/incomplete/validation) | Task 5 |
| Testing — manual smoke | Task 12 Step 3 |
| QR sharing | Explicitly out of scope — not planned |

No gaps.

**2. Placeholder scan** — no TBDs; every code step has full code; test steps have full test bodies; error strings are verbatim from the spec.

**3. Type consistency** — `RoutineShareFile` / `ShareRoutine` / `ShareRoutineStep` / `ShareTasbih` / `ShareImportResult` / `ImportPlan` / `ImportPreview` / `PreviewRoutine` / `PreviewStep` defined in Task 1 and used with the same field names in Tasks 3–11. `RoutineShareCodec(base64: Base64Port)` ctor consistent Task 2→3→11. `RoutineShareRepository(database, codec)` ctor consistent Task 6→7→8→11. `RoutineShareViewModel.Factory(shareRepository, routineRepository, codec, appVersionName)` consistent Task 7→11. `RoutineImportViewModel.Factory(repository)` consistent Task 8→11. `RoutinesScreen` param list (adds `shareViewModel`, `onImportRequested`) consistent Task 10/11→11 DhikrApp call. DAO method names `getManyWithSteps` / `getByIds` consistent Task 6→6 repo.

One deliberate spec deviation: `RoutineShareRepository` exposes a third method `preview(payload)` (returns `ImportPreview`) beyond the table's `buildShare` / `import`. The spec's Data-flow "Import (file intent)" step 5 explicitly calls for a repo-side parse-only preview with resolved tasbih names, so this realizes that flow. `ImportPreview` lives in `core/share` (not the feature layer) because core cannot depend on feature.
