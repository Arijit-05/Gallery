package com.arijit.gallery.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.MediaType

@Entity(tableName = "timeline_snapshot")
data class TimelineSnapshotEntity(
    @PrimaryKey val id: Long,
    val positionIndex: Int,
    val contentUri: String,
    val displayName: String,
    val mimeType: String,
    val mediaType: String,
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
    fun toDomain(): MediaItem = MediaItem(
        id = id,
        contentUri = contentUri,
        displayName = displayName,
        mimeType = mimeType,
        mediaType = runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.IMAGE),
        dateTakenMs = dateTakenMs,
        dateAddedSec = dateAddedSec,
        dateModifiedSec = dateModifiedSec,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        durationMs = durationMs,
        bucketId = bucketId,
        bucketName = bucketName,
        relativePath = relativePath,
        isFavorite = isFavorite,
        orientation = orientation
    )

    companion object {
        fun fromDomain(item: MediaItem, positionIndex: Int): TimelineSnapshotEntity =
            TimelineSnapshotEntity(
                id = item.id,
                positionIndex = positionIndex,
                contentUri = item.contentUri,
                displayName = item.displayName,
                mimeType = item.mimeType,
                mediaType = item.mediaType.name,
                dateTakenMs = item.dateTakenMs,
                dateAddedSec = item.dateAddedSec,
                dateModifiedSec = item.dateModifiedSec,
                sizeBytes = item.sizeBytes,
                width = item.width,
                height = item.height,
                durationMs = item.durationMs,
                bucketId = item.bucketId,
                bucketName = item.bucketName,
                relativePath = item.relativePath,
                isFavorite = item.isFavorite,
                orientation = item.orientation
            )
    }
}

