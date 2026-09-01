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
