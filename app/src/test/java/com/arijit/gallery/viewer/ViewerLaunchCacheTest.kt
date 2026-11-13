package com.arijit.gallery.viewer

import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewerLaunchCacheTest {

    @Test
    fun viewerLaunchCache_returnsItemOnlyForMatchingId() {
        val cache = ViewerLaunchCache()
        val item = MediaItem(
            id = 42L,
            contentUri = "content://media/external/images/media/42",
            displayName = "IMG_42.jpg",
            mimeType = "image/jpeg",
            mediaType = MediaType.IMAGE,
            dateTakenMs = 1_762_944_000_000L,
            dateAddedSec = 1_762_944_000L,
            dateModifiedSec = 1_762_944_000L,
            sizeBytes = 1024L,
            width = 1920,
            height = 1080,
            durationMs = 0L,
            bucketId = "1",
            bucketName = "Camera",
            relativePath = "DCIM/Camera/",
            isFavorite = false,
            orientation = 0
        )

        cache.put(item, thumbnailSizePx = 240)
        assertEquals(item, cache.get(42L)?.item)
        assertNull(cache.get(99L))
    }
}

