package com.arijit.gallery.memories

import com.arijit.gallery.core.domain.model.MediaItem
import java.util.Calendar

data class MemoryStory(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverMedia: MediaItem,
    val items: List<MediaItem>
)

object MemoriesExtractor {

    /**
     * Extracts memories from the provided [mediaList]:
     * - "On this day" (matching month and day in previous years)
     * - "Throwback" (6+ months ago clusters)
     * - "Best of this month"
     */
    fun extractMemories(mediaList: List<MediaItem>): List<MemoryStory> {
        if (mediaList.isEmpty()) return emptyList()

        val stories = mutableListOf<MemoryStory>()
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH)
        val currentDay = now.get(Calendar.DAY_OF_MONTH)

        val cal = Calendar.getInstance()

        // 1. "On this day" memories
        val onThisDayGroups = mutableMapOf<Int, MutableList<MediaItem>>() // year -> items
        for (item in mediaList) {
            val ts = item.effectiveTimestampMs
            if (ts <= 0) continue
            cal.timeInMillis = ts
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH)
            val d = cal.get(Calendar.DAY_OF_MONTH)

            if (m == currentMonth && d == currentDay && y < currentYear) {
                onThisDayGroups.getOrPut(y) { mutableListOf() }.add(item)
            }
        }

        for ((year, items) in onThisDayGroups) {
            val yearsAgo = currentYear - year
            val yearLabel = if (yearsAgo == 1) "1 year ago today" else "$yearsAgo years ago today"
            stories.add(
                MemoryStory(
                    id = "on_this_day_$year",
                    title = "On this day",
                    subtitle = "$yearLabel • $year",
                    coverMedia = items.first(),
                    items = items
                )
            )
        }

        // 2. "Throwback" memories (cluster from 6 months to 2 years ago)
        val sixMonthsAgoMs = now.timeInMillis - (180L * 24 * 3600 * 1000)
        val throwbackItems = mediaList.filter {
            val ts = it.effectiveTimestampMs
            ts in 1..sixMonthsAgoMs
        }
        if (throwbackItems.size >= 4) {
            // Group by year-month
            val byMonth = throwbackItems.groupBy {
                cal.timeInMillis = it.effectiveTimestampMs
                "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}"
            }
            // Pick the largest cluster
            val largestMonthCluster = byMonth.values.maxByOrNull { it.size }
            if (largestMonthCluster != null && largestMonthCluster.size >= 4) {
                cal.timeInMillis = largestMonthCluster.first().effectiveTimestampMs
                val monthNames = arrayOf(
                    "January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December"
                )
                val mName = monthNames.getOrElse(cal.get(Calendar.MONTH)) { "" }
                val y = cal.get(Calendar.YEAR)
                stories.add(
                    MemoryStory(
                        id = "throwback_${y}_${cal.get(Calendar.MONTH)}",
                        title = "Throwback",
                        subtitle = "$mName $y",
                        coverMedia = largestMonthCluster.first(),
                        items = largestMonthCluster.take(15)
                    )
                )
            }
        }

        // 3. "Highlights of this Month"
        val thisMonthItems = mediaList.filter {
            cal.timeInMillis = it.effectiveTimestampMs
            cal.get(Calendar.YEAR) == currentYear && cal.get(Calendar.MONTH) == currentMonth
        }
        if (thisMonthItems.size >= 5) {
            val monthNames = arrayOf(
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
            )
            val mName = monthNames.getOrElse(currentMonth) { "" }
            stories.add(
                MemoryStory(
                    id = "this_month_${currentYear}_$currentMonth",
                    title = "Highlights",
                    subtitle = "$mName $currentYear",
                    coverMedia = thisMonthItems.first(),
                    items = thisMonthItems.take(15)
                )
            )
        }

        return stories
    }
}

