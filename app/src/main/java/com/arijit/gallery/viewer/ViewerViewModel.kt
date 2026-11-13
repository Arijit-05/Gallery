package com.arijit.gallery.viewer

import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.SortOrder
import com.arijit.gallery.core.domain.repository.MediaRepository
import com.arijit.gallery.core.domain.repository.SettingsRepository
import com.arijit.gallery.core.media.MediaDetailsDataSource
import com.arijit.gallery.core.media.MediaMutationManager
import com.arijit.gallery.core.media.MutationOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository,
    private val detailsDataSource: MediaDetailsDataSource,
    private val mutationManager: MediaMutationManager,
    private val motionPhotoExtractor: MotionPhotoExtractor,
    private val launchCache: ViewerLaunchCache
) : ViewModel() {

    private val sessionCounter = AtomicLong(0L)
    private val _session = MutableStateFlow<ViewerSession?>(null)
    val session: StateFlow<ViewerSession?> = _session.asStateFlow()

    private var activeMediaId: Long = savedStateHandle.get<Long>("mediaId") ?: -1L
    private var hasResolvedInitialPage = false
    private var pendingConfirmAction: (suspend () -> Unit)? = null
    private var observeJob: Job? = null

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    init {
        if (activeMediaId > 0L) {
            openSessionById(activeMediaId)
        }
    }

    /**
     * Atomically opens a fresh viewer session with a brand new [ViewerSession.sessionId].
     * The snapshot of items and the exact initial index for [item] are computed immediately
     * so the very first composition frame renders the tapped photo without displaying stale
     * media from a previous session.
     */
    fun openSession(
        item: MediaItem,
        thumbnailSizePx: Int = 0,
        itemsOverride: List<MediaItem>? = null
    ): ViewerSession {
        val sessionId = sessionCounter.incrementAndGet()
        val thumbKey = if (thumbnailSizePx > 0) {
            "ms_thumb_${item.id}_${item.dateModifiedSec}_$thumbnailSizePx"
        } else {
            null
        }

        // Determine snapshot items immediately
        val snapshotItems: ImmutableList<MediaItem> = when {
            itemsOverride != null && itemsOverride.isNotEmpty() -> itemsOverride.toImmutableList()
            else -> {
                val payload = launchCache.get(item.id)
                val sortOrder = payload?.sortOrder ?: SortOrder.DATE_TAKEN_DESC
                val hiddenBuckets = payload?.hiddenBuckets ?: emptySet()
                val cached = mediaRepository.getCachedMediaIfFresh(sortOrder, hiddenBuckets)
                if (cached != null && cached.isNotEmpty()) {
                    cached.toImmutableList()
                } else {
                    persistentListOf(item)
                }
            }
        }

        // Resolve initial page strictly from the stable media ID
        val initialIndex = snapshotItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0)

        val newSession = ViewerSession(
            sessionId = sessionId,
            mediaId = item.id,
            initialIndex = initialIndex,
            heroItem = item,
            heroThumbnailKey = thumbKey,
            thumbnailSizePx = thumbnailSizePx,
            items = snapshotItems
        )

        activeMediaId = item.id
        hasResolvedInitialPage = true

        // Atomically update both uiState and session before composition
        _uiState.value = ViewerUiState(
            items = snapshotItems,
            initialPageIndex = initialIndex,
            heroItem = item,
            heroThumbnailKey = thumbKey,
            thumbnailSizePx = thumbnailSizePx,
            isLoading = false
        )
        _session.value = newSession

        observeLibraryFor(item.id)
        return newSession
    }

    fun openSessionById(mediaId: Long) {
        val payload = launchCache.get(mediaId)
        if (payload != null) {
            openSession(payload.item, payload.thumbnailSizePx, payload.itemsOverride)
        } else {
            viewModelScope.launch {
                val item = mediaRepository.getMediaByIds(listOf(mediaId)).firstOrNull()
                if (item != null) {
                    openSession(item, 0, null)
                }
            }
        }
    }

    /**
     * Completely resets all viewer state on dismiss.
     * Guarantees that subsequent opens do not retain stale PagerState, indices, or hero bitmaps.
     */
    fun closeSession() {
        observeJob?.cancel()
        observeJob = null
        activeMediaId = -1L
        hasResolvedInitialPage = false
        savedStateHandle.remove<Long>("mediaId")
        _session.value = null
        _uiState.value = ViewerUiState()
    }

    // Deprecated compat
    fun loadMedia(mediaId: Long) {
        openSessionById(mediaId)
    }

    private fun observeLibraryFor(mediaId: Long) {
        observeJob?.cancel()
        val payload = launchCache.get(mediaId)
        observeJob = viewModelScope.launch {
            launch {
                settingsRepository.userSettings
                    .map { it.videoAutoplay }
                    .distinctUntilChanged()
                    .collect { autoplay ->
                        _uiState.update { it.copy(videoAutoplay = autoplay) }
                    }
            }

            if (payload?.itemsOverride != null) {
                launch {
                    mediaRepository.observeMediaChanges().collect {
                        val allCurrent = mediaRepository.getAllMedia(payload.sortOrder, payload.hiddenBuckets).associateBy { it.id }
                        _uiState.update { state ->
                            val updated = state.items.mapNotNull { allCurrent[it.id] }
                            state.copy(items = updated.toImmutableList())
                        }
                    }
                }
                return@launch
            }

            launch {
                combine(
                    settingsRepository.userSettings
                        .map { it.sortOrder to it.hiddenBuckets }
                        .distinctUntilChanged(),
                    mediaRepository.observeMediaChanges().onStart { emit(Unit) }
                ) { (sortOrder, hiddenBuckets), _ ->
                    sortOrder to hiddenBuckets
                }.collect { (sortOrder, hiddenBuckets) ->
                    val list = mediaRepository.getAllMedia(sortOrder, hiddenBuckets)
                    val targetIndex = if (!hasResolvedInitialPage && mediaId > 0L) {
                        hasResolvedInitialPage = true
                        list.indexOfFirst { it.id == mediaId }.coerceAtLeast(0)
                    } else {
                        _uiState.value.initialPageIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0))
                    }
                    _uiState.update { state ->
                        state.copy(
                            items = list.toImmutableList(),
                            initialPageIndex = targetIndex,
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    fun toggleImmersive() {
        _uiState.update { it.copy(isImmersive = !it.isImmersive) }
    }

    fun toggleSlideshow() {
        _uiState.update { state ->
            state.copy(isSlideshowActive = !state.isSlideshowActive)
        }
    }

    fun stopSlideshow() {
        _uiState.update { it.copy(isSlideshowActive = false) }
    }

    fun toggleFavorite(item: MediaItem) {
        viewModelScope.launch {
            handleMutation(mutationManager.requestFavorite(listOf(item), !item.isFavorite))
        }
    }

    fun trashItem(item: MediaItem) {
        viewModelScope.launch {
            handleMutation(mutationManager.requestTrash(listOf(item)))
        }
    }

    fun renameItem(item: MediaItem, newDisplayName: String) {
        viewModelScope.launch {
            handleMutation(mutationManager.requestRename(item, newDisplayName))
        }
    }

    fun copyToAlbum(item: MediaItem, targetRelativePath: String) {
        viewModelScope.launch {
            handleMutation(mutationManager.copyToAlbum(item, targetRelativePath))
        }
    }

    fun loadDetails(item: MediaItem) {
        _uiState.update { it.copy(isDetailsLoading = true) }
        viewModelScope.launch {
            val details = detailsDataSource.loadDetails(item)
            _uiState.update { it.copy(details = details, isDetailsLoading = false) }
        }
    }

    fun clearDetails() {
        _uiState.update { it.copy(details = null, isDetailsLoading = false) }
    }

    fun saveVideoPosition(mediaId: Long, positionMs: Long) {
        _uiState.update { state ->
            state.copy(videoPositionsMs = (state.videoPositionsMs + (mediaId to positionMs)).toImmutableMap())
        }
    }

    suspend fun resolveMotionPhotoVideoUri(item: MediaItem) = motionPhotoExtractor.extractMotionVideoUri(item.id, item.contentUri.toUri())

    fun onIntentSenderFinished(confirmed: Boolean) {
        val callback = pendingConfirmAction
        pendingConfirmAction = null
        _uiState.update { it.copy(pendingIntentSender = null) }
        if (confirmed && callback != null) {
            viewModelScope.launch {
                callback()
            }
        }
    }

    fun consumeStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun handleMutation(outcome: MutationOutcome) {
        when (outcome) {
            is MutationOutcome.Success -> Unit
            is MutationOutcome.RequiresConfirmation -> {
                pendingConfirmAction = outcome.onConfirmed
                _uiState.update { it.copy(pendingIntentSender = outcome.intentSender) }
            }
            is MutationOutcome.Error -> showMessage(outcome.message)
        }
    }

    private fun showMessage(msg: String) {
        _uiState.update { it.copy(statusMessage = msg) }
    }
}
