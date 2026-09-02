package com.dhikr.app.core.database.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// All fields are primitives / String / nullable primitives — genuinely
// immutable. Annotated so Compose treats CounterUiState (which holds one) as
// stable and can skip counter-screen recompositions.
@Immutable
@Entity(
    tableName = "tasbih",
    indices = [Index(value = ["isFavorite"], name = "idx_tasbih_favorite")],
)
data class TasbihEntity(
    @PrimaryKey val id: String,
    val name: String,
    val arabic: String,
    val pronunciation: String,
    val translation: String,
    val note: String = "",
    val source: String? = null,
    val lapTarget: Int,
    val lapCount: Int,
    val dailyGoal: Int? = null,
    val isFavorite: Boolean = false,
    /** Cached Gemini-generated virtues/benefits text for this dhikr; null until generated. */
    val benefitsText: String? = null,
    /** Epoch millis when [benefitsText] was generated; null when absent. */
    val benefitsGeneratedAt: Long? = null,
    val isBuiltIn: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
