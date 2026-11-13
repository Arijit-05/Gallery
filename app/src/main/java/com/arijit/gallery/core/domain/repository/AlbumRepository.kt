package com.arijit.gallery.core.domain.repository

import com.arijit.gallery.core.domain.model.Album
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.SearchMediaFilter
import com.arijit.gallery.core.domain.model.SortOrder
import com.arijit.gallery.core.domain.model.SystemAlbumSummary
import com.arijit.gallery.core.domain.model.SystemAlbumType
import com.arijit.gallery.core.domain.model.TrashedMediaItem
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {
    fun observeAlbums(): Flow<List<Album>>
    fun observeSystemAlbumSummaries(): Flow<Map<SystemAlbumType, SystemAlbumSummary>>
    fun observeTrashCount(): Flow<Int>

    suspend fun getMediaForAlbum(albumId: String, sortOrder: SortOrder): List<MediaItem>
    suspend fun getMediaForSystemAlbum(type: SystemAlbumType, sortOrder: SortOrder): List<MediaItem>
    suspend fun getTrashedItems(): List<TrashedMediaItem>

    suspend fun createCustomAlbum(name: String)
    suspend fun renameAlbum(albumId: String, newName: String)
    suspend fun setAlbumPinned(albumId: String, pinned: Boolean)
    suspend fun setAlbumHiddenFromTimeline(albumId: String, hidden: Boolean)
    suspend fun moveAlbumOrder(albumId: String, moveUp: Boolean)

    fun observeRecentSearches(): Flow<List<String>>
    suspend fun recordSearch(query: String)
    suspend fun removeRecentSearch(query: String)
    suspend fun clearSearchHistory()

    suspend fun searchMedia(
        query: String,
        filter: SearchMediaFilter?,
        sortOrder: SortOrder
    ): List<MediaItem>
}

