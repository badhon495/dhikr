package com.dhikr.app.core.counter

import android.content.Context
import com.dhikr.app.DhikrApplication
import com.dhikr.app.core.database.HistoryRepository
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.database.entity.TasbihEntity
import com.dhikr.app.core.datastore.SessionRepository
import com.dhikr.app.core.model.CounterSessionState
import kotlinx.coroutines.flow.first

/**
 * The counter widget's [+] path. [evaluate] is the pure engine-math decision
 * (unit-tested); [applyIncrement] wires the repositories the same way
 * ReminderReceiver does and performs the writes.
 *
 * Invariant after an Apply: newState.loggedTotal == engineTotal, so when the
 * app next opens this session CounterViewModel.logCurrentSessionIfNonZero()
 * computes unlogged == 0 and logs nothing — no double count. History gains one
 * count = 1 row per widget tap.
 *
 * No haptics, no sound, no timer advance (elapsedSeconds untouched): a widget
 * tap is not an in-app tap.
 */
object WidgetCounter {

    sealed interface Outcome {
        data object NoOp : Outcome
        data class Apply(val newState: CounterSessionState, val engineTotal: Int) : Outcome
    }

    sealed interface Result {
        data object Applied : Result
        data object NoOp : Result
    }

    fun evaluate(state: CounterSessionState?, tasbih: TasbihEntity?): Outcome {
        if (state == null || state.routineId != null) return Outcome.NoOp
        if (tasbih == null || tasbih.id != state.activeDhikrId) return Outcome.NoOp

        val engine = TasbihCounter(tasbih.lapTarget, tasbih.lapCount)
        val previous = if (state.previousCount != null && state.previousLap != null) {
            state.previousCount to state.previousLap
        } else {
            null
        }
        engine.restore(count = state.count, lap = state.lap, previous = previous)
        val snap = engine.increment()
        val total = engine.totalCount()
        return Outcome.Apply(
            newState = state.copy(
                count = snap.count,
                lap = snap.lap,
                previousCount = snap.previousCount,
                previousLap = snap.previousLap,
                loggedTotal = total,
            ),
            engineTotal = total,
        )
    }

    suspend fun applyIncrement(context: Context): Result {
        val app = context.applicationContext as DhikrApplication
        val sessionRepository = SessionRepository(context.applicationContext)
        val tasbihRepository = TasbihRepository(
            app.database.tasbihDao(),
            app.database.routineDao(),
            app.database.tasbihProgressDao(),
            app.database.sessionDao(),
        )
        val historyRepository = HistoryRepository(app.database.sessionDao(), tasbihRepository)

        val state = sessionRepository.sessionFlow.first()
        if (state == null || state.routineId != null) return Result.NoOp
        val tasbih = tasbihRepository.getById(state.activeDhikrId) ?: return Result.NoOp

        val outcome = evaluate(state, tasbih)
        if (outcome !is Outcome.Apply) return Result.NoOp

        val now = System.currentTimeMillis()
        historyRepository.logSession(
            tasbihId = tasbih.id,
            routineId = null,
            count = 1,
            startedAt = now,
            endedAt = now,
        )
        sessionRepository.save(outcome.newState)
        tasbihRepository.saveSessionProgress(
            tasbihId = tasbih.id,
            count = outcome.newState.count,
            lap = outcome.newState.lap,
            loggedInSession = outcome.engineTotal,
        )
        return Result.Applied
    }
}
