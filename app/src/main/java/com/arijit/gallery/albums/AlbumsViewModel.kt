package com.arijit.gallery.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arijit.gallery.core.domain.model.Album
import com.arijit.gallery.core.domain.repository.AlbumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val albumRepository: AlbumRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumsUiState())
    val uiState: StateFlow<AlbumsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                albumRepository.observeSystemAlbumSummaries(),
                albumRepository.observeTrashCount(),
                albumRepository.observeAlbums()
            ) { summaries, trashCount, albums ->
                Triple(summaries, trashCount, albums)
            }.collect { (summaries, trashCount, albums) ->
                _uiState.update { state ->
                    state.copy(
                        systemSummaries = summaries.toImmutableMap(),
                        trashCount = trashCount,
                        albums = albums.toImmutableList(),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun createAlbum(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            albumRepository.createCustomAlbum(trimmed)
            _uiState.update { it.copy(statusMessage = "Created album \"$trimmed\"") }
        }
    }

    fun renameAlbum(albumId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            albumRepository.renameAlbum(albumId, trimmed)
            _uiState.update { it.copy(statusMessage = "Renamed to \"$trimmed\"") }
        }
    }

    fun togglePin(album: Album) {
        viewModelScope.launch {
            albumRepository.setAlbumPinned(album.id, !album.isPinned)
        }
    }

    fun toggleHideFromTimeline(album: Album) {
        viewModelScope.launch {
            val nextHidden = !album.isHiddenFromTimeline
            albumRepository.setAlbumHiddenFromTimeline(album.id, nextHidden)
            val msg = if (nextHidden) {
                "\"${album.name}\" hidden from Photos timeline"
            } else {
                "\"${album.name}\" shown in Photos timeline"
            }
            _uiState.update { it.copy(statusMessage = msg) }
        }
    }

    fun moveAlbumOrder(album: Album, moveUp: Boolean) {
        viewModelScope.launch {
            albumRepository.moveAlbumOrder(album.id, moveUp)
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}

