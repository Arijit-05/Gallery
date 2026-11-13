package com.arijit.gallery.core.media

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.SortOrder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class MediaStorePagingSource(
    private val dataSource: MediaStoreDataSource,
    private val sortOrder: SortOrder,
    private val hiddenBuckets: Set<String>,
    private val ioDispatcher: CoroutineDispatcher,
    private val transformItems: (List<MediaItem>) -> List<MediaItem> = { it }
) : PagingSource<Int, MediaItem>() {

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
        val anchor = state.anchorPosition ?: return null
        val pageSize = state.config.pageSize
        return ((anchor / pageSize) * pageSize).coerceAtLeast(0)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        return try {
            val offset = params.key ?: 0
            val limit = params.loadSize
            val rawItems = withContext(ioDispatcher) {
                dataSource.queryMediaPage(
                    limit = limit,
                    offset = offset,
                    sortOrder = sortOrder,
                    hiddenBuckets = hiddenBuckets
                )
            }
            val items = transformItems(rawItems)
            val prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0)
            val nextKey = if (items.size < limit) null else offset + items.size
            LoadResult.Page(
                data = items,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }
    }
}

