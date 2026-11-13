package com.arijit.gallery.photos

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.arijit.gallery.albums.MoveCopyToAlbumSheet
import com.arijit.gallery.albums.TransferMode
import com.arijit.gallery.core.common.HapticController
import com.arijit.gallery.core.common.rememberHapticController
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.ThemeMode
import com.arijit.gallery.core.domain.model.TimelineGranularity
import com.arijit.gallery.core.domain.model.TimelineUiItem
import com.arijit.gallery.core.permissions.MediaAccessLevel
import com.arijit.gallery.core.permissions.PartialAccessBanner
import com.arijit.gallery.core.presentation.LocalItemBoundsRegistry
import com.arijit.gallery.photos.components.MediaGridTile
import com.arijit.gallery.photos.components.TimelineFastScroller
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun PhotosScreen(
    uiState: PhotosUiState,
    pagingItems: LazyPagingItems<TimelineUiItem>,
    accessLevel: MediaAccessLevel,
    onRequestFullAccess: () -> Unit,
    onSetGranularity: (TimelineGranularity) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onPinchZoomStep: (Boolean) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onSelectIds: (Collection<Long>) -> Unit,
    onClearSelection: () -> Unit,
    onToggleSelectGroup: (String) -> Unit,
    onFavoriteSelected: () -> Unit,
    onTrashSelected: () -> Unit,
    onIntentSenderFinished: (Boolean) -> Unit,
    onConsumeStatusMessage: () -> Unit,
    onMediaClick: (MediaItem, Int) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
    onCopySelectedToAlbum: ((String, String?) -> Unit)? = null,
    onMoveSelectedToAlbum: ((String, String?) -> Unit)? = null,
    onMoveSelectedToVault: (() -> Unit)? = null,
    isPickerMode: Boolean = false,
    allowMultiPick: Boolean = false,
    onConfirmMultiPick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptics = rememberHapticController()
    val gridState = rememberLazyGridState()
    val boundsRegistry = LocalItemBoundsRegistry.current
    var gridCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    DisposableEffect(gridState, boundsRegistry) {
        boundsRegistry.registerGrid(
            gridState = gridState,
            getGridBoundsInRoot = {
                gridCoordinates?.takeIf { it.isAttached }?.boundsInRoot()
            }
        )
        onDispose {
            boundsRegistry.unregisterGrid(gridState)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showMoveCopySheet by remember { mutableStateOf(false) }
    var lastLongPressTimeMs by remember { mutableLongStateOf(0L) }

    val gridPinchScale = remember { Animatable(1f) }
    var pinchCentroidNorm by remember { mutableStateOf(Offset(0.5f, 0.5f)) }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        onIntentSenderFinished(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(uiState.pendingIntentSender) {
        val sender = uiState.pendingIntentSender ?: return@LaunchedEffect
        intentSenderLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onConsumeStatusMessage()
    }

    var hidePartialBanner by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val thumbnailSizePx = remember(configuration.screenWidthDp, uiState.gridColumns) {
        val tileDp = (configuration.screenWidthDp.toFloat() / uiState.gridColumns).dp
        with(density) { tileDp.roundToPx().coerceIn(128, 280) }
    }

    // Show cached snapshot immediately on cold start before Paging finishes its initial load.
    val showSnapshot by remember(pagingItems.itemCount, uiState.snapshotItems.size) {
        derivedStateOf {
            pagingItems.itemCount == 0 &&
                pagingItems.loadState.refresh is LoadState.Loading &&
                uiState.snapshotItems.isNotEmpty()
        }
    }

    val totalRenderCount = if (showSnapshot) uiState.snapshotItems.size else pagingItems.itemCount

    // Lookup helper to collect MediaItems from loaded items for sharing / drag-range selection
    fun currentLoadedMediaAt(index: Int): TimelineUiItem? {
        return if (showSnapshot) {
            uiState.snapshotItems.getOrNull(index)
        } else {
            if (index in 0 until pagingItems.itemCount) pagingItems.peek(index) else null
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PhotosTopBar(
                uiState = uiState,
                onClearSelection = onClearSelection,
                onSetGranularity = onSetGranularity,
                onOpenSettings = onOpenSettings,
                isPickerMode = isPickerMode
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.isSelectionMode,
                enter = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { it } + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = slideOutVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) { it } + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            ) {
                if (isPickerMode && allowMultiPick && onConfirmMultiPick != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(onClick = onConfirmMultiPick) {
                                Icon(Icons.Rounded.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select (${uiState.selectedIds.size})")
                            }
                        }
                    }
                } else {
                    SelectionActionBar(
                        selectedCount = uiState.selectedIds.size,
                        onShare = {
                            val uris = collectSelectedUris(
                                selectedIds = uiState.selectedIds,
                                pagingItems = pagingItems,
                                snapshotItems = uiState.snapshotItems
                            )
                            if (uris.isNotEmpty()) {
                                launchShareIntent(context, uris)
                            }
                        },
                        onFavorite = onFavoriteSelected,
                        onDelete = onTrashSelected,
                        onAddToAlbum = {
                            if (onCopySelectedToAlbum != null || onMoveSelectedToAlbum != null) {
                                showMoveCopySheet = true
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Albums transfer unavailable")
                                }
                            }
                        },
                        onMoveToSafe = onMoveSelectedToVault
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            if (accessLevel == MediaAccessLevel.PARTIAL && !hidePartialBanner) {
                PartialAccessBanner(
                    onGrantFullAccess = onRequestFullAccess,
                    onDismiss = { hidePartialBanner = true }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                val isInitialEmpty = !showSnapshot &&
                    pagingItems.itemCount == 0 &&
                    pagingItems.loadState.refresh is LoadState.NotLoading

                if (!showSnapshot && pagingItems.itemCount == 0 && pagingItems.loadState.refresh is LoadState.Loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                } else if (isInitialEmpty) {
                    EmptyGalleryState()
                } else {
                    val dragSelectionModifier = Modifier.dragSelectAfterLongPress(
                        gridState = gridState,
                        itemAt = ::currentLoadedMediaAt,
                        onSelectionStart = { id ->
                            lastLongPressTimeMs = System.currentTimeMillis()
                            haptics.selectionStart()
                            onSelectIds(listOf(id))
                        },
                        onRangeChanged = { ids ->
                            lastLongPressTimeMs = System.currentTimeMillis()
                            haptics.selectionTick()
                            onSelectIds(ids)
                        },
                        onDragEnd = {
                            lastLongPressTimeMs = System.currentTimeMillis()
                        }
                    )

                    val pinchModifier = Modifier.pinchToChangeDensityWithFeedback(
                        onZoomStep = { zoomIn ->
                            haptics.snap()
                            onPinchZoomStep(zoomIn)
                        },
                        onPinchUpdate = { scale, centroidNorm ->
                            pinchCentroidNorm = centroidNorm
                            scope.launch {
                                gridPinchScale.snapTo(scale)
                            }
                        },
                        onPinchEnd = {
                            scope.launch {
                                gridPinchScale.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    )

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(uiState.gridColumns),
                        contentPadding = PaddingValues(
                            bottom = innerPadding.calculateBottomPadding() + 96.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { gridCoordinates = it }
                            .graphicsLayer {
                                scaleX = gridPinchScale.value
                                scaleY = gridPinchScale.value
                                transformOrigin = TransformOrigin(pinchCentroidNorm.x, pinchCentroidNorm.y)
                            }
                            .then(pinchModifier)
                            .then(dragSelectionModifier)
                    ) {
                        if (showSnapshot) {
                            items(
                                count = uiState.snapshotItems.size,
                                key = { index -> uiState.snapshotItems[index].stableKey },
                                contentType = { index ->
                                    when (uiState.snapshotItems[index]) {
                                        is TimelineUiItem.Header -> "header"
                                        is TimelineUiItem.Media -> "media"
                                    }
                                },
                                span = { index ->
                                    when (uiState.snapshotItems[index]) {
                                        is TimelineUiItem.Header -> GridItemSpan(maxLineSpan)
                                        is TimelineUiItem.Media -> GridItemSpan(1)
                                    }
                                }
                            ) { index ->
                                TimelineItemSlot(
                                    uiItem = uiState.snapshotItems[index],
                                    thumbnailSizePx = thumbnailSizePx,
                                    isSelectionMode = uiState.isSelectionMode,
                                    selectedIds = uiState.selectedIds,
                                    lastLongPressTimeMs = lastLongPressTimeMs,
                                    haptics = haptics,
                                    onToggleSelectGroup = onToggleSelectGroup,
                                    onToggleSelection = onToggleSelection,
                                    onMediaClick = onMediaClick
                                )
                            }
                        } else {
                            items(
                                count = pagingItems.itemCount,
                                key = pagingItems.itemKey { it.stableKey },
                                contentType = pagingItems.itemContentType {
                                    when (it) {
                                        is TimelineUiItem.Header -> "header"
                                        is TimelineUiItem.Media -> "media"
                                    }
                                },
                                span = { index ->
                                    when (pagingItems.peek(index)) {
                                        is TimelineUiItem.Header -> GridItemSpan(maxLineSpan)
                                        else -> GridItemSpan(1)
                                    }
                                }
                            ) { index ->
                                val uiItem = pagingItems[index]
                                if (uiItem != null) {
                                    TimelineItemSlot(
                                        uiItem = uiItem,
                                        thumbnailSizePx = thumbnailSizePx,
                                        isSelectionMode = uiState.isSelectionMode,
                                        selectedIds = uiState.selectedIds,
                                        lastLongPressTimeMs = lastLongPressTimeMs,
                                        haptics = haptics,
                                        onToggleSelectGroup = onToggleSelectGroup,
                                        onToggleSelection = onToggleSelection,
                                        onMediaClick = onMediaClick
                                    )
                                }
                            }
                        }
                    }

                    TimelineFastScroller(
                        gridState = gridState,
                        totalItemCount = totalRenderCount,
                        dateAnchors = uiState.dateAnchors
                    )
                }
            }
        }
    }

    if (showMoveCopySheet) {
        MoveCopyToAlbumSheet(
            itemCount = uiState.selectedIds.size,
            albums = uiState.albumsForTransfer,
            onDismiss = { showMoveCopySheet = false },
            onConfirmTransfer = { mode, albumName, explicitPath ->
                showMoveCopySheet = false
                if (mode == TransferMode.COPY) {
                    onCopySelectedToAlbum?.invoke(albumName, explicitPath)
                } else {
                    onMoveSelectedToAlbum?.invoke(albumName, explicitPath)
                }
            }
        )
    }
}

@Composable
private fun TimelineItemSlot(
    uiItem: TimelineUiItem,
    thumbnailSizePx: Int,
    isSelectionMode: Boolean,
    selectedIds: Set<Long>,
    lastLongPressTimeMs: Long,
    haptics: HapticController,
    onToggleSelectGroup: (String) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onMediaClick: (MediaItem, Int) -> Unit
) {
    when (uiItem) {
        is TimelineUiItem.Header -> {
            TimelineGroupHeader(
                title = uiItem.title,
                isSelectionMode = isSelectionMode,
                onSelectAllClick = { onToggleSelectGroup(uiItem.groupKey) }
            )
        }
        is TimelineUiItem.Media -> {
            val item = uiItem.item
            val isSelected = item.id in selectedIds
            MediaGridTile(
                item = item,
                thumbnailSizePx = thumbnailSizePx,
                isSelectionMode = isSelectionMode,
                isSelected = isSelected,
                modifier = Modifier.combinedClickable(
                    interactionSource = null,
                    indication = null,
                    onClick = {
                        if (System.currentTimeMillis() - lastLongPressTimeMs < 600L) {
                            return@combinedClickable
                        }
                        if (isSelectionMode) {
                            onToggleSelection(item.id)
                        } else {
                            onMediaClick(item, thumbnailSizePx)
                        }
                    },
                    onLongClick = {
                        haptics.selectionStart()
                        onToggleSelection(item.id)
                    }
                )
            )
        }
    }
}

@Composable
private fun TimelineGroupHeader(
    title: String,
    isSelectionMode: Boolean,
    onSelectAllClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (isSelectionMode) {
            TextButton(onClick = onSelectAllClick) {
                Text(
                    text = "Select all",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun PhotosTopBar(
    uiState: PhotosUiState,
    onClearSelection: () -> Unit,
    onSetGranularity: (TimelineGranularity) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    isPickerMode: Boolean = false
) {
    val context = LocalContext.current
    val haptics = rememberHapticController()

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        if (uiState.isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    haptics.click()
                    onClearSelection()
                }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancel selection")
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${uiState.selectedIds.size} selected",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isPickerMode) "Select Media" else "Media",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (uiState.totalMediaCount > 0) {
                        Text(
                            text = "${uiState.totalMediaCount} items",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Clock icon to toggle through Day -> Month -> Year with a toast
                IconButton(
                    onClick = {
                        haptics.click()
                        val (nextGranularity, toastLabel) = when (uiState.granularity) {
                            TimelineGranularity.DAY -> TimelineGranularity.MONTH to "monthly"
                            TimelineGranularity.MONTH -> TimelineGranularity.YEAR to "yearly"
                            TimelineGranularity.YEAR -> TimelineGranularity.DAY to "daily"
                        }
                        onSetGranularity(nextGranularity)
                        Toast.makeText(context, "Changed to \"$toastLabel\"", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = "Timeline grouping"
                    )
                }

                if (!isPickerMode && onOpenSettings != null) {
                    IconButton(onClick = {
                        haptics.click()
                        onOpenSettings()
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onAddToAlbum: () -> Unit,
    onMoveToSafe: (() -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectionActionButton(
                icon = Icons.Rounded.Share,
                label = "Share",
                enabled = selectedCount > 0,
                onClick = onShare
            )
            SelectionActionButton(
                icon = Icons.Rounded.FavoriteBorder,
                label = "Favorite",
                enabled = selectedCount > 0,
                onClick = onFavorite
            )
            SelectionActionButton(
                icon = Icons.Rounded.CreateNewFolder,
                label = "Album",
                enabled = selectedCount > 0,
                onClick = onAddToAlbum
            )
            if (onMoveToSafe != null) {
                SelectionActionButton(
                    icon = Icons.Rounded.Lock,
                    label = "Safe",
                    enabled = selectedCount > 0,
                    onClick = onMoveToSafe
                )
            }
            SelectionActionButton(
                icon = Icons.Rounded.DeleteOutline,
                label = "Delete",
                enabled = selectedCount > 0,
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun SelectionActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val haptics = rememberHapticController()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = enabled,
                onClick = {
                    when (label) {
                        "Favorite" -> haptics.favoritePulse()
                        "Delete" -> haptics.heavy()
                        else -> haptics.click()
                    }
                    onClick()
                }
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EmptyGalleryState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No photos or videos found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Photos and videos saved on your device will appear here automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Two-finger pinch gesture detector with smooth graphicsLayer scale feedback.
 * Leaves single-finger vertical scrolling completely untouched.
 */
private fun Modifier.pinchToChangeDensityWithFeedback(
    onZoomStep: (zoomIn: Boolean) -> Unit,
    onPinchUpdate: (scale: Float, centroidNorm: Offset) -> Unit,
    onPinchEnd: () -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var cumulativeZoom = 1f
        var isPinching = false
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                isPinching = true
                val zoomChange = event.calculateZoom()
                val centroid = event.calculateCentroid(useCurrent = true)
                val normX = if (size.width > 0) (centroid.x / size.width).coerceIn(0f, 1f) else 0.5f
                val normY = if (size.height > 0) (centroid.y / size.height).coerceIn(0f, 1f) else 0.5f

                if (zoomChange != 1f) {
                    cumulativeZoom *= zoomChange
                    onPinchUpdate(cumulativeZoom, Offset(normX, normY))
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })

        if (isPinching) {
            if (cumulativeZoom >= 1.25f) {
                onZoomStep(true)
            } else if (cumulativeZoom <= 0.80f) {
                onZoomStep(false)
            }
            onPinchEnd()
        }
    }
}

/**
 * Enables drag-to-select across grid items after a long press without breaking regular taps or scrolling.
 */
private fun Modifier.dragSelectAfterLongPress(
    gridState: LazyGridState,
    itemAt: (Int) -> TimelineUiItem?,
    onSelectionStart: (Long) -> Unit,
    onRangeChanged: (List<Long>) -> Unit,
    onDragEnd: (() -> Unit)? = null
): Modifier = pointerInput(gridState) {
    var anchorGridIndex: Int? = null
    var lastHoveredIndex: Int? = null

    fun findGridIndexAt(position: Offset): Int? {
        return gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            position.x >= info.offset.x &&
                position.x <= info.offset.x + info.size.width &&
                position.y >= info.offset.y &&
                position.y <= info.offset.y + info.size.height
        }?.index
    }

    detectDragGesturesAfterLongPress(
        onDragStart = { startOffset ->
            val index = findGridIndexAt(startOffset) ?: return@detectDragGesturesAfterLongPress
            val uiItem = itemAt(index) as? TimelineUiItem.Media ?: return@detectDragGesturesAfterLongPress
            anchorGridIndex = index
            lastHoveredIndex = index
            onSelectionStart(uiItem.item.id)
        },
        onDrag = { change, _ ->
            val anchor = anchorGridIndex ?: return@detectDragGesturesAfterLongPress
            val currentIdx = findGridIndexAt(change.position) ?: return@detectDragGesturesAfterLongPress
            if (currentIdx != lastHoveredIndex) {
                lastHoveredIndex = currentIdx
                val range = minOf(anchor, currentIdx)..maxOf(anchor, currentIdx)
                val ids = range.mapNotNull { idx -> (itemAt(idx) as? TimelineUiItem.Media)?.item?.id }
                if (ids.isNotEmpty()) {
                    onRangeChanged(ids)
                }
            }
        },
        onDragEnd = {
            onDragEnd?.invoke()
            anchorGridIndex = null
            lastHoveredIndex = null
        },
        onDragCancel = {
            onDragEnd?.invoke()
            anchorGridIndex = null
            lastHoveredIndex = null
        }
    )
}

private fun collectSelectedUris(
    selectedIds: Set<Long>,
    pagingItems: LazyPagingItems<TimelineUiItem>,
    snapshotItems: List<TimelineUiItem>
): ArrayList<Uri> {
    val uris = ArrayList<Uri>(selectedIds.size)
    val seen = HashSet<Long>(selectedIds.size)
    for (i in 0 until pagingItems.itemCount) {
        val item = (pagingItems.peek(i) as? TimelineUiItem.Media)?.item ?: continue
        if (item.id in selectedIds && seen.add(item.id)) {
            uris.add(item.contentUri.toUri())
        }
    }
    if (uris.isEmpty()) {
        for (entry in snapshotItems) {
            val item = (entry as? TimelineUiItem.Media)?.item ?: continue
            if (item.id in selectedIds && seen.add(item.id)) {
                uris.add(item.contentUri.toUri())
            }
        }
    }
    return uris
}

private fun launchShareIntent(context: android.content.Context, uris: ArrayList<Uri>) {
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uris.first())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(Intent.createChooser(intent, "Share media"))
}
