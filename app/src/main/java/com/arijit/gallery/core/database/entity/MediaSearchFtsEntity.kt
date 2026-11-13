package com.arijit.gallery.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "media_search_fts")
@Fts4
data class MediaSearchFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val mediaId: Long,
    val displayName: String,
    val bucketName: String,
    val dateTokens: String,
    val placeName: String,
    val mediaType: String,
    val labels: String
)

