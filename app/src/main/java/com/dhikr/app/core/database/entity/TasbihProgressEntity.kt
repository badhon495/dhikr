package com.dhikr.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * One row per Tasbih holding the in-progress counting position for the current
 * local calendar day: engine count + lap, plus how much of it has already been
 * written to History. Unlike the single saved session in DataStore, this
 * survives opening a different Tasbih — each Tasbih keeps its own row — so a
 * half-finished count is restored where you left it when you come back to it.
 *
 * This is the engine-resume store only; the Tasbih card's progress fill is
 * sourced separately from logged History totals (see
 * TasbihRepository.observeSessionProgressToday).
 *
 * Day-stamped ([dayStartMillis]); the resume read filters on today's midnight
 * so a row left from a previous day is ignored (and cleaned up on the next
 * write) — that is the "reset at midnight" behaviour.
 */
@Entity(
    tableName = "tasbih_progress",
    primaryKeys = ["tasbihId"],
    foreignKeys = [
        ForeignKey(
            entity = TasbihEntity::class,
            parentColumns = ["id"],
            childColumns = ["tasbihId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TasbihProgressEntity(
    val tasbihId: String,
    /** Local midnight (millis) of the day this progress belongs to. */
    val dayStartMillis: Long,
    /** Engine count within the current lap. */
    val count: Int,
    /** Current lap (1-based). */
    val lap: Int,
    /**
     * Cumulative count already written to permanent History for this session.
     * Mirrors CounterViewModel's `loggedTotal` so resuming doesn't re-log taps
     * that were logged when the screen was last left.
     */
    val loggedInSession: Int,
    val updatedAt: Long,
)
