package com.arijit.gallery.core.domain.model

import androidx.compose.runtime.Immutable

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED
}

enum class TimelineGranularity {
    DAY,
    MONTH,
    YEAR
}

enum class SortOrder {
    DATE_TAKEN_DESC,
    DATE_TAKEN_ASC,
    DATE_MODIFIED_DESC
}

@Immutable
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val gridColumns: Int = 4,
    val timelineGranularity: TimelineGranularity = TimelineGranularity.DAY,
    val sortOrder: SortOrder = SortOrder.DATE_TAKEN_DESC,
    val hiddenBuckets: Set<String> = emptySet(),
    val videoAutoplay: Boolean = true
)

