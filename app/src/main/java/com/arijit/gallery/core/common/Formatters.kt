package com.arijit.gallery.core.common

import com.arijit.gallery.core.domain.model.TimelineGranularity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {

    private val daySameYearFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    private val dayOtherYearFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val yearFormatter = DateTimeFormatter.ofPattern("yyyy", Locale.getDefault())
    private val bubbleFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())

    /** Formats duration in milliseconds as `m:ss` or `h:mm:ss`. */
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "0:00"
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /** Formats bytes into a human-readable file size string. */
    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    fun groupKey(
        timestampMs: Long,
        granularity: TimelineGranularity,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val date = Instant.ofEpochMilli(timestampMs).atZone(zoneId).toLocalDate()
        return when (granularity) {
            TimelineGranularity.DAY -> String.format(
                Locale.US,
                "%04d-%02d-%02d",
                date.year,
                date.monthValue,
                date.dayOfMonth
            )
            TimelineGranularity.MONTH -> String.format(
                Locale.US,
                "%04d-%02d",
                date.year,
                date.monthValue
            )
            TimelineGranularity.YEAR -> String.format(Locale.US, "%04d", date.year)
        }
    }

    fun formatDate(timestampMs: Long): String {
        return formatTimelineHeader(timestampMs, TimelineGranularity.DAY)
    }

    fun formatTimelineHeader(
        timestampMs: Long,
        granularity: TimelineGranularity,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val date = Instant.ofEpochMilli(timestampMs).atZone(zoneId).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        return when (granularity) {
            TimelineGranularity.DAY -> when {
                date == today -> "Today"
                date == today.minusDays(1) -> "Yesterday"
                date.year == today.year -> date.format(daySameYearFormatter)
                else -> date.format(dayOtherYearFormatter)
            }
            TimelineGranularity.MONTH -> date.format(monthFormatter)
            TimelineGranularity.YEAR -> date.format(yearFormatter)
        }
    }

    fun formatFastScrollBubble(
        timestampMs: Long,
        granularity: TimelineGranularity,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val date = Instant.ofEpochMilli(timestampMs).atZone(zoneId).toLocalDate()
        return when (granularity) {
            TimelineGranularity.DAY -> date.format(bubbleFormatter)
            TimelineGranularity.MONTH -> date.format(monthFormatter)
            TimelineGranularity.YEAR -> date.format(yearFormatter)
        }
    }

    fun dayEpochRangeMs(
        groupKey: String,
        granularity: TimelineGranularity,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Pair<Long, Long>? {
        return runCatching {
            val parts = groupKey.split("-")
            val startDate = when (granularity) {
                TimelineGranularity.DAY -> LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                TimelineGranularity.MONTH -> LocalDate.of(parts[0].toInt(), parts[1].toInt(), 1)
                TimelineGranularity.YEAR -> LocalDate.of(parts[0].toInt(), 1, 1)
            }
            val endDate = when (granularity) {
                TimelineGranularity.DAY -> startDate.plusDays(1)
                TimelineGranularity.MONTH -> startDate.plusMonths(1)
                TimelineGranularity.YEAR -> startDate.plusYears(1)
            }
            val startMs = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endMs = endDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            startMs to endMs
        }.getOrNull()
    }
}

