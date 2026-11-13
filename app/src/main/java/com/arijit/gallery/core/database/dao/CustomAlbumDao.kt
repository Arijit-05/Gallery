package com.arijit.gallery.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arijit.gallery.core.database.entity.CustomAlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomAlbumDao {
    @Query("SELECT * FROM custom_albums ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<CustomAlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: CustomAlbumEntity)

    @Query("DELETE FROM custom_albums WHERE id = :id")
    suspend fun deleteById(id: String)
}

