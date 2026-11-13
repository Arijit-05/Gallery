package com.arijit.gallery.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_albums")
data class CustomAlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val relativePath: String,
    val createdAtMs: Long
)

