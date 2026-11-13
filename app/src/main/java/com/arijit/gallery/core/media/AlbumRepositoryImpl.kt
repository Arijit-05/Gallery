package com.arijit.gallery.core.media

import com.arijit.gallery.core.common.IoDispatcher
import com.arijit.gallery.core.database.dao.AlbumSettingsDao
import com.arijit.gallery.core.database.dao.CustomAlbumDao
import com.arijit.gallery.core.database.dao.MediaLocationDao
import com.arijit.gallery.core.database.dao.SearchHistoryDao
import com.arijit.gallery.core.database.entity.AlbumSettingsEntity
import com.arijit.gallery.core.database.entity.CustomAlbumEntity
import com.arijit.gallery.core.database.entity.SearchHistoryEntity
import com.arijit.gallery.core.domain.model.Album
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.SearchMediaFilter
import com.arijit.gallery.core.domain.model.SortOrder
import com.arijit.gallery.core.domain.model.SystemAlbumSummary
import com.arijit.gallery.core.domain.model.SystemAlbumType
import com.arijit.gallery.core.domain.model.TrashedMediaItem
import com.arijit.gallery.core.domain.repository.AlbumRepository
import com.arijit.gallery.core.domain.repository.MediaRepository
import com.arijit.gallery.core.domain.repository.SettingsRepository
import com.arijit.gallery.search.SearchIndexManager
import com.arijit.gallery.search.SearchQueryParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumRepositoryImpl @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val settingsRepository: SettingsRepository,
    private val albumSettingsDao: AlbumSettingsDao,
    private val customAlbumDao: CustomAlbumDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val mediaLocationDao: MediaLocationDao,
    private val searchIndexManager: SearchIndexManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AlbumRepository {

    private val repoScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mediaTrigger = mediaRepository.observeMediaChanges().onStart { emit(Unit) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val allUnfilteredMediaFlow: SharedFlow<List<MediaItem>> = combine(
        settingsRepository.userSettings.map { it.sortOrder }.distinctUntilChanged(),
        mediaTrigger
    ) { sortOrder, _ -> sortOrder }
        .flatMapLatest { sortOrder ->
            flow {
                emit(mediaRepository.getAllMedia(sortOrder, emptySet()))
            }
        }
        .flowOn(ioDispatcher)
        .shareIn(
            scope = repoScope,
            started = SharingStarted.WhileSubscribed(15_000L),
            replay = 1
        )

    private val sharedAlbumsFlow: SharedFlow<List<Album>> = combine(
        allUnfilteredMediaFlow,
        albumSettingsDao.observeAll(),
        customAlbumDao.observeAll(),
        settingsRepository.userSettings.map { it.hiddenBuckets }.distinctUntilChanged()
    ) { allMedia, settingsRows, customRows, hiddenBuckets ->
        val settingsMap = settingsRows.associateBy { it.bucketId }

        val grouped = LinkedHashMap<String, MutableList<MediaItem>>()
        for (item in allMedia) {
            val bucketKey = item.bucketId.ifBlank { item.bucketName.ifBlank { "other" } }
            grouped.getOrPut(bucketKey) { mutableListOf() }.add(item)
        }

        val albums = mutableListOf<Album>()
        var defaultOrder = 0
        for ((bucketId, items) in grouped) {
            val first = items.first()
            val pref = settingsMap[bucketId]
            val isHidden = bucketId in hiddenBuckets || (pref?.isHiddenFromTimeline == true)
            val fallbackName = first.bucketName.ifBlank {
                first.relativePath.trim('/').substringAfterLast('/').ifBlank { "Other" }
            }
            albums += Album(
                id = bucketId,
                name = pref?.customName?.takeIf { it.isNotBlank() } ?: fallbackName,
                coverUri = first.contentUri,
                coverId = first.id,
                coverDateModifiedSec = first.dateModifiedSec,
                itemCount = items.size,
                relativePath = first.relativePath.ifBlank { "Pictures/$fallbackName/" },
                isPinned = pref?.isPinned ?: false,
                isHiddenFromTimeline = isHidden,
                isCustomEmpty = false,
                sortPosition = pref?.sortPosition ?: defaultOrder++
            )
        }

        // Include user-created custom albums that do not yet have files in MediaStore.
        val existingPaths = albums.map { it.relativePath.trimEnd('/').lowercase() }.toSet()
        for (custom in customRows) {
            val normalizedPath = custom.relativePath.trimEnd('/').lowercase()
            if (normalizedPath !in existingPaths && albums.none { it.id == custom.id }) {
                val pref = settingsMap[custom.id]
                albums += Album(
                    id = custom.id,
                    name = pref?.customName ?: custom.name,
                    coverUri = null,
                    itemCount = 0,
                    relativePath = custom.relativePath,
                    isPinned = pref?.isPinned ?: false,
                    isHiddenFromTimeline = false,
                    isCustomEmpty = true,
                    sortPosition = pref?.sortPosition ?: defaultOrder++
                )
            }
        }

        albums.sortedWith(
            compareByDescending<Album> { it.isPinned }
                .thenBy { it.sortPosition }
                .thenByDescending { it.itemCount }
        )
    }.flowOn(ioDispatcher)
        .shareIn(
            scope = repoScope,
            started = SharingStarted.WhileSubscribed(15_000L),
            replay = 1
        )

    private val sharedSystemSummariesFlow: SharedFlow<Map<SystemAlbumType, SystemAlbumSummary>> =
        allUnfilteredMediaFlow.map { allMedia ->
            var videoCount = 0
            var favCount = 0
            var screenshotCount = 0
            var firstVideo: MediaItem? = null
            var firstFav: MediaItem? = null
            var firstScreenshot: MediaItem? = null
            for (item in allMedia) {
                if (item.isVideo) {
                    if (firstVideo == null) firstVideo = item
                    videoCount++
                }
                if (item.isFavorite) {
                    if (firstFav == null) firstFav = item
                    favCount++
                }
                if (item.bucketName.contains("screenshot", ignoreCase = true) ||
                    item.relativePath.contains("screenshot", ignoreCase = true)
                ) {
                    if (firstScreenshot == null) firstScreenshot = item
                    screenshotCount++
                }
            }
            val firstAll = allMedia.firstOrNull()
            mapOf(
                SystemAlbumType.ALL_PHOTOS to SystemAlbumSummary(
                    coverUri = firstAll?.contentUri,
                    coverId = firstAll?.id ?: 0L,
                    coverDateModifiedSec = firstAll?.dateModifiedSec ?: 0L,
                    count = allMedia.size
                ),
                SystemAlbumType.VIDEOS to SystemAlbumSummary(
                    coverUri = firstVideo?.contentUri,
                    coverId = firstVideo?.id ?: 0L,
                    coverDateModifiedSec = firstVideo?.dateModifiedSec ?: 0L,
                    count = videoCount
                ),
                SystemAlbumType.FAVORITES to SystemAlbumSummary(
                    coverUri = firstFav?.contentUri,
                    coverId = firstFav?.id ?: 0L,
                    coverDateModifiedSec = firstFav?.dateModifiedSec ?: 0L,
                    count = favCount
                ),
                SystemAlbumType.SCREENSHOTS to SystemAlbumSummary(
                    coverUri = firstScreenshot?.contentUri,
                    coverId = firstScreenshot?.id ?: 0L,
                    coverDateModifiedSec = firstScreenshot?.dateModifiedSec ?: 0L,
                    count = screenshotCount
                )
            )
        }.flowOn(ioDispatcher)
            .shareIn(
                scope = repoScope,
                started = SharingStarted.WhileSubscribed(15_000L),
                replay = 1
            )

    override fun observeAlbums(): Flow<List<Album>> = sharedAlbumsFlow

    override fun observeSystemAlbumSummaries(): Flow<Map<SystemAlbumType, SystemAlbumSummary>> =
        sharedSystemSummariesFlow

    override fun observeTrashCount(): Flow<Int> = mediaTrigger.map {
        mediaStoreDataSource.queryTrashCount()
    }.flowOn(ioDispatcher)

    override suspend fun getMediaForAlbum(albumId: String, sortOrder: SortOrder): List<MediaItem> =
        withContext(ioDispatcher) {
            val all = mediaRepository.getAllMedia(sortOrder, emptySet())
            val custom = customAlbumDao.observeAll().first().firstOrNull { it.id == albumId }
            if (custom != null) {
                val targetPath = custom.relativePath.trimEnd('/').lowercase()
                all.filter { it.relativePath.trimEnd('/').lowercase() == targetPath }
            } else {
                all.filter {
                    val key = it.bucketId.ifBlank { it.bucketName.ifBlank { "other" } }
                    key == albumId
                }
            }
        }

    override suspend fun getMediaForSystemAlbum(
        type: SystemAlbumType,
        sortOrder: SortOrder
    ): List<MediaItem> = withContext(ioDispatcher) {
        val all = mediaRepository.getAllMedia(sortOrder, emptySet())
        when (type) {
            SystemAlbumType.ALL_PHOTOS -> all
            SystemAlbumType.VIDEOS -> all.filter { it.isVideo }
            SystemAlbumType.FAVORITES -> all.filter { it.isFavorite }
            SystemAlbumType.SCREENSHOTS -> all.filter {
                it.bucketName.contains("screenshot", ignoreCase = true) ||
                    it.relativePath.contains("screenshot", ignoreCase = true)
            }
        }
    }

    override suspend fun getTrashedItems(): List<TrashedMediaItem> = withContext(ioDispatcher) {
        mediaStoreDataSource.queryTrashedItems()
    }

    override suspend fun createCustomAlbum(name: String) = withContext(ioDispatcher) {
        val clean = name.trim().replace("/", "_")
        if (clean.isEmpty()) return@withContext
        customAlbumDao.insert(
            CustomAlbumEntity(
                id = "custom_${UUID.randomUUID()}",
                name = clean,
                relativePath = "Pictures/$clean/",
                createdAtMs = System.currentTimeMillis()
            )
        )
    }

    override suspend fun renameAlbum(albumId: String, newName: String) = withContext(ioDispatcher) {
        val clean = newName.trim()
        if (clean.isEmpty()) return@withContext
        val existing = albumSettingsDao.getById(albumId) ?: AlbumSettingsEntity(bucketId = albumId)
        albumSettingsDao.upsert(existing.copy(customName = clean))
    }

    override suspend fun setAlbumPinned(albumId: String, pinned: Boolean) = withContext(ioDispatcher) {
        val existing = albumSettingsDao.getById(albumId) ?: AlbumSettingsEntity(bucketId = albumId)
        albumSettingsDao.upsert(existing.copy(isPinned = pinned))
    }

    override suspend fun setAlbumHiddenFromTimeline(albumId: String, hidden: Boolean) =
        withContext(ioDispatcher) {
            val existing = albumSettingsDao.getById(albumId) ?: AlbumSettingsEntity(bucketId = albumId)
            albumSettingsDao.upsert(existing.copy(isHiddenFromTimeline = hidden))

            val currentHidden = settingsRepository.userSettings.first().hiddenBuckets
            val updatedHidden = if (hidden) currentHidden + albumId else currentHidden - albumId
            settingsRepository.setHiddenBuckets(updatedHidden)
        }

    override suspend fun moveAlbumOrder(albumId: String, moveUp: Boolean) = withContext(ioDispatcher) {
        val currentAlbums = observeAlbums().first()
        val idx = currentAlbums.indexOfFirst { it.id == albumId }
        if (idx < 0) return@withContext
        val targetIdx = if (moveUp) idx - 1 else idx + 1
        if (targetIdx !in currentAlbums.indices) return@withContext

        val mutable = currentAlbums.toMutableList()
        val item = mutable.removeAt(idx)
        mutable.add(targetIdx, item)

        val updatedEntities = mutable.mapIndexed { position, album ->
            val existing = albumSettingsDao.getById(album.id) ?: AlbumSettingsEntity(bucketId = album.id)
            existing.copy(sortPosition = position)
        }
        albumSettingsDao.upsertAll(updatedEntities)
    }

    override fun observeRecentSearches(): Flow<List<String>> =
        searchHistoryDao.observeRecent().map { list -> list.map { it.query } }

    override suspend fun recordSearch(query: String) = withContext(ioDispatcher) {
        val clean = query.trim()
        if (clean.isNotEmpty()) {
            searchHistoryDao.insert(SearchHistoryEntity(query = clean, timestampMs = System.currentTimeMillis()))
        }
    }

    override suspend fun removeRecentSearch(query: String) = withContext(ioDispatcher) {
        searchHistoryDao.delete(query.trim())
    }

    override suspend fun clearSearchHistory() = withContext(ioDispatcher) {
        searchHistoryDao.clear()
    }

    override suspend fun searchMedia(
        query: String,
        filter: SearchMediaFilter?,
        sortOrder: SortOrder
    ): List<MediaItem> = withContext(ioDispatcher) {
        searchIndexManager.search(query, filter, sortOrder)
    }
}

