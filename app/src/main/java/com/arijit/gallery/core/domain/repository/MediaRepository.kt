package com.arijit.gallery.core.domain.repository

import androidx.paging.PagingData
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.SortOrder
import com.arijit.gallery.core.domain.model.TimelineDateAnchor
import com.arijit.gallery.core.domain.model.TimelineGranularity
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun observeMediaChanges(): Flow<Unit>

    fun notifyMediaChanged()

    fun getTimelinePagingFlow(
        sortOrder: SortOrder,
        hiddenBuckets: Set<String>
    ): Flow<PagingData<MediaItem>>

    fun getCachedSnapshot(): Flow<List<MediaItem>>

    suspend fun refreshSnapshot(
        sortOrder: SortOrder,
        hiddenBuckets: Set<String>
    )

    suspend fun getTimelineDateAnchors(
        granularity: TimelineGranularity,
        sortOrder: SortOrder,
        hiddenBuckets: Set<String>
    ): List<TimelineDateAnchor>

    suspend fun getMediaIdsForGroup(
        groupKey: String,
        granularity: TimelineGranularity,
        hiddenBuckets: Set<String>
    ): List<Long>

    suspend fun getMediaCount(hiddenBuckets: Set<String>): Int

    suspend fun getAllMedia(
        sortOrder: SortOrder,
        hiddenBuckets: Set<String>
    ): List<MediaItem>

    fun getCachedMediaIfFresh(
        sortOrder: SortOrder,
        hiddenBuckets: Set<String>
    ): List<MediaItem>?

    suspend fun getMediaByIds(ids: Collection<Long>): List<MediaItem>
}

