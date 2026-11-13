package com.arijit.gallery.memories

import com.arijit.gallery.core.domain.model.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MemoriesExtractorTest {

    @Test
    fun extractMemories_returnsEmpty_whenMediaListIsEmpty() {
        val result = MemoriesExtractor.extractMemories(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun extractMemories_detectsOnThisDay_fromPreviousYears() {
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH)
        val currentDay = now.get(Calendar.DAY_OF_MONTH)

        val oneYearAgoCal = Calendar.getInstance().apply {
            set(currentYear - 1, currentMonth, currentDay, 14, 30, 0)
        }

        val media = listOf(
            createTestMediaItem(id = 1L, timestampMs = oneYearAgoCal.timeInMillis),
            createTestMediaItem(id = 2L, timestampMs = oneYearAgoCal.timeInMillis + 1000L)
        )

        val stories = MemoriesExtractor.extractMemories(media)
        val onThisDayStory = stories.find { it.title == "On this day" }

        assertNotNull(onThisDayStory)
        assertEquals(2, onThisDayStory?.items?.size)
        assertTrue(onThisDayStory?.subtitle?.contains("1 year ago today") == true)
    }

    @Test
    fun extractMemories_detectsThrowback_whenClustersExistPastSixMonths() {
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)

        val pastCal = Calendar.getInstance().apply {
            set(currentYear - 1, Calendar.JUNE, 15, 10, 0, 0)
        }

        val media = (1..6).map { i ->
            createTestMediaItem(id = i.toLong(), timestampMs = pastCal.timeInMillis + i * 3600000L)
        }

        val stories = MemoriesExtractor.extractMemories(media)
        val throwbackStory = stories.find { it.title == "Throwback" }

        assertNotNull(throwbackStory)
        assertTrue((throwbackStory?.items?.size ?: 0) >= 4)
    }

    private fun createTestMediaItem(id: Long, timestampMs: Long): MediaItem {
        return MediaItem(
            id = id,
            contentUri = "content://media/external/images/media/$id",
            displayName = "IMG_$id.jpg",
            mimeType = "image/jpeg",
            mediaType = com.arijit.gallery.core.domain.model.MediaType.IMAGE,
            dateTakenMs = timestampMs,
            dateAddedSec = timestampMs / 1000L,
            dateModifiedSec = timestampMs / 1000L,
            sizeBytes = 2048L,
            width = 1920,
            height = 1080,
            durationMs = 0L,
            bucketId = "100",
            bucketName = "Camera",
            relativePath = "DCIM/Camera/",
            isFavorite = false,
            orientation = 0
        )
    }
}
