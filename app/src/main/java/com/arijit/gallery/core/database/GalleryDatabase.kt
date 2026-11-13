package com.arijit.gallery.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.arijit.gallery.core.database.dao.AlbumSettingsDao
import com.arijit.gallery.core.database.dao.CustomAlbumDao
import com.arijit.gallery.core.database.dao.FavoriteDao
import com.arijit.gallery.core.database.dao.MediaLocationDao
import com.arijit.gallery.core.database.dao.MediaSearchDao
import com.arijit.gallery.core.database.dao.SearchHistoryDao
import com.arijit.gallery.core.database.dao.TimelineSnapshotDao
import com.arijit.gallery.core.database.dao.VaultDao
import com.arijit.gallery.core.database.entity.AlbumSettingsEntity
import com.arijit.gallery.core.database.entity.CustomAlbumEntity
import com.arijit.gallery.core.database.entity.FavoriteOverrideEntity
import com.arijit.gallery.core.database.entity.MediaIndexStatusEntity
import com.arijit.gallery.core.database.entity.MediaLocationEntity
import com.arijit.gallery.core.database.entity.MediaSearchFtsEntity
import com.arijit.gallery.core.database.entity.SearchHistoryEntity
import com.arijit.gallery.core.database.entity.TimelineSnapshotEntity
import com.arijit.gallery.core.database.entity.VaultItemEntity

@Database(
    entities = [
        TimelineSnapshotEntity::class,
        FavoriteOverrideEntity::class,
        AlbumSettingsEntity::class,
        CustomAlbumEntity::class,
        MediaLocationEntity::class,
        SearchHistoryEntity::class,
        VaultItemEntity::class,
        MediaSearchFtsEntity::class,
        MediaIndexStatusEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun timelineSnapshotDao(): TimelineSnapshotDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun albumSettingsDao(): AlbumSettingsDao
    abstract fun customAlbumDao(): CustomAlbumDao
    abstract fun mediaLocationDao(): MediaLocationDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun vaultDao(): VaultDao
    abstract fun mediaSearchDao(): MediaSearchDao
}
