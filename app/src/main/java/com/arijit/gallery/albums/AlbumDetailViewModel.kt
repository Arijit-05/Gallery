package com.arijit.gallery.albums

import android.content.IntentSender
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arijit.gallery.core.domain.model.Album
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.SortOrder
import com.arijit.gallery.core.domain.model.SystemAlbumType
import com.arijit.gallery.core.domain.repository.AlbumRepository
import com.arijit.gallery.core.domain.repository.MediaRepository
import com.arijit.gallery.core.domain.repository.SettingsRepository
import com.arijit.gallery.core.media.MediaMutationManager
import com.arijit.gallery.core.media.MutationOutcome
import com.arijit.gallery.viewer.ViewerLaunchCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class AlbumDetailUiState(
    val title: String = "",
    val isSystemAlbum: Boolean = false,
    val items: ImmutableList<MediaItem> = persistentListOf(),
    val albumsForTransfer: ImmutableList<Album> = persistentListOf(),
    val gridColumns: Int = 4,
    val sortOrder: SortOrder = SortOrder.DATE_TAKEN_DESC,
    val selectedIds: ImmutableSet<Long> = persistentSetOf(),
    val isSelectionMode: Boolean = false,
    val isLoading: Boolean = true,
    val pendingIntentSender: IntentSender? = null,
    val statusMessage: String? = null
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val albumRepository: AlbumRepository,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository,
    private val mutationManager: MediaMutationManager,
    private val viewerLaunchCache: ViewerLaunchCache
) : ViewModel() {

    private val rawAlbumKey: String = savedStateHandle.get<String>("albumKey") ?: SystemAlbumType.ALL_PHOTOS.routeKey
    private val systemType: SystemAlbumType? = SystemAlbumType.entries.firstOrNull { it.routeKey == rawAlbumKey }
    private var pendingConfirmAction: (suspend () -> Unit)? = null

    private val _uiState = MutableStateFlow(
        AlbumDetailUiState(
            title = systemType?.title ?: "Album",
            isSystemAlbum = systemType != null
        )
    )
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    init {
        observeAlbumMedia()
    }

    private fun observeAlbumMedia() {
        viewModelScope.launch {
            combine(
                settingsRepository.userSettings,
                albumRepository.observeAlbums(),
                mediaRepository.observeMediaChanges().onStart { emit(Unit) }
            ) { settings, albums, _ ->
                Triple(settings, albums, Unit)
            }.collect { (settings, albums, _) ->
                val items = if (systemType != null) {
                    albumRepository.getMediaForSystemAlbum(systemType, settings.sortOrder)
                } else {
                    albumRepository.getMediaForAlbum(rawAlbumKey, settings.sortOrder)
                }
                val albumTitle = systemType?.title
                    ?: albums.firstOrNull { it.id == rawAlbumKey }?.name
                    ?: items.firstOrNull()?.bucketName
                    ?: "Album"

                _uiState.update { state ->
                    val validIds = items.asSequence().map { it.id }.toSet()
                    val retainedSelected = state.selectedIds.filter { it in validIds }.toImmutableSet()
                    state.copy(
                        title = albumTitle,
                        items = items.toImmutableList(),
                        albumsForTransfer = albums.toImmutableList(),
                        gridColumns = settings.gridColumns,
                        sortOrder = settings.sortOrder,
                        selectedIds = retainedSelected,
                        isSelectionMode = state.isSelectionMode && retainedSelected.isNotEmpty(),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun prepareViewerLaunch(item: MediaItem, thumbnailSizePx: Int) {
        viewerLaunchCache.put(
            item = item,
            thumbnailSizePx = thumbnailSizePx,
            sortOrder = _uiState.value.sortOrder,
            itemsOverride = _uiState.value.items
        )
    }

    fun enterSelectionWith(id: Long) {
        _uiState.update { state ->
            state.copy(
                isSelectionMode = true,
                selectedIds = (state.selectedIds + id).toImmutableSet()
            )
        }
    }

    fun toggleSelection(id: Long) {
        _uiState.update { state ->
            val next = if (id in state.selectedIds) state.selectedIds - id else state.selectedIds + id
            state.copy(
                selectedIds = next.toImmutableSet(),
                isSelectionMode = next.isNotEmpty()
            )
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            val allIds = state.items.map { it.id }.toImmutableSet()
            state.copy(
                selectedIds = allIds,
                isSelectionMode = allIds.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update { state ->
            state.copy(
                selectedIds = persistentSetOf(),
                isSelectionMode = false
            )
        }
    }

    fun getSelectedItems(): List<MediaItem> {
        val selected = _uiState.value.selectedIds
        return _uiState.value.items.filter { it.id in selected }
    }

    fun favoriteSelected(favorite: Boolean) {
        val items = getSelectedItems()
        if (items.isEmpty()) return
        viewModelScope.launch {
            handleMutationOutcome(mutationManager.requestFavorite(items, favorite))
        }
    }

    fun trashSelected() {
        val items = getSelectedItems()
        if (items.isEmpty()) return
        viewModelScope.launch {
            handleMutationOutcome(mutationManager.requestTrash(items))
        }
    }

    fun copySelectedToAlbum(albumName: String, explicitRelativePath: String?) {
        val items = getSelectedItems()
        if (items.isEmpty()) return
        viewModelScope.launch {
            val outcome = mutationManager.copyItemsToAlbum(
                items = items,
                albumName = albumName,
                explicitRelativePath = explicitRelativePath
            )
            if (outcome is MutationOutcome.Success) {
                albumRepository.createCustomAlbum(albumName)
                clearSelection()
                _uiState.update { it.copy(statusMessage = "Copied ${items.size} item(s) to $albumName") }
            } else {
                handleMutationOutcome(outcome)
            }
        }
    }

    fun moveSelectedToAlbum(albumName: String, explicitRelativePath: String?) {
        val items = getSelectedItems()
        if (items.isEmpty()) return
        viewModelScope.launch {
            albumRepository.createCustomAlbum(albumName)
            val outcome = mutationManager.moveItemsToAlbum(
                items = items,
                albumName = albumName,
                explicitRelativePath = explicitRelativePath
            )
            handleMutationOutcome(outcome)
        }
    }

    fun onIntentSenderResult(confirmed: Boolean) {
        val action = pendingConfirmAction
        pendingConfirmAction = null
        _uiState.update { it.copy(pendingIntentSender = null) }
        if (confirmed) {
            viewModelScope.launch {
                action?.invoke()
                clearSelection()
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private suspend fun handleMutationOutcome(outcome: MutationOutcome) {
        when (outcome) {
            is MutationOutcome.Success -> {
                clearSelection()
            }
            is MutationOutcome.RequiresConfirmation -> {
                pendingConfirmAction = outcome.onConfirmed
                _uiState.update { it.copy(pendingIntentSender = outcome.intentSender) }
            }
            is MutationOutcome.Error -> {
                _uiState.update { it.copy(statusMessage = outcome.message) }
            }
        }
    }
}

