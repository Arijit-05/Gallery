package com.arijit.gallery.viewer

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Scale
import com.arijit.gallery.core.common.rememberHapticController
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.MediaType
import com.arijit.gallery.viewer.components.VideoPlayerPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun ExternalMediaViewerScreen(
    uri: Uri,
    mimeType: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = rememberHapticController()
    val dismissController = rememberViewerDismissController(haptics)

    val resolvedMime = remember(uri, mimeType) {
        mimeType ?: context.contentResolver.getType(uri) ?: ""
    }
    val isVideo = remember(resolvedMime) {
        resolvedMime.startsWith("video/", ignoreCase = true)
    }

    val displayName = remember(uri) {
        var name: String? = null
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0)
                    }
                }
            }
        }
        name ?: uri.lastPathSegment ?: "Media"
    }

    var isImmersive by remember { mutableStateOf(false) }

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
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(isImmersive) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isImmersive) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val handleDismiss: () -> Unit = {
        if (!dismissController.isDismissing) {
            dismissController.animateDismiss(
                targetBounds = null,
                onDismissComplete = onClose
            )
        }
    }

    BackHandler { handleDismiss() }

    PredictiveBackHandler(enabled = !dismissController.isDismissing) { progress: Flow<BackEventCompat> ->
        try {
            progress.collect { backEvent ->
                dismissController.onPredictiveBackProgress(backEvent.progress, backEvent.swipeEdge)
            }
            dismissController.animateDismiss(
                targetBounds = null,
                onDismissComplete = onClose
            )
        } catch (e: CancellationException) {
            dismissController.onPredictiveBackCancelled()
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
            if (isVideo) {
                val dummyItem = remember(uri, displayName, resolvedMime) {
                    MediaItem(
                        id = -1L,
                        contentUri = uri.toString(),
                        displayName = displayName,
                        mimeType = resolvedMime.ifBlank { "video/mp4" },
                        mediaType = MediaType.VIDEO,
                        dateTakenMs = System.currentTimeMillis(),
                        dateAddedSec = System.currentTimeMillis() / 1000L,
                        dateModifiedSec = System.currentTimeMillis() / 1000L,
                        sizeBytes = 0L,
                        width = 0,
                        height = 0,
                        durationMs = 0L,
                        bucketId = "",
                        bucketName = "",
                        relativePath = "",
                        isFavorite = false,
                        orientation = 0
                    )
                }
                VideoPlayerPage(
                    item = dummyItem,
                    isActivePage = true,
                    isImmersive = isImmersive,
                    savedPositionMs = 0L,
                    onSavePosition = { _, _ -> },
                    onTap = { isImmersive = !isImmersive },
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
                    onDismiss = onClose
                )
            } else {
                ExternalZoomablePhoto(
                    uri = uri,
                    displayName = displayName,
                    onTap = { isImmersive = !isImmersive },
                    dismissController = dismissController,
                    onDismiss = onClose
                )
            }
        }

        // Top Bar
        AnimatedVisibility(
            visible = !isImmersive,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    alpha = dismissController.chromeAlpha
                }
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    IconButton(onClick = handleDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = displayName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                }
            }
        }

        // Bottom Bar
        AnimatedVisibility(
            visible = !isImmersive,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    alpha = dismissController.chromeAlpha
                }
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(vertical = 10.dp)
                ) {
                    ExternalActionButton(
                        icon = Icons.Rounded.Share,
                        label = "Share",
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = resolvedMime.ifBlank { "*/*" }
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share"))
                        }
                    )

                    if (!isVideo) {
                        ExternalActionButton(
                            icon = Icons.Rounded.Palette,
                            label = "Set wallpaper",
                            onClick = {
                                runCatching {
                                    val wpIntent = WallpaperManager.getInstance(context)
                                        .getCropAndSetWallpaperIntent(uri)
                                    context.startActivity(wpIntent)
                                }.onFailure {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Unable to launch wallpaper cropper")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 72.dp)
        )
    }
}

@Composable
private fun ExternalZoomablePhoto(
    uri: Uri,
    displayName: String,
    onTap: () -> Unit,
    dismissController: ViewerDismissController? = null,
    onDismiss: (() -> Unit)? = null
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = Offset(
                                (size.width / 2f - tapOffset.x) * 1.5f,
                                (size.height / 2f - tapOffset.y) * 1.5f
                            )
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    var verticalSwipeAccum = 0f
                    var horizontalSwipeAccum = 0f
                    var draggingDismiss = false

                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }

                        if (pressedCount >= 2) {
                            if (draggingDismiss) {
                                draggingDismiss = false
                                dismissController?.onDragCancel()
                            }
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            scale = newScale
                            offset = if (newScale > 1f) offset + panChange else Offset.Zero
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (pressedCount == 1) {
                            val change = event.changes.first()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val delta = change.position - change.previousPosition

                            if (scale > 1.05f) {
                                offset += delta
                                if (change.positionChanged()) change.consume()
                            } else if (delta != Offset.Zero) {
                                if (draggingDismiss) {
                                    dismissController?.onDrag(delta.x, delta.y)
                                    change.consume()
                                } else {
                                    verticalSwipeAccum += delta.y
                                    horizontalSwipeAccum += delta.x
                                    if (abs(verticalSwipeAccum) > abs(horizontalSwipeAccum) * 1.1f) {
                                        if (verticalSwipeAccum > 4f) {
                                            draggingDismiss = true
                                            dismissController?.onDragStart()
                                            dismissController?.onDrag(delta.x, verticalSwipeAccum)
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (draggingDismiss) {
                        val velocity = velocityTracker.calculateVelocity()
                        if (dismissController != null && onDismiss != null) {
                            dismissController.onDragEnd(
                                velocityX = velocity.x,
                                velocityY = velocity.y,
                                density = density,
                                targetBounds = null,
                                onDismissComplete = onDismiss
                            )
                        } else if (onDismiss != null && (verticalSwipeAccum > 180f || velocity.y > with(density) { 950.dp.toPx() })) {
                            onDismiss()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val context = LocalContext.current
        val request = remember(uri) {
            ImageRequest.Builder(context)
                .data(uri)
                .size(2560, 2560)
                .scale(Scale.FIT)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

@Composable
private fun ExternalActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}
