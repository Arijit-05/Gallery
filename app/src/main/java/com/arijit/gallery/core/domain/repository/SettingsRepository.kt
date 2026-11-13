package com.arijit.gallery.core.domain.repository

import com.arijit.gallery.core.domain.model.SortOrder
import com.arijit.gallery.core.domain.model.ThemeMode
import com.arijit.gallery.core.domain.model.TimelineGranularity
import com.arijit.gallery.core.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val userSettings: Flow<UserSettings>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setGridColumns(columns: Int)
    suspend fun setTimelineGranularity(granularity: TimelineGranularity)
    suspend fun setSortOrder(sortOrder: SortOrder)
    suspend fun setHiddenBuckets(buckets: Set<String>)
    suspend fun setVideoAutoplay(enabled: Boolean)
}

