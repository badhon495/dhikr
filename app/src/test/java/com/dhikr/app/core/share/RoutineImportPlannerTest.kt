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
