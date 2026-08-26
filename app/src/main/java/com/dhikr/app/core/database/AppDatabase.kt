package com.dhikr.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase()
