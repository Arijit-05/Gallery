package com.arijit.gallery.photos

import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.arijit.gallery.core.common.DefaultDispatcher
import com.arijit.gallery.core.common.Formatters
import com.arijit.gallery.core.domain.model.Album
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.SortOrder
import com.arijit.gallery.core.domain.model.ThemeMode
import com.arijit.gallery.core.domain.model.TimelineDateAnchor
import com.arijit.gallery.core.domain.model.TimelineGranularity
import com.arijit.gallery.core.domain.model.TimelineUiItem
import com.arijit.gallery.core.domain.repository.AlbumRepository
import com.arijit.gallery.core.domain.repository.MediaRepository
import com.arijit.gallery.core.domain.repository.SettingsRepository
import com.arijit.gallery.core.media.MediaMutationManager
import com.arijit.gallery.core.media.MutationOutcome
import com.arijit.gallery.viewer.ViewerLaunchCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhotosViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val albumRepository: AlbumRepository,
    private val settingsRepository: SettingsRepository,
    private val mutationManager: MediaMutationManager,
    private val viewerLaunchCache: ViewerLaunchCache,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : ViewModel() {

    private data class QueryParams(
        val sortOrder: SortOrder,
        val hiddenBuckets: Set<String>,
        val granularity: TimelineGranularity
    )

    private data class MutationUiState(
        val pendingIntentSender: IntentSender? = null,
        val statusMessage: String? = null
    )

    private val selectedIdsFlow = MutableStateFlow<Set<Long>>(emptySet())
    private val anchorsFlow = MutableStateFlow<List<TimelineDateAnchor>>(emptyList())
    private val totalCountFlow = MutableStateFlow(0)
    private val mutationUiFlow = MutableStateFlow(MutationUiState())
    private var pendingConfirmAction: (suspend () -> Unit)? = null
    @Volatile
    private var activeQueryParams = QueryParams(
        sortOrder = SortOrder.DATE_TAKEN_DESC,
        hiddenBuckets = emptySet(),
        granularity = TimelineGranularity.DAY
    )

    private val queryParamsFlow = settingsRepository.userSettings
        .map { QueryParams(it.sortOrder, it.hiddenBuckets, it.timelineGranularity) }
        .distinctUntilChanged()

    private val mediaTriggerFlow = mediaRepository.observeMediaChanges()
        .onStart { emit(Unit) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val timelinePagingFlow: Flow<PagingData<TimelineUiItem>> = combine(
        queryParamsFlow,
        mediaTriggerFlow
    ) { params, _ -> params }
        .flatMapLatest { params ->
            mediaRepository.getTimelinePagingFlow(
                sortOrder = params.sortOrder,
                hiddenBuckets = params.hiddenBuckets
            ).map { pagingData ->
                buildTimelinePagingData(pagingData, params.granularity)
            }
        }
        .flowOn(defaultDispatcher)
        .cachedIn(viewModelScope)

    private val groupedSnapshotFlow = combine(
        mediaRepository.getCachedSnapshot(),
        settingsRepository.userSettings.map { it.timelineGranularity }.distinctUntilChanged()
    ) { items, granularity ->
        groupSnapshotItems(items, granularity)
    }.flowOn(defaultDispatcher)

    private data class ExtraUiMetadata(
        val anchors: List<TimelineDateAnchor>,
        val totalCount: Int,
        val mutation: MutationUiState,
        val albums: List<Album>
    )

    private val baseMetadataFlow = combine(
        anchorsFlow,
        totalCountFlow,
        mutationUiFlow,
        albumRepository.observeAlbums()
    ) { anchors, totalCount, mutation, albums ->
        ExtraUiMetadata(anchors, totalCount, mutation, albums)
    }

    val uiState: StateFlow<PhotosUiState> = combine(
        settingsRepository.userSettings,
        selectedIdsFlow,
        groupedSnapshotFlow,
        baseMetadataFlow
    ) { settings, selectedIds, snapshot, extra ->
        PhotosUiState(
            gridColumns = settings.gridColumns,
            granularity = settings.timelineGranularity,
            themeMode = settings.themeMode,
            dynamicColor = settings.dynamicColor,
            selectedIds = selectedIds.toImmutableSet(),
            snapshotItems = snapshot.toImmutableList(),
            dateAnchors = extra.anchors.toImmutableList(),
            totalMediaCount = extra.totalCount,
            albumsForTransfer = extra.albums.toImmutableList(),
            pendingIntentSender = extra.mutation.pendingIntentSender,
            statusMessage = extra.mutation.statusMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = PhotosUiState()
    )

    init {
        observeMetadataRefresh()
    }

    private fun observeMetadataRefresh() {
        viewModelScope.launch {
            combine(queryParamsFlow, mediaTriggerFlow) { params, _ -> params }
                .collectLatest { params ->
                    activeQueryParams = params
                    mediaRepository.refreshSnapshot(params.sortOrder, params.hiddenBuckets)
                    anchorsFlow.value = mediaRepository.getTimelineDateAnchors(
                        granularity = params.granularity,
                        sortOrder = params.sortOrder,
                        hiddenBuckets = params.hiddenBuckets
                    )
                    totalCountFlow.value = mediaRepository.getMediaCount(params.hiddenBuckets)
                    // Pre-warm viewer index cache silently in the background.
                    mediaRepository.getAllMedia(params.sortOrder, params.hiddenBuckets)
                }
        }
    }

    fun onAppResumed(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            // Fast-scan DCIM/Camera to ensure newly taken photos are immediately indexed by MediaStore
            runCatching {
                val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                val cameraDir = File(dcim, "Camera")
                if (cameraDir.exists() && cameraDir.isDirectory) {
                    val now = System.currentTimeMillis()
                    val recentFiles = cameraDir.listFiles()
                        ?.filter { it.isFile && (now - it.lastModified() < 180_000L) }
                        ?.map { it.absolutePath }
                        ?.toTypedArray()
                    if (!recentFiles.isNullOrEmpty()) {
                        MediaScannerConnection.scanFile(context, recentFiles, null, null)
                    }
                }
            }

            mediaRepository.notifyMediaChanged()
            val settings = settingsRepository.userSettings.first()
            mediaRepository.refreshSnapshot(settings.sortOrder, settings.hiddenBuckets)
            anchorsFlow.value = mediaRepository.getTimelineDateAnchors(
                granularity = settings.timelineGranularity,
                sortOrder = settings.sortOrder,
                hiddenBuckets = settings.hiddenBuckets
            )
            totalCountFlow.value = mediaRepository.getMediaCount(settings.hiddenBuckets)
        }
    }

    fun refreshAfterPermission() {
        viewModelScope.launch {
            val settings = settingsRepository.userSettings.first()
            mediaRepository.refreshSnapshot(settings.sortOrder, settings.hiddenBuckets)
            anchorsFlow.value = mediaRepository.getTimelineDateAnchors(
                granularity = settings.timelineGranularity,
                sortOrder = settings.sortOrder,
                hiddenBuckets = settings.hiddenBuckets
            )
            totalCountFlow.value = mediaRepository.getMediaCount(settings.hiddenBuckets)
        }
    }

    fun setGridColumns(columns: Int) {
        val clamped = columns.coerceIn(2, 6)
        viewModelScope.launch {
            settingsRepository.setGridColumns(clamped)
        }
    }

    fun setGranularity(granularity: TimelineGranularity) {
        viewModelScope.launch {
            settingsRepository.setTimelineGranularity(granularity)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun onPinchZoomStep(zoomIn: Boolean) {
        val currentCols = uiState.value.gridColumns
        val currentGranularity = uiState.value.granularity
        viewModelScope.launch {
            if (zoomIn) {
                when (currentGranularity) {
                    TimelineGranularity.YEAR -> settingsRepository.setTimelineGranularity(TimelineGranularity.MONTH)
                    TimelineGranularity.MONTH -> settingsRepository.setTimelineGranularity(TimelineGranularity.DAY)
                    TimelineGranularity.DAY -> {
                        if (currentCols > 2) settingsRepository.setGridColumns(currentCols - 1)
                    }
                }
            } else {
                when (currentGranularity) {
                    TimelineGranularity.DAY -> {
                        if (currentCols < 6) {
                            settingsRepository.setGridColumns(currentCols + 1)
                        } else {
                            settingsRepository.setTimelineGranularity(TimelineGranularity.MONTH)
                        }
                    }
                    TimelineGranularity.MONTH -> settingsRepository.setTimelineGranularity(TimelineGranularity.YEAR)
                    TimelineGranularity.YEAR -> Unit
                }
            }
        }
    }

    fun toggleSelection(mediaId: Long) {
        selectedIdsFlow.update { current ->
            if (mediaId in current) current - mediaId else current + mediaId
        }
    }

    fun selectIds(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        selectedIdsFlow.update { current -> current + ids }
    }

    fun clearSelection() {
        selectedIdsFlow.value = emptySet()
    }

    fun toggleSelectGroup(groupKey: String) {
        viewModelScope.launch {
            val settings = settingsRepository.userSettings.first()
            val ids = mediaRepository.getMediaIdsForGroup(
                groupKey = groupKey,
                granularity = settings.timelineGranularity,
                hiddenBuckets = settings.hiddenBuckets
            )
            if (ids.isEmpty()) return@launch
            selectedIdsFlow.update { current ->
                val allSelected = ids.all { it in current }
                if (allSelected) current - ids.toSet() else current + ids
            }
        }
    }

    fun prepareViewerLaunch(item: MediaItem, thumbnailSizePx: Int) {
        val params = activeQueryParams
        viewerLaunchCache.put(
            item = item,
            thumbnailSizePx = thumbnailSizePx,
            sortOrder = params.sortOrder,
            hiddenBuckets = params.hiddenBuckets
        )
    }

    fun trashSelectedItems() {
        val ids = selectedIdsFlow.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val items = mediaRepository.getMediaByIds(ids)
            when (val outcome = mutationManager.requestTrash(items)) {
                is MutationOutcome.Success -> selectedIdsFlow.value = emptySet()
                is MutationOutcome.RequiresConfirmation -> {
                    pendingConfirmAction = {
                        outcome.onConfirmed()
                        selectedIdsFlow.value = emptySet()
                    }
                    mutationUiFlow.update { it.copy(pendingIntentSender = outcome.intentSender) }
                }
                is MutationOutcome.Error -> {
                    mutationUiFlow.update { it.copy(statusMessage = outcome.message) }
                }
            }
        }
    }

    fun favoriteSelectedItems() {
        val ids = selectedIdsFlow.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val items = mediaRepository.getMediaByIds(ids)
            val targetFavorite = items.any { !it.isFavorite }
            when (val outcome = mutationManager.requestFavorite(items, targetFavorite)) {
                is MutationOutcome.Success -> selectedIdsFlow.value = emptySet()
                is MutationOutcome.RequiresConfirmation -> {
                    pendingConfirmAction = {
                        outcome.onConfirmed()
                        selectedIdsFlow.value = emptySet()
                    }
                    mutationUiFlow.update { it.copy(pendingIntentSender = outcome.intentSender) }
                }
                is MutationOutcome.Error -> {
                    mutationUiFlow.update { it.copy(statusMessage = outcome.message) }
                }
            }
        }
    }

    fun copySelectedItemsToAlbum(albumName: String, explicitRelativePath: String?) {
        val ids = selectedIdsFlow.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val items = mediaRepository.getMediaByIds(ids)
            when (val outcome = mutationManager.copyItemsToAlbum(items, albumName, explicitRelativePath)) {
                is MutationOutcome.Success -> {
                    albumRepository.createCustomAlbum(albumName)
                    selectedIdsFlow.value = emptySet()
                    mutationUiFlow.update { it.copy(statusMessage = "Copied ${items.size} item(s) to $albumName") }
                }
                is MutationOutcome.RequiresConfirmation -> {
                    pendingConfirmAction = {
                        outcome.onConfirmed()
                        albumRepository.createCustomAlbum(albumName)
                        selectedIdsFlow.value = emptySet()
                    }
                    mutationUiFlow.update { it.copy(pendingIntentSender = outcome.intentSender) }
                }
                is MutationOutcome.Error -> {
                    mutationUiFlow.update { it.copy(statusMessage = outcome.message) }
                }
            }
        }
    }

    fun moveSelectedItemsToAlbum(albumName: String, explicitRelativePath: String?) {
        val ids = selectedIdsFlow.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            albumRepository.createCustomAlbum(albumName)
            val items = mediaRepository.getMediaByIds(ids)
            when (val outcome = mutationManager.moveItemsToAlbum(items, albumName, explicitRelativePath)) {
                is MutationOutcome.Success -> selectedIdsFlow.value = emptySet()
                is MutationOutcome.RequiresConfirmation -> {
                    pendingConfirmAction = {
                        outcome.onConfirmed()
                        selectedIdsFlow.value = emptySet()
                    }
                    mutationUiFlow.update { it.copy(pendingIntentSender = outcome.intentSender) }
                }
                is MutationOutcome.Error -> {
                    mutationUiFlow.update { it.copy(statusMessage = outcome.message) }
                }
            }
        }
    }

    suspend fun getSelectedMediaItems(): List<MediaItem> {
        return mediaRepository.getMediaByIds(selectedIdsFlow.value)
    }

    fun onIntentSenderFinished(confirmed: Boolean) {
        val callback = pendingConfirmAction
        pendingConfirmAction = null
        mutationUiFlow.update { it.copy(pendingIntentSender = null) }
        if (confirmed && callback != null) {
            viewModelScope.launch {
                callback()
            }
        }
    }

    fun consumeStatusMessage() {
        mutationUiFlow.update { it.copy(statusMessage = null) }
    }

    companion object {
        fun buildTimelinePagingData(
            pagingData: PagingData<MediaItem>,
            granularity: TimelineGranularity,
            nowMs: Long = System.currentTimeMillis()
        ): PagingData<TimelineUiItem> {
            return pagingData
                .map { item ->
                    val key = Formatters.groupKey(item.effectiveTimestampMs, granularity)
                    TimelineUiItem.Media(item = item, groupKey = key)
                }
                .insertSeparators { before: TimelineUiItem.Media?, after: TimelineUiItem.Media? ->
                    if (after == null) return@insertSeparators null
                    if (before == null || before.groupKey != after.groupKey) {
                        val ts = after.item.effectiveTimestampMs
                        TimelineUiItem.Header(
                            groupKey = after.groupKey,
                            title = Formatters.formatTimelineHeader(ts, granularity, nowMs),
                            timestampMs = ts
                        )
                    } else {
                        null
                    }
                }
        }

        fun groupSnapshotItems(
            items: List<MediaItem>,
            granularity: TimelineGranularity,
            nowMs: Long = System.currentTimeMillis()
        ): List<TimelineUiItem> {
            if (items.isEmpty()) return emptyList()
            val result = ArrayList<TimelineUiItem>(items.size + 16)
            var lastKey: String? = null
            for (item in items) {
                val ts = item.effectiveTimestampMs
                val key = Formatters.groupKey(ts, granularity)
                if (key != lastKey) {
                    result += TimelineUiItem.Header(
                        groupKey = key,
                        title = Formatters.formatTimelineHeader(ts, granularity, nowMs),
                        timestampMs = ts
                    )
                    lastKey = key
                }
                result += TimelineUiItem.Media(item = item, groupKey = key)
            }
            return result
        }
    }
}
