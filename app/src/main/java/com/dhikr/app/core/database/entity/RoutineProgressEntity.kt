package com.dhikr.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * One row per routine holding the in-progress position for the current local
 * calendar day: which step is active and how far into that step the count has
 * reached. Unlike the single saved session in DataStore, this survives opening
 * a different routine — each routine keeps its own row — so a half-finished
 * routine is still where you left it when you come back to it.
 *
 * The row is day-stamped ([dayStartMillis]). Reads filter on today's midnight,
 * so any row left over from a previous day is ignored (and cleaned up on the
 * next write) — that is the "reset at midnight" behaviour.
 */
@Entity(
    tableName = "routine_progress",
    primaryKeys = ["routineId"],
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RoutineProgressEntity(
    val routineId: String,
    /** Local midnight (millis) of the day this progress belongs to. */
    val dayStartMillis: Long,
    /** Index (0-based, in stepOrder) of the step currently in progress. */
    val stepIndex: Int,
    /** Tap count reached within the current step. */
    val countInStep: Int,
    /**
     * Portion of [countInStep] already written to permanent History. Mirrors
     * CounterViewModel's `loggedTotal` for this step so resuming the routine
     * doesn't re-log taps that were logged when the screen was last left.
     */
    val loggedInStep: Int,
    val updatedAt: Long,
)
