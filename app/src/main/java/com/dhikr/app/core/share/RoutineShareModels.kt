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
