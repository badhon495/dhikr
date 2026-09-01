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
