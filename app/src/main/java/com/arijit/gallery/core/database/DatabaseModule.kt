package com.arijit.gallery.core.database

import android.content.Context
import androidx.room.Room
import com.arijit.gallery.core.database.dao.AlbumSettingsDao
import com.arijit.gallery.core.database.dao.CustomAlbumDao
import com.arijit.gallery.core.database.dao.FavoriteDao
import com.arijit.gallery.core.database.dao.MediaLocationDao
import com.arijit.gallery.core.database.dao.MediaSearchDao
import com.arijit.gallery.core.database.dao.SearchHistoryDao
import com.arijit.gallery.core.database.dao.TimelineSnapshotDao
import com.arijit.gallery.core.database.dao.VaultDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideGalleryDatabase(
        @ApplicationContext context: Context
    ): GalleryDatabase = Room.databaseBuilder(
        context,
        GalleryDatabase::class.java,
        "arijit_gallery.db"
    )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides
    fun provideTimelineSnapshotDao(db: GalleryDatabase): TimelineSnapshotDao =
        db.timelineSnapshotDao()

    @Provides
    fun provideFavoriteDao(db: GalleryDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideAlbumSettingsDao(db: GalleryDatabase): AlbumSettingsDao = db.albumSettingsDao()

    @Provides
    fun provideCustomAlbumDao(db: GalleryDatabase): CustomAlbumDao = db.customAlbumDao()

    @Provides
    fun provideMediaLocationDao(db: GalleryDatabase): MediaLocationDao = db.mediaLocationDao()

    @Provides
    fun provideSearchHistoryDao(db: GalleryDatabase): SearchHistoryDao = db.searchHistoryDao()

    @Provides
    fun provideVaultDao(db: GalleryDatabase): VaultDao = db.vaultDao()

    @Provides
    fun provideMediaSearchDao(db: GalleryDatabase): MediaSearchDao = db.mediaSearchDao()
}

