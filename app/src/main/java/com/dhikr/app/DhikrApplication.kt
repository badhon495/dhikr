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
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
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
