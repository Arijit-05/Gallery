package com.arijit.gallery.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_index_status")
data class MediaIndexStatusEntity(
    @PrimaryKey val mediaId: Long,
    val dateModifiedSec: Long,
    val textIndexedAtMs: Long,
    val embeddingIndexedAtMs: Long,
    val modelIndexVersion: Int
)

