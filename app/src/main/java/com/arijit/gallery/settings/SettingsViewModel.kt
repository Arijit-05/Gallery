package com.arijit.gallery.settings

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.arijit.gallery.core.common.IoDispatcher
import com.arijit.gallery.core.domain.model.Album
import com.arijit.gallery.core.domain.model.SortOrder
import com.arijit.gallery.core.domain.model.ThemeMode
import com.arijit.gallery.core.domain.model.TimelineGranularity
import com.arijit.gallery.core.domain.model.UserSettings
import com.arijit.gallery.core.domain.repository.AlbumRepository
import com.arijit.gallery.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@Immutable
data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val albums: ImmutableList<Album> = persistentListOf(),
    val cacheSizeBytes: Long = 0L,
    val statusMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val albumRepository: AlbumRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.userSettings,
                albumRepository.observeAlbums()
            ) { settings, albums ->
                settings to albums
            }.collect { (settings, albums) ->
                _uiState.update {
                    it.copy(
                        settings = settings,
                        albums = albums.toImmutableList()
                    )
                }
            }
        }
        refreshCacheSize()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch { settingsRepository.setGridColumns(columns) }
    }

    fun setTimelineGranularity(granularity: TimelineGranularity) {
        viewModelScope.launch { settingsRepository.setTimelineGranularity(granularity) }
    }

    fun setSortOrder(sortOrder: SortOrder) {
        viewModelScope.launch { settingsRepository.setSortOrder(sortOrder) }
    }

    fun setVideoAutoplay(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVideoAutoplay(enabled) }
    }

    fun toggleAlbumHiddenFromTimeline(album: Album) {
        viewModelScope.launch {
            albumRepository.setAlbumHiddenFromTimeline(album.id, !album.isHiddenFromTimeline)
        }
    }

    fun clearCaches() {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                runCatching {
                    SingletonImageLoader.get(context).memoryCache?.clear()
                }
                val imageCache = File(context.cacheDir, "image_cache")
                val motionCache = File(context.cacheDir, "motion_cache")
                imageCache.deleteRecursively()
                motionCache.deleteRecursively()
            }
            refreshCacheSize()
            _uiState.update { it.copy(statusMessage = "Cleared image & motion caches") }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun refreshCacheSize() {
        viewModelScope.launch {
            val size = withContext(ioDispatcher) {
                dirSize(File(context.cacheDir, "image_cache")) +
                        dirSize(File(context.cacheDir, "motion_cache"))
            }
            _uiState.update { it.copy(cacheSizeBytes = size) }
        }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}

