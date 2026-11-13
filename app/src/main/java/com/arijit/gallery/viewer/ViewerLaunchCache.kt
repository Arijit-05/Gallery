package com.arijit.gallery.viewer

import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.SortOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries the tapped [MediaItem], its grid thumbnail memory cache key, and the active sort/filter
 * parameters so the Viewer mounts once at the exact page index without flashing or double-opening.
 */
@Singleton
class ViewerLaunchCache @Inject constructor() {
    data class LaunchPayload(
        val item: MediaItem,
        val thumbnailMemoryKey: String?,
        val thumbnailSizePx: Int = 0,
        val sortOrder: SortOrder,
        val hiddenBuckets: Set<String>,
        val itemsOverride: List<MediaItem>? = null
    )

    @Volatile
    private var lastPayload: LaunchPayload? = null

    fun put(
        item: MediaItem,
        thumbnailSizePx: Int,
        sortOrder: SortOrder = SortOrder.DATE_TAKEN_DESC,
        hiddenBuckets: Set<String> = emptySet(),
        itemsOverride: List<MediaItem>? = null
    ) {
        val thumbKey = if (thumbnailSizePx > 0) {
            "ms_thumb_${item.id}_${item.dateModifiedSec}_$thumbnailSizePx"
        } else {
            null
        }
        lastPayload = LaunchPayload(
            item = item,
            thumbnailMemoryKey = thumbKey,
            thumbnailSizePx = thumbnailSizePx,
            sortOrder = sortOrder,
            hiddenBuckets = hiddenBuckets,
            itemsOverride = itemsOverride
        )
    }

    fun get(mediaId: Long): LaunchPayload? {
        return lastPayload?.takeIf { it.item.id == mediaId }
    }
}
