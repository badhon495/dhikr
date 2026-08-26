package com.dhikr.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasbih",
    indices = [Index(value = ["isFavorite"], name = "idx_tasbih_favorite")],
)
data class TasbihEntity(
    @PrimaryKey val id: String,
    val name: String,
    val arabic: String,
    val transliteration: String,
    val translation: String,
    val note: String = "",
    val source: String? = null,
    val lapTarget: Int,
    val lapCount: Int,
    val dailyGoal: Int? = null,
    val isFavorite: Boolean = false,
    val isBuiltIn: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
