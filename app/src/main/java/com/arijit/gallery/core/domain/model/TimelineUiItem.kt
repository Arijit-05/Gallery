package com.arijit.gallery.core.domain.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface TimelineUiItem {
    val stableKey: String

    @Immutable
    data class Header(
        val groupKey: String,
        val title: String,
        val timestampMs: Long
    ) : TimelineUiItem {
        override val stableKey: String = "header_$groupKey"
    }

    @Immutable
    data class Media(
        val item: MediaItem,
        val groupKey: String
    ) : TimelineUiItem {
        override val stableKey: String = "media_${item.id}"
    }
}

@Immutable
data class TimelineDateAnchor(
    val groupKey: String,
    val label: String,
    val itemOffset: Int,
    val timestampMs: Long
)

