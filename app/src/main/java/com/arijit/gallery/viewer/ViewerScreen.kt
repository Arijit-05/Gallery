package com.arijit.gallery.viewer

import android.app.Activity
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.print.PrintHelper
import kotlinx.collections.immutable.persistentListOf
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Scale
import com.arijit.gallery.core.common.rememberHapticController
import com.arijit.gallery.core.designsystem.HeartRed
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.presentation.LocalItemBoundsRegistry
import com.arijit.gallery.viewer.components.MediaDetailsSheet
import com.arijit.gallery.viewer.components.VideoPlayerPage
import com.arijit.gallery.viewer.components.ZoomablePhotoPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ViewerScreen(
    uiState: ViewerUiState,
    onBack: () -> Unit,
    onToggleImmersive: () -> Unit,
    onToggleSlideshow: () -> Unit,
    onStopSlideshow: () -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onTrashItem: (MediaItem) -> Unit,
    onRenameItem: (MediaItem, String) -> Unit,
    onCopyToAlbum: (MediaItem, String) -> Unit,
    onLoadDetails: (MediaItem) -> Unit,
    onClearDetails: () -> Unit,
    onSaveVideoPosition: (Long, Long) -> Unit,
    resolveMotionPhotoUri: suspend (MediaItem) -> android.net.Uri?,
    onIntentSenderFinished: (Boolean) -> Unit,
    onConsumeStatusMessage: () -> Unit,
    onMoveToVault: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    session: ViewerSession? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = rememberHapticController()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val dismissController = rememberViewerDismissController(haptics)
    val boundsRegistry = LocalItemBoundsRegistry.current

    var isZoomed by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<MediaItem?>(null) }
    var showCopyDialog by remember { mutableStateOf<MediaItem?>(null) }

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

    // Enable HDR/Ultra HDR rendering while inside viewer and reset on exit.
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.colorMode = ActivityInfo.COLOR_MODE_HDR
        }
        onDispose {
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Sync system bars with immersive mode
    LaunchedEffect(uiState.isImmersive) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (uiState.isImmersive) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { dismissController.containerSize = it }
            .drawBehind {
                drawRect(Color.Black.copy(alpha = dismissController.scrimAlpha))
            }
    ) {
        val displayItems = if (uiState.items.isNotEmpty()) uiState.items else (session?.items ?: persistentListOf())
        if (uiState.isLoading && displayItems.isEmpty()) {
            val hero = session?.heroItem ?: uiState.heroItem
            if (hero != null && !hero.isVideo) {
                val thumbKey = session?.heroThumbnailKey ?: uiState.heroThumbnailKey
                val heroRequest = remember(hero.id, hero.contentUri, thumbKey) {
                    ImageRequest.Builder(context)
                        .data(hero.contentUri)
                        .apply {
                            thumbKey?.let { placeholderMemoryCacheKey(it) }
                        }
                        .size(2560, 2560)
                        .scale(Scale.FIT)
                        .build()
                }
                AsyncImage(
                    model = heroRequest,
                    contentDescription = hero.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else if (displayItems.isEmpty()) {
            LaunchedEffect(Unit) { onBack() }
        } else {
            val initialIndex = session?.initialIndex ?: uiState.initialPageIndex.coerceIn(0, displayItems.lastIndex)
            val pagerState = rememberPagerState(
                initialPage = initialIndex.coerceIn(0, displayItems.lastIndex),
                pageCount = { displayItems.size }
            )

            val currentItem = displayItems.getOrNull(pagerState.currentPage) ?: session?.heroItem

            LaunchedEffect(currentItem) {
                dismissController.setActiveMediaItem(currentItem)
            }

            // Hide thumbnail of the currently viewed item in the grid underneath
            LaunchedEffect(currentItem?.id) {
                boundsRegistry.hideThumbnail(currentItem?.id)
            }
            DisposableEffect(session?.sessionId ?: Unit) {
                onDispose {
                    boundsRegistry.showAllThumbnails()
                }
            }

            val handleDismiss: () -> Unit = {
                if (uiState.isSlideshowActive) {
                    onStopSlideshow()
                } else if (!dismissController.isDismissing) {
                    dismissController.animateDismiss(
                        targetBoundsProvider = { currentItem?.let { boundsRegistry.getLiveBounds(it.id) } },
                        onRevealThumbnail = { boundsRegistry.showAllThumbnails() },
                        onDismissComplete = onBack
                    )
                }
            }

            BackHandler {
                handleDismiss()
            }

            PredictiveBackHandler(enabled = !dismissController.isDismissing) { progress: Flow<BackEventCompat> ->
                if (uiState.isSlideshowActive) {
                    onStopSlideshow()
                    return@PredictiveBackHandler
                }
                try {
                    progress.collect { backEvent ->
                        dismissController.onPredictiveBackProgress(backEvent.progress, backEvent.swipeEdge)
                    }
                    dismissController.animateDismiss(
                        targetBoundsProvider = { currentItem?.let { boundsRegistry.getLiveBounds(it.id) } },
                        onRevealThumbnail = { boundsRegistry.showAllThumbnails() },
                        onDismissComplete = onBack
                    )
                } catch (e: CancellationException) {
                    dismissController.onPredictiveBackCancelled()
                }
            }

            // Slideshow auto-advance
            LaunchedEffect(uiState.isSlideshowActive, pagerState.currentPage) {
                if (uiState.isSlideshowActive && uiState.items.size > 1) {
                    delay(3500L)
                    val nextPage = (pagerState.currentPage + 1) % uiState.items.size
                    pagerState.animateScrollToPage(nextPage)
                }
            }

            val layerShape = remember(dismissController) {
                object : Shape {
                    override fun createOutline(
                        size: Size,
                        layoutDirection: LayoutDirection,
                        density: Density
                    ): Outline {
                        return dismissController.getClipOutline(size, density)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = dismissController.layerScale
                        scaleY = dismissController.layerScale
                        translationX = dismissController.layerTranslationX
                        translationY = dismissController.layerTranslationY
                        alpha = dismissController.contentAlpha
                        clip = dismissController.shouldClip
                        shape = layerShape
                    }
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    userScrollEnabled = !isZoomed && !dismissController.isDragging,
                    key = { index -> displayItems[index].id },
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val item = displayItems[pageIndex]
                    val isActive = pageIndex == pagerState.currentPage
                    val targetBoundsProvider: () -> Rect? = { boundsRegistry.getLiveBounds(item.id) }
                    val thumbKey = if (uiState.thumbnailSizePx > 0) {
                        "ms_thumb_${item.id}_${item.dateModifiedSec}_${uiState.thumbnailSizePx}"
                    } else if (item.id == uiState.heroItem?.id) {
                        uiState.heroThumbnailKey
                    } else null

                    if (item.isVideo) {
                        VideoPlayerPage(
                            item = item,
                            isActivePage = isActive,
                            isImmersive = uiState.isImmersive,
                            savedPositionMs = uiState.videoPositionsMs[item.id] ?: 0L,
                            onSavePosition = onSaveVideoPosition,
                            onTap = {
                                if (uiState.isSlideshowActive) onStopSlideshow()
                                onToggleImmersive()
                            },
                            onToggleRotationLock = {
                                val activity = context as? Activity ?: return@VideoPlayerPage
                                val isLandscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                                activity.requestedOrientation = if (isLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            },
                            dismissController = dismissController,
                            targetBoundsProvider = targetBoundsProvider,
                            onRevealThumbnail = { boundsRegistry.showAllThumbnails() },
                            onDismiss = onBack,
                            onSwipeUpDetails = {
                                onLoadDetails(item)
                                showDetailsSheet = true
                            },
                            autoPlay = uiState.videoAutoplay
                        )
                    } else {
                        ZoomablePhotoPage(
                            item = item,
                            isActivePage = isActive,
                            resolveMotionPhotoUri = resolveMotionPhotoUri,
                            onTap = {
                                if (uiState.isSlideshowActive) onStopSlideshow()
                                onToggleImmersive()
                            },
                            onSwipeUpDetails = {
                                onLoadDetails(item)
                                showDetailsSheet = true
                            },
                            onDismiss = onBack,
                            onRevealThumbnail = { boundsRegistry.showAllThumbnails() },
                            onZoomStateChanged = { zoomed -> isZoomed = zoomed },
                            dismissController = dismissController,
                            targetBoundsProvider = targetBoundsProvider,
                            placeholderMemoryKey = thumbKey
                        )
                    }
                }
            }

            // Top Chrome Bar with subtle ColorOS spring motion
            AnimatedVisibility(
                visible = !uiState.isImmersive && currentItem != null,
                enter = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { -it } + fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
                exit = slideOutVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) { -it } + fadeOut(spring(stiffness = Spring.StiffnessMediumLow)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        alpha = dismissController.chromeAlpha
                    }
            ) {
                currentItem?.let { item ->
                    ViewerTopBar(
                        item = item,
                        onBack = handleDismiss,
                        onInfoClick = {
                            onLoadDetails(item)
                            showDetailsSheet = true
                        }
                    )
                }
            }

            // Bottom Chrome Bar with subtle ColorOS spring motion
            AnimatedVisibility(
                visible = !uiState.isImmersive && currentItem != null,
                enter = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { it } + fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
                exit = slideOutVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) { it } + fadeOut(spring(stiffness = Spring.StiffnessMediumLow)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        alpha = dismissController.chromeAlpha
                    }
            ) {
                currentItem?.let { item ->
                    Box {
                        ViewerBottomBar(
                            item = item,
                            onShare = { shareSingleItem(context, item) },
                            onFavorite = { onToggleFavorite(item) },
                            onEdit = {
                                launchSystemEditor(context, item) { msg ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(msg)
                                    }
                                }
                            },
                            onDelete = { onTrashItem(item) },
                            onMore = { showMoreMenu = true }
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(end = 12.dp, bottom = 58.dp)
                        ) {
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                if (!item.isVideo) {
                                    DropdownMenuItem(
                                        text = { Text("Set as wallpaper") },
                                        onClick = {
                                            haptics.click()
                                            showMoreMenu = false
                                            launchSetWallpaper(context, item)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        haptics.click()
                                        showMoreMenu = false
                                        showRenameDialog = item
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy to album") },
                                    onClick = {
                                        haptics.click()
                                        showMoreMenu = false
                                        showCopyDialog = item
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Move to Private Safe") },
                                    onClick = {
                                        haptics.click()
                                        showMoreMenu = false
                                        onMoveToVault?.invoke(item)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (uiState.isSlideshowActive) "Stop slideshow" else "Slideshow") },
                                    onClick = {
                                        haptics.click()
                                        showMoreMenu = false
                                        onToggleSlideshow()
                                    }
                                )
                                if (!item.isVideo) {
                                    DropdownMenuItem(
                                        text = { Text("Print") },
                                        onClick = {
                                            haptics.click()
                                            showMoreMenu = false
                                            scope.launch { printImage(context, item) }
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Open with") },
                                    onClick = {
                                        haptics.click()
                                        showMoreMenu = false
                                        openWithExternal(context, item)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy to clipboard") },
                                    onClick = {
                                        showMoreMenu = false
                                        copyUriToClipboard(context, item)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Copied to clipboard")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 84.dp)
        )
    }

    if (showDetailsSheet) {
        MediaDetailsSheet(
            details = uiState.details,
            isLoading = uiState.isDetailsLoading,
            onDismiss = {
                showDetailsSheet = false
                onClearDetails()
            }
        )
    }

    showRenameDialog?.let { target ->
        RenameMediaDialog(
            currentName = target.displayName,
            onDismiss = { showRenameDialog = null },
            onConfirm = { newName ->
                showRenameDialog = null
                onRenameItem(target, newName)
            }
        )
    }

    showCopyDialog?.let { target ->
        CopyToAlbumDialog(
            onDismiss = { showCopyDialog = null },
            onConfirm = { albumName ->
                showCopyDialog = null
                onCopyToAlbum(target, albumName)
            }
        )
    }
}

@Composable
private fun ViewerTopBar(
    item: MediaItem,
    onBack: () -> Unit,
    onInfoClick: () -> Unit
) {
    val haptics = rememberHapticController()
    val headerDate = remember(item.effectiveTimestampMs) {
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", Locale.getDefault())
        Instant.ofEpochMilli(item.effectiveTimestampMs)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            haptics.click()
            onBack()
        }) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headerDate,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.bucketName.isNotBlank()) {
                Text(
                    text = item.bucketName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = {
            haptics.click()
            onInfoClick()
        }) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = "Details",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun ViewerBottomBar(
    item: MediaItem,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMore: () -> Unit
) {
    val haptics = rememberHapticController()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ViewerBarAction(Icons.Rounded.Share, "Share", Color.White) {
            haptics.click()
            onShare()
        }
        ViewerBarAction(
            icon = if (item.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            label = "Favorite",
            tint = if (item.isFavorite) HeartRed else Color.White,
            onClick = {
                haptics.favoritePulse()
                onFavorite()
            }
        )
        ViewerBarAction(Icons.Rounded.Edit, "Edit", Color.White) {
            haptics.click()
            onEdit()
        }
        ViewerBarAction(Icons.Rounded.DeleteOutline, "Delete", Color.White) {
            haptics.heavy()
            onDelete()
        }
        ViewerBarAction(Icons.Rounded.MoreVert, "More", Color.White) {
            haptics.click()
            onMore()
        }
    }
}

@Composable
private fun ViewerBarAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

@Composable
private fun RenameMediaDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val haptics = rememberHapticController()
    var name by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename file") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Filename") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    haptics.click()
                    onConfirm(name)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptics.click()
                onDismiss()
            }) { Text("Cancel") }
        }
    )
}

@Composable
private fun CopyToAlbumDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val haptics = rememberHapticController()
    var albumName by remember { mutableStateOf("Saved") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy to album") },
        text = {
            OutlinedTextField(
                value = albumName,
                onValueChange = { albumName = it },
                singleLine = true,
                label = { Text("Album folder name") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    haptics.click()
                    onConfirm(albumName)
                },
                enabled = albumName.isNotBlank()
            ) {
                Text("Copy")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptics.click()
                onDismiss()
            }) { Text("Cancel") }
        }
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun launchSystemEditor(
    context: Context,
    item: MediaItem,
    onFallbackMessage: (String) -> Unit
) {
    val uri = item.contentUri.toUri()
    val mime = item.mimeType.ifBlank {
        if (item.isVideo) "video/*" else "image/*"
    }
    val activity = context.findActivity()
    val targetContext = activity ?: context

    // 1. Direct ACTION_EDIT: triggers system disambiguation dialog with "Just once" / "Always"
    val editIntent = Intent(Intent.ACTION_EDIT).apply {
        setDataAndType(uri, mime)
        clipData = ClipData.newUri(context.contentResolver, item.displayName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (activity == null) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    val editLaunched = runCatching {
        targetContext.startActivity(editIntent)
        true
    }.getOrElse { false }

    if (editLaunched) return

    // 2. Fallback: If no app declares ACTION_EDIT (e.g. video editors that only register for SEND),
    // launch system chooser with ACTION_SEND
    val sendLaunched = runCatching {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, item.displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (activity == null) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        val chooser = Intent.createChooser(sendIntent, "Edit with").apply {
            if (activity == null) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        targetContext.startActivity(chooser)
        true
    }.getOrElse { false }

    if (!sendLaunched) {
        onFallbackMessage("No editing app found for this media")
    }
}

private fun shareSingleItem(context: Context, item: MediaItem) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = item.mimeType.ifBlank { if (item.isVideo) "video/*" else "image/*" }
        putExtra(Intent.EXTRA_STREAM, item.contentUri.toUri())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share"))
}

private fun launchSetWallpaper(context: Context, item: MediaItem) {
    val uri = item.contentUri.toUri()
    val wm = WallpaperManager.getInstance(context)
    runCatching {
        val intent = wm.getCropAndSetWallpaperIntent(uri)
        context.startActivity(intent)
    }.onFailure {
        val fallback = Intent(Intent.ACTION_ATTACH_DATA).apply {
            setDataAndType(uri, item.mimeType.ifBlank { "image/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(fallback, "Set as wallpaper"))
    }
}

private suspend fun printImage(context: Context, item: MediaItem) {
    val bitmap = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(item.contentUri.toUri())?.use {
                BitmapFactory.decodeStream(it)
            }
        }.getOrNull()
    } ?: return
    val helper = PrintHelper(context).apply {
        scaleMode = PrintHelper.SCALE_MODE_FIT
    }
    helper.printBitmap(item.displayName.ifBlank { "Gallery Photo" }, bitmap)
}

private fun openWithExternal(context: Context, item: MediaItem) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(
            item.contentUri.toUri(),
            item.mimeType.ifBlank { if (item.isVideo) "video/*" else "image/*" }
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Open with"))
}

private fun copyUriToClipboard(context: Context, item: MediaItem) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newUri(context.contentResolver, item.displayName, item.contentUri.toUri())
    cm.setPrimaryClip(clip)
}
