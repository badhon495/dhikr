package com.dhikr.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dhikr.app.core.database.entity.TasbihEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TasbihDao {

    @Query("SELECT * FROM tasbih ORDER BY isFavorite DESC, isBuiltIn DESC, name ASC")
    fun observeAll(): Flow<List<TasbihEntity>>

    @Query("SELECT * FROM tasbih WHERE isFavorite = 1 ORDER BY name ASC")
    fun observeFavorites(): Flow<List<TasbihEntity>>

    @Query("SELECT * FROM tasbih WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TasbihEntity?

    @Query(
        """
        SELECT * FROM tasbih
        WHERE name LIKE '%' || :query || '%'
           OR arabic LIKE '%' || :query || '%'
           OR pronunciation LIKE '%' || :query || '%'
           OR translation LIKE '%' || :query || '%'
        ORDER BY isFavorite DESC, name ASC
        """
    )
    fun search(query: String): Flow<List<TasbihEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tasbih: TasbihEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tasbih: List<TasbihEntity>)

    /** Backup restore: an id already present is overwritten by the backup row
     *  (merge semantics, backup wins). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasbih: List<TasbihEntity>)

    @Query("SELECT * FROM tasbih")
    suspend fun getAll(): List<TasbihEntity>

    @Query("SELECT * FROM tasbih WHERE isBuiltIn = 0")
    suspend fun getAllCustom(): List<TasbihEntity>

    /** Routine sharing: pick out the tasbih a shared routine's steps reference. */
    @Query("SELECT * FROM tasbih WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<TasbihEntity>

    @Query("SELECT id FROM tasbih WHERE isBuiltIn = 1 AND isFavorite = 1")
    suspend fun getBuiltInFavoriteIds(): List<String>

    @Query("SELECT id FROM tasbih")
    suspend fun getAllIds(): List<String>

    @Update
    suspend fun update(tasbih: TasbihEntity)

    @Query("UPDATE tasbih SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Delete
    suspend fun delete(tasbih: TasbihEntity)

    @Query("SELECT COUNT(*) FROM tasbih")
    suspend fun count(): Int
}
