package com.arijit.gallery.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_locations")
data class MediaLocationEntity(
    @PrimaryKey val mediaId: Long,
    val locationText: String,
    val updatedAtMs: Long
)

