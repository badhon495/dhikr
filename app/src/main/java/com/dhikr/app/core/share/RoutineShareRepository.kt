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
