package com.arijit.gallery.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.arijit.gallery.core.domain.model.SortOrder
import com.arijit.gallery.core.domain.model.ThemeMode
import com.arijit.gallery.core.domain.model.TimelineGranularity
import com.arijit.gallery.core.domain.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.galleryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gallery_preferences"
)

@Singleton
class GalleryPreferencesDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val TIMELINE_GRANULARITY = stringPreferencesKey("timeline_granularity")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val HIDDEN_BUCKETS = stringSetPreferencesKey("hidden_buckets")
        val VIDEO_AUTOPLAY = booleanPreferencesKey("video_autoplay")
    }

    val settingsFlow: Flow<UserSettings> = context.galleryDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            UserSettings(
                themeMode = prefs[Keys.THEME_MODE]
                    ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.SYSTEM,
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
                gridColumns = (prefs[Keys.GRID_COLUMNS] ?: 4).coerceIn(2, 6),
                timelineGranularity = prefs[Keys.TIMELINE_GRANULARITY]
                    ?.let { runCatching { TimelineGranularity.valueOf(it) }.getOrNull() }
                    ?: TimelineGranularity.DAY,
                sortOrder = prefs[Keys.SORT_ORDER]
                    ?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() }
                    ?: SortOrder.DATE_TAKEN_DESC,
                hiddenBuckets = prefs[Keys.HIDDEN_BUCKETS] ?: emptySet(),
                videoAutoplay = prefs[Keys.VIDEO_AUTOPLAY] ?: true
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.galleryDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.galleryDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setGridColumns(columns: Int) {
        context.galleryDataStore.edit { it[Keys.GRID_COLUMNS] = columns.coerceIn(2, 6) }
    }

    suspend fun setTimelineGranularity(granularity: TimelineGranularity) {
        context.galleryDataStore.edit { it[Keys.TIMELINE_GRANULARITY] = granularity.name }
    }

    suspend fun setSortOrder(sortOrder: SortOrder) {
        context.galleryDataStore.edit { it[Keys.SORT_ORDER] = sortOrder.name }
    }

    suspend fun setHiddenBuckets(buckets: Set<String>) {
        context.galleryDataStore.edit { it[Keys.HIDDEN_BUCKETS] = buckets }
    }

    suspend fun setVideoAutoplay(enabled: Boolean) {
        context.galleryDataStore.edit { it[Keys.VIDEO_AUTOPLAY] = enabled }
    }
}

