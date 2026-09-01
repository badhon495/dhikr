# Phase 6 — Routine Sharing — Design

Date: 2026-09-01
Status: Approved for implementation

## Goal

Let a user send one or more routines to another user with no server: a
`.dhikrroutine` file through the Android share sheet, or a one-line
copy-paste text blob for chat apps. The recipient opens the file (or
pastes the text), previews what it contains, and imports it as new
custom routines.

QR sharing (plan §38) is deferred to its own later spec.

## Scope

In scope:

- Share a checklist-selected set of routines (custom or preset) as a
  single payload. The payload bundles the definitions of any custom
  tasbih the routines' steps reference.
- Two delivery forms from one build step:
  - a `.dhikrroutine` JSON file via `ACTION_SEND` (share sheet), and
  - a `DHIKR-ROUTINE-v1:<base64(gzip(json))>` single-line string via a
    "Copy as text" action.
- Import via:
  - a manifest `intent-filter` — tapping a received `.dhikrroutine`
    file opens the app straight into an import preview, and
  - an in-app "Import routine" entry on the Routines screen (SAF file
    pick, or paste text).
- An import preview screen: routine names, step list, and a note of how
  many new tasbih will be added. Import / Cancel.
- Imported routines are always written fresh (new ids, `isPreset = 0`,
  `isFavorite = 0`, reminders off).

Out of scope:

- QR code generate / scan (separate spec).
- Any server, account, deep-link URL, or App Links domain.
- Sharing session history, goals, favorites, preferences (that is what
  the backup file in `2026-09-01-manual-backup-design.md` is for).
- Editing a routine during the import preview.
- Deduping routines on re-import (a re-import intentionally makes a new
  copy — see "Import semantics").

## Relationship to the backup format

The backup format (`dhikr.backup`, `core/backup/`) and the share format
(`dhikr.routine`, `core/share/`) are independent. They version
separately, use separate DTOs, and neither parser accepts the other's
`format` string. A share is a *template* — no ids, no per-user state —
while a backup is a *merge-by-primary-key snapshot* of one user's whole
dataset.

## Payload format

A single JSON object. File extension `.dhikrroutine`, MIME
`application/json`.

```
{
  "format": "dhikr.routine",
  "version": 1,
  "createdAt": <epochMillis>,
  "appVersionName": "1.0",
  "routines": [
    {
      "name": "Morning Dhikr",
      "steps": [
        { "tasbihId": "<id>", "stepOrder": 0, "targetCount": 33 }
      ]
    }
  ],
  "tasbih": [
    { "id": "<id>", "name", "arabic", "pronunciation", "translation",
      "note", "source", "lapTarget", "lapCount", "dailyGoal" }
  ]
}
```

Rules:

- Routine `id`, `createdAt`, `updatedAt`, `isPreset`, `isFavorite`, and
  all reminder fields are **never serialized**. `RoutineStepEntity.stepId`
  is never serialized.
- `tasbih[]` contains a definition for **every custom tasbih**
  (`isBuiltIn = 0`) referenced by any step in `routines[]`, and nothing
  else. Built-in tasbih are referenced by bare `tasbihId` only — the
  recipient has the same seed data (`core/database/seed/SeedData.kt`),
  so the id resolves locally.
- Bundled tasbih keep their **original `id`** so that re-importing, or
  importing two shares that reference the same custom tasbih, dedupes
  by identity instead of creating duplicates.
- `TasbihEntity.isBuiltIn`, `createdAt`, `updatedAt`, `isFavorite` are
  not serialized; every imported tasbih is written custom with fresh
  timestamps.
- The JSON parser uses `ignoreUnknownKeys = true`; `encodeDefaults =
  true`; `prettyPrint = true` for the file form.

### Text form

`DHIKR-ROUTINE-v1:` followed by
`Base64.encodeToString(gzip(minifiedJsonBytes), Base64.NO_WRAP)`.

- Minified JSON = the same object serialized without `prettyPrint`.
- Decode: check the exact `DHIKR-ROUTINE-v1:` prefix, strip it, base64
  decode (`Base64.DEFAULT` tolerates wrapping), gunzip, then hand the
  bytes to the same JSON parser the file path uses.
- The `-v1` in the prefix guards against a future incompatible text
  envelope; the JSON `version` field still guards the payload shape.
- Any failure (missing/exact-mismatch prefix, bad base64, bad gzip,
  bad JSON) raises `ShareFormatException` with a user-safe message and
  mutates nothing.

## Import semantics

`RoutineShareRepository.import(payload: String): ShareImportResult`.

1. Decode (file JSON text or `DHIKR-ROUTINE-v1:` string — the repo
   accepts either; it sniffs the prefix) and parse.
2. Validate: `format == "dhikr.routine"`; `version <= SHARE_VERSION`;
   `routines` non-empty; every routine `name` non-blank after trim;
   every step `targetCount >= 1`. On any failure raise
   `ShareFormatException`, no DB writes.
3. Resolve every step's `tasbihId` against (a) the ids present in the
   `tasbih` table and (b) the ids in the payload's `tasbih[]`. If any
   step resolves to **neither**, abort with
   `ShareFormatException("This shared file is incomplete.")` — no
   partial import.
4. In one `database.withTransaction { }`:
   - For each payload tasbih: if its `id` already exists in the `tasbih`
     table, **leave the existing row untouched** (count as reused).
     Otherwise insert it as `TasbihEntity(isBuiltIn = false,
     isFavorite = false, createdAt = now, updatedAt = now, ...)` (count
     as added).
   - For each payload routine: insert a new `RoutineEntity` with a
     freshly minted `UUID` id, `isPreset = false`, `isFavorite = false`,
     reminder fields at their defaults, `createdAt = updatedAt = now`.
     Insert its steps as new `RoutineStepEntity` rows (autogen `stepId`),
     `stepOrder` taken from the payload (re-normalized to 0..n-1 in
     payload order), `targetCount` from the payload.
5. Return `ShareImportResult(routinesImported, tasbihAdded,
   tasbihReused)`.

Notes:

- Fresh routine ids: re-importing the same file produces a second copy.
  This is intentional and predictable for a template. There is no
  routine-level merge.
- A name collision with an existing routine is allowed (the Routines
  list already tolerates duplicate names; the user can rename after).
- Preset routines can be shared. They export with `isPreset` stripped
  and import as ordinary custom routines on the recipient's device.

## Components

The pure / impure split follows `core/counter/WidgetCounter` (a pure
`evaluate` returning an outcome object, plus a thin caller that applies
it). All format, validation, and merge-decision logic sits in pure
functions with plain-Kotlin in/out, covered by JVM unit tests in
`app/src/test/`. `RoutineShareRepository` is the only impure unit — it
reads DAOs, calls the pure functions, writes the result in one
transaction — and is covered by the pure tests beneath it plus a
manual two-device smoke, not an instrumented test (the project has no
`androidTest` infrastructure; its verify step is a Gradle build).

| Unit | Pure? | Responsibility | Depends on |
|---|---|---|---|
| `core/share/RoutineShareModels.kt` | yes | `@Serializable` `RoutineShareFile`, `ShareRoutine`, `ShareRoutineStep`, `ShareTasbih`; `ShareImportResult`, `ImportPlan`; `SHARE_FORMAT` (`"dhikr.routine"`), `SHARE_VERSION` (`1`), `SHARE_TEXT_PREFIX` (`"DHIKR-ROUTINE-v1:"`); `ShareFormatException` | kotlinx.serialization |
| `core/share/Base64Port.kt` | no | `interface Base64Port { fun encode(bytes: ByteArray): String; fun decode(text: String): ByteArray }` + `object AndroidBase64 : Base64Port` wrapping `android.util.Base64` (`NO_WRAP` encode, `DEFAULT` decode). Keeps `android.util.*` out of the codec so the codec unit-tests with a `java.util.Base64` double | `android.util.Base64` |
| `core/share/RoutineShareCodec.kt` | yes | `encodeFile(RoutineShareFile): String` (pretty JSON), `encodeText(RoutineShareFile): String` (`SHARE_TEXT_PREFIX` + base64(gzip(minified JSON))), `decode(String): RoutineShareFile` (sniffs the prefix; else parses raw JSON). gzip via `java.util.zip`, base64 via injected `Base64Port`. Throws `ShareFormatException` | `Json`, `Base64Port` |
| `core/share/RoutineShareBuilder.kt` | yes | `build(routines: List<RoutineWithSteps>, customTasbih: List<TasbihEntity>, appVersionName: String, now: Long): RoutineShareFile` — strips ids / per-user state, bundles the passed custom tasbih, re-normalizes `stepOrder` | models |
| `core/share/RoutineImportPlanner.kt` | yes | `plan(file: RoutineShareFile, existingTasbihIds: Set<String>, now: Long, newRoutineId: () -> String): ImportPlan` — validates, resolves each step's tasbih against `existingTasbihIds` ∪ payload `tasbih[]`, decides insert-vs-reuse per bundled tasbih, mints routine ids, builds the entity lists + `ShareImportResult`. Throws `ShareFormatException` | models |
| `core/share/RoutineShareRepository.kt` | no | `buildShare(routineIds): RoutineShareFile` (DAO reads → `RoutineShareBuilder.build`); `import(payload: String): ShareImportResult` (`codec.decode` → `TasbihDao.getAllIds` → `RoutineImportPlanner.plan` → apply `ImportPlan` in `database.withTransaction`) | `RoutineDao`, `TasbihDao`, `AppDatabase`, codec, builder, planner |
| `feature/routines/RoutineShareViewModel.kt` | no | Checklist of shareable routines (id, name, isPreset), pre-checked target, toggle, select-all. `buildPayload()` runs repo + codec on `Dispatchers.IO`, emits `SharePayload(fileText, clipboardText, suggestedFileName)` | `RoutineShareRepository`, `RoutineShareCodec`, `RoutineRepository` |
| `feature/routines/RoutineImportViewModel.kt` | no | State machine `Loading -> Preview(ImportPreview) -> Working -> Done(ShareImportResult)` / `Error(message)`. `load(readText: suspend () -> String)` parses only; `confirm()` imports | `RoutineShareRepository`, `RoutineShareCodec` |
| `feature/routines/RoutineImportScreen.kt` | no | Preview UI (routine names, per-step tasbih name + target, "adds N tasbih" line), Import / Cancel, result summary, then pop | `RoutineImportViewModel` |
| `feature/routines/RoutinesScreen.kt` | no | `Share` row in `RoutineActionMenu`; the routine-checklist dialog; an `Import routine` entry (header area) that offers *Pick file* / *Paste text*; owns the `Intent` / `FileProvider` / clipboard wiring | `RoutineShareViewModel` |
| `MainActivity.kt` | no | `pendingShareUri: Uri?` set from an `ACTION_VIEW` launch / new intent, consumed once | — |
| `DhikrApp.kt` | no | New `routines/import` route; `LaunchedEffect(pendingShareUri)` navigates to it; passes a `readText` lambda that reads the uri via `ContentResolver` | — |
| `AndroidManifest.xml` | no | `<provider>` `androidx.core.content.FileProvider` (`${applicationId}.fileprovider`); `MainActivity` `intent-filter` for `ACTION_VIEW` | — |
| `res/xml/file_paths.xml` | no | `cache-path` entry for `shared/` | — |

### Share sheet plumbing

`RoutinesScreen` (which already holds a `Context`) does the Android
wiring, mirroring `SettingsScreen`'s `BackupControls`:

- **Send file:** write `fileText` to
  `File(context.cacheDir, "shared/<name>.dhikrroutine")`, get a
  `content://` uri from `FileProvider.getUriForFile`, fire
  `Intent(ACTION_SEND){ type = "application/json"; putExtra(EXTRA_STREAM,
  uri); addFlags(FLAG_GRANT_READ_URI_PERMISSION) }` via
  `Intent.createChooser`.
- **Copy as text:** put `clipboardText` on the `ClipboardManager`, show a
  confirmation snackbar/inline text.

The ViewModel never touches a `Context`, `Uri`, `Intent`, or clipboard —
same split as `BackupViewModel`.

### Import intent filter

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="content" android:mimeType="application/json" />
    <data android:scheme="file"    android:mimeType="application/json" />
</intent-filter>
```

`application/json` is broad, so the import preview must handle "this
isn't ours" gracefully: `RoutineShareCodec.decode` throwing
`ShareFormatException` puts `RoutineImportViewModel` in `Error` with
"This isn't a Dhikr routine file." and no DB change. A
`pathPattern`/extension filter is unreliable for `content://` uris
(no path suffix guaranteed), so MIME is the filter and the parser is
the real gate.

### New / reused DAO methods

`RoutineDao`:

- add `@Transaction @Query("SELECT * FROM routine WHERE id IN (:ids)")
  suspend fun getManyWithSteps(ids: List<String>): List<RoutineWithSteps>`
  for `buildShare`.
- reuse `insertRoutine` (`IGNORE`), `insertSteps` (`IGNORE`).

`TasbihDao`:

- add `@Query("SELECT * FROM tasbih WHERE id IN (:ids)")
  suspend fun getByIds(ids: List<String>): List<TasbihEntity>` — used by
  `buildShare` to pick out the custom (`isBuiltIn = 0`) referenced ids.
- reuse `getAllIds()` (the planner's `existingTasbihIds`) and
  `insertAll()` (already `@Insert(onConflict = IGNORE)`) — the planner
  only ever hands over tasbih whose ids are absent, so `IGNORE` is a
  safe apply.

## Data flow

### Share

1. Long-press a routine → `RoutineActionMenu` → `Share`.
2. A dialog lists every routine (name, "preset" tag) with checkboxes;
   the long-pressed one is checked. User adjusts, taps `Share`.
3. `RoutineShareViewModel.buildPayload()` → `Working` →
   `repo.buildShare(checkedIds)` on IO:
   - `routineDao.getManyWithSteps(checkedIds)`,
   - collect referenced `tasbihId`s, `tasbihDao.getByIds(those)`,
     keep the ones with `isBuiltIn == 0`,
   - `RoutineShareBuilder.build(routines, customTasbih, appVersion, now)`.
4. VM emits `fileText = codec.encodeFile(file)`,
   `clipboardText = codec.encodeText(file)`,
   `suggestedFileName` = the single routine's name slug + `.dhikrroutine`,
   or `dhikr-routines-<yyyy-MM-dd>.dhikrroutine` for 2+.
5. `RoutinesScreen` shows a small sheet: **Send file** / **Copy as text**.

### Import (file intent)

1. User taps a `.dhikrroutine` in Files / Gmail / a chat app.
2. `MainActivity` receives `ACTION_VIEW`, stores `intent.data` in
   `pendingShareUri`.
3. `DhikrApp`'s `LaunchedEffect(pendingShareUri)` navigates to
   `routines/import`, then calls `onPendingShareConsumed()`.
4. The route builds `RoutineImportViewModel` and calls
   `load { contentResolver.openInputStream(uri)!!.reader().readText() }`.
5. VM: `Loading` → `repo`-parse (parse only, no writes) → `Preview` with
   a summary object (routine names, step rows resolved to tasbih display
   names using the payload's own `tasbih[]` plus a `TasbihDao` lookup
   for built-ins, count of not-yet-present tasbih).
6. User taps `Import` → `Working` → `repo.import(rawText)` →
   `Done(ShareImportResult)`.
7. Screen shows "Added 2 routines, 3 new tasbih." and a `Done` button
   that pops back to the Routines list (which reactively shows the new
   rows).

### Import (in-app)

Same screen and VM. The Routines header exposes `Import routine`:

- **Pick file:** `ActivityResultContracts.OpenDocument(arrayOf("application/json"))`
  → navigate to `routines/import` with a uri-reading lambda.
- **Paste text:** a dialog with a multiline field; on confirm, navigate
  with a `{ pastedText }` lambda.

## Error handling

| Situation | Behavior |
|---|---|
| Share sheet / SAF dialog cancelled | Silent return; no state change. |
| `openInputStream` null or throws | `Error("Couldn't read that file.")` |
| Prefix / base64 / gzip / JSON parse failure | `Error("This isn't a Dhikr routine file.")` — no DB write. |
| `format` mismatch | Same message as above. |
| `version` > `SHARE_VERSION` | `Error("This routine was shared from a newer version of the app.")` |
| Step references a tasbih neither present nor bundled | `Error("This shared file is incomplete.")` — no DB write. |
| Empty `routines`, blank name, `targetCount < 1` | `Error("This shared file is incomplete.")` |
| Exception inside the Room transaction | Whole import rolls back; `Error("Import failed. Your routines haven't changed.")` |
| `FileProvider` write fails on share | Inline `Couldn't prepare the file to share.`; text copy still offered. |

Nothing in the import path mutates the database until `confirm()`, and
that mutation is a single transaction.

## Manifest / Gradle

- **No new Gradle dependency.** `java.util.zip.GZIPOutputStream` /
  `GZIPInputStream` are `java.base` (all API levels).
  `android.util.Base64` is a platform API, reached only through
  `Base64Port` so the codec stays JVM-testable. `androidx.core`
  (already present) provides `FileProvider`.
- `AndroidManifest.xml`: add the `FileProvider` `<provider>` with
  authority `${applicationId}.fileprovider`, and the `ACTION_VIEW`
  `intent-filter` on `MainActivity`.
- `res/xml/file_paths.xml`: `<cache-path name="shared" path="shared/" />`.
- The Settings "About" claims ("works offline", "no account", "never
  uploads") stay accurate — sharing writes a local file the user
  explicitly sends and contacts no server.

## Testing

All automated tests are JVM unit tests in `app/src/test/` (JUnit 4,
`kotlinx-coroutines-test` — the harness added in commit c8324e0). No
`androidTest` source set is created. The repository's correctness is
covered by exhaustive tests of the pure planner / builder / codec it
delegates to, plus a manual two-device smoke.

`RoutineShareModelsTest`:

- Serialize a fully populated `RoutineShareFile`, deserialize, assert
  equality.
- JSON with an unknown extra key deserializes without throwing.

`RoutineShareCodecTest` (constructed with a `java.util.Base64`-backed
`Base64Port` double):

- `encodeFile` then `decode` → equal `RoutineShareFile`.
- `encodeText` then `decode` → equal object; output is one line and
  starts with `DHIKR-ROUTINE-v1:`.
- `decode` accepts a raw pretty-JSON file body (no prefix).
- `decode` throws `ShareFormatException` on each of: wrong prefix
  (`DHIKR-ROUTINE-v2:...`), truncated base64 after the prefix, valid
  base64 of non-gzip bytes, `{}`, `{"format":"dhikr.backup","version":1}`.

`RoutineShareBuilderTest`:

- `build` on a `RoutineWithSteps` list → `isPreset` / routine id /
  reminder fields absent from the DTOs; `stepOrder` re-normalized to
  `0..n-1` in list order; every passed custom tasbih present in
  `tasbih[]`; `format` / `version` / `appVersionName` / `createdAt` set.

`RoutineImportPlannerTest` (the core logic — `plan` takes plain
values, `newRoutineId` is a stub counter):

- **Happy path:** one routine, one built-in step + one custom-tasbih
  step, `existingTasbihIds` holds the built-in id → `ImportPlan` has one
  `RoutineEntity` (`isPreset = false`, `isFavorite = false`,
  reminders default, id from the stub), two `RoutineStepEntity`
  (`stepOrder` 0,1; `stepId = 0`), one `TasbihEntity` to insert
  (`isBuiltIn = false`, original id, `now` timestamps); result
  `routinesImported = 1, tasbihAdded = 1, tasbihReused = 0`.
- **Reuse:** the bundled tasbih's id is already in `existingTasbihIds`
  → no `TasbihEntity` in the plan, `tasbihReused = 1`.
- **Built-in-only routine:** empty `tasbih[]`, step id in
  `existingTasbihIds` → plan has no tasbih inserts, one routine.
- **Multi-routine:** two routines → two distinct minted ids, steps
  attributed to the right routine id.
- **Incomplete:** a step id in neither set → throws
  `ShareFormatException`.
- **Validation:** empty `routines`; blank routine `name`;
  step `targetCount = 0`; `version = 999`; `format = "dhikr.backup"`
  → each throws `ShareFormatException`.

### Manual smoke (recorded in the task's report, not automated)

Two emulators (or one device, sharing to itself via Files): share a
routine that has a custom tasbih step — once as a file, once via "Copy
as text" pasted into the import dialog. Confirm the preview lists the
right steps, Import creates the routine and the custom tasbih, and a
second import of the same payload makes a second routine without
duplicating the tasbih.
