package com.arijit.gallery.core.domain.model

import androidx.compose.runtime.Immutable

enum class SystemAlbumType(val routeKey: String, val title: String) {
    ALL_PHOTOS("system_all", "All Photos"),
    VIDEOS("system_videos", "Videos"),
    FAVORITES("system_favorites", "Favorites"),
    SCREENSHOTS("system_screenshots", "Screenshots")
}

@Immutable
data class SystemAlbumSummary(
    val coverUri: String? = null,
    val coverId: Long = 0L,
    val coverDateModifiedSec: Long = 0L,
    val count: Int = 0
)

@Immutable
data class Album(
    val id: String,
    val name: String,
    val coverUri: String?,
    val itemCount: Int,
    val relativePath: String,
    val isPinned: Boolean = false,
    val isHiddenFromTimeline: Boolean = false,
    val isCustomEmpty: Boolean = false,
    val sortPosition: Int = 0,
    val coverId: Long = 0L,
    val coverDateModifiedSec: Long = 0L
)

@Immutable
data class TrashedMediaItem(
    val item: MediaItem,
    val dateExpiresSec: Long,
    val daysRemaining: Int
)

enum class SearchMediaFilter(val label: String) {
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    FAVORITES("Favorites"),
    SCREENSHOTS("Screenshots"),
    SELFIES("Selfies"),
    GIFS("GIFs"),
    MOTION_PHOTOS("Motion Photos")
}

