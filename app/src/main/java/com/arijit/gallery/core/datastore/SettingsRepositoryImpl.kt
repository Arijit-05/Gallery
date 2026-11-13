package com.arijit.gallery.core.datastore

import com.arijit.gallery.core.domain.model.SortOrder
import com.arijit.gallery.core.domain.model.ThemeMode
import com.arijit.gallery.core.domain.model.TimelineGranularity
import com.arijit.gallery.core.domain.model.UserSettings
import com.arijit.gallery.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataSource: GalleryPreferencesDataSource
) : SettingsRepository {

    override val userSettings: Flow<UserSettings> = dataSource.settingsFlow

    override suspend fun setThemeMode(mode: ThemeMode) = dataSource.setThemeMode(mode)
    override suspend fun setDynamicColor(enabled: Boolean) = dataSource.setDynamicColor(enabled)
    override suspend fun setGridColumns(columns: Int) = dataSource.setGridColumns(columns)
    override suspend fun setTimelineGranularity(granularity: TimelineGranularity) =
        dataSource.setTimelineGranularity(granularity)
    override suspend fun setSortOrder(sortOrder: SortOrder) = dataSource.setSortOrder(sortOrder)
    override suspend fun setHiddenBuckets(buckets: Set<String>) = dataSource.setHiddenBuckets(buckets)
    override suspend fun setVideoAutoplay(enabled: Boolean) = dataSource.setVideoAutoplay(enabled)
}

