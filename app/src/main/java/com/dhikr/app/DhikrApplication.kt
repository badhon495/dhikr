package com.dhikr.app

import android.app.Application
import androidx.room.Room
import com.dhikr.app.core.database.AppDatabase
import com.dhikr.app.core.database.seed.SeedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DhikrApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, "dhikr.db")
            // The DB carries only seeded/derived data (built-in dhikr, presets,
            // session history) — all rebuildable. If a restored backup or an
            // old install leaves a schema Room can't reconcile, wipe and
            // reseed rather than crash on launch.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        com.dhikr.app.core.notifications.ReminderNotifications.ensureChannel(this)
        // B7: the seed check/insert are suspend Room calls — dispatch on IO,
        // not Default. Room runs the actual SQLite work on its own executor,
        // but the coroutine still shouldn't sit on a CPU-bound dispatcher.
        applicationScope.launch(Dispatchers.IO) {
            if (database.tasbihDao().count() == 0) {
                database.tasbihDao().insertAll(SeedData.builtInTasbih)
            }
            if (database.routineDao().count() == 0) {
                database.routineDao().insertRoutines(SeedData.presetRoutines)
                database.routineDao().insertSteps(SeedData.presetRoutineSteps)
            }
        }
    }
}
