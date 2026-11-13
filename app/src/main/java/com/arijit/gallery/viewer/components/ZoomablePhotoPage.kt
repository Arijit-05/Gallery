package com.arijit.gallery.viewer.components

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Scale
import com.arijit.gallery.core.common.rememberHapticController
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.viewer.ViewerDismissController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

@Stable
class ZoomTransformState {
    var scale by mutableFloatStateOf(1f)
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)

    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }
}

private fun clampOffset(
    candidate: Offset,
    currentScale: Float,
    containerWidth: Int,
    containerHeight: Int
): Offset {
    if (currentScale <= 1f || containerWidth <= 0 || containerHeight <= 0) return Offset.Zero
    val maxX = ((containerWidth * (currentScale - 1f)) / 2f).coerceAtLeast(0f)
    val maxY = ((containerHeight * (currentScale - 1f)) / 2f).coerceAtLeast(0f)
    return Offset(
        x = candidate.x.coerceIn(-maxX, maxX),
        y = candidate.y.coerceIn(-maxY, maxY)
    )
}

@Composable
fun ZoomablePhotoPage(
    item: MediaItem,
    isActivePage: Boolean,
    resolveMotionPhotoUri: suspend (MediaItem) -> Uri?,
    onTap: () -> Unit,
    onSwipeUpDetails: () -> Unit,
    onDismiss: () -> Unit,
    onRevealThumbnail: () -> Unit = {},
    onZoomStateChanged: (Boolean) -> Unit,
    dismissController: ViewerDismissController? = null,
    targetBoundsProvider: (() -> Rect?)? = null,
    targetBounds: Rect? = null,
    placeholderMemoryKey: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = rememberHapticController()
    val scope = rememberCoroutineScope()

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val transformState = remember(item.id) { ZoomTransformState() }

    // Motion photo state
    var motionVideoUri by remember(item.id) { mutableStateOf<Uri?>(null) }
    var isPlayingMotion by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(item.id, isActivePage) {
        if (isActivePage && !item.isGif) {
            motionVideoUri = resolveMotionPhotoUri(item)
        } else if (!isActivePage) {
            isPlayingMotion = false
            transformState.reset()
        }
    }

    // Only notify pager when zoom threshold crosses, avoiding recompositions on every gesture delta
    LaunchedEffect(transformState) {
        snapshotFlow { transformState.scale > 1.02f }
            .distinctUntilChanged()
            .collect { zoomed ->
                onZoomStateChanged(zoomed)
            }
    }

    val motionPlayer = remember(item.id, motionVideoUri) {
        motionVideoUri?.let { uri ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(ExoMediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                prepare()
            }
        }
    }

    DisposableEffect(motionPlayer) {
        onDispose {
            motionPlayer?.release()
        }
    }

    LaunchedEffect(isPlayingMotion, motionPlayer) {
        motionPlayer?.playWhenReady = isPlayingMotion
    }

    val fullResRequest = remember(item.id, item.contentUri, item.dateModifiedSec, placeholderMemoryKey) {
        ImageRequest.Builder(context)
            .data(item.contentUri)
            .apply {
                if (placeholderMemoryKey != null) {
                    placeholderMemoryCacheKey(placeholderMemoryKey)
                }
            }
            .size(2560, 2560)
            .scale(Scale.FIT)
            .build()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .systemGestureExclusion()
            .onSizeChanged { containerSize = it }
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapOffset ->
                        val currentScale = transformState.scale
                        val targetScale = if (currentScale > 1.3f) 1f else 2.5f
                        val center = Offset(
                            containerSize.width / 2f,
                            containerSize.height / 2f
                        )
                        val targetOffset = if (targetScale > 1f) {
                            clampOffset(
                                candidate = (center - tapOffset) * (targetScale - 1f),
                                currentScale = targetScale,
                                containerWidth = containerSize.width,
                                containerHeight = containerSize.height
                            )
                        } else {
                            Offset.Zero
                        }
                        scope.launch {
                            val anim = Animatable(0f)
                            val startScale = currentScale
                            val startX = transformState.offsetX
                            val startY = transformState.offsetY
                            anim.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) {
                                val t = value
                                transformState.scale = startScale + (targetScale - startScale) * t
                                transformState.offsetX = startX + (targetOffset.x - startX) * t
                                transformState.offsetY = startY + (targetOffset.y - startY) * t
                            }
                        }
                    },
                    onLongPress = {
                        if (motionPlayer != null) {
                            haptics.selectionStart()
                            motionPlayer.seekTo(0L)
                            isPlayingMotion = true
                        }
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (isPlayingMotion) {
                            isPlayingMotion = false
                        }
                    }
                )
            }
            .pointerInput(item.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    var verticalSwipeAccum = 0f
                    var horizontalSwipeAccum = 0f
                    var draggingDismiss = false

                    try {
                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }

                            if (pressedCount >= 2) {
                                if (draggingDismiss) {
                                    draggingDismiss = false
                                    dismissController?.onDragCancel(
                                        velocityX = 0f,
                                        velocityY = 0f,
                                        density = density,
                                        targetBounds = targetBounds,
                                        onDismissComplete = onDismiss
                                    )
                                }
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)

                                val currentScale = transformState.scale
                                val newScale = (currentScale * zoomChange).coerceIn(1f, 5f)
                                val center = Offset(
                                    containerSize.width / 2f,
                                    containerSize.height / 2f
                                )
                                val focusShift = (centroid - center) * (1f - newScale / currentScale)
                                val nextOffset = clampOffset(
                                    candidate = Offset(transformState.offsetX, transformState.offsetY) + panChange + focusShift,
                                    currentScale = newScale,
                                    containerWidth = containerSize.width,
                                    containerHeight = containerSize.height
                                )

                                transformState.scale = newScale
                                transformState.offsetX = nextOffset.x
                                transformState.offsetY = nextOffset.y
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (pressedCount == 1) {
                                val change = event.changes.first()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                val delta = change.position - change.previousPosition

                                if (transformState.scale > 1.02f) {
                                    val clamped = clampOffset(
                                        candidate = Offset(transformState.offsetX, transformState.offsetY) + delta,
                                        currentScale = transformState.scale,
                                        containerWidth = containerSize.width,
                                        containerHeight = containerSize.height
                                    )
                                    transformState.offsetX = clamped.x
                                    transformState.offsetY = clamped.y
                                    if (change.positionChanged()) change.consume()
                                } else if (delta != Offset.Zero) {
                                    if (draggingDismiss) {
                                        dismissController?.onDrag(delta.x, delta.y)
                                        change.consume()
                                    } else {
                                        verticalSwipeAccum += delta.y
                                        horizontalSwipeAccum += delta.x
                                        // Vertical-dominant touch slop to avoid hijacking HorizontalPager
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
                            // Read velocity BEFORE tracker reset or consuming pointer
                            val velocity = velocityTracker.calculateVelocity()
                            if (dismissController != null) {
                                val boundsProvider = targetBoundsProvider ?: { targetBounds }
                                dismissController.onDragEnd(
                                    velocityX = velocity.x,
                                    velocityY = velocity.y,
                                    density = density,
                                    targetBoundsProvider = boundsProvider,
                                    onRevealThumbnail = onRevealThumbnail,
                                    onDismissComplete = onDismiss
                                )
                            } else {
                                onDismiss()
                            }
                        } else if (transformState.scale <= 1.02f && verticalSwipeAccum < -140f) {
                            onSwipeUpDetails()
                        }
                    } catch (e: CancellationException) {
                        // Gesture cancelled near bottom edge (e.g. system navigation gesture bar)
                        if (draggingDismiss) {
                            val velocity = velocityTracker.calculateVelocity()
                            val boundsProvider = targetBoundsProvider ?: { targetBounds }
                            dismissController?.onDragCancel(
                                velocityX = velocity.x,
                                velocityY = velocity.y,
                                density = density,
                                targetBoundsProvider = boundsProvider,
                                onRevealThumbnail = onRevealThumbnail,
                                onDismissComplete = onDismiss
                            )
                        }
                        throw e
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // GPU graphicsLayer transform: reads state strictly during draw pass, eliminating recompositions!
        val transformModifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = transformState.scale
                scaleY = transformState.scale
                translationX = transformState.offsetX
                translationY = transformState.offsetY
            }

        AsyncImage(
            model = fullResRequest,
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = transformModifier
        )

        if (isPlayingMotion && motionPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        player = motionPlayer
                    }
                },
                update = { view ->
                    if (view.player !== motionPlayer) {
                        view.player = motionPlayer
                    }
                },
                modifier = transformModifier
            )
        }

        if (motionVideoUri != null && !isPlayingMotion) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 86.dp)
            ) {
                Text(
                    text = "LIVE • Hold to play",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
