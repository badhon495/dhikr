package com.dhikr.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routine")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isPreset: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
