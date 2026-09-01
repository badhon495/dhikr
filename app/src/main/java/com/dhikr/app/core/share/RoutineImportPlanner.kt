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
