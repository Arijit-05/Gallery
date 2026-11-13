package com.arijit.gallery.search

import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.SearchMediaFilter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

data class ParsedSearchQuery(
    val rawQuery: String,
    val dateRange: LongRange? = null,
    val inferredFilter: SearchMediaFilter? = null,
    val isFavoriteOnly: Boolean = false,
    val textTokens: List<String> = emptyList(),
    val ftsQuery: String? = null
)

object SearchQueryParser {

    private val monthLookup: Map<String, Month> = buildMap {
        Month.entries.forEach { m ->
            put(m.name.lowercase(Locale.ROOT), m)
            put(m.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase(Locale.ROOT), m)
            put(m.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase(Locale.ROOT), m)
            put(m.getDisplayName(TextStyle.FULL, Locale.getDefault()).lowercase(Locale.ROOT), m)
            put(m.getDisplayName(TextStyle.SHORT, Locale.getDefault()).lowercase(Locale.ROOT), m)
        }
        put("sept", Month.SEPTEMBER)
    }

    // Known Diwali dates (approximate 8-day festival celebration window)
    private val diwaliWindows = listOf(
        LocalDate.of(2021, 10, 31) to LocalDate.of(2021, 11, 8),
        LocalDate.of(2022, 10, 20) to LocalDate.of(2022, 10, 28),
        LocalDate.of(2023, 11, 8) to LocalDate.of(2023, 11, 16),
        LocalDate.of(2024, 10, 28) to LocalDate.of(2024, 11, 5),
        LocalDate.of(2025, 10, 16) to LocalDate.of(2025, 10, 24),
        LocalDate.of(2026, 11, 4) to LocalDate.of(2026, 11, 12),
        LocalDate.of(2027, 10, 25) to LocalDate.of(2027, 11, 2)
    )

    fun parse(
        query: String,
        now: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ParsedSearchQuery {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return ParsedSearchQuery(rawQuery = "")

        var workingQuery = trimmed.lowercase(Locale.ROOT)
            .replace(",", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        var detectedDateRange: LongRange? = null
        var detectedFilter: SearchMediaFilter? = null
        var isFavorite = false

        // 1. Natural language relative dates
        when {
            workingQuery == "today" -> {
                detectedDateRange = dayRange(now, zoneId)
                workingQuery = ""
            }
            workingQuery == "yesterday" -> {
                detectedDateRange = dayRange(now.minusDays(1), zoneId)
                workingQuery = ""
            }
            workingQuery.contains("yesterday") -> {
                detectedDateRange = dayRange(now.minusDays(1), zoneId)
                workingQuery = workingQuery.replace("yesterday", "").trim()
            }
            workingQuery.contains("last week") -> {
                val startOfLastWeek = now.minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val endOfLastWeek = startOfLastWeek.plusDays(6)
                detectedDateRange = dateRange(startOfLastWeek, endOfLastWeek, zoneId)
                workingQuery = workingQuery.replace("last week", "").trim()
            }
            workingQuery.contains("this week") -> {
                val startOfThisWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                detectedDateRange = dateRange(startOfThisWeek, now, zoneId)
                workingQuery = workingQuery.replace("this week", "").trim()
            }
            workingQuery.contains("last month") -> {
                val lastMonthDate = now.minusMonths(1)
                val ym = YearMonth.of(lastMonthDate.year, lastMonthDate.month)
                detectedDateRange = dateRange(ym.atDay(1), ym.atEndOfMonth(), zoneId)
                workingQuery = workingQuery.replace("last month", "").trim()
            }
            workingQuery.contains("this month") -> {
                val ym = YearMonth.of(now.year, now.month)
                detectedDateRange = dateRange(ym.atDay(1), ym.atEndOfMonth(), zoneId)
                workingQuery = workingQuery.replace("this month", "").trim()
            }
            workingQuery.contains("last year") -> {
                val lastYear = now.year - 1
                detectedDateRange = dateRange(LocalDate.of(lastYear, 1, 1), LocalDate.of(lastYear, 12, 31), zoneId)
                workingQuery = workingQuery.replace("last year", "").trim()
            }
        }

        // 2. Festivals and Holidays
        if (detectedDateRange == null) {
            when {
                workingQuery.contains("diwali") -> {
                    val targetYear = Regex("""\b(20\d\d)\b""").find(workingQuery)?.groupValues?.get(1)?.toIntOrNull()
                    val window = if (targetYear != null) {
                        diwaliWindows.firstOrNull { it.first.year == targetYear }
                    } else {
                        // Find closest diwali to today
                        diwaliWindows.minByOrNull { abs(it.first.toEpochDay() - now.toEpochDay()) }
                    }
                    if (window != null) {
                        detectedDateRange = dateRange(window.first, window.second, zoneId)
                        workingQuery = workingQuery.replace("last diwali", "").replace("diwali", "").trim()
                    }
                }
                workingQuery.contains("christmas") || workingQuery.contains("xmas") -> {
                    val targetYear = Regex("""\b(20\d\d)\b""").find(workingQuery)?.groupValues?.get(1)?.toIntOrNull()
                        ?: if (now.month == Month.DECEMBER && now.dayOfMonth >= 24) now.year else (now.year - 1)
                    val start = LocalDate.of(targetYear, 12, 24)
                    val end = LocalDate.of(targetYear, 12, 26)
                    detectedDateRange = dateRange(start, end, zoneId)
                    workingQuery = workingQuery.replace("last christmas", "").replace("christmas", "").replace("xmas", "").trim()
                }
                workingQuery.contains("new year") -> {
                    val targetYear = Regex("""\b(20\d\d)\b""").find(workingQuery)?.groupValues?.get(1)?.toIntOrNull() ?: now.year
                    val start = LocalDate.of(targetYear - 1, 12, 31)
                    val end = LocalDate.of(targetYear, 1, 2)
                    detectedDateRange = dateRange(start, end, zoneId)
                    workingQuery = workingQuery.replace("new year", "").trim()
                }
            }
        }

        // 3. Seasons (summer, winter, spring, autumn/fall) + optional year
        if (detectedDateRange == null) {
            val seasonRegex = Regex("""\b(summer|winter|spring|autumn|fall)(?:\s+(20\d\d))?\b""")
            seasonRegex.find(workingQuery)?.let { match ->
                val season = match.groupValues[1]
                val year = match.groupValues[2].toIntOrNull() ?: now.year
                val (start, end) = when (season) {
                    "spring" -> LocalDate.of(year, 3, 1) to LocalDate.of(year, 5, 31)
                    "summer" -> LocalDate.of(year, 6, 1) to LocalDate.of(year, 8, 31)
                    "autumn", "fall" -> LocalDate.of(year, 9, 1) to LocalDate.of(year, 11, 30)
                    "winter" -> LocalDate.of(year - 1, 12, 1) to LocalDate.of(year, 2, 28)
                    else -> null to null
                }
                if (start != null && end != null) {
                    detectedDateRange = dateRange(start, end, zoneId)
                    workingQuery = workingQuery.removeRange(match.range).trim()
                }
            }
        }

        // 4. Date ranges: "jan to march 2025", "march - may 2024", "2022 to 2024"
        if (detectedDateRange == null) {
            val monthToMonthRegex = Regex("""\b([a-z]+)\s*(?:to|-)\s*([a-z]+)(?:\s+(20\d\d))?\b""")
            monthToMonthRegex.find(workingQuery)?.let { match ->
                val m1 = monthLookup[match.groupValues[1]]
                val m2 = monthLookup[match.groupValues[2]]
                val year = match.groupValues[3].toIntOrNull() ?: now.year
                if (m1 != null && m2 != null) {
                    val start = LocalDate.of(year, m1, 1)
                    val endMonth = YearMonth.of(year, m2)
                    val end = endMonth.atEndOfMonth()
                    detectedDateRange = dateRange(start, end, zoneId)
                    workingQuery = workingQuery.removeRange(match.range).trim()
                }
            }
        }

        if (detectedDateRange == null) {
            val yearToYearRegex = Regex("""\b(20\d\d)\s*(?:to|-)\s*(20\d\d)\b""")
            yearToYearRegex.find(workingQuery)?.let { match ->
                val y1 = match.groupValues[1].toInt()
                val y2 = match.groupValues[2].toInt()
                val start = LocalDate.of(min(y1, y2), 1, 1)
                val end = LocalDate.of(maxOf(y1, y2), 12, 31)
                detectedDateRange = dateRange(start, end, zoneId)
                workingQuery = workingQuery.removeRange(match.range).trim()
            }
        }

        // 5. Explicit Month + Year or Year alone: "March 2025", "2024"
        if (detectedDateRange == null) {
            val monthYearRegex = Regex("""\b([a-z]+)\s+(20\d\d)\b""")
            monthYearRegex.find(workingQuery)?.let { match ->
                val month = monthLookup[match.groupValues[1]]
                val year = match.groupValues[2].toIntOrNull()
                if (month != null && year != null) {
                    val ym = YearMonth.of(year, month)
                    detectedDateRange = dateRange(ym.atDay(1), ym.atEndOfMonth(), zoneId)
                    workingQuery = workingQuery.removeRange(match.range).trim()
                }
            }
        }

        if (detectedDateRange == null) {
            val standaloneYearRegex = Regex("""\b(20\d\d)\b""")
            standaloneYearRegex.find(workingQuery)?.let { match ->
                val year = match.groupValues[1].toInt()
                detectedDateRange = dateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), zoneId)
                workingQuery = workingQuery.removeRange(match.range).trim()
            }
        }

        // 6. Media type keywords
        val rawTokens = workingQuery.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val remainingTokens = mutableListOf<String>()

        for (token in rawTokens) {
            when (token) {
                "video", "videos", "mp4", "recording" -> {
                    detectedFilter = SearchMediaFilter.VIDEOS
                }
                "photo", "photos", "pic", "pics", "pictures", "image", "images" -> {
                    detectedFilter = SearchMediaFilter.PHOTOS
                }
                "screenshot", "screenshots", "screen" -> {
                    detectedFilter = SearchMediaFilter.SCREENSHOTS
                }
                "selfie", "selfies" -> {
                    detectedFilter = SearchMediaFilter.SELFIES
                }
                "gif", "gifs" -> {
                    detectedFilter = SearchMediaFilter.GIFS
                }
                "motion", "livephoto" -> {
                    detectedFilter = SearchMediaFilter.MOTION_PHOTOS
                }
                "favorite", "favorites", "fav", "liked", "starred" -> {
                    detectedFilter = SearchMediaFilter.FAVORITES
                    isFavorite = true
                }
                "from", "in", "at", "with", "of", "and", "the", "on" -> {
                    // Stopwords in search phrases like "videos from goa 2024" or "photos with sunset"
                }
                else -> {
                    // Check if month name alone was passed: e.g. "march"
                    val monthSolo = monthLookup[token]
                    if (monthSolo != null && detectedDateRange == null) {
                        val ym = YearMonth.of(now.year, monthSolo)
                        detectedDateRange = dateRange(ym.atDay(1), ym.atEndOfMonth(), zoneId)
                    } else {
                        remainingTokens.add(token)
                    }
                }
            }
        }

        // Build FTS prefix query
        val ftsQuery = if (remainingTokens.isNotEmpty()) {
            remainingTokens.joinToString(" ") { "$it*" }
        } else null

        return ParsedSearchQuery(
            rawQuery = trimmed,
            dateRange = detectedDateRange,
            inferredFilter = detectedFilter,
            isFavoriteOnly = isFavorite,
            textTokens = remainingTokens,
            ftsQuery = ftsQuery
        )
    }

    private fun dayRange(day: LocalDate, zoneId: ZoneId): LongRange {
        val start = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = day.atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli()
        return start..end
    }

    private fun dateRange(startDay: LocalDate, endDay: LocalDate, zoneId: ZoneId): LongRange {
        val start = startDay.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = endDay.atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli()
        return start..end
    }

    fun matchesFilter(item: MediaItem, filter: SearchMediaFilter): Boolean = when (filter) {
        SearchMediaFilter.PHOTOS -> !item.isVideo && !item.isGif
        SearchMediaFilter.VIDEOS -> item.isVideo
        SearchMediaFilter.FAVORITES -> item.isFavorite
        SearchMediaFilter.SCREENSHOTS ->
            item.bucketName.contains("screenshot", ignoreCase = true) ||
                item.relativePath.contains("screenshot", ignoreCase = true) ||
                item.displayName.contains("screenshot", ignoreCase = true)
        SearchMediaFilter.SELFIES ->
            item.bucketName.contains("selfie", ignoreCase = true) ||
                item.relativePath.contains("selfie", ignoreCase = true) ||
                item.displayName.contains("selfie", ignoreCase = true) ||
                item.displayName.startsWith("IMG_FRONT", ignoreCase = true) ||
                item.displayName.contains("_FRONT_", ignoreCase = true)
        SearchMediaFilter.GIFS -> item.isGif
        SearchMediaFilter.MOTION_PHOTOS -> item.isMotionPhoto
    }

    fun generateDateTokens(timestampMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val ldt = Instant.ofEpochMilli(timestampMs).atZone(zoneId).toLocalDateTime()
        val year = ldt.year
        val monthFull = ldt.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()
        val monthShort = ldt.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase()
        val monthNum = String.format(Locale.ROOT, "%02d", ldt.monthValue)
        val day = ldt.dayOfMonth
        val daySuffix = when {
            day in 11..13 -> "${day}th"
            day % 10 == 1 -> "${day}st"
            day % 10 == 2 -> "${day}nd"
            day % 10 == 3 -> "${day}rd"
            else -> "${day}th"
        }
        val dayOfWeek = ldt.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()
        val dayOfWeekShort = ldt.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase()
        val isWeekend = ldt.dayOfWeek == DayOfWeek.SATURDAY || ldt.dayOfWeek == DayOfWeek.SUNDAY
        val weekendTag = if (isWeekend) "weekend" else "weekday"

        val season = when (ldt.month) {
            Month.MARCH, Month.APRIL, Month.MAY -> "spring"
            Month.JUNE, Month.JULY, Month.AUGUST -> "summer"
            Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER -> "autumn fall"
            Month.DECEMBER, Month.JANUARY, Month.FEBRUARY -> "winter"
        }

        val holidayTag = when {
            ldt.month == Month.DECEMBER && ldt.dayOfMonth in 24..26 -> "christmas xmas"
            (ldt.month == Month.DECEMBER && ldt.dayOfMonth == 31) ||
                (ldt.month == Month.JANUARY && ldt.dayOfMonth in 1..2) -> "newyear new year"
            else -> ""
        }

        return "$year $monthFull $monthShort $monthNum $day $daySuffix $dayOfWeek $dayOfWeekShort $weekendTag $season $holidayTag".trim()
    }

    fun generateMediaTypeTokens(item: MediaItem): String = buildString {
        if (item.isVideo) append("video mp4 movie clip ")
        if (item.isGif) append("gif animation ")
        if (item.isMotionPhoto) append("motion live livephoto ")
        if (item.isFavorite) append("favorite fav liked starred ")
        if (item.bucketName.contains("screenshot", ignoreCase = true) ||
            item.relativePath.contains("screenshot", ignoreCase = true) ||
            item.displayName.contains("screenshot", ignoreCase = true)
        ) {
            append("screenshot screen capture ")
        }
        if (item.bucketName.contains("selfie", ignoreCase = true) ||
            item.displayName.contains("selfie", ignoreCase = true) ||
            item.displayName.contains("_front_", ignoreCase = true)
        ) {
            append("selfie front portrait ")
        }
        if (!item.isVideo && !item.isGif) append("photo image picture pic ")
    }.trim()

    /**
     * Compute Levenshtein distance between two strings for fuzzy matching.
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
