package com.arijit.gallery.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_overrides")
data class FavoriteOverrideEntity(
    @PrimaryKey val mediaId: Long,
    val isFavorite: Boolean,
    val updatedAtMs: Long
)

