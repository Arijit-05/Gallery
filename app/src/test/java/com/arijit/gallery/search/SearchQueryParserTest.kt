package com.arijit.gallery.search

import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.MediaType
import com.arijit.gallery.core.domain.model.SearchMediaFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.ZoneOffset

class SearchQueryParserTest {

    private val fixedToday = LocalDate.of(2026, 11, 13) // Friday, Nov 13, 2026
    private val utcZone = ZoneId.of("UTC")

    private fun createMediaItem(
        id: Long = 1L,
        displayName: String = "IMG_20261113_001.jpg",
        dateTakenMs: Long = fixedToday.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        mediaType: MediaType = MediaType.IMAGE,
        bucketName: String = "Camera",
        relativePath: String = "DCIM/Camera/",
        isFavorite: Boolean = false
    ): MediaItem {
        return MediaItem(
            id = id,
            contentUri = "content://media/external/images/media/$id",
            displayName = displayName,
            mimeType = if (mediaType == MediaType.VIDEO) "video/mp4" else "image/jpeg",
            mediaType = mediaType,
            dateTakenMs = dateTakenMs,
            dateAddedSec = dateTakenMs / 1000L,
            dateModifiedSec = dateTakenMs / 1000L,
            sizeBytes = 2048L,
            width = 1920,
            height = 1080,
            durationMs = if (mediaType == MediaType.VIDEO) 12_000L else 0L,
            bucketId = "100",
            bucketName = bucketName,
            relativePath = relativePath,
            isFavorite = isFavorite,
            orientation = 0
        )
    }

    @Test
    fun parse_handlesTodayAndYesterday() {
        val todayParsed = SearchQueryParser.parse("today", now = fixedToday, zoneId = utcZone)
        assertNotNull(todayParsed.dateRange)
        val todayStart = fixedToday.atStartOfDay(utcZone).toInstant().toEpochMilli()
        assertTrue(todayStart in todayParsed.dateRange!!)

        val yesterdayParsed = SearchQueryParser.parse("yesterday", now = fixedToday, zoneId = utcZone)
        assertNotNull(yesterdayParsed.dateRange)
        val yesterdayStart = fixedToday.minusDays(1).atStartOfDay(utcZone).toInstant().toEpochMilli()
        assertTrue(yesterdayStart in yesterdayParsed.dateRange!!)
        assertFalse(todayStart in yesterdayParsed.dateRange!!)
    }

    @Test
    fun parse_handlesWeeksAndMonths() {
        val lastWeek = SearchQueryParser.parse("last week", now = fixedToday, zoneId = utcZone)
        assertNotNull(lastWeek.dateRange)

        val lastMonth = SearchQueryParser.parse("last month", now = fixedToday, zoneId = utcZone)
        assertNotNull(lastMonth.dateRange)
        // Fixed date is Nov 2026, so last month is October 2026
        val oct15 = LocalDate.of(2026, 10, 15).atStartOfDay(utcZone).toInstant().toEpochMilli()
        assertTrue(oct15 in lastMonth.dateRange!!)
        val nov1 = LocalDate.of(2026, 11, 1).atStartOfDay(utcZone).toInstant().toEpochMilli()
        assertFalse(nov1 in lastMonth.dateRange!!)
    }

    @Test
    fun parse_handlesMonthAndYear() {
        val parsed = SearchQueryParser.parse("March 2025", now = fixedToday, zoneId = utcZone)
        assertNotNull(parsed.dateRange)
        val march15 = LocalDate.of(2025, 3, 15).atStartOfDay(utcZone).toInstant().toEpochMilli()
        assertTrue(march15 in parsed.dateRange!!)
        val apr1 = LocalDate.of(2025, 4, 1).atStartOfDay(utcZone).toInstant().toEpochMilli()
        assertFalse(apr1 in parsed.dateRange!!)
    }

    @Test
    fun parse_handlesStandaloneYear() {
        val parsed = SearchQueryParser.parse("2024", now = fixedToday, zoneId = utcZone)
        assertNotNull(parsed.dateRange)
        val mid2024 = LocalDate.of(2024, 7, 10).atStartOfDay(utcZone).toInstant().toEpochMilli()
        assertTrue(mid2024 in parsed.dateRange!!)
        val in2025 = LocalDate.of(2025, 1, 1).atStartOfDay(utcZone).toInstant().toEpochMilli()
        assertFalse(in2025 in parsed.dateRange!!)
    }

    @Test
    fun parse_handlesSeasons() {
        val summer2023 = SearchQueryParser.parse("summer 2023", now = fixedToday, zoneId = utcZone)
        assertNotNull(summer2023.dateRange)
        val jul2023 = LocalDate.of(2023, 7, 15).atStartOfDay(utcZone).toInstant().toEpochMilli()
        assertTrue(jul2023 in summer2023.dateRange!!)
        val nov2023 = LocalDate.of(2023, 11, 1).atStartOfDay(utcZone).toInstant().toEpochMilli()
        assertFalse(nov2023 in summer2023.dateRange!!)
    }

    @Test
    fun parse_handlesFestivals() {
        val diwali = SearchQueryParser.parse("Diwali 2024", now = fixedToday, zoneId = utcZone)
        assertNotNull(diwali.dateRange)
        val diwaliDay = LocalDate.of(2024, 11, 1).atStartOfDay(utcZone).toInstant().toEpochMilli()
        assertTrue(diwaliDay in diwali.dateRange!!)

        val christmas = SearchQueryParser.parse("Christmas", now = fixedToday, zoneId = utcZone)
        assertNotNull(christmas.dateRange)
        val xmasDay = LocalDate.of(2025, 12, 25).atStartOfDay(utcZone).toInstant().toEpochMilli()
        assertTrue(xmasDay in christmas.dateRange!!)
    }

    @Test
    fun parse_handlesDateRanges() {
        val range = SearchQueryParser.parse("jan to march 2025", now = fixedToday, zoneId = utcZone)
        assertNotNull(range.dateRange)
        val feb = LocalDate.of(2025, 2, 10).atStartOfDay(utcZone).toInstant().toEpochMilli()
        assertTrue(feb in range.dateRange!!)
        val apr = LocalDate.of(2025, 4, 1).atStartOfDay(utcZone).toInstant().toEpochMilli()
        assertFalse(apr in range.dateRange!!)

        val yearRange = SearchQueryParser.parse("2022 to 2024", now = fixedToday, zoneId = utcZone)
        assertNotNull(yearRange.dateRange)
        val in2023 = LocalDate.of(2023, 6, 1).atStartOfDay(utcZone).toInstant().toEpochMilli()
        assertTrue(in2023 in yearRange.dateRange!!)
    }

    @Test
    fun parse_combinedQuery_videosFromGoa2024() {
        val parsed = SearchQueryParser.parse("videos from goa 2024", now = fixedToday, zoneId = utcZone)
        assertEquals(SearchMediaFilter.VIDEOS, parsed.inferredFilter)
        assertNotNull(parsed.dateRange)
        val jul2024 = LocalDate.of(2024, 7, 1).atStartOfDay(utcZone).toInstant().toEpochMilli()
        assertTrue(jul2024 in parsed.dateRange!!)
        assertEquals(listOf("goa"), parsed.textTokens)
        assertEquals("goa*", parsed.ftsQuery)
    }

    @Test
    fun parse_combinedQuery_screenshotsLastMonth() {
        val parsed = SearchQueryParser.parse("screenshots last month", now = fixedToday, zoneId = utcZone)
        assertEquals(SearchMediaFilter.SCREENSHOTS, parsed.inferredFilter)
        assertNotNull(parsed.dateRange)
        assertTrue(parsed.textTokens.isEmpty())
    }

    @Test
    fun parse_combinedQuery_favoritesWithSunset() {
        val parsed = SearchQueryParser.parse("favorites with sunset", now = fixedToday, zoneId = utcZone)
        assertEquals(SearchMediaFilter.FAVORITES, parsed.inferredFilter)
        assertTrue(parsed.isFavoriteOnly)
        assertEquals(listOf("sunset"), parsed.textTokens)
        assertEquals("sunset*", parsed.ftsQuery)
    }

    @Test
    fun parse_combinedQuery_selfiesInParis() {
        val parsed = SearchQueryParser.parse("selfies in paris", now = fixedToday, zoneId = utcZone)
        assertEquals(SearchMediaFilter.SELFIES, parsed.inferredFilter)
        assertEquals(listOf("paris"), parsed.textTokens)
        assertEquals("paris*", parsed.ftsQuery)
    }

    @Test
    fun matchesFilter_correctlyIdentifiesTypes() {
        val photo = createMediaItem(mediaType = MediaType.IMAGE)
        val video = createMediaItem(mediaType = MediaType.VIDEO)
        val screenshot = createMediaItem(bucketName = "Screenshots")
        val selfie = createMediaItem(displayName = "selfie_2026.jpg")
        val favorite = createMediaItem(isFavorite = true)

        assertTrue(SearchQueryParser.matchesFilter(photo, SearchMediaFilter.PHOTOS))
        assertFalse(SearchQueryParser.matchesFilter(video, SearchMediaFilter.PHOTOS))

        assertTrue(SearchQueryParser.matchesFilter(video, SearchMediaFilter.VIDEOS))
        assertFalse(SearchQueryParser.matchesFilter(photo, SearchMediaFilter.VIDEOS))

        assertTrue(SearchQueryParser.matchesFilter(screenshot, SearchMediaFilter.SCREENSHOTS))
        assertTrue(SearchQueryParser.matchesFilter(selfie, SearchMediaFilter.SELFIES))
        assertTrue(SearchQueryParser.matchesFilter(favorite, SearchMediaFilter.FAVORITES))
    }

    @Test
    fun tokenGeneration_generatesComprehensiveTokens() {
        val timeMs = LocalDate.of(2024, 3, 15).atStartOfDay(utcZone).toInstant().toEpochMilli()
        val tokens = SearchQueryParser.generateDateTokens(timeMs, utcZone)
        assertTrue(tokens.contains("2024"))
        assertTrue(tokens.contains("march"))
        assertTrue(tokens.contains("mar"))
        assertTrue(tokens.contains("15"))
        assertTrue(tokens.contains("friday"))
        assertTrue(tokens.contains("spring"))
    }

    @Test
    fun levenshteinDistance_computesAccurateTypoDistance() {
        assertEquals(0, SearchQueryParser.levenshteinDistance("goa", "goa"))
        assertEquals(1, SearchQueryParser.levenshteinDistance("goaa", "goa"))
        assertEquals(1, SearchQueryParser.levenshteinDistance("sunsett", "sunset"))
        assertEquals(1, SearchQueryParser.levenshteinDistance("bech", "beach"))
        assertEquals(2, SearchQueryParser.levenshteinDistance("bch", "beach"))
        assertEquals(3, SearchQueryParser.levenshteinDistance("kitten", "sitting"))
    }
}
