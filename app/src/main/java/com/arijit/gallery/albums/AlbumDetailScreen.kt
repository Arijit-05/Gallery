package com.arijit.gallery.albums

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.arijit.gallery.core.common.rememberHapticController
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.presentation.LocalItemBoundsRegistry
import com.arijit.gallery.photos.components.MediaGridTile
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(
    uiState: AlbumDetailUiState,
    onBack: () -> Unit,
    onOpenMedia: (MediaItem, Int) -> Unit,
    onEnterSelection: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onFavoriteSelected: (Boolean) -> Unit,
    onTrashSelected: () -> Unit,
    onCopySelectedToAlbum: (String, String?) -> Unit,
    onMoveSelectedToAlbum: (String, String?) -> Unit,
    onIntentSenderResult: (Boolean) -> Unit,
    onClearStatusMessage: () -> Unit,
    modifier: Modifier = Modifier,
    isPickerMode: Boolean = false,
    allowMultiPick: Boolean = false,
    onPickItems: ((List<MediaItem>) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptics = rememberHapticController()
    val density = LocalDensity.current
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
    var showMoveCopySheet by remember { mutableStateOf(false) }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        onIntentSenderResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(uiState.pendingIntentSender) {
        val sender = uiState.pendingIntentSender ?: return@LaunchedEffect
        intentSenderLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onClearStatusMessage()
    }

    BackHandler(enabled = uiState.isSelectionMode) {
        onClearSelection()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            haptics.click()
                            onClearSelection()
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear selection")
                        }
                    },
                    title = {
                        Text(
                            text = "${uiState.selectedIds.size} selected",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            haptics.click()
                            onSelectAll()
                        }) {
                            Icon(Icons.Rounded.DoneAll, contentDescription = "Select all")
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            haptics.click()
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = uiState.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (uiState.items.size == 1) "1 item" else "${uiState.items.size} items",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (isPickerMode && allowMultiPick && uiState.selectedIds.isNotEmpty()) {
                Surface(
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                val selected = uiState.items.filter { it.id in uiState.selectedIds }
                                onPickItems?.invoke(selected)
                            }
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select (${uiState.selectedIds.size})")
                        }
                    }
                }
            } else if (!isPickerMode && uiState.isSelectionMode) {
                AlbumSelectionBottomBar(
                    onShare = {
                        val selected = uiState.items.filter { it.id in uiState.selectedIds }
                        shareMediaItems(context, selected)
                    },
                    onFavorite = {
                        val selected = uiState.items.filter { it.id in uiState.selectedIds }
                        val shouldFav = selected.any { !it.isFavorite }
                        onFavoriteSelected(shouldFav)
                    },
                    onMoveCopy = { showMoveCopySheet = true },
                    onTrash = onTrashSelected
                )
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val spacingDp = 2.dp
            val totalSpacingPx = with(density) { (spacingDp * (uiState.gridColumns - 1)).roundToPx() }
            val maxWidthPx = with(density) { maxWidth.roundToPx() }
            val thumbSizePx = ((maxWidthPx - totalSpacingPx) / uiState.gridColumns.coerceAtLeast(1))
                .coerceIn(120, 480)

            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.items.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No items in ${uiState.title}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(uiState.gridColumns),
                        horizontalArrangement = Arrangement.spacedBy(spacingDp),
                        verticalArrangement = Arrangement.spacedBy(spacingDp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { gridCoordinates = it }
                    ) {
                        items(
                            items = uiState.items,
                            key = { it.id }
                        ) { item ->
                            val isSelected = item.id in uiState.selectedIds
                            MediaGridTile(
                                item = item,
                                thumbnailSizePx = thumbSizePx,
                                isSelectionMode = uiState.isSelectionMode,
                                isSelected = isSelected,
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        if (isPickerMode) {
                                            if (allowMultiPick) {
                                                onToggleSelection(item.id)
                                            } else {
                                                onPickItems?.invoke(listOf(item))
                                            }
                                        } else if (uiState.isSelectionMode) {
                                            onToggleSelection(item.id)
                                        } else {
                                            onOpenMedia(item, thumbSizePx)
                                        }
                                    },
                                    onLongClick = {
                                        haptics.selectionStart()
                                        if (isPickerMode && !allowMultiPick) {
                                            onPickItems?.invoke(listOf(item))
                                        } else {
                                            onEnterSelection(item.id)
                                        }
                                    }
                                )
                            )
                        }
                    }
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
                    onCopySelectedToAlbum(albumName, explicitPath)
                } else {
                    onMoveSelectedToAlbum(albumName, explicitPath)
                }
            }
        )
    }
}

@Composable
private fun AlbumSelectionBottomBar(
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onMoveCopy: () -> Unit,
    onTrash: () -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SelectionActionButton(Icons.Rounded.Share, "Share", onShare)
            SelectionActionButton(Icons.Rounded.Favorite, "Favorite", onFavorite)
            SelectionActionButton(Icons.AutoMirrored.Rounded.DriveFileMove, "Move/Copy", onMoveCopy)
            SelectionActionButton(Icons.Rounded.DeleteOutline, "Delete", onTrash)
        }
    }
}

@Composable
private fun SelectionActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val haptics = rememberHapticController()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        IconButton(onClick = {
            when (label) {
                "Favorite" -> haptics.favoritePulse()
                "Delete" -> haptics.heavy()
                else -> haptics.click()
            }
            onClick()
        }) {
            Icon(imageVector = icon, contentDescription = label)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun shareMediaItems(context: android.content.Context, items: List<MediaItem>) {
    if (items.isEmpty()) return
    val uris: ArrayList<Uri> = ArrayList(items.map { it.contentUri.toUri() })
    val shareIntent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = items.first().mimeType.ifBlank { "*/*" }
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
    context.startActivity(Intent.createChooser(shareIntent, "Share"))
}

