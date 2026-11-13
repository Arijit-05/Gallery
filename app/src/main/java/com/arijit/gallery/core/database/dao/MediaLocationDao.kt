package com.arijit.gallery.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arijit.gallery.core.database.entity.MediaLocationEntity

@Dao
interface MediaLocationDao {
    @Query("SELECT * FROM media_locations")
    suspend fun getAll(): List<MediaLocationEntity>

    @Query("SELECT * FROM media_locations WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getByMediaId(mediaId: Long): MediaLocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaLocationEntity)
}

