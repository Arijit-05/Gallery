package com.arijit.gallery.photos

import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.MediaType
import com.arijit.gallery.core.domain.model.TimelineGranularity
import com.arijit.gallery.core.domain.model.TimelineUiItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PhotosTimelineGroupingTest {

    private fun sampleItem(id: Long, date: LocalDate): MediaItem {
        val ms = date.atStartOfDay(ZoneOffset.UTC).plusHours(12).toInstant().toEpochMilli()
        return MediaItem(
            id = id,
            contentUri = "content://media/external/images/media/$id",
            displayName = "IMG_$id.jpg",
            mimeType = "image/jpeg",
            mediaType = MediaType.IMAGE,
            dateTakenMs = ms,
            dateAddedSec = ms / 1000L,
            dateModifiedSec = ms / 1000L,
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
    }

    @Test
    fun groupSnapshotItems_groupsByDayAndMonthCorrectly() {
        val d1 = LocalDate.of(2026, 11, 12)
        val d2 = LocalDate.of(2026, 11, 11)
        val d3 = LocalDate.of(2026, 10, 5)
        val items = listOf(
            sampleItem(1, d1),
            sampleItem(2, d1),
            sampleItem(3, d2),
            sampleItem(4, d3)
        )

        val dayGrouped = PhotosViewModel.groupSnapshotItems(items, TimelineGranularity.DAY)
        assertEquals(3, dayGrouped.count { it is TimelineUiItem.Header })
        assertEquals(4, dayGrouped.count { it is TimelineUiItem.Media })
        assertTrue(dayGrouped.first() is TimelineUiItem.Header)

        val monthGrouped = PhotosViewModel.groupSnapshotItems(items, TimelineGranularity.MONTH)
        assertEquals(2, monthGrouped.count { it is TimelineUiItem.Header })
        assertEquals(4, monthGrouped.count { it is TimelineUiItem.Media })
    }
}

