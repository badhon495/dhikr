package com.dhikr.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * One row per (routine, local calendar day) on which the routine's last step
 * was completed. The composite primary key makes a second completion on the
 * same day a no-op insert, so the "green today" state is sticky and set once.
 */
@Entity(
    tableName = "routine_completion",
    primaryKeys = ["routineId", "dayStartMillis"],
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RoutineCompletionEntity(
    val routineId: String,
    /** Local midnight (millis) of the day the routine was completed. */
    val dayStartMillis: Long,
)
