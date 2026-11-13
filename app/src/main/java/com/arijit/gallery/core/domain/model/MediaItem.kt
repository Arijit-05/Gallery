package com.arijit.gallery.core.domain.model

import androidx.compose.runtime.Immutable

enum class MediaType {
    IMAGE,
    VIDEO,
    GIF,
    MOTION_PHOTO
}

@Immutable
data class MediaItem(
    val id: Long,
    val contentUri: String,
    val displayName: String,
    val mimeType: String,
    val mediaType: MediaType,
    val dateTakenMs: Long,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bucketId: String,
    val bucketName: String,
    val relativePath: String,
    val isFavorite: Boolean,
    val orientation: Int
) {
    val effectiveTimestampMs: Long
        get() = when {
            dateTakenMs > 0L -> dateTakenMs
            dateModifiedSec > 0L -> dateModifiedSec * 1000L
            else -> dateAddedSec * 1000L
        }

    val isVideo: Boolean
        get() = mediaType == MediaType.VIDEO

    val isGif: Boolean
        get() = mediaType == MediaType.GIF

    val isMotionPhoto: Boolean
        get() = mediaType == MediaType.MOTION_PHOTO
}

