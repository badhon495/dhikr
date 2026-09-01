# Gemini-generated Tasbih Benefits — Design

Date: 2026-09-01
Status: Approved for implementation planning

## Summary

Let a user supply their own Google Gemini API key and, from the Tasbih
editor screen, generate a short "virtues and benefits" (fada'il) write-up
for that specific dhikr. The result is cached per-tasbih in Room so it
shows instantly on return and can be regenerated on demand.

This is the app's first network feature. It is opt-in: with no key
configured, nothing about the app changes.

## Goals

- User pastes a Gemini API key once (Settings), stored encrypted on-device.
- From an existing tasbih's editor screen, tap "Generate benefits" → get
  4–8 bullet points sourced from Qur'an and authentic Sunnah.
- Result persists across app restarts; "Regenerate" replaces it.
- Graceful, specific error messages (no key, network down, bad key, rate
  limited, safety-blocked, malformed response).

## Non-goals

- No app-provided/default API key. User's key only.
- No model picker, no temperature/prompt controls in the UI. Fixed model
  and prompt in v1.
- No benefits generation for unsaved (brand-new) tasbihs.
- No streaming token display.
- No markdown rendering — plain text with bullet lines.
- No benefits surface on the Counter screen or elsewhere in v1.

## Architecture

New package `app/src/main/java/com/dhikr/app/core/ai/`:

| File | Responsibility |
|------|----------------|
| `SecureKeyStore.kt` | Encrypted read/write/clear of the Gemini API key. |
| `GeminiClient.kt` | One HTTP POST to Gemini `generateContent`; parse to a typed result. Knows nothing about tasbihs. |
| `BenefitsRepository.kt` | Orchestrates: read key → load tasbih → build prompt → call client → cache success to Room. |

Wiring: constructed in the existing composition-root spots
(`DhikrApp.kt`, `MainActivity.kt`) alongside the other repositories, and
threaded into `TasbihEditorViewModel.Factory` and
`SettingsViewModel.Factory`.

### Data flow — generate

```
TasbihEditorScreen  ── generateBenefits() ──▶  TasbihEditorViewModel
                                                     │
                                                     ▼
                                          BenefitsRepository.generate(id)
                                              1. SecureKeyStore.getGeminiKey()   ── null/blank ─▶ Error(NO_KEY)
                                              2. TasbihRepository.getById(id)     ── null ───────▶ Error(UNKNOWN)
                                              3. build prompt from tasbih fields
                                              4. GeminiClient.generateContent(key, prompt)
                                              5. Success ─▶ TasbihRepository.saveBenefits(id, text, now) ─▶ return Success
                                                 Error   ─▶ return Error (cache untouched)
                                                     │
                                                     ▼
                              ViewModel updates uiState (text / generatedAt / loading / error)
```

## Data layer

### `TasbihEntity` (core/database/entity/TasbihEntity.kt)

Add two nullable columns, default `null`:

```kotlin
val benefitsText: String? = null,
val benefitsGeneratedAt: Long? = null,   // epoch millis
```

### `AppDatabase` (core/database/AppDatabase.kt)

Bump `version = 8` → `version = 9`. Add a comment line matching the
existing v2–v8 style:

```
// v9: added TasbihEntity.benefitsText / benefitsGeneratedAt (cached
// Gemini-generated fada'il, per-tasbih). No hand migration —
// fallbackToDestructiveMigration rebuilds + reseeds.
```

No hand migration. This follows the established repo pattern: every
schema change since v1 has relied on `fallbackToDestructiveMigration`,
which rebuilds and reseeds. The known cost — a user's custom tasbihs and
history are wiped on the upgrade that crosses this version — is the
existing accepted behavior of this codebase, not introduced here.

### `TasbihDao` (core/database/dao/TasbihDao.kt)

```kotlin
@Query("UPDATE tasbih SET benefitsText = :text, benefitsGeneratedAt = :generatedAt, updatedAt = :generatedAt WHERE id = :id")
suspend fun updateBenefits(id: String, text: String, generatedAt: Long)
```

### `TasbihRepository` (core/database/TasbihRepository.kt)

```kotlin
suspend fun saveBenefits(id: String, text: String, generatedAt: Long) =
    tasbihDao.updateBenefits(id, text, generatedAt)
```

Existing `getById` / observe queries expose the new fields with no change.

## Key storage

### Dependency

Add to `gradle/libs.versions.toml` and `app/build.gradle.kts`:

```
androidx.security:security-crypto
```

Use the latest stable that is not affected by the Android 14 keyset
regression; confirm the exact version at implementation time (expected
`1.1.0-alpha06` or a later `1.1.x`). `minSdk` is 24 — well above the
API 23 floor for `EncryptedSharedPreferences`.

### `SecureKeyStore.kt`

```kotlin
class SecureKeyStore(context: Context) {
    // EncryptedSharedPreferences, file "ai_secrets",
    // MasterKey with AES256_GCM spec.
    fun getGeminiKey(): String?          // null when unset or blank
    suspend fun setGeminiKey(value: String?)  // null/blank => remove the entry
    val hasKey: Boolean
}
```

- Reads/writes are cheap; `set` is `suspend` + `Dispatchers.IO` for the
  disk write and consistency with the rest of the codebase.
- The key is never written to logcat anywhere in the feature.

## Gemini client

### Manifest

Add to `app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### `GeminiClient.kt`

- Endpoint:
  `POST https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=<KEY>`
- Model constant: `gemini-2.0-flash`. Not user-configurable.
- Transport: `HttpURLConnection` inside `withContext(Dispatchers.IO)`.
  Connect timeout 30s, read timeout 30s.
- JSON: `kotlinx.serialization`, `Json { ignoreUnknownKeys = true }`.
  `@Serializable` request/response DTOs local to this file.
- Request body: one `contents` entry with role `user` and the prompt
  text; `generationConfig { temperature = 0.4, maxOutputTokens = 800 }`.

Result type:

```kotlin
sealed interface GeminiResult {
    data class Success(val text: String) : GeminiResult
    data class Error(val kind: Kind, val message: String) : GeminiResult
    enum class Kind { NO_KEY, NETWORK, AUTH, RATE_LIMIT, BLOCKED, MALFORMED, UNKNOWN }
}
```

Mapping:

| Condition | Kind |
|-----------|------|
| `IOException` / `UnknownHostException` / timeout | `NETWORK` |
| HTTP 401 / 403 | `AUTH` |
| HTTP 429 | `RATE_LIMIT` |
| 200 but `promptFeedback.blockReason` set, or no candidate / `finishReason == "SAFETY"` | `BLOCKED` |
| 200 but body not parseable / no text part | `MALFORMED` |
| any other non-2xx | `UNKNOWN` |

`NO_KEY` is produced by `BenefitsRepository`, not the client (client is
never called without a key).

### Prompt (built in `BenefitsRepository`)

```
You are an Islamic knowledge assistant. For the following dhikr, describe its
virtues and benefits (fada'il) as reported in the Qur'an and authentic Sunnah.

Name: <name>
Arabic: <arabic>
Pronunciation: <pronunciation>
Translation: <translation>
Source: <source>            (line omitted when source is null/blank)

Rules:
- Only cite what is established in authentic sources; name the source (surah/ayah,
  or hadith collection) where possible.
- If a commonly-attributed benefit is weak or fabricated, say so briefly.
- 4-8 short bullet points. No greeting, no preamble.
- If you cannot verify benefits for this specific wording, say that plainly.
```

## Repository

### `BenefitsRepository.kt`

```kotlin
class BenefitsRepository(
    private val keyStore: SecureKeyStore,
    private val client: GeminiClient,
    private val tasbihRepository: TasbihRepository,
) {
    suspend fun generate(tasbihId: String): GeminiResult {
        val key = keyStore.getGeminiKey()
            ?: return GeminiResult.Error(Kind.NO_KEY, "...")
        val tasbih = tasbihRepository.getById(tasbihId)
            ?: return GeminiResult.Error(Kind.UNKNOWN, "...")
        val prompt = buildPrompt(tasbih)
        return when (val r = client.generateContent(key, prompt)) {
            is GeminiResult.Success -> {
                tasbihRepository.saveBenefits(tasbihId, r.text, System.currentTimeMillis())
                r
            }
            is GeminiResult.Error -> r
        }
    }
}
```

`buildPrompt` is a private pure function (unit-testable via the repo or
extracted as internal).

## ViewModel

### `TasbihEditorViewModel` (feature/tasbih/TasbihEditorViewModel.kt)

UI-state additions (seeded from the loaded `TasbihEntity`):

| Field | Source |
|-------|--------|
| `benefitsText: String?` | entity.benefitsText |
| `benefitsGeneratedAt: Long?` | entity.benefitsGeneratedAt |
| `benefitsLoading: Boolean` | false; true during a call |
| `benefitsError: BenefitsError?` | null; set from `GeminiResult.Error.kind` |

`BenefitsError` is a view enum (`NO_KEY, NETWORK, AUTH, RATE_LIMIT,
BLOCKED, MALFORMED, UNKNOWN`) mapped 1:1 from `GeminiResult.Error.Kind`;
the screen turns it into a `stringResource`.

```kotlin
fun generateBenefits() {
    if (state.benefitsLoading) return          // guard double-tap
    val id = currentTasbihId ?: return         // edit mode only
    viewModelScope.launch {
        update { it.copy(benefitsLoading = true, benefitsError = null) }
        when (val r = benefitsRepository.generate(id)) {
            is GeminiResult.Success -> update {
                it.copy(benefitsLoading = false, benefitsText = r.text,
                        benefitsGeneratedAt = System.currentTimeMillis())
            }
            is GeminiResult.Error -> update {
                it.copy(benefitsLoading = false,
                        benefitsError = r.kind.toViewError())
            }
        }
    }
}
```

Errors live only in memory — reopening the editor shows the last cached
text (if any) and no error.

`Factory` gains a `BenefitsRepository` parameter.

### `SettingsViewModel` (feature/settings/SettingsViewModel.kt)

- Exposes `hasGeminiKey: Boolean` (or a masked-state flag) for the UI.
- `fun saveGeminiKey(raw: String)` — trims; blank clears.
- `fun clearGeminiKey()`.
- `Factory` gains a `SecureKeyStore` parameter.

## UI

### `SettingsScreen` (feature/settings/SettingsScreen.kt)

New "AI features" section:

- A text field for the API key. When a key is set, show a masked
  placeholder (`••••••••`) and a "Change" affordance; editing replaces
  it. Save and Clear actions.
- Helper text: get a key at `aistudio.google.com/apikey`; the key is
  stored encrypted on this device and is sent only to Google's Gemini
  API when you generate benefits.
- All strings via `strings.xml`.

### `TasbihEditorScreen` (feature/tasbih/TasbihEditorScreen.kt)

New block below the existing fields, rendered **only in edit mode**
(an existing, saved tasbih — a new unsaved one has no id):

| State | UI |
|-------|-----|
| no `benefitsText`, key set | Button "Generate benefits" |
| no `benefitsText`, no key | Button shown disabled; tapping shows inline hint "Add a Gemini API key in Settings" |
| `benefitsLoading` | spinner + "Generating…" |
| `benefitsText` present | card with the text (bullet lines, plain), "Generated <relative time>" line, "Regenerate" button |
| `benefitsError` set | inline message for that error kind + "Try again" button (keeps any existing cached text visible above it) |

Relative time via the existing date/time utility in `core/utilities/`
(reuse whatever the Insights/history screens use; add a helper there if
none fits).

### Composition root

`DhikrApp.kt` / `MainActivity.kt`: build `SecureKeyStore`,
`GeminiClient`, `BenefitsRepository` next to the existing repositories
and pass them into the two factories.

## Error handling summary

| Kind | User-facing message (final wording in strings.xml) |
|------|--------------------------------------|
| NO_KEY | "Add a Gemini API key in Settings to use this." |
| NETWORK | "Couldn't reach Gemini. Check your connection and try again." |
| AUTH | "That API key was rejected. Check it in Settings." |
| RATE_LIMIT | "Gemini is rate-limiting requests. Try again in a bit." |
| BLOCKED | "Gemini declined to answer for this dhikr." |
| MALFORMED | "Gemini sent a response we couldn't read. Try again." |
| UNKNOWN | "Something went wrong generating benefits. Try again." |

## Testing

Plain JUnit4 (repo convention; no Robolectric in `src/test`).

| Test | Location | Covers |
|------|----------|--------|
| `GeminiClientTest` | src/test | Parse canned JSON: a normal success; a `promptFeedback.blockReason` body → `BLOCKED`; a `finishReason: SAFETY` body → `BLOCKED`; a 401 error body → `AUTH`; a garbage body → `MALFORMED`. Network is not exercised (parse/map functions extracted so they're callable without a socket). |
| `BenefitsRepositoryTest` | src/test | Fake `GeminiClient` + fake/in-memory `TasbihRepository`: NO_KEY when key store empty; UNKNOWN when tasbih missing; success path writes text + timestamp to the repo and returns Success; error path returns the error and does **not** touch the repo. Prompt-builder output contains each tasbih field and omits the Source line when source is null. |
| `SecureKeyStoreTest` | src/androidTest | set/get/clear round-trip on a real device/emulator (EncryptedSharedPreferences needs the Android framework). Instrumented-only; noted so CI expectations are clear. |

No test performs a live network call.

## Open items / confirm at implementation time

- Exact `androidx.security:security-crypto` version (Android 14 keyset
  regression — pick the current safe release).
- Whether `gemini-2.0-flash` is still the right free-tier default when
  implemented, or a newer flash model has superseded it. Change the
  constant only; no other code moves.
