package com.dhikr.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dhikr.app.core.model.CounterSessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.sessionDataStore by preferencesDataStore(name = "session")

class SessionRepository(private val context: Context) {

    private object Keys {
        val ACTIVE_DHIKR_ID = stringPreferencesKey("active_dhikr_id")
        val COUNT = intPreferencesKey("count")
        val LAP = intPreferencesKey("lap")
        val PREVIOUS_COUNT = intPreferencesKey("previous_count")
        val PREVIOUS_LAP = intPreferencesKey("previous_lap")
        val RUNNING = booleanPreferencesKey("running")
        val ELAPSED_SECONDS = intPreferencesKey("elapsed_seconds")
        val LOCKED = booleanPreferencesKey("locked")
        val ROUTINE_ID = stringPreferencesKey("routine_id")
        val ROUTINE_STEP = intPreferencesKey("routine_step")
        val LOGGED_TOTAL = intPreferencesKey("logged_total")
    }

    val sessionFlow: Flow<CounterSessionState?> = context.sessionDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            val activeId = prefs[Keys.ACTIVE_DHIKR_ID] ?: return@map null
            CounterSessionState(
                activeDhikrId = activeId,
                count = prefs[Keys.COUNT] ?: 0,
                lap = prefs[Keys.LAP] ?: 1,
                previousCount = prefs[Keys.PREVIOUS_COUNT],
                previousLap = prefs[Keys.PREVIOUS_LAP],
                running = prefs[Keys.RUNNING] ?: true,
                elapsedSeconds = prefs[Keys.ELAPSED_SECONDS] ?: 0,
                locked = prefs[Keys.LOCKED] ?: false,
                routineId = prefs[Keys.ROUTINE_ID],
                routineStep = prefs[Keys.ROUTINE_STEP] ?: 0,
                loggedTotal = prefs[Keys.LOGGED_TOTAL] ?: 0,
            )
        }

    suspend fun save(state: CounterSessionState) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_DHIKR_ID] = state.activeDhikrId
            prefs[Keys.COUNT] = state.count
            prefs[Keys.LAP] = state.lap
            if (state.previousCount != null) {
                prefs[Keys.PREVIOUS_COUNT] = state.previousCount
            } else {
                prefs.remove(Keys.PREVIOUS_COUNT)
            }
            if (state.previousLap != null) {
                prefs[Keys.PREVIOUS_LAP] = state.previousLap
            } else {
                prefs.remove(Keys.PREVIOUS_LAP)
            }
            prefs[Keys.RUNNING] = state.running
            prefs[Keys.ELAPSED_SECONDS] = state.elapsedSeconds
            prefs[Keys.LOCKED] = state.locked
            if (state.routineId != null) {
                prefs[Keys.ROUTINE_ID] = state.routineId
            } else {
                prefs.remove(Keys.ROUTINE_ID)
            }
            prefs[Keys.ROUTINE_STEP] = state.routineStep
            prefs[Keys.LOGGED_TOTAL] = state.loggedTotal
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
