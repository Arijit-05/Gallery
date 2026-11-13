package com.arijit.gallery.viewer.components

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.arijit.gallery.core.common.Formatters
import com.arijit.gallery.core.common.rememberHapticController
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.viewer.ViewerDismissController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.abs

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerPage(
    item: MediaItem,
    isActivePage: Boolean,
    isImmersive: Boolean,
    savedPositionMs: Long,
    onSavePosition: (Long, Long) -> Unit,
    onTap: () -> Unit,
    onToggleRotationLock: () -> Unit,
    modifier: Modifier = Modifier,
    dismissController: ViewerDismissController? = null,
    targetBoundsProvider: (() -> Rect?)? = null,
    targetBounds: Rect? = null,
    onRevealThumbnail: () -> Unit = {},
    onDismiss: (() -> Unit)? = null,
    onSwipeUpDetails: (() -> Unit)? = null,
    autoPlay: Boolean = true
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = rememberHapticController()

    var isPlaying by remember(item.id) { mutableStateOf(false) }
    var isMuted by remember(item.id) { mutableStateOf(false) }
    var isLooping by remember(item.id) { mutableStateOf(true) }
    var speed by remember(item.id) { mutableFloatStateOf(1f) }
    var currentPosMs by remember(item.id) { mutableLongStateOf(savedPositionMs) }
    var durationMs by remember(item.id) { mutableLongStateOf(item.durationMs.coerceAtLeast(1L)) }
    var isSeeking by remember { mutableStateOf(false) }

    val exoPlayer = remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(item.contentUri.toUri()))
            repeatMode = Player.REPEAT_MODE_ONE
            if (savedPositionMs > 0L) {
                seekTo(savedPositionMs)
            }
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && exoPlayer.duration > 0L) {
                    durationMs = exoPlayer.duration
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            onSavePosition(item.id, exoPlayer.currentPosition)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Initialize playback when page becomes active; never pause upon recomposition
    var hasInitializedPlayback by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(isActivePage) {
        if (isActivePage) {
            if (!hasInitializedPlayback) {
                hasInitializedPlayback = true
                exoPlayer.playWhenReady = autoPlay
            }
        } else {
            onSavePosition(item.id, exoPlayer.currentPosition)
            exoPlayer.playWhenReady = false
        }
    }

    // App-level background/foreground lifecycle: pause on background, restore if playing
    var wasPlayingBeforePause by remember(item.id) { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    wasPlayingBeforePause = exoPlayer.playWhenReady
                    onSavePosition(item.id, exoPlayer.currentPosition)
                    exoPlayer.playWhenReady = false
                }
                Lifecycle.Event.ON_STOP -> {
                    onSavePosition(item.id, exoPlayer.currentPosition)
                    exoPlayer.playWhenReady = false
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isActivePage && wasPlayingBeforePause) {
                        exoPlayer.playWhenReady = true
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isPlaying, isSeeking, isActivePage) {
        while (isPlaying && !isSeeking && isActivePage) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (exoPlayer.duration > 0L) durationMs = exoPlayer.duration
            delay(200L)
        }
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(isLooping) {
        exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    LaunchedEffect(speed) {
        exoPlayer.setPlaybackSpeed(speed)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .systemGestureExclusion()
            .pointerInput(item.id) {
                detectTapGestures(onTap = { onTap() })
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

                            if (pressedCount == 1 && !isSeeking) {
                                val change = event.changes.first()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                val delta = change.position - change.previousPosition

                                if (delta != Offset.Zero) {
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
                                val boundsProvider = targetBoundsProvider ?: { targetBounds }
                                dismissController.onDragEnd(
                                    velocityX = velocity.x,
                                    velocityY = velocity.y,
                                    density = density,
                                    targetBoundsProvider = boundsProvider,
                                    onRevealThumbnail = onRevealThumbnail,
                                    onDismissComplete = onDismiss
                                )
                            } else if (onDismiss != null && (verticalSwipeAccum > 180f || velocity.y > with(density) { 950.dp.toPx() })) {
                                onDismiss()
                            }
                        } else if (verticalSwipeAccum < -140f) {
                            onSwipeUpDetails?.invoke()
                        }
                    } catch (e: CancellationException) {
                        if (draggingDismiss && dismissController != null && onDismiss != null) {
                            val velocity = velocityTracker.calculateVelocity()
                            val boundsProvider = targetBoundsProvider ?: { targetBounds }
                            dismissController.onDragCancel(
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
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = exoPlayer
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) view.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )

        // Center Play / Pause button
        AnimatedVisibility(
            visible = !isImmersive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    alpha = dismissController?.chromeAlpha ?: 1f
                }
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable {
                        haptics.click()
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                exoPlayer.seekTo(0L)
                            }
                            exoPlayer.play()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        // Bottom Seek & Video Controls
        AnimatedVisibility(
            visible = !isImmersive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 76.dp)
                .graphicsLayer {
                    alpha = dismissController?.chromeAlpha ?: 1f
                }
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.62f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Formatters.formatDuration(currentPosMs),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        Slider(
                            value = (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                            onValueChange = { fraction ->
                                isSeeking = true
                                currentPosMs = (fraction * durationMs).toLong()
                            },
                            onValueChangeFinished = {
                                exoPlayer.seekTo(currentPosMs)
                                isSeeking = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        )
                        Text(
                            text = Formatters.formatDuration(durationMs),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                haptics.click()
                                isMuted = !isMuted
                            }) {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = {
                                haptics.click()
                                isLooping = !isLooping
                            }) {
                                Icon(
                                    imageVector = if (isLooping) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    contentDescription = "Toggle loop",
                                    tint = if (isLooping) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.16f),
                                modifier = Modifier.clickable {
                                    haptics.click()
                                    speed = when (speed) {
                                        0.5f -> 1f
                                        1f -> 1.25f
                                        1.25f -> 1.5f
                                        1.5f -> 2f
                                        else -> 0.5f
                                    }
                                }
                            ) {
                                Text(
                                    text = "${speed}x",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(onClick = {
                                haptics.click()
                                onToggleRotationLock()
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.ScreenRotation,
                                    contentDescription = "Rotate screen",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

