package com.dhikr.app.core.backup

import androidx.room.withTransaction
import com.dhikr.app.core.database.AppDatabase
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.datastore.CounterScript
import com.dhikr.app.core.datastore.HapticMode
import com.dhikr.app.core.datastore.ThemeMode
import kotlinx.serialization.json.Json

/** Thrown by [BackupRepository.restore] when the file isn't a backup this app
 *  can read. The message is safe to show to the user. */
class BackupFormatException(message: String) : Exception(message)

/**
 * Builds and applies backup files. A backup carries the user's custom tasbih,
 * custom routines, favourite flags on built-in tasbih, session history and app
 * preferences — everything the app can't rebuild from seed data.
 *
 * Restore uses merge semantics: rows are upserted by primary key, so a backup
 * item with an id that already exists overwrites the local one, and anything
 * created since the backup is left in place.
 */
class BackupRepository(
    private val database: AppDatabase,
    private val preferencesRepository: AppPreferencesRepository,
) {
    // Matches SettingsViewModel's clamp; kept local to avoid a core -> feature
    // dependency. A restored goal outside this range is coerced in.
    private val goalRange = 1..99_999

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(appVersionName: String): String {
        val tasbihDao = database.tasbihDao()
        val routineDao = database.routineDao()
        val sessionDao = database.sessionDao()

        val prefs = preferencesRepository.snapshot()

        val file = BackupFile(
            createdAt = System.currentTimeMillis(),
            appVersionName = appVersionName,
            customTasbih = tasbihDao.getAllCustom().map(BackupTasbih::from),
            builtInFavorites = tasbihDao.getBuiltInFavoriteIds(),
            customRoutines = routineDao.getAllCustomWithSteps().map { rws ->
                BackupRoutine(
                    id = rws.routine.id,
                    name = rws.routine.name,
                    createdAt = rws.routine.createdAt,
                    updatedAt = rws.routine.updatedAt,
                    isFavorite = rws.routine.isFavorite,
                    steps = rws.steps
                        .sortedBy { it.stepOrder }
                        .map(BackupRoutineStep::from),
                )
            },
            sessions = sessionDao.getAll().map(BackupSession::from),
            preferences = BackupPreferences(
                dailyGoalTarget = prefs.dailyGoalTarget,
                themeMode = prefs.themeMode.name,
                hapticMode = prefs.hapticMode.name,
                reducedMotion = prefs.reducedMotion,
                dynamicColor = prefs.dynamicColorEnabled,
                counterScript = prefs.counterScript.name,
            ),
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    /** @throws BackupFormatException if [content] isn't a readable backup. */
    suspend fun restore(content: String): RestoreResult {
        val file = parse(content)

        val tasbihDao = database.tasbihDao()
        val routineDao = database.routineDao()
        val sessionDao = database.sessionDao()

        var sessionsRestored = 0
        var sessionsSkipped = 0

        database.withTransaction {
            tasbihDao.upsertAll(file.customTasbih.map(BackupTasbih::toEntity))

            file.builtInFavorites.forEach { id ->
                // Only marks a favourite if the id actually exists as a
                // built-in on this device; a no-op otherwise.
                tasbihDao.setFavorite(id, true)
            }

            file.customRoutines.forEach { routine ->
                routineDao.upsertRoutine(routine.toEntity())
                routineDao.replaceSteps(
                    routine.id,
                    routine.steps.map { it.toEntity(routine.id) },
                )
            }

            val validTasbihIds = tasbihDao.getAllIds().toHashSet()
            file.sessions.forEach { session ->
                if (session.tasbihId in validTasbihIds) {
                    sessionDao.insert(session.toEntity())
                    sessionsRestored++
                } else {
                    sessionsSkipped++
                }
            }
        }

        val preferencesApplied = runCatching {
            preferencesRepository.restore(
                dailyGoalTarget = file.preferences.dailyGoalTarget
                    ?.coerceIn(goalRange.first, goalRange.last),
                themeMode = file.preferences.themeMode.toThemeMode(),
                hapticMode = file.preferences.hapticMode.toHapticMode(),
                reducedMotion = file.preferences.reducedMotion,
                dynamicColorEnabled = file.preferences.dynamicColor,
                counterScript = file.preferences.counterScript.toCounterScript(),
            )
        }.isSuccess

        return RestoreResult(
            tasbihRestored = file.customTasbih.size,
            routinesRestored = file.customRoutines.size,
            sessionsRestored = sessionsRestored,
            sessionsSkipped = sessionsSkipped,
            preferencesApplied = preferencesApplied,
        )
    }

    private fun parse(content: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), content)
        } catch (e: Exception) {
            throw BackupFormatException("This isn't a valid Dhikr backup file.")
        }
        if (file.format != BACKUP_FORMAT) {
            throw BackupFormatException("This isn't a valid Dhikr backup file.")
        }
        if (file.version > BACKUP_VERSION) {
            throw BackupFormatException(
                "This backup was made by a newer version of the app.",
            )
        }
        return file
    }

    private fun String?.toThemeMode(): ThemeMode? = when (this) {
        ThemeMode.LIGHT.name -> ThemeMode.LIGHT
        ThemeMode.DARK.name -> ThemeMode.DARK
        ThemeMode.SYSTEM.name -> ThemeMode.SYSTEM
        else -> null
    }

    private fun String?.toHapticMode(): HapticMode? = when (this) {
        HapticMode.OFF.name -> HapticMode.OFF
        HapticMode.EVERY_TAP.name -> HapticMode.EVERY_TAP
        HapticMode.LAP_ONLY.name -> HapticMode.LAP_ONLY
        else -> null
    }

    private fun String?.toCounterScript(): CounterScript? = when (this) {
        CounterScript.PRONUNCIATION.name -> CounterScript.PRONUNCIATION
        CounterScript.ARABIC.name -> CounterScript.ARABIC
        else -> null
    }
}
