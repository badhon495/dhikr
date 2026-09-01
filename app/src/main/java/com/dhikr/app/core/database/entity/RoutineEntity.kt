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
)
