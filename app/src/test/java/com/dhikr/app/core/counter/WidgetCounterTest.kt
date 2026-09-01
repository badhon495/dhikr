package com.dhikr.app.core.counter

import com.dhikr.app.core.database.entity.TasbihEntity
import com.dhikr.app.core.model.CounterSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private fun tasbih(id: String, lapTarget: Int, lapCount: Int) = TasbihEntity(
    id = id, name = "x", arabic = "", pronunciation = "", translation = "",
    lapTarget = lapTarget, lapCount = lapCount, isBuiltIn = true,
    createdAt = 0L, updatedAt = 0L,
)

private fun session(
    dhikrId: String, count: Int, lap: Int, loggedTotal: Int,
    routineId: String? = null,
) = CounterSessionState(
    activeDhikrId = dhikrId, count = count, lap = lap,
    previousCount = null, previousLap = null, running = true,
    elapsedSeconds = 0, locked = false, routineId = routineId,
    routineStep = 0, loggedTotal = loggedTotal,
)

class WidgetCounterTest {

    @Test
    fun nonRoutineSession_advancesByOne_andSyncsLoggedTotal() {
        val out = WidgetCounter.evaluate(
            state = session("a", count = 5, lap = 1, loggedTotal = 5),
            tasbih = tasbih("a", lapTarget = 33, lapCount = 3),
        )
        out as WidgetCounter.Outcome.Apply
        assertEquals(6, out.newState.count)
        assertEquals(1, out.newState.lap)
        assertEquals(6, out.engineTotal)
        assertEquals(out.engineTotal, out.newState.loggedTotal)
    }

    @Test
    fun lapBoundaryTap_rollsLap_countResetsToZero() {
        val out = WidgetCounter.evaluate(
            state = session("a", count = 32, lap = 1, loggedTotal = 32),
            tasbih = tasbih("a", lapTarget = 33, lapCount = 3),
        )
        out as WidgetCounter.Outcome.Apply
        assertEquals(0, out.newState.count)
        assertEquals(2, out.newState.lap)
        assertEquals(33, out.engineTotal)
        assertEquals(33, out.newState.loggedTotal)
    }

    @Test
    fun routineSession_isNoOp() {
        val out = WidgetCounter.evaluate(
            state = session("a", count = 1, lap = 1, loggedTotal = 1, routineId = "r1"),
            tasbih = tasbih("a", lapTarget = 33, lapCount = 3),
        )
        assertSame(WidgetCounter.Outcome.NoOp, out)
    }

    @Test
    fun nullSession_isNoOp() {
        assertSame(WidgetCounter.Outcome.NoOp, WidgetCounter.evaluate(null, tasbih("a", 33, 3)))
    }

    @Test
    fun unknownTasbih_isNoOp() {
        val out = WidgetCounter.evaluate(session("a", 1, 1, 1), tasbih = null)
        assertSame(WidgetCounter.Outcome.NoOp, out)
    }

    @Test
    fun tasbihIdMismatch_isNoOp() {
        val out = WidgetCounter.evaluate(session("a", 1, 1, 1), tasbih("b", 33, 3))
        assertSame(WidgetCounter.Outcome.NoOp, out)
    }

    @Test
    fun previousPointers_areCarriedFromSnapshot() {
        val out = WidgetCounter.evaluate(
            state = session("a", count = 5, lap = 1, loggedTotal = 5),
            tasbih = tasbih("a", lapTarget = 33, lapCount = 3),
        )
        out as WidgetCounter.Outcome.Apply
        assertEquals(5, out.newState.previousCount)
        assertEquals(1, out.newState.previousLap)
        assertTrue(out.newState.running)
    }
}
