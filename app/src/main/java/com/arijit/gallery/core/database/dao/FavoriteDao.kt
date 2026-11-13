package com.arijit.gallery.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arijit.gallery.core.database.entity.FavoriteOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_overrides WHERE isFavorite = 1")
    fun observeFavorites(): Flow<List<FavoriteOverrideEntity>>

    @Query("SELECT * FROM favorite_overrides")
    fun observeAllOverrides(): Flow<List<FavoriteOverrideEntity>>

    @Query("SELECT * FROM favorite_overrides")
    suspend fun getAllOverrides(): List<FavoriteOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteOverrideEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<FavoriteOverrideEntity>)

    @Query("SELECT mediaId FROM favorite_overrides WHERE isFavorite = 1")
    suspend fun getFavoriteIds(): List<Long>

    @Query("DELETE FROM favorite_overrides WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: Long)
}

