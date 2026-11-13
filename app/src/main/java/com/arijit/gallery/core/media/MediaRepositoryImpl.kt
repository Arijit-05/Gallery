package com.arijit.gallery.core.media

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.arijit.gallery.core.common.IoDispatcher
import com.arijit.gallery.core.database.dao.FavoriteDao
import com.arijit.gallery.core.database.dao.TimelineSnapshotDao
import com.arijit.gallery.core.database.entity.TimelineSnapshotEntity
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.SortOrder
import com.arijit.gallery.core.domain.model.TimelineDateAnchor
import com.arijit.gallery.core.domain.model.TimelineGranularity
import com.arijit.gallery.core.domain.repository.MediaRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val dataSource: MediaStoreDataSource,
    private val snapshotDao: TimelineSnapshotDao,
    private val favoriteDao: FavoriteDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : MediaRepository {

    private val inMemoryListCache = ConcurrentHashMap<Pair<SortOrder, Set<String>>, List<MediaItem>>()
    private val inMemoryFavoriteOverrides = ConcurrentHashMap<Long, Boolean>()
    private val manualTriggerFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun notifyMediaChanged() {
        inMemoryListCache.clear()
        manualTriggerFlow.tryEmit(Unit)
    }

    private fun applyFavoriteOverrides(items: List<MediaItem>): List<MediaItem> {
        if (inMemoryFavoriteOverrides.isEmpty()) return items
        return items.map { item ->
            val override = inMemoryFavoriteOverrides[item.id]
            if (override != null && override != item.isFavorite) {
                item.copy(isFavorite = override)
            } else {
                item
            }
        }
    }

    private suspend fun ensureFavoriteOverridesLoaded() {
        if (inMemoryFavoriteOverrides.isEmpty()) {
            favoriteDao.getAllOverrides().forEach {
                inMemoryFavoriteOverrides[it.mediaId] = it.isFavorite
            }
        }
    }

    override fun observeMediaChanges(): Flow<Unit> =
        merge(
            dataSource.observeMediaChanges(),
            manualTriggerFlow,
            favoriteDao.observeAllOverrides().map { overrides ->
                inMemoryFavoriteOverrides.clear()
                overrides.forEach { inMemoryFavoriteOverrides[it.mediaId] = it.isFavorite }
            }
        ).onEach { inMemoryListCache.clear() }

    override fun getTimelinePagingFlow(
        sortOrder: SortOrder,
        hiddenBuckets: Set<String>
    ): Flow<PagingData<MediaItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                maxSize = MAX_CACHED_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                MediaStorePagingSource(
                    dataSource = dataSource,
                    sortOrder = sortOrder,
                    hiddenBuckets = hiddenBuckets,
                    ioDispatcher = ioDispatcher,
                    transformItems = { applyFavoriteOverrides(it) }
                )
            }
        ).flow
    }

    override fun getCachedSnapshot(): Flow<List<MediaItem>> =
        snapshotDao.observeSnapshot().map { rows -> applyFavoriteOverrides(rows.map { it.toDomain() }) }

    override suspend fun refreshSnapshot(
        sortOrder: SortOrder,
        hiddenBuckets: Set<String>
    ) = withContext(ioDispatcher) {
        ensureFavoriteOverridesLoaded()
        val topItems = dataSource.queryMediaPage(
            limit = SNAPSHOT_LIMIT,
            offset = 0,
            sortOrder = sortOrder,
            hiddenBuckets = hiddenBuckets
        )
        val entities = applyFavoriteOverrides(topItems).mapIndexed { index, item ->
            TimelineSnapshotEntity.fromDomain(item, index)
        }
        snapshotDao.replaceSnapshot(entities)
    }

    override suspend fun getTimelineDateAnchors(
        granularity: TimelineGranularity,
        sortOrder: SortOrder,
        hiddenBuckets: Set<String>
    ): List<TimelineDateAnchor> = withContext(ioDispatcher) {
        dataSource.queryTimelineDateAnchors(granularity, sortOrder, hiddenBuckets)
    }

    override suspend fun getMediaIdsForGroup(
        groupKey: String,
        granularity: TimelineGranularity,
        hiddenBuckets: Set<String>
    ): List<Long> = withContext(ioDispatcher) {
        dataSource.queryMediaIdsForGroup(groupKey, granularity, hiddenBuckets)
    }

    override suspend fun getMediaCount(hiddenBuckets: Set<String>): Int = withContext(ioDispatcher) {
        dataSource.queryMediaCount(hiddenBuckets)
    }

    override suspend fun getAllMedia(
        sortOrder: SortOrder,
        hiddenBuckets: Set<String>
    ): List<MediaItem> = withContext(ioDispatcher) {
        ensureFavoriteOverridesLoaded()
        val key = sortOrder to hiddenBuckets
        val raw = inMemoryListCache[key] ?: dataSource.queryAllMedia(sortOrder, hiddenBuckets).also { loaded ->
            if (inMemoryListCache.size >= 4) inMemoryListCache.clear()
            inMemoryListCache[key] = loaded
        }
        applyFavoriteOverrides(raw)
    }

    override fun getCachedMediaIfFresh(
        sortOrder: SortOrder,
        hiddenBuckets: Set<String>
    ): List<MediaItem>? {
        val cached = inMemoryListCache[sortOrder to hiddenBuckets] ?: return null
        return applyFavoriteOverrides(cached)
    }

    override suspend fun getMediaByIds(ids: Collection<Long>): List<MediaItem> =
        withContext(ioDispatcher) {
            ensureFavoriteOverridesLoaded()
            applyFavoriteOverrides(dataSource.queryMediaByIds(ids))
        }

    companion object {
        private const val PAGE_SIZE = 60
        private const val INITIAL_LOAD_SIZE = 60
        private const val PREFETCH_DISTANCE = 30
        private const val MAX_CACHED_SIZE = 480
        private const val SNAPSHOT_LIMIT = 60
    }
}

