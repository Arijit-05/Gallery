package com.arijit.gallery.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arijit.gallery.core.database.entity.AlbumSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumSettingsDao {
    @Query("SELECT * FROM album_settings ORDER BY isPinned DESC, sortPosition ASC")
    fun observeAll(): Flow<List<AlbumSettingsEntity>>

    @Query("SELECT * FROM album_settings")
    suspend fun getAll(): List<AlbumSettingsEntity>

    @Query("SELECT * FROM album_settings WHERE bucketId = :bucketId LIMIT 1")
    suspend fun getById(bucketId: String): AlbumSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AlbumSettingsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AlbumSettingsEntity>)
}

