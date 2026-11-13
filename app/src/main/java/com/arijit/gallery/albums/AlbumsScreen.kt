package com.arijit.gallery.albums

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.arijit.gallery.core.common.rememberHapticController
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.arijit.gallery.core.domain.model.Album
import com.arijit.gallery.core.domain.model.SystemAlbumSummary
import com.arijit.gallery.core.domain.model.SystemAlbumType
import com.arijit.gallery.core.media.MediaThumbnailRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    uiState: AlbumsUiState,
    onOpenAlbum: (String) -> Unit,
    onOpenSystemAlbum: (SystemAlbumType) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenVault: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onCreateAlbum: (String) -> Unit,
    onRenameAlbum: (String, String) -> Unit,
    onTogglePin: (Album) -> Unit,
    onToggleHideFromTimeline: (Album) -> Unit,
    onMoveOrder: (Album, Boolean) -> Unit,
    onClearStatusMessage: () -> Unit,
    modifier: Modifier = Modifier,
    isPickerMode: Boolean = false,
    onCancelPicker: (() -> Unit)? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHapticController()
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Album?>(null) }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onClearStatusMessage()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = if (isPickerMode) 16.dp else 96.dp)
            )
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    if (isPickerMode && onCancelPicker != null) {
                        IconButton(onClick = {
                            haptics.click()
                            onCancelPicker()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Cancel"
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = if (isPickerMode) "Pick from Album" else "Albums",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    if (!isPickerMode) {
                        IconButton(onClick = {
                            haptics.click()
                            showCreateDialog = true
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "New album"
                            )
                        }
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
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = innerPadding.calculateBottomPadding() + if (isPickerMode) 24.dp else 112.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(2) }, key = "system_section_header") {
                Text(
                    text = "Collections",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            items(
                items = SystemAlbumType.entries,
                key = { it.routeKey }
            ) { type ->
                val summary = uiState.systemSummaries[type]
                SystemAlbumCard(
                    type = type,
                    summary = summary,
                    onClick = {
                        haptics.click()
                        onOpenSystemAlbum(type)
                    }
                )
            }

            item(span = { GridItemSpan(2) }, key = "albums_section_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "My Albums",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${uiState.albums.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(
                items = uiState.albums,
                key = { it.id }
            ) { album ->
                AlbumGridCard(
                    album = album,
                    isPickerMode = isPickerMode,
                    onClick = {
                        haptics.click()
                        onOpenAlbum(album.id)
                    },
                    onTogglePin = { onTogglePin(album) },
                    onRename = { renameTarget = album },
                    onToggleHidden = { onToggleHideFromTimeline(album) },
                    onMoveEarlier = { onMoveOrder(album, true) },
                    onMoveLater = { onMoveOrder(album, false) }
                )
            }

            if (!isPickerMode) {
                item(span = { GridItemSpan(2) }, key = "utilities_section_header") {
                    Text(
                        text = "Utilities",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                    )
                }

                item(span = { GridItemSpan(2) }, key = "utility_rows") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        UtilityAlbumRow(
                            icon = Icons.Rounded.DeleteOutline,
                            title = "Recently Deleted",
                            subtitle = if (uiState.trashCount == 0) {
                                "Items kept for 30 days"
                            } else {
                                "${uiState.trashCount} item(s) • 30-day auto purge"
                            },
                            countText = uiState.trashCount.toString(),
                            onClick = {
                                haptics.click()
                                onOpenTrash()
                            }
                        )
                        UtilityAlbumRow(
                            icon = Icons.Rounded.Lock,
                            title = "Private Safe",
                            subtitle = "Encrypted biometric vault",
                            countText = "",
                            onClick = {
                                haptics.click()
                                onOpenVault()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlbumNameDialog(
            title = "New album",
            initialName = "",
            confirmLabel = "Create",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                showCreateDialog = false
                onCreateAlbum(name)
            }
        )
    }

    renameTarget?.let { target ->
        AlbumNameDialog(
            title = "Rename album",
            initialName = target.name,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                onRenameAlbum(target.id, newName)
            }
        )
    }
}

@Composable
private fun SystemAlbumCard(
    type: SystemAlbumType,
    summary: SystemAlbumSummary?,
    onClick: () -> Unit
) {
    val icon: ImageVector = when (type) {
        SystemAlbumType.ALL_PHOTOS -> Icons.Rounded.PhotoLibrary
        SystemAlbumType.VIDEOS -> Icons.Rounded.Videocam
        SystemAlbumType.FAVORITES -> Icons.Rounded.Favorite
        SystemAlbumType.SCREENSHOTS -> Icons.Rounded.Screenshot
    }
    val coverUri = summary?.coverUri
    val count = summary?.count ?: 0
    val thumbModel = remember(summary?.coverId, summary?.coverDateModifiedSec, coverUri) {
        if (summary != null && summary.coverId > 0L && coverUri != null) {
            MediaThumbnailRequest(
                id = summary.coverId,
                uri = coverUri.toUri(),
                dateModifiedSec = summary.coverDateModifiedSec,
                sizePx = 144
            )
        } else {
            coverUri
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(48.dp)
            ) {
                if (thumbModel != null) {
                    AsyncImage(
                        model = thumbModel,
                        contentDescription = type.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = type.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UtilityAlbumRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    countText: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (countText.isNotEmpty()) {
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumGridCard(
    album: Album,
    isPickerMode: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onToggleHidden: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.08f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (!isPickerMode) menuExpanded = true }
                )
        ) {
            val coverUri = album.coverUri
            val thumbModel = remember(album.coverId, album.coverDateModifiedSec, coverUri) {
                if (album.coverId > 0L && coverUri != null) {
                    MediaThumbnailRequest(
                        id = album.coverId,
                        uri = coverUri.toUri(),
                        dateModifiedSec = album.coverDateModifiedSec,
                        sizePx = 360
                    )
                } else {
                    coverUri
                }
            }
            if (thumbModel != null) {
                AsyncImage(
                    model = thumbModel,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            if (album.isPinned || album.isHiddenFromTimeline) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    if (album.isPinned) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = "Pinned",
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(5.dp)
                                    .size(13.dp)
                            )
                        }
                    }
                    if (album.isHiddenFromTimeline) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VisibilityOff,
                                contentDescription = "Hidden from timeline",
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(5.dp)
                                    .size(13.dp)
                            )
                        }
                    }
                }
            }

            if (!isPickerMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.42f),
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { menuExpanded = true }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Album options",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                            text = { Text(if (album.isPinned) "Unpin" else "Pin to top") },
                            onClick = {
                                menuExpanded = false
                                onTogglePin()
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                            text = { Text("Rename") },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) },
                            text = { Text("Move earlier") },
                            onClick = {
                                menuExpanded = false
                                onMoveEarlier()
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null) },
                            text = { Text("Move later") },
                            onClick = {
                                menuExpanded = false
                                onMoveLater()
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = if (album.isHiddenFromTimeline) {
                                        Icons.Rounded.Visibility
                                    } else {
                                        Icons.Rounded.VisibilityOff
                                    },
                                    contentDescription = null
                                )
                            },
                            text = {
                                Text(
                                    if (album.isHiddenFromTimeline) {
                                        "Show in Photos"
                                    } else {
                                        "Hide from Photos"
                                    }
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleHidden()
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (album.itemCount == 1) "1 item" else "${album.itemCount} items",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AlbumNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Album name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty(),
                onClick = { onConfirm(name.trim()) }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

