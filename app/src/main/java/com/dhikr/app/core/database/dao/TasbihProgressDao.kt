package com.dhikr.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dhikr.app.core.database.entity.TasbihProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TasbihProgressDao {

    /** Upsert — one row per Tasbih, overwritten as the count advances. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: TasbihProgressEntity)

    @Query("SELECT * FROM tasbih_progress WHERE tasbihId = :tasbihId AND dayStartMillis = :dayStartMillis LIMIT 1")
    suspend fun getForTasbih(tasbihId: String, dayStartMillis: Long): TasbihProgressEntity?

    @Query("SELECT * FROM tasbih_progress WHERE dayStartMillis = :dayStartMillis")
    fun observeForDay(dayStartMillis: Long): Flow<List<TasbihProgressEntity>>

    @Query("DELETE FROM tasbih_progress WHERE tasbihId = :tasbihId")
    suspend fun deleteForTasbih(tasbihId: String)

    /** Drops rows from previous days so the table doesn't accumulate stale progress. */
    @Query("DELETE FROM tasbih_progress WHERE dayStartMillis <> :dayStartMillis")
    suspend fun deleteStale(dayStartMillis: Long)
}
