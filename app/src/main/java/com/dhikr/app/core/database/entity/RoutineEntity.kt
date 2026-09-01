package com.dhikr.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routine",
    indices = [Index(value = ["isFavorite"], name = "idx_routine_favorite")],
)
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isPreset: Boolean,
    val isFavorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val reminderEnabled: Boolean = false,
    /** Local wall-clock minute of day, 0..1439. */
    val reminderMinuteOfDay: Int = 0,
    /** 7-bit weekday mask; bit 0 = Sunday .. bit 6 = Saturday. 0 = every day. */
    val reminderDays: Int = 0,
)
