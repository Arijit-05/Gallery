package com.arijit.gallery.core.common

import com.arijit.gallery.core.domain.model.TimelineGranularity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FormattersTest {

    @Test
    fun formatDuration_formatsMinutesAndHoursCorrectly() {
        assertEquals("0:00", Formatters.formatDuration(0L))
        assertEquals("0:09", Formatters.formatDuration(9_000L))
        assertEquals("1:05", Formatters.formatDuration(65_000L))
        assertEquals("1:01:05", Formatters.formatDuration(3_665_000L))
    }

    @Test
    fun formatFileSize_formatsBytesKbMbGb() {
        assertEquals("512 B", Formatters.formatFileSize(512L))
        assertEquals("2.0 KB", Formatters.formatFileSize(2048L))
        assertEquals("3.5 MB", Formatters.formatFileSize((3.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun formatTimelineHeader_detectsTodayYesterdayAndOlder() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 11, 12)
        val todayMs = today.atStartOfDay(zone).plusHours(10).toInstant().toEpochMilli()
        val yesterdayMs = today.minusDays(1).atStartOfDay(zone).plusHours(10).toInstant().toEpochMilli()
        val lastYearMs = LocalDate.of(2025, 5, 3).atStartOfDay(zone).plusHours(10).toInstant().toEpochMilli()

        assertEquals("Today", Formatters.formatTimelineHeader(todayMs, TimelineGranularity.DAY, todayMs, zone))
        assertEquals("Yesterday", Formatters.formatTimelineHeader(yesterdayMs, TimelineGranularity.DAY, todayMs, zone))
        assertEquals("May 3, 2025", Formatters.formatTimelineHeader(lastYearMs, TimelineGranularity.DAY, todayMs, zone))
        assertEquals("2025", Formatters.formatTimelineHeader(lastYearMs, TimelineGranularity.YEAR, todayMs, zone))
    }

    @Test
    fun dayEpochRangeMs_computesValidStartAndEnd() {
        val zone = ZoneId.of("UTC")
        val range = Formatters.dayEpochRangeMs("2026-11-12", TimelineGranularity.DAY, zone)
        assertNotNull(range)
        val (start, end) = range!!
        assertEquals(24L * 60L * 60L * 1000L, end - start)
    }
}

