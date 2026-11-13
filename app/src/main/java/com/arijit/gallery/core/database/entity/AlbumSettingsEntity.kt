package com.arijit.gallery.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "album_settings")
data class AlbumSettingsEntity(
    @PrimaryKey val bucketId: String,
    val customName: String? = null,
    val isPinned: Boolean = false,
    val isHiddenFromTimeline: Boolean = false,
    val sortPosition: Int = 0
)

