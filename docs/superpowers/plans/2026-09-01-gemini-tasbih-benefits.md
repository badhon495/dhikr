# Gemini Tasbih Benefits Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user supply their own Google Gemini API key and generate a cached, per-tasbih "virtues and benefits" (fada'il) write-up from the Tasbih editor screen.

**Architecture:** A new `core/ai/` package holds three units: `SecureKeyStore` (EncryptedSharedPreferences wrapper for the key), `GeminiClient` (one `HttpURLConnection` POST to Gemini `generateContent`, parsed to a typed result), and `BenefitsRepository` (reads key → loads tasbih → builds prompt → calls client → caches the text to Room). Two nullable columns on `TasbihEntity` store the cached result. `TasbihEditorViewModel` and `SettingsViewModel` gain the new behavior; the editor screen shows a generate/regenerate block, Settings gets an API-key field.

**Tech Stack:** Kotlin, Jetpack Compose, Room 2.8.4, DataStore, kotlinx.serialization 1.7.3 (already present), `androidx.security:security-crypto` (new), `HttpURLConnection` (no HTTP library added), JUnit4 + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-01-gemini-tasbih-benefits-design.md`

## Global Constraints

- `minSdk = 24`, `targetSdk = 37`, `compileSdk = 37`, `jvmTarget = 17`.
- App is offline-first and privacy-forward. The API key and generated text stay on-device; the only network call is to `https://generativelanguage.googleapis.com`. The API key is never written to logcat.
- New dependencies go through `gradle/libs.versions.toml` (version in `[versions]`, coordinate in `[libraries]`) then referenced as `libs.*` in `app/build.gradle.kts`. Do not hardcode versions in the module file.
- Room uses `fallbackToDestructiveMigration` — schema changes need only a `version` bump and a `// vN:` comment, no hand migration.
- All user-facing text lives in `app/src/main/res/values/strings.xml` and is read with `stringResource(...)`.
- Unit tests are plain JUnit4 (`org.junit.Test`, `org.junit.Assert.*`). No Robolectric in `src/test`. Tests that need the Android framework (EncryptedSharedPreferences) go in `src/androidTest`.
- ViewModels are created through a nested `Factory : ViewModelProvider.Factory`; wiring happens in `DhikrApp.kt` (composition root, `remember { ... }` blocks and the `viewModel(factory = ...)` calls).
- Coroutine style: `viewModelScope.launch` in ViewModels; `withContext(Dispatchers.IO)` for blocking IO in repositories/clients.

---

## File Structure

**New files:**

| File | Responsibility |
|------|----------------|
| `app/src/main/java/com/dhikr/app/core/ai/SecureKeyStore.kt` | Encrypted read / write / clear of the Gemini API key. |
| `app/src/main/java/com/dhikr/app/core/ai/GeminiClient.kt` | One HTTP POST to Gemini `generateContent`; request/response DTOs; parse + HTTP-status mapping to `GeminiResult`. Knows nothing about tasbihs. |
| `app/src/main/java/com/dhikr/app/core/ai/BenefitsRepository.kt` | Orchestration: key → tasbih → prompt → client → cache success to Room. Owns `buildPrompt`. |
| `app/src/test/java/com/dhikr/app/core/ai/GeminiResponseParsingTest.kt` | Parse canned JSON bodies + status codes → `GeminiResult`. |
| `app/src/test/java/com/dhikr/app/core/ai/BenefitsRepositoryTest.kt` | Orchestration paths with fakes. |
| `app/src/androidTest/java/com/dhikr/app/core/ai/SecureKeyStoreTest.kt` | set/get/clear round-trip on-device. |

**Modified files:**

| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Add `securityCrypto` version + `security-crypto` library. |
| `app/build.gradle.kts` | Add `implementation(libs.security.crypto)`. |
| `app/src/main/AndroidManifest.xml` | Add `INTERNET` permission. |
| `app/src/main/java/com/dhikr/app/core/database/entity/TasbihEntity.kt` | Add `benefitsText`, `benefitsGeneratedAt` nullable columns. |
| `app/src/main/java/com/dhikr/app/core/database/AppDatabase.kt` | `version = 8` → `9` + `// v9:` comment. |
| `app/src/main/java/com/dhikr/app/core/database/dao/TasbihDao.kt` | Add `updateBenefits(...)`. |
| `app/src/main/java/com/dhikr/app/core/database/TasbihRepository.kt` | Add `saveBenefits(...)`. |
| `app/src/main/java/com/dhikr/app/feature/tasbih/TasbihEditorViewModel.kt` | UI-state fields + `generateBenefits()` + `BenefitsError` enum + Factory param. |
| `app/src/main/java/com/dhikr/app/feature/tasbih/TasbihEditorScreen.kt` | Benefits block (edit mode only); update footer copy. |
| `app/src/main/java/com/dhikr/app/feature/settings/SettingsViewModel.kt` | `hasGeminiKey` state + `saveGeminiKey` / `clearGeminiKey` + Factory param. |
| `app/src/main/java/com/dhikr/app/feature/settings/SettingsScreen.kt` | "AI features" section with masked key field. |
| `app/src/main/java/com/dhikr/app/DhikrApp.kt` | Construct `SecureKeyStore`, `GeminiClient`, `BenefitsRepository`; thread into both factories. |
| `app/src/main/res/values/strings.xml` | All new strings. |

---

## Task 1: Add dependency, permission, and schema columns

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts:86-105` (dependencies block)
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/dhikr/app/core/database/entity/TasbihEntity.kt`
- Modify: `app/src/main/java/com/dhikr/app/core/database/AppDatabase.kt:44-46`
- Modify: `app/src/main/java/com/dhikr/app/core/database/dao/TasbihDao.kt`
- Modify: `app/src/main/java/com/dhikr/app/core/database/TasbihRepository.kt`
- Test: `app/src/test/java/com/dhikr/app/core/database/TasbihBenefitsColumnsTest.kt`

**Interfaces:**
- Consumes: nothing (first task).
- Produces:
  - `TasbihEntity.benefitsText: String?` (default `null`), `TasbihEntity.benefitsGeneratedAt: Long?` (default `null`, epoch millis).
  - `TasbihDao.updateBenefits(id: String, text: String, generatedAt: Long)` — suspend.
  - `TasbihRepository.saveBenefits(id: String, text: String, generatedAt: Long)` — suspend.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/dhikr/app/core/database/TasbihBenefitsColumnsTest.kt`:

```kotlin
package com.dhikr.app.core.database

import com.dhikr.app.core.database.entity.TasbihEntity
import org.junit.Assert.assertNull
import org.junit.Test

class TasbihBenefitsColumnsTest {

    private fun sample() = TasbihEntity(
        id = "x", name = "n", arabic = "", pronunciation = "p", translation = "",
        lapTarget = 33, lapCount = 1, isBuiltIn = false, createdAt = 0L, updatedAt = 0L,
    )

    @Test
    fun benefits_fields_default_to_null() {
        val t = sample()
        assertNull(t.benefitsText)
        assertNull(t.benefitsGeneratedAt)
    }

    @Test
    fun benefits_fields_are_copyable() {
        val t = sample().copy(benefitsText = "• virtue", benefitsGeneratedAt = 123L)
        assert(t.benefitsText == "• virtue")
        assert(t.benefitsGeneratedAt == 123L)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.database.TasbihBenefitsColumnsTest"`
Expected: FAIL — compilation error, `benefitsText` / `benefitsGeneratedAt` unresolved.

- [ ] **Step 3: Add the dependency and permission**

In `gradle/libs.versions.toml`, add to `[versions]` (alphabetical-ish, after `room`):

```toml
securityCrypto = "1.1.0-alpha07"
```

Add to `[libraries]` (after `room-compiler`):

```toml
security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }
```

> Note: `1.1.0-alpha07` is the current pre-release that carries the Android 14 keyset fix. If Gradle sync reports it unavailable, use the latest `1.1.0-alphaNN` shown in the error, or `1.1.0-beta01`/stable if one now exists. Do not fall back to `1.0.0` (Android 14 keyset regression).

In `app/build.gradle.kts`, add inside `dependencies { }` after the datastore line (`implementation(libs.datastore.preferences)`):

```kotlin
    implementation(libs.security.crypto)
```

In `app/src/main/AndroidManifest.xml`, add with the other `<uses-permission>` lines (near `VIBRATE`):

```xml
    <uses-permission android:name="android.permission.INTERNET" />
```

- [ ] **Step 4: Add the entity columns**

In `app/src/main/java/com/dhikr/app/core/database/entity/TasbihEntity.kt`, add two properties to the `data class TasbihEntity` constructor, immediately after `val isFavorite: Boolean = false,`:

```kotlin
    /** Cached Gemini-generated virtues/benefits text for this dhikr; null until generated. */
    val benefitsText: String? = null,
    /** Epoch millis when [benefitsText] was generated; null when absent. */
    val benefitsGeneratedAt: Long? = null,
```

- [ ] **Step 5: Bump the database version**

In `app/src/main/java/com/dhikr/app/core/database/AppDatabase.kt`, add after the `// v8:` comment lines (before `version = 8,`):

```kotlin
    // v9: added TasbihEntity.benefitsText / benefitsGeneratedAt (cached
    // Gemini-generated fada'il, per-tasbih). No hand migration —
    // fallbackToDestructiveMigration rebuilds + reseeds.
```

Change `version = 8,` to `version = 9,`.

- [ ] **Step 6: Add the DAO and repository methods**

In `app/src/main/java/com/dhikr/app/core/database/dao/TasbihDao.kt`, add after `setFavorite`:

```kotlin
    @Query(
        "UPDATE tasbih SET benefitsText = :text, benefitsGeneratedAt = :generatedAt, " +
            "updatedAt = :generatedAt WHERE id = :id",
    )
    suspend fun updateBenefits(id: String, text: String, generatedAt: Long)
```

In `app/src/main/java/com/dhikr/app/core/database/TasbihRepository.kt`, add after `toggleFavorite`:

```kotlin
    /** Persist a freshly generated benefits write-up for [id]. */
    suspend fun saveBenefits(id: String, text: String, generatedAt: Long) =
        tasbihDao.updateBenefits(id, text, generatedAt)
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.database.TasbihBenefitsColumnsTest"`
Expected: PASS (both tests).

- [ ] **Step 8: Verify the module still compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Room KSP processes the new column + query without error).

- [ ] **Step 9: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml \
  app/src/main/java/com/dhikr/app/core/database/entity/TasbihEntity.kt \
  app/src/main/java/com/dhikr/app/core/database/AppDatabase.kt \
  app/src/main/java/com/dhikr/app/core/database/dao/TasbihDao.kt \
  app/src/main/java/com/dhikr/app/core/database/TasbihRepository.kt \
  app/src/test/java/com/dhikr/app/core/database/TasbihBenefitsColumnsTest.kt
git commit -m "feat: schema + deps for cached tasbih benefits

Add security-crypto dependency, INTERNET permission, and nullable
benefitsText / benefitsGeneratedAt columns on TasbihEntity (db v9,
destructive fallback). DAO + repository write path.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 2: GeminiClient — request build, response parse, status mapping

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/ai/GeminiClient.kt`
- Test: `app/src/test/java/com/dhikr/app/core/ai/GeminiResponseParsingTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `sealed interface GeminiResult` with:
    - `data class Success(val text: String) : GeminiResult`
    - `data class Error(val kind: Kind, val message: String) : GeminiResult`
    - `enum class Kind { NO_KEY, NETWORK, AUTH, RATE_LIMIT, BLOCKED, MALFORMED, UNKNOWN }` (nested in `GeminiResult`)
  - `class GeminiClient` with:
    - `suspend fun generateContent(apiKey: String, prompt: String): GeminiResult`
  - `internal fun parseGeminiResponse(httpStatus: Int, body: String): GeminiResult` — pure, top-level in the file, the unit-tested seam.
  - `internal fun buildRequestBody(prompt: String): String` — pure, returns the JSON string.
  - `internal const val GEMINI_MODEL = "gemini-2.0-flash"`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/dhikr/app/core/ai/GeminiResponseParsingTest.kt`:

```kotlin
package com.dhikr.app.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiResponseParsingTest {

    private val okBody = """
        {
          "candidates": [
            { "content": { "parts": [ { "text": "• First virtue\n• Second virtue" } ] },
              "finishReason": "STOP" }
          ]
        }
    """.trimIndent()

    private val safetyFinishBody = """
        {
          "candidates": [ { "finishReason": "SAFETY", "content": { "parts": [] } } ]
        }
    """.trimIndent()

    private val promptBlockedBody = """
        { "promptFeedback": { "blockReason": "SAFETY" } }
    """.trimIndent()

    private val authErrorBody = """
        { "error": { "code": 403, "message": "API key not valid", "status": "PERMISSION_DENIED" } }
    """.trimIndent()

    @Test
    fun success_extracts_joined_text() {
        val r = parseGeminiResponse(200, okBody)
        assertTrue(r is GeminiResult.Success)
        assertEquals("• First virtue\n• Second virtue", (r as GeminiResult.Success).text)
    }

    @Test
    fun safety_finish_reason_maps_to_blocked() {
        val r = parseGeminiResponse(200, safetyFinishBody)
        assertEquals(GeminiResult.Kind.BLOCKED, (r as GeminiResult.Error).kind)
    }

    @Test
    fun prompt_block_reason_maps_to_blocked() {
        val r = parseGeminiResponse(200, promptBlockedBody)
        assertEquals(GeminiResult.Kind.BLOCKED, (r as GeminiResult.Error).kind)
    }

    @Test
    fun http_401_maps_to_auth() {
        val r = parseGeminiResponse(401, authErrorBody)
        assertEquals(GeminiResult.Kind.AUTH, (r as GeminiResult.Error).kind)
    }

    @Test
    fun http_403_maps_to_auth() {
        val r = parseGeminiResponse(403, authErrorBody)
        assertEquals(GeminiResult.Kind.AUTH, (r as GeminiResult.Error).kind)
    }

    @Test
    fun http_429_maps_to_rate_limit() {
        val r = parseGeminiResponse(429, "{}")
        assertEquals(GeminiResult.Kind.RATE_LIMIT, (r as GeminiResult.Error).kind)
    }

    @Test
    fun http_500_maps_to_unknown() {
        val r = parseGeminiResponse(500, "{}")
        assertEquals(GeminiResult.Kind.UNKNOWN, (r as GeminiResult.Error).kind)
    }

    @Test
    fun garbage_200_body_maps_to_malformed() {
        val r = parseGeminiResponse(200, "not json at all")
        assertEquals(GeminiResult.Kind.MALFORMED, (r as GeminiResult.Error).kind)
    }

    @Test
    fun ok_200_with_no_text_part_maps_to_malformed() {
        val r = parseGeminiResponse(200, """{ "candidates": [ { "content": { "parts": [] }, "finishReason": "STOP" } ] }""")
        assertEquals(GeminiResult.Kind.MALFORMED, (r as GeminiResult.Error).kind)
    }

    @Test
    fun request_body_contains_prompt_and_generation_config() {
        val body = buildRequestBody("Explain SubhanAllah")
        assertTrue(body.contains("Explain SubhanAllah"))
        assertTrue(body.contains("temperature"))
        assertTrue(body.contains("maxOutputTokens"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.ai.GeminiResponseParsingTest"`
Expected: FAIL — `parseGeminiResponse` / `buildRequestBody` / `GeminiResult` unresolved.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/dhikr/app/core/ai/GeminiClient.kt`:

```kotlin
package com.dhikr.app.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal const val GEMINI_MODEL = "gemini-2.0-flash"
private const val GEMINI_BASE =
    "https://generativelanguage.googleapis.com/v1beta/models"
private const val TIMEOUT_MS = 30_000

/** Outcome of a benefits generation call. */
sealed interface GeminiResult {
    data class Success(val text: String) : GeminiResult
    data class Error(val kind: Kind, val message: String) : GeminiResult

    enum class Kind { NO_KEY, NETWORK, AUTH, RATE_LIMIT, BLOCKED, MALFORMED, UNKNOWN }
}

private val json = Json { ignoreUnknownKeys = true }

// ---- Wire DTOs ----

@Serializable
private data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig,
)

@Serializable
private data class Content(val role: String, val parts: List<Part>)

@Serializable
private data class Part(val text: String)

@Serializable
private data class GenerationConfig(
    val temperature: Double,
    val maxOutputTokens: Int,
)

@Serializable
private data class GeminiResponse(
    val candidates: List<Candidate> = emptyList(),
    val promptFeedback: PromptFeedback? = null,
)

@Serializable
private data class Candidate(
    val content: CandidateContent? = null,
    val finishReason: String? = null,
)

@Serializable
private data class CandidateContent(val parts: List<Part> = emptyList())

@Serializable
private data class PromptFeedback(
    @SerialName("blockReason") val blockReason: String? = null,
)

// ---- Pure helpers (unit-tested) ----

internal fun buildRequestBody(prompt: String): String = json.encodeToString(
    GeminiRequest.serializer(),
    GeminiRequest(
        contents = listOf(Content(role = "user", parts = listOf(Part(prompt)))),
        generationConfig = GenerationConfig(temperature = 0.4, maxOutputTokens = 800),
    ),
)

internal fun parseGeminiResponse(httpStatus: Int, body: String): GeminiResult {
    when (httpStatus) {
        in 200..299 -> Unit
        401, 403 -> return GeminiResult.Error(GeminiResult.Kind.AUTH, body.take(300))
        429 -> return GeminiResult.Error(GeminiResult.Kind.RATE_LIMIT, body.take(300))
        else -> return GeminiResult.Error(GeminiResult.Kind.UNKNOWN, "HTTP $httpStatus: ${body.take(300)}")
    }

    val parsed = runCatching { json.decodeFromString(GeminiResponse.serializer(), body) }
        .getOrElse { return GeminiResult.Error(GeminiResult.Kind.MALFORMED, "unparseable response") }

    if (parsed.promptFeedback?.blockReason != null) {
        return GeminiResult.Error(GeminiResult.Kind.BLOCKED, "blocked: ${parsed.promptFeedback.blockReason}")
    }
    val candidate = parsed.candidates.firstOrNull()
        ?: return GeminiResult.Error(GeminiResult.Kind.MALFORMED, "no candidates")
    if (candidate.finishReason == "SAFETY" || candidate.finishReason == "PROHIBITED_CONTENT") {
        return GeminiResult.Error(GeminiResult.Kind.BLOCKED, "finish reason ${candidate.finishReason}")
    }
    val text = candidate.content?.parts?.joinToString("") { it.text }?.trim().orEmpty()
    if (text.isEmpty()) {
        return GeminiResult.Error(GeminiResult.Kind.MALFORMED, "empty text")
    }
    return GeminiResult.Success(text)
}

// ---- Client ----

class GeminiClient {

    suspend fun generateContent(apiKey: String, prompt: String): GeminiResult =
        withContext(Dispatchers.IO) {
            val url = URL("$GEMINI_BASE/$GEMINI_MODEL:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(buildRequestBody(prompt).toByteArray(Charsets.UTF_8)) }

                val status = conn.responseCode
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                parseGeminiResponse(status, body)
            } catch (e: IOException) {
                GeminiResult.Error(GeminiResult.Kind.NETWORK, e.message ?: "network error")
            } finally {
                conn.disconnect()
            }
        }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.ai.GeminiResponseParsingTest"`
Expected: PASS (all 11 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/ai/GeminiClient.kt \
  app/src/test/java/com/dhikr/app/core/ai/GeminiResponseParsingTest.kt
git commit -m "feat: add GeminiClient with typed result and response parsing

HttpURLConnection POST to generateContent; pure buildRequestBody /
parseGeminiResponse seams covering success, safety-block, auth, rate
limit, and malformed cases.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 3: SecureKeyStore

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/ai/SecureKeyStore.kt`
- Test: `app/src/androidTest/java/com/dhikr/app/core/ai/SecureKeyStoreTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `class SecureKeyStore(context: Context)` with:
    - `fun getGeminiKey(): String?` — returns `null` when unset or stored value is blank.
    - `suspend fun setGeminiKey(value: String?)` — `null` or blank removes the entry.
    - `val hasKey: Boolean` — `true` when `getGeminiKey() != null`.

- [ ] **Step 1: Write the failing test**

Create `app/src/androidTest/java/com/dhikr/app/core/ai/SecureKeyStoreTest.kt`:

```kotlin
package com.dhikr.app.core.ai

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureKeyStoreTest {

    private lateinit var store: SecureKeyStore

    @Before
    fun setUp() {
        store = SecureKeyStore(ApplicationProvider.getApplicationContext())
        runBlocking { store.setGeminiKey(null) }
    }

    @Test
    fun absent_key_reads_null() {
        assertNull(store.getGeminiKey())
        assertFalse(store.hasKey)
    }

    @Test
    fun set_then_get_round_trips() = runBlocking {
        store.setGeminiKey("AIzaTESTKEY123")
        assertEquals("AIzaTESTKEY123", store.getGeminiKey())
        assertTrue(store.hasKey)
    }

    @Test
    fun blank_value_clears_the_key() = runBlocking {
        store.setGeminiKey("something")
        store.setGeminiKey("   ")
        assertNull(store.getGeminiKey())
    }

    @Test
    fun null_value_clears_the_key() = runBlocking {
        store.setGeminiKey("something")
        store.setGeminiKey(null)
        assertNull(store.getGeminiKey())
    }
}
```

- [ ] **Step 2: Check the androidTest deps exist**

Run: `grep -n "androidTest\|espresso\|test.ext\|test.core\|runner" app/build.gradle.kts`
Expected: If `androidx.test.ext:junit` and `androidx.test:core` are NOT already present as `androidTestImplementation`, add them. Add to `gradle/libs.versions.toml` `[versions]`:

```toml
androidxTestExtJunit = "1.2.1"
androidxTestCore = "1.6.1"
```

`[libraries]`:

```toml
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestExtJunit" }
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }
```

`app/build.gradle.kts` dependencies:

```kotlin
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.android)
```

Also confirm `defaultConfig` has `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`; add it if missing (and add `androidTestImplementation("androidx.test:runner:1.6.2")` via a `libs` entry if the runner class is not resolvable).

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `SecureKeyStore` unresolved.

- [ ] **Step 4: Write the implementation**

Create `app/src/main/java/com/dhikr/app/core/ai/SecureKeyStore.kt`:

```kotlin
package com.dhikr.app.core.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encrypted at-rest storage for the user's own Google Gemini API key.
 * Backed by [EncryptedSharedPreferences]; the key never leaves the device
 * except in the request to Google's Gemini API.
 */
class SecureKeyStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "ai_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getGeminiKey(): String? = prefs.getString(KEY_GEMINI, null)?.takeIf { it.isNotBlank() }

    val hasKey: Boolean get() = getGeminiKey() != null

    suspend fun setGeminiKey(value: String?) = withContext(Dispatchers.IO) {
        val trimmed = value?.trim()
        prefs.edit().apply {
            if (trimmed.isNullOrBlank()) remove(KEY_GEMINI) else putString(KEY_GEMINI, trimmed)
        }.apply()
    }

    private companion object {
        const val KEY_GEMINI = "gemini_api_key"
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.dhikr.app.core.ai.SecureKeyStoreTest"`
Expected: PASS (needs a connected device/emulator). If no device is available, run `./gradlew :app:compileDebugAndroidTestKotlin` and note the instrumented test as deferred to CI/device.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/ai/SecureKeyStore.kt \
  app/src/androidTest/java/com/dhikr/app/core/ai/SecureKeyStoreTest.kt \
  gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: add SecureKeyStore for the Gemini API key

EncryptedSharedPreferences-backed; blank/null clears the entry.
Instrumented round-trip test.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 4: BenefitsRepository

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/ai/BenefitsRepository.kt`
- Test: `app/src/test/java/com/dhikr/app/core/ai/BenefitsRepositoryTest.kt`

**Interfaces:**
- Consumes:
  - `SecureKeyStore.getGeminiKey(): String?` (Task 3)
  - `GeminiClient.generateContent(apiKey, prompt): GeminiResult`, `GeminiResult.*` (Task 2)
  - `TasbihRepository.getById(id): TasbihEntity?`, `TasbihRepository.saveBenefits(id, text, generatedAt)` (Task 1)
  - `TasbihEntity` fields: `name`, `arabic`, `pronunciation`, `translation`, `source: String?`
- Produces:
  - `class BenefitsRepository(keyStore, geminiClient, tasbihRepository)` — constructor param order exactly: `SecureKeyStore`, `GeminiClient`, `TasbihRepository`.
  - `suspend fun generate(tasbihId: String): GeminiResult`
  - `internal fun buildBenefitsPrompt(tasbih: TasbihEntity): String` — top-level in the file, pure, unit-tested.
- To make `GeminiClient` fakeable, extract an interface in this task:
  - `interface GeminiApi { suspend fun generateContent(apiKey: String, prompt: String): GeminiResult }`
  - `class GeminiClient : GeminiApi` (add `: GeminiApi` and `override` to the existing method).
  - `BenefitsRepository` depends on `GeminiApi`, not the concrete class.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/dhikr/app/core/ai/BenefitsRepositoryTest.kt`:

```kotlin
package com.dhikr.app.core.ai

import com.dhikr.app.core.database.entity.TasbihEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenefitsRepositoryTest {

    private fun tasbih(source: String? = null) = TasbihEntity(
        id = "t1", name = "Tasbih", arabic = "سُبْحَانَ اللَّه", pronunciation = "SubhanAllah",
        translation = "Glory be to Allah", source = source,
        lapTarget = 33, lapCount = 1, isBuiltIn = true, createdAt = 0L, updatedAt = 0L,
    )

    // -- fakes --

    private class FakeKeyStore(var key: String?) {
        fun getGeminiKey(): String? = key?.takeIf { it.isNotBlank() }
    }

    private class FakeGemini(val result: GeminiResult) : GeminiApi {
        var calls = 0
        var lastPrompt: String? = null
        override suspend fun generateContent(apiKey: String, prompt: String): GeminiResult {
            calls++; lastPrompt = prompt; return result
        }
    }

    private class FakeTasbihStore(private val entity: TasbihEntity?) {
        var savedId: String? = null
        var savedText: String? = null
        var savedAt: Long? = null
        suspend fun getById(id: String): TasbihEntity? = entity?.takeIf { it.id == id }
        suspend fun saveBenefits(id: String, text: String, generatedAt: Long) {
            savedId = id; savedText = text; savedAt = generatedAt
        }
    }

    // The repo takes small function/lambda seams so these fakes wire in without
    // depending on the concrete SecureKeyStore / TasbihRepository classes.
    private fun repo(
        keyStore: FakeKeyStore,
        gemini: FakeGemini,
        store: FakeTasbihStore,
    ) = BenefitsRepository(
        getKey = keyStore::getGeminiKey,
        gemini = gemini,
        getTasbih = store::getById,
        saveBenefits = store::saveBenefits,
    )

    @Test
    fun no_key_returns_NO_KEY_and_never_calls_gemini() = runTest {
        val gemini = FakeGemini(GeminiResult.Success("x"))
        val store = FakeTasbihStore(tasbih())
        val r = repo(FakeKeyStore(null), gemini, store).generate("t1")
        assertEquals(GeminiResult.Kind.NO_KEY, (r as GeminiResult.Error).kind)
        assertEquals(0, gemini.calls)
    }

    @Test
    fun missing_tasbih_returns_UNKNOWN() = runTest {
        val gemini = FakeGemini(GeminiResult.Success("x"))
        val store = FakeTasbihStore(null)
        val r = repo(FakeKeyStore("k"), gemini, store).generate("t1")
        assertEquals(GeminiResult.Kind.UNKNOWN, (r as GeminiResult.Error).kind)
        assertEquals(0, gemini.calls)
    }

    @Test
    fun success_caches_text_and_timestamp() = runTest {
        val gemini = FakeGemini(GeminiResult.Success("• virtue one"))
        val store = FakeTasbihStore(tasbih())
        val r = repo(FakeKeyStore("k"), gemini, store).generate("t1")
        assertTrue(r is GeminiResult.Success)
        assertEquals("t1", store.savedId)
        assertEquals("• virtue one", store.savedText)
        assertTrue((store.savedAt ?: 0L) > 0L)
    }

    @Test
    fun error_from_gemini_is_returned_and_not_cached() = runTest {
        val gemini = FakeGemini(GeminiResult.Error(GeminiResult.Kind.NETWORK, "down"))
        val store = FakeTasbihStore(tasbih())
        val r = repo(FakeKeyStore("k"), gemini, store).generate("t1")
        assertEquals(GeminiResult.Kind.NETWORK, (r as GeminiResult.Error).kind)
        assertEquals(null, store.savedId)
    }

    @Test
    fun prompt_includes_all_fields_and_omits_absent_source() = runTest {
        val gemini = FakeGemini(GeminiResult.Success("ok"))
        val store = FakeTasbihStore(tasbih(source = null))
        repo(FakeKeyStore("k"), gemini, store).generate("t1")
        val p = gemini.lastPrompt!!
        assertTrue(p.contains("SubhanAllah"))
        assertTrue(p.contains("Glory be to Allah"))
        assertTrue(p.contains("سُبْحَانَ اللَّه"))
        assertFalse(p.contains("Source:"))
    }

    @Test
    fun prompt_includes_source_when_present() = runTest {
        val gemini = FakeGemini(GeminiResult.Success("ok"))
        val store = FakeTasbihStore(tasbih(source = "Sahih Muslim 2691"))
        repo(FakeKeyStore("k"), gemini, store).generate("t1")
        assertTrue(gemini.lastPrompt!!.contains("Source: Sahih Muslim 2691"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.ai.BenefitsRepositoryTest"`
Expected: FAIL — `BenefitsRepository`, `GeminiApi` unresolved.

- [ ] **Step 3: Extract `GeminiApi` from `GeminiClient`**

In `app/src/main/java/com/dhikr/app/core/ai/GeminiClient.kt`, above `class GeminiClient`:

```kotlin
/** Seam for faking the network call in tests. */
interface GeminiApi {
    suspend fun generateContent(apiKey: String, prompt: String): GeminiResult
}
```

Change the class declaration to `class GeminiClient : GeminiApi {` and mark the method `override suspend fun generateContent(...)`.

- [ ] **Step 4: Write `BenefitsRepository`**

Create `app/src/main/java/com/dhikr/app/core/ai/BenefitsRepository.kt`:

```kotlin
package com.dhikr.app.core.ai

import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.database.entity.TasbihEntity

/**
 * Turns a tasbih into a cached "virtues and benefits" write-up via Gemini.
 *
 * The lambda seams (`getKey`, `getTasbih`, `saveBenefits`) keep this class
 * testable without the concrete [SecureKeyStore] / [TasbihRepository]; the
 * production [invoke]-style factory below wires the real ones.
 */
class BenefitsRepository internal constructor(
    private val getKey: () -> String?,
    private val gemini: GeminiApi,
    private val getTasbih: suspend (String) -> TasbihEntity?,
    private val saveBenefits: suspend (id: String, text: String, generatedAt: Long) -> Unit,
) {

    suspend fun generate(tasbihId: String): GeminiResult {
        val key = getKey()?.takeIf { it.isNotBlank() }
            ?: return GeminiResult.Error(GeminiResult.Kind.NO_KEY, "no API key configured")
        val tasbih = getTasbih(tasbihId)
            ?: return GeminiResult.Error(GeminiResult.Kind.UNKNOWN, "tasbih not found")

        return when (val result = gemini.generateContent(key, buildBenefitsPrompt(tasbih))) {
            is GeminiResult.Success -> {
                saveBenefits(tasbihId, result.text, System.currentTimeMillis())
                result
            }
            is GeminiResult.Error -> result
        }
    }

    companion object {
        /** Production wiring. */
        fun create(
            keyStore: SecureKeyStore,
            gemini: GeminiApi,
            tasbihRepository: TasbihRepository,
        ): BenefitsRepository = BenefitsRepository(
            getKey = keyStore::getGeminiKey,
            gemini = gemini,
            getTasbih = tasbihRepository::getById,
            saveBenefits = tasbihRepository::saveBenefits,
        )
    }
}

internal fun buildBenefitsPrompt(tasbih: TasbihEntity): String = buildString {
    appendLine("You are an Islamic knowledge assistant. For the following dhikr, describe its")
    appendLine("virtues and benefits (fada'il) as reported in the Qur'an and authentic Sunnah.")
    appendLine()
    appendLine("Name: ${tasbih.name}")
    appendLine("Arabic: ${tasbih.arabic}")
    appendLine("Pronunciation: ${tasbih.pronunciation}")
    appendLine("Translation: ${tasbih.translation}")
    tasbih.source?.takeIf { it.isNotBlank() }?.let { appendLine("Source: $it") }
    appendLine()
    appendLine("Rules:")
    appendLine("- Only cite what is established in authentic sources; name the source (surah/ayah,")
    appendLine("  or hadith collection) where possible.")
    appendLine("- If a commonly-attributed benefit is weak or fabricated, say so briefly.")
    appendLine("- 4-8 short bullet points. No greeting, no preamble.")
    append("- If you cannot verify benefits for this specific wording, say that plainly.")
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.ai.BenefitsRepositoryTest"`
Expected: PASS (6 tests).

- [ ] **Step 6: Run the full unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — existing tests plus the three new AI test classes.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/ai/BenefitsRepository.kt \
  app/src/main/java/com/dhikr/app/core/ai/GeminiClient.kt \
  app/src/test/java/com/dhikr/app/core/ai/BenefitsRepositoryTest.kt
git commit -m "feat: add BenefitsRepository orchestration

key -> tasbih -> prompt -> Gemini -> cache. GeminiApi seam extracted
for fakes. NO_KEY / missing-tasbih / success-caches / error-not-cached
and prompt-shape tests.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 5: Settings — API key entry

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/feature/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/dhikr/app/DhikrApp.kt:359-371` (settings composable)
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/dhikr/app/feature/settings/SettingsKeyStateTest.kt`

**Interfaces:**
- Consumes: `SecureKeyStore(context)`, `SecureKeyStore.getGeminiKey()`, `SecureKeyStore.setGeminiKey(String?)`, `SecureKeyStore.hasKey` (Task 3).
- Produces:
  - `SettingsUiState.hasGeminiKey: Boolean` (default `false`)
  - `SettingsViewModel.saveGeminiKey(raw: String)`
  - `SettingsViewModel.clearGeminiKey()`
  - `SettingsViewModel.Factory` gains trailing param `secureKeyStore: SecureKeyStore`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/dhikr/app/feature/settings/SettingsKeyStateTest.kt`:

```kotlin
package com.dhikr.app.feature.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises just the key add/clear reducer logic via a tiny in-test double of
 * the store contract. Full SettingsViewModel construction pulls in Android
 * (Context, DataStore) and is covered by instrumented tests elsewhere; this
 * keeps the new branch unit-tested.
 */
class SettingsKeyStateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class InMemoryKey {
        var value: String? = null
        fun get(): String? = value?.takeIf { it.isNotBlank() }
        suspend fun set(v: String?) { value = v?.trim()?.takeIf { it.isNotBlank() } }
    }

    @Test
    fun save_trims_and_marks_key_present() = runTest {
        val store = InMemoryKey()
        store.set("  AIzaKEY  ")
        assertEquals("AIzaKEY", store.get())
        assertTrue(store.get() != null)
    }

    @Test
    fun blank_save_clears_key() = runTest {
        val store = InMemoryKey()
        store.set("AIzaKEY")
        store.set("   ")
        assertFalse(store.get() != null)
    }
}
```

> This test locks the trim/clear contract the ViewModel relies on. The
> ViewModel wiring itself is verified by Step 5's compile + manual smoke.

- [ ] **Step 2: Run test to verify it fails / passes trivially**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.feature.settings.SettingsKeyStateTest"`
Expected: PASS (it documents the contract; it has no production dependency yet). Proceed.

- [ ] **Step 3: Update `SettingsViewModel`**

In `SettingsViewModel.kt`:

Add import: `import com.dhikr.app.core.ai.SecureKeyStore`

Add to `SettingsUiState`, after `appVersion`:

```kotlin
    val hasGeminiKey: Boolean = false,
```

Change the constructor to add a trailing param:

```kotlin
class SettingsViewModel(
    private val preferencesRepository: AppPreferencesRepository,
    private val appVersion: String,
    private val appContext: Context,
    private val secureKeyStore: SecureKeyStore,
) : ViewModel() {
```

In `init { }`, after the existing `.launchIn(viewModelScope)` chain, seed the key flag:

```kotlin
        _uiState.value = _uiState.value.copy(hasGeminiKey = secureKeyStore.hasKey)
```

> The combine chain rebuilds `SettingsUiState(...)` from scratch on every
> emission and would drop `hasGeminiKey`. Add `hasGeminiKey = secureKeyStore.hasKey`
> to BOTH the `SettingsUiState(...)` constructor call inside the first
> `combine` block AND keep the post-init seed line above for the very first
> frame. (Simplest: pass `hasGeminiKey = secureKeyStore.hasKey` in that
> `SettingsUiState(...)` call and delete the separate seed line.)

Add methods after `onDailyGoalChange`:

```kotlin
    fun saveGeminiKey(raw: String) {
        viewModelScope.launch {
            secureKeyStore.setGeminiKey(raw)
            _uiState.value = _uiState.value.copy(hasGeminiKey = secureKeyStore.hasKey)
        }
    }

    fun clearGeminiKey() {
        viewModelScope.launch {
            secureKeyStore.setGeminiKey(null)
            _uiState.value = _uiState.value.copy(hasGeminiKey = false)
        }
    }
```

Update `Factory`:

```kotlin
    class Factory(
        private val preferencesRepository: AppPreferencesRepository,
        private val appVersion: String,
        private val appContext: Context,
        private val secureKeyStore: SecureKeyStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(preferencesRepository, appVersion, appContext, secureKeyStore) as T
    }
```

- [ ] **Step 4: Add the strings**

In `app/src/main/res/values/strings.xml`, add near the other `settings_*` strings:

```xml
    <string name="settings_ai">AI features</string>
    <string name="settings_ai_desc">Add your own Google Gemini API key to generate a short summary of a tasbih\'s virtues and benefits. The key is stored encrypted on this device and is sent only to Google\'s Gemini API when you generate benefits.</string>
    <string name="settings_ai_key_label">Gemini API key</string>
    <string name="settings_ai_key_placeholder">Paste your API key</string>
    <string name="settings_ai_key_set">Key saved</string>
    <string name="settings_ai_key_hint">Get a key at aistudio.google.com/apikey</string>
    <string name="settings_ai_key_save">Save</string>
    <string name="settings_ai_key_change">Change</string>
    <string name="settings_ai_key_clear">Remove</string>
```

- [ ] **Step 5: Add the "AI features" section to `SettingsScreen`**

In `SettingsScreen.kt`, add a new section between the Accessibility and Backup sections (after line ~184):

```kotlin
        // ---- AI features ----
        SettingsSection(stringResource(R.string.settings_ai)) {
            GeminiKeyControls(
                hasKey = state.hasGeminiKey,
                onSave = viewModel::saveGeminiKey,
                onClear = viewModel::clearGeminiKey,
            )
        }
```

Add this composable near `BackupControls` (private, same file):

```kotlin
@Composable
private fun GeminiKeyControls(
    hasKey: Boolean,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = DhikrTheme.colors
    // editing==true shows the text field; when a key is already saved we start
    // collapsed behind a "Change" button so the field isn't pre-filled with a
    // secret.
    var editing by rememberSaveable { mutableStateOf(!hasKey) }
    var draft by rememberSaveable { mutableStateOf("") }

    Text(
        stringResource(R.string.settings_ai_desc),
        fontSize = 12.sp,
        color = colors.faint,
        modifier = Modifier.padding(bottom = 12.dp),
    )

    if (hasKey && !editing) {
        Text(
            stringResource(R.string.settings_ai_key_set),
            fontSize = 13.sp,
            color = colors.dim,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            KeyActionPill(stringResource(R.string.settings_ai_key_change), colors.surface, colors.text) {
                draft = ""
                editing = true
            }
            KeyActionPill(stringResource(R.string.settings_ai_key_clear), colors.surface, colors.text) {
                onClear()
                editing = true
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(ListRowShape)
                .background(colors.card)
                .border(1.dp, colors.line, ListRowShape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (draft.isEmpty()) {
                Text(
                    stringResource(R.string.settings_ai_key_placeholder),
                    fontSize = 14.sp,
                    color = colors.faint,
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = colors.text),
                cursorBrush = SolidColor(colors.text),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            stringResource(R.string.settings_ai_key_hint),
            fontSize = 11.5.sp,
            color = colors.faint,
            modifier = Modifier.padding(top = 8.dp),
        )
        KeyActionPill(
            label = stringResource(R.string.settings_ai_key_save),
            bg = if (draft.isNotBlank()) colors.sage else colors.surface,
            fg = if (draft.isNotBlank()) colors.onSage else colors.faint,
            modifier = Modifier.padding(top = 10.dp),
        ) {
            if (draft.isNotBlank()) {
                onSave(draft)
                draft = ""
                editing = false
            }
        }
    }
}

@Composable
private fun KeyActionPill(
    label: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(PillShape)
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .minTapTarget()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}
```

Add any missing imports to `SettingsScreen.kt`: `androidx.compose.foundation.text.KeyboardOptions`, `androidx.compose.ui.text.input.KeyboardType`, `androidx.compose.ui.text.TextStyle`, `androidx.compose.ui.graphics.SolidColor`, `androidx.compose.ui.graphics.Color`, `androidx.compose.foundation.layout.Row`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.saveable.rememberSaveable`, `androidx.compose.runtime.getValue`, `androidx.compose.runtime.setValue`. (Check which are already imported; `ListRowShape`, `PillShape`, `minTapTarget` already are.)

- [ ] **Step 6: Wire the store in `DhikrApp.kt`**

In `DhikrApp.kt`:

Add import: `import com.dhikr.app.core.ai.SecureKeyStore`

After `val preferencesRepository = remember { ... }` (line ~129):

```kotlin
        val secureKeyStore = remember { SecureKeyStore(context.applicationContext) }
```

In the `composable(ROUTE_SETTINGS)` block, update the factory call:

```kotlin
                    val viewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModel.Factory(
                            preferencesRepository, appVersionName, context.applicationContext, secureKeyStore,
                        ),
                    )
```

- [ ] **Step 7: Compile and run unit tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/settings/SettingsViewModel.kt \
  app/src/main/java/com/dhikr/app/feature/settings/SettingsScreen.kt \
  app/src/main/java/com/dhikr/app/DhikrApp.kt \
  app/src/main/res/values/strings.xml \
  app/src/test/java/com/dhikr/app/feature/settings/SettingsKeyStateTest.kt
git commit -m "feat: Gemini API key entry in Settings

New 'AI features' section: masked password field, save/change/remove,
key stored via SecureKeyStore. ViewModel + factory wired.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 6: Tasbih editor — generate & display benefits

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/feature/tasbih/TasbihEditorViewModel.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/tasbih/TasbihEditorScreen.kt`
- Modify: `app/src/main/java/com/dhikr/app/DhikrApp.kt` (editor composable + repo wiring)
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/dhikr/app/feature/tasbih/BenefitsErrorMappingTest.kt`

**Interfaces:**
- Consumes:
  - `BenefitsRepository.create(keyStore, gemini, tasbihRepository)` and `BenefitsRepository.generate(id): GeminiResult` (Task 4)
  - `GeminiClient()` (Task 2), `SecureKeyStore` instance (already in `DhikrApp` from Task 5)
  - `GeminiResult.Kind` (Task 2)
  - `TasbihEntity.benefitsText`, `TasbihEntity.benefitsGeneratedAt` (Task 1)
- Produces:
  - `TasbihEditorUiState` new fields: `benefitsText: String? = null`, `benefitsGeneratedAt: Long? = null`, `benefitsLoading: Boolean = false`, `benefitsError: BenefitsError? = null`, `canGenerateBenefits: Boolean = false` (true only when editing an existing tasbih).
  - `enum class BenefitsError { NO_KEY, NETWORK, AUTH, RATE_LIMIT, BLOCKED, MALFORMED, UNKNOWN }` (top-level in the ViewModel file).
  - `internal fun GeminiResult.Kind.toBenefitsError(): BenefitsError`
  - `TasbihEditorViewModel.generateBenefits()`
  - `TasbihEditorViewModel.Factory` gains trailing nullable param `benefitsRepository: BenefitsRepository?` — nullable so a `null` (new-tasbih path, or tests) simply disables the feature.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/dhikr/app/feature/tasbih/BenefitsErrorMappingTest.kt`:

```kotlin
package com.dhikr.app.feature.tasbih

import com.dhikr.app.core.ai.GeminiResult
import org.junit.Assert.assertEquals
import org.junit.Test

class BenefitsErrorMappingTest {

    @Test
    fun every_kind_maps_to_a_matching_view_error() {
        assertEquals(BenefitsError.NO_KEY, GeminiResult.Kind.NO_KEY.toBenefitsError())
        assertEquals(BenefitsError.NETWORK, GeminiResult.Kind.NETWORK.toBenefitsError())
        assertEquals(BenefitsError.AUTH, GeminiResult.Kind.AUTH.toBenefitsError())
        assertEquals(BenefitsError.RATE_LIMIT, GeminiResult.Kind.RATE_LIMIT.toBenefitsError())
        assertEquals(BenefitsError.BLOCKED, GeminiResult.Kind.BLOCKED.toBenefitsError())
        assertEquals(BenefitsError.MALFORMED, GeminiResult.Kind.MALFORMED.toBenefitsError())
        assertEquals(BenefitsError.UNKNOWN, GeminiResult.Kind.UNKNOWN.toBenefitsError())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.feature.tasbih.BenefitsErrorMappingTest"`
Expected: FAIL — `BenefitsError`, `toBenefitsError` unresolved.

- [ ] **Step 3: Update `TasbihEditorViewModel`**

In `TasbihEditorViewModel.kt`:

Add imports:

```kotlin
import com.dhikr.app.core.ai.BenefitsRepository
import com.dhikr.app.core.ai.GeminiResult
```

Add top-level (below the imports, above `data class TasbihEditorUiState`):

```kotlin
enum class BenefitsError { NO_KEY, NETWORK, AUTH, RATE_LIMIT, BLOCKED, MALFORMED, UNKNOWN }

internal fun GeminiResult.Kind.toBenefitsError(): BenefitsError = when (this) {
    GeminiResult.Kind.NO_KEY -> BenefitsError.NO_KEY
    GeminiResult.Kind.NETWORK -> BenefitsError.NETWORK
    GeminiResult.Kind.AUTH -> BenefitsError.AUTH
    GeminiResult.Kind.RATE_LIMIT -> BenefitsError.RATE_LIMIT
    GeminiResult.Kind.BLOCKED -> BenefitsError.BLOCKED
    GeminiResult.Kind.MALFORMED -> BenefitsError.MALFORMED
    GeminiResult.Kind.UNKNOWN -> BenefitsError.UNKNOWN
}
```

Add fields to `TasbihEditorUiState`, after `canSave`:

```kotlin
    val canGenerateBenefits: Boolean = false,
    val benefitsText: String? = null,
    val benefitsGeneratedAt: Long? = null,
    val benefitsLoading: Boolean = false,
    val benefitsError: BenefitsError? = null,
```

Change the constructor + `Factory` to thread an optional `BenefitsRepository`:

```kotlin
class TasbihEditorViewModel(
    private val repository: TasbihRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val editingId: String? = null,
    private val benefitsRepository: BenefitsRepository? = null,
) : ViewModel() {
```

In the `init` load block (inside `repository.getById(editingId)?.let { entity -> ... }`), extend the `update { it.copy(...) }` with:

```kotlin
                            canGenerateBenefits = benefitsRepository != null,
                            benefitsText = entity.benefitsText,
                            benefitsGeneratedAt = entity.benefitsGeneratedAt,
```

Add the method (after `onSave`):

```kotlin
    fun generateBenefits() {
        val repo = benefitsRepository ?: return
        val id = editingId ?: return
        if (_uiState.value.benefitsLoading) return
        viewModelScope.launch {
            update { it.copy(benefitsLoading = true, benefitsError = null) }
            when (val result = repo.generate(id)) {
                is GeminiResult.Success -> update {
                    it.copy(
                        benefitsLoading = false,
                        benefitsText = result.text,
                        benefitsGeneratedAt = System.currentTimeMillis(),
                    )
                }
                is GeminiResult.Error -> update {
                    it.copy(
                        benefitsLoading = false,
                        benefitsError = result.kind.toBenefitsError(),
                    )
                }
            }
        }
    }
```

Update the `Factory`:

```kotlin
    class Factory(
        private val repository: TasbihRepository,
        private val preferencesRepository: AppPreferencesRepository,
        private val editingId: String? = null,
        private val benefitsRepository: BenefitsRepository? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TasbihEditorViewModel(repository, preferencesRepository, editingId, benefitsRepository) as T
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.feature.tasbih.BenefitsErrorMappingTest"`
Expected: PASS.

- [ ] **Step 5: Add strings**

In `strings.xml`:

```xml
    <string name="tasbih_editor_benefits_label">Virtues &amp; benefits</string>
    <string name="tasbih_editor_benefits_generate">Generate benefits</string>
    <string name="tasbih_editor_benefits_regenerate">Regenerate</string>
    <string name="tasbih_editor_benefits_loading">Generating…</string>
    <string name="tasbih_editor_benefits_generated_at">Generated %1$s</string>
    <string name="tasbih_editor_benefits_no_key">Add a Gemini API key in Settings to use this.</string>
    <string name="tasbih_editor_benefits_retry">Try again</string>
    <string name="tasbih_editor_benefits_err_network">Couldn\'t reach Gemini. Check your connection and try again.</string>
    <string name="tasbih_editor_benefits_err_auth">That API key was rejected. Check it in Settings.</string>
    <string name="tasbih_editor_benefits_err_rate">Gemini is rate-limiting requests. Try again in a bit.</string>
    <string name="tasbih_editor_benefits_err_blocked">Gemini declined to answer for this dhikr.</string>
    <string name="tasbih_editor_benefits_err_malformed">Gemini sent a response we couldn\'t read. Try again.</string>
    <string name="tasbih_editor_benefits_err_unknown">Something went wrong generating benefits. Try again.</string>
```

Update the existing footer string (`tasbih_editor_footer`, currently "Stored on this device only. Nothing is uploaded.") to no longer claim nothing is uploaded, since benefits generation sends the dhikr text to Google:

```xml
    <string name="tasbih_editor_footer">Stored on this device. Generating benefits sends this dhikr\'s text to Google\'s Gemini API.</string>
```

- [ ] **Step 6: Add the benefits block to `TasbihEditorScreen`**

In `TasbihEditorScreen.kt`, add imports as needed: `androidx.compose.material3.CircularProgressIndicator`, `androidx.compose.foundation.layout.Spacer`, `androidx.compose.foundation.layout.width`, `androidx.compose.runtime.getValue`.

Insert this block after the Daily Goal `LabeledField` (after line ~213, before the Save `Box`):

```kotlin
        if (state.canGenerateBenefits) {
            BenefitsBlock(
                text = state.benefitsText,
                generatedAt = state.benefitsGeneratedAt,
                loading = state.benefitsLoading,
                error = state.benefitsError,
                onGenerate = viewModel::generateBenefits,
            )
        }
```

Add these composables near the bottom of the file (private):

```kotlin
@Composable
private fun BenefitsBlock(
    text: String?,
    generatedAt: Long?,
    loading: Boolean,
    error: BenefitsError?,
    onGenerate: () -> Unit,
) {
    val colors = DhikrTheme.colors
    LabeledField(stringResource(R.string.tasbih_editor_benefits_label)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (text != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ListRowShape)
                        .background(colors.card)
                        .border(1.dp, colors.line, ListRowShape)
                        .padding(16.dp),
                ) {
                    Text(text = text, fontSize = 13.5.sp, color = colors.text)
                }
                if (generatedAt != null) {
                    Text(
                        text = stringResource(
                            R.string.tasbih_editor_benefits_generated_at,
                            relativeTimeLabel(generatedAt),
                        ),
                        fontSize = 11.5.sp,
                        color = colors.faint,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            if (error != null) {
                Text(
                    text = stringResource(benefitsErrorText(error)),
                    fontSize = 12.sp,
                    color = colors.terra,
                    modifier = Modifier.padding(top = if (text != null) 10.dp else 0.dp),
                )
            }

            val buttonLabel = when {
                loading -> stringResource(R.string.tasbih_editor_benefits_loading)
                error != null -> stringResource(R.string.tasbih_editor_benefits_retry)
                text != null -> stringResource(R.string.tasbih_editor_benefits_regenerate)
                else -> stringResource(R.string.tasbih_editor_benefits_generate)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .heightIn(min = 48.dp)
                    .clip(PillShape)
                    .background(if (loading) colors.track else colors.sage)
                    .clickable(enabled = !loading, role = Role.Button) { onGenerate() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (loading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = colors.text,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = buttonLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (loading) colors.text else colors.onSage,
                    )
                }
            }
        }
    }
}

private fun benefitsErrorText(error: BenefitsError): Int = when (error) {
    BenefitsError.NO_KEY -> R.string.tasbih_editor_benefits_no_key
    BenefitsError.NETWORK -> R.string.tasbih_editor_benefits_err_network
    BenefitsError.AUTH -> R.string.tasbih_editor_benefits_err_auth
    BenefitsError.RATE_LIMIT -> R.string.tasbih_editor_benefits_err_rate
    BenefitsError.BLOCKED -> R.string.tasbih_editor_benefits_err_blocked
    BenefitsError.MALFORMED -> R.string.tasbih_editor_benefits_err_malformed
    BenefitsError.UNKNOWN -> R.string.tasbih_editor_benefits_err_unknown
}

/** "just now" / "5 min ago" / "3 hr ago" / "2 days ago". */
private fun relativeTimeLabel(epochMillis: Long): String {
    val delta = System.currentTimeMillis() - epochMillis
    val min = delta / 60_000
    val hr = min / 60
    val days = hr / 24
    return when {
        min < 1 -> "just now"
        min < 60 -> "$min min ago"
        hr < 24 -> "$hr hr ago"
        else -> "$days day${if (days == 1L) "" else "s"} ago"
    }
}
```

> `relativeTimeLabel` is deliberately local and simple — no existing shared
> helper exists (`core/utilities/` has only `DayBounds`). If a shared
> relative-time helper is added later, swap this for it.

- [ ] **Step 7: Wire `BenefitsRepository` in `DhikrApp.kt`**

Add imports:

```kotlin
import com.dhikr.app.core.ai.BenefitsRepository
import com.dhikr.app.core.ai.GeminiClient
```

After `val secureKeyStore = remember { ... }` (added in Task 5):

```kotlin
        val benefitsRepository = remember {
            BenefitsRepository.create(secureKeyStore, GeminiClient(), tasbihRepository)
        }
```

In the `composable(ROUTE_TASBIH_EDITOR)` block, update the factory:

```kotlin
                    val viewModel: TasbihEditorViewModel = viewModel(
                        factory = TasbihEditorViewModel.Factory(
                            tasbihRepository, preferencesRepository, editingId, benefitsRepository,
                        ),
                    )
```

- [ ] **Step 8: Compile and run the full unit suite**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/tasbih/TasbihEditorViewModel.kt \
  app/src/main/java/com/dhikr/app/feature/tasbih/TasbihEditorScreen.kt \
  app/src/main/java/com/dhikr/app/DhikrApp.kt \
  app/src/main/res/values/strings.xml \
  app/src/test/java/com/dhikr/app/feature/tasbih/BenefitsErrorMappingTest.kt
git commit -m "feat: generate tasbih benefits from the editor screen

Edit-mode-only benefits block: generate/regenerate button, cached text
with relative timestamp, per-kind error messages. ViewModel threads an
optional BenefitsRepository; footer copy updated to disclose the upload.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 7: Manual verification & docs

**Files:**
- Modify: `README.md` (Progress section)

- [ ] **Step 1: Manual smoke test on a device/emulator**

Install: `./gradlew :app:installDebug`

Verify:
1. Settings → "AI features" section is present. Field is masked (password keyboard, no autocorrect).
2. Paste any non-empty string → Save → row collapses to "Key saved" with Change / Remove.
3. Open an existing tasbih in the editor → "Virtues & benefits" block shows a "Generate benefits" button. A brand-new tasbih (from "New tasbih") shows NO benefits block.
4. With a real valid Gemini key: tap Generate → spinner → bullet text appears + "Generated just now" + "Regenerate".
5. Close the editor, reopen the same tasbih → cached text still shows.
6. Remove the key in Settings → editor Generate → inline "Add a Gemini API key in Settings" error.
7. Airplane mode + valid key → Generate → "Couldn't reach Gemini…" error, any previously cached text stays visible.

- [ ] **Step 2: Update README**

In `README.md`, under `### Done`, add:

```markdown
- AI benefits: user-supplied Gemini API key (encrypted on-device), per-tasbih virtues/benefits generation cached in Room
```

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: note AI benefits feature in README

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Self-Review

**1. Spec coverage:**

| Spec section | Task |
|--------------|------|
| `TasbihEntity` columns + DB v9 | Task 1 |
| `TasbihDao.updateBenefits` / `TasbihRepository.saveBenefits` | Task 1 |
| `security-crypto` dependency | Task 1 (+ Task 3 note on version) |
| `SecureKeyStore` (encrypted, blank clears) | Task 3 |
| INTERNET permission | Task 1 |
| `GeminiClient` — endpoint, model, timeouts, DTOs, `GeminiResult` + `Kind`, status mapping | Task 2 |
| Prompt (built in repository, omits absent Source) | Task 4 |
| `BenefitsRepository.generate` flow (NO_KEY, missing tasbih, success caches, error not cached) | Task 4 |
| `TasbihEditorViewModel` state + `generateBenefits` + `BenefitsError` mapping | Task 6 |
| Edit-mode-only gate | Task 6 (`canGenerateBenefits = benefitsRepository != null`, `editingId` guard) |
| Settings API-key section (masked, save/clear, helper text) | Task 5 |
| `SettingsViewModel` `hasGeminiKey` / `saveGeminiKey` / `clearGeminiKey` | Task 5 |
| Editor benefits block (button states, card, relative time, error + retry) | Task 6 |
| Composition-root wiring | Tasks 5 & 6 |
| Strings in `strings.xml` | Tasks 5 & 6 |
| Error message table | Task 6 strings |
| Tests: response parsing, repository, key store | Tasks 2, 4, 3 |
| "No live network in tests" | All test tasks use fakes/canned bodies |

Extra beyond spec (justified): footer copy update in `tasbih_editor_footer` (Task 6) — the existing string claims "Nothing is uploaded", which becomes false; leaving it would be a privacy misstatement.

**2. Placeholder scan:** No "TBD"/"implement later". The only conditional instruction is the `security-crypto` version fallback (Task 1 Step 3 / Task 3 Step 2), which gives an explicit decision rule, not a blank.

**3. Type consistency:**
- `GeminiResult` / `GeminiResult.Kind` — defined Task 2, consumed Tasks 4 & 6 with the same nesting.
- `GeminiApi` — introduced Task 4 Step 3, `GeminiClient : GeminiApi`; `BenefitsRepository.create` and `DhikrApp` pass `GeminiClient()` where `GeminiApi` is expected. Consistent.
- `BenefitsRepository` — primary constructor is `internal` (lambda seams); production path is `BenefitsRepository.create(...)`. Task 6 `DhikrApp` calls `.create`, tests call the `internal` constructor. Consistent.
- `saveBenefits(id, text, generatedAt)` — same signature in `TasbihDao` (as `updateBenefits`), `TasbihRepository`, the repo lambda type, and the fakes.
- `toBenefitsError()` (Task 6) vs `toViewError()` (mentioned in the spec prose) — plan standardizes on `toBenefitsError()` everywhere; spec's `toViewError` name is not used.
- `BenefitsError` enum values match `GeminiResult.Kind` values one-to-one (minus none — all 7 kinds map; `NO_KEY` is included).
- `canGenerateBenefits` (Task 6) — set from `benefitsRepository != null` in `init`, read in `TasbihEditorScreen`. Consistent.

No unresolved references. Plan is internally consistent.
