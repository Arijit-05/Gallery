package com.arijit.gallery.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arijit.gallery.core.database.entity.MediaIndexStatusEntity
import com.arijit.gallery.core.database.entity.MediaSearchFtsEntity

@Dao
interface MediaSearchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: MediaSearchFtsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MediaSearchFtsEntity>)

    @Query("SELECT rowid FROM media_search_fts WHERE media_search_fts MATCH :matchQuery")
    suspend fun searchMatchingIds(matchQuery: String): List<Long>

    @Query("SELECT rowid, * FROM media_search_fts WHERE rowid IN (:ids)")
    suspend fun getEntitiesByIds(ids: List<Long>): List<MediaSearchFtsEntity>

    @Query("SELECT rowid, * FROM media_search_fts WHERE rowid = :mediaId")
    suspend fun getById(mediaId: Long): MediaSearchFtsEntity?

    @Query("SELECT DISTINCT placeName FROM media_search_fts WHERE placeName != ''")
    suspend fun getAllPlaceNames(): List<String>

    @Query("SELECT rowid FROM media_search_fts")
    suspend fun getAllIndexedIds(): List<Long>

    @Query("DELETE FROM media_search_fts WHERE rowid = :mediaId")
    suspend fun deleteById(mediaId: Long)

    @Query("DELETE FROM media_search_fts WHERE rowid IN (:mediaIds)")
    suspend fun deleteByIds(mediaIds: List<Long>)

    @Query("DELETE FROM media_search_fts")
    suspend fun clearFts()

    @Query("SELECT COUNT(*) FROM media_search_fts")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStatus(status: MediaIndexStatusEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllStatus(statuses: List<MediaIndexStatusEntity>)

    @Query("SELECT * FROM media_index_status")
    suspend fun getAllStatuses(): List<MediaIndexStatusEntity>

    @Query("DELETE FROM media_index_status WHERE mediaId = :mediaId")
    suspend fun deleteStatusById(mediaId: Long)

    @Query("DELETE FROM media_index_status")
    suspend fun clearStatus()
}

