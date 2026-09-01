package com.dhikr.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dhikr.app.core.database.dao.RoutineCompletionDao
import com.dhikr.app.core.database.dao.RoutineDao
import com.dhikr.app.core.database.dao.RoutineProgressDao
import com.dhikr.app.core.database.dao.SessionDao
import com.dhikr.app.core.database.dao.TasbihDao
import com.dhikr.app.core.database.dao.TasbihProgressDao
import com.dhikr.app.core.database.entity.RoutineCompletionEntity
import com.dhikr.app.core.database.entity.RoutineEntity
import com.dhikr.app.core.database.entity.RoutineProgressEntity
import com.dhikr.app.core.database.entity.RoutineStepEntity
import com.dhikr.app.core.database.entity.SessionEntity
import com.dhikr.app.core.database.entity.TasbihEntity
import com.dhikr.app.core.database.entity.TasbihProgressEntity

@Database(
    entities = [
        TasbihEntity::class,
        RoutineEntity::class,
        RoutineStepEntity::class,
        SessionEntity::class,
        RoutineCompletionEntity::class,
        RoutineProgressEntity::class,
        TasbihProgressEntity::class,
    ],
    // v2: no schema change from v1, but the version had been left at 1 across
    // several real schema edits (session/routine tables added without a bump).
    // Bumping now gives Room a version delta so an older on-device database
    // hits fallbackToDestructiveMigration (below) and is rebuilt+reseeded
    // instead of crashing the app on launch with an identity-hash mismatch.
    // v3: added routine_completion table (per-day "routine done" markers).
    // v4: renamed TasbihEntity.transliteration -> pronunciation. No hand
    // migration — fallbackToDestructiveMigration rebuilds + reseeds.
    // v5: added RoutineEntity.isFavorite (+ index). No hand migration —
    // fallbackToDestructiveMigration rebuilds + reseeds.
    // v6: added routine_progress table (per-day in-progress routine position).
    // No hand migration — fallbackToDestructiveMigration rebuilds + reseeds.
    // v7: added RoutineEntity.reminderEnabled / reminderMinuteOfDay /
    // reminderDays (per-routine local reminder). No hand migration —
    // fallbackToDestructiveMigration rebuilds + reseeds.
    // v8: added tasbih_progress table (per-day in-progress counting position).
    // No hand migration — fallbackToDestructiveMigration rebuilds + reseeds.
    version = 8,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasbihDao(): TasbihDao
    abstract fun routineDao(): RoutineDao
    abstract fun sessionDao(): SessionDao
    abstract fun routineCompletionDao(): RoutineCompletionDao
    abstract fun routineProgressDao(): RoutineProgressDao
    abstract fun tasbihProgressDao(): TasbihProgressDao
}
