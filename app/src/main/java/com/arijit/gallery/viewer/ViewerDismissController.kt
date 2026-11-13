package com.arijit.gallery.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.arijit.gallery.core.common.HapticController
import com.arijit.gallery.core.domain.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Tunable gesture and release rules for swipe-down-to-dismiss.
 */
const val DISMISS_DISTANCE_FRACTION = 0.08f // 8% of screen height
const val DISMISS_VELOCITY_DP_PER_S = 400f   // 400 dp/s
const val RESTORE_MAX_FRACTION = 0.03f       // 3% of screen height

/**
 * State machine for the swipe-down-to-dismiss gesture and return-to-grid transition.
 * Invariant: Once in SettlingDismissing, no other input can move it back to Restoring.
 */
enum class DismissState {
    Idle,
    Dragging,
    SettlingDismissing,
    SettlingRestoring,
    Dismissed
}

/**
 * Orchestrates the fluid, physical swipe-down-to-dismiss gesture and return-to-grid transition.
 * Drives scrim alpha, image translation/scale/corner-radius, and chrome bar fading entirely
 * in layout/draw phases at 120Hz without per-frame recompositions.
 *
 * Interpolates bounds, clip, AND content scale from "fit full image" to "center-crop in the cell",
 * removing letterbox bars and ensuring the landing image is pixel-identical to the grid thumbnail.
 */
@Stable
class ViewerDismissController(
    private val scope: CoroutineScope,
    private val haptics: HapticController
) {
    var containerSize by mutableStateOf(IntSize.Zero)
    var activeItem by mutableStateOf<MediaItem?>(null)

    // State machine
    var state by mutableStateOf(DismissState.Idle)
        private set

    val isDragging: Boolean
        get() = state == DismissState.Dragging

    val isDismissing: Boolean
        get() = state == DismissState.SettlingDismissing || state == DismissState.Dismissed

    // Raw finger translation (resistance applied on display)
    var dragX by mutableFloatStateOf(0f)
        private set
    var dragY by mutableFloatStateOf(0f)
        private set

    // Animated values for restoring and single-progress return
    val animOffset = Animatable(Offset.Zero, Offset.VectorConverter)
    val animScale = Animatable(1f)
    val animScrimAlpha = Animatable(1f)
    val animContentAlpha = Animatable(1f)

    // Single progress spring driver for the return animation (0f -> 1f)
    val returnProgress = Animatable(0f)

    // Capture geometry at release for smooth, critically-damped return
    private var initialWindowRect = Rect.Zero
    private var initialImageRect = Rect.Zero
    private var initialCornerRadiusPx = 0f
    private var initialScrimAlpha = 1f
    private var fallbackTx = 0f
    private var fallbackTy = 0f
    private var hasRevealedThumbnail = false
    private var targetBoundsProvider: (() -> Rect?)? = null

    private var hasTickedThreshold = false
    private var animationJob: Job? = null

    // Touch resistance multiplier (0.90 for physical weight)
    private val resistance = 0.90f

    val currentProgress: Float
        get() {
            val h = containerSize.height.toFloat()
            if (h <= 0f) return 0f
            val thresholdY = h * 0.35f
            val y = when (state) {
                DismissState.Dragging -> dragY * resistance
                DismissState.SettlingDismissing -> {
                    val t = returnProgress.value.coerceIn(0f, 1f)
                    lerp(initialImageRect.center.y - (h / 2f), fallbackTy, t)
                }
                DismissState.SettlingRestoring -> animOffset.value.y
                else -> 0f
            }
            return (abs(y) / thresholdY).coerceIn(0f, 1f)
        }

    val translationX: Float
        get() = layerTranslationX

    val translationY: Float
        get() = layerTranslationY

    val scale: Float
        get() = layerScale

    val cornerRadiusDp: Dp
        get() {
            val p = currentProgress
            return (28f * p).dp
        }

    val scrimAlpha: Float
        get() = when (state) {
            DismissState.Dragging -> {
                val p = currentProgress
                ((1f - p) * (1f - p)).coerceIn(0f, 1f)
            }
            DismissState.SettlingDismissing -> {
                val t = returnProgress.value.coerceIn(0f, 1f)
                // Scrim finishes fading slightly before image lands (at ~82% progress)
                (initialScrimAlpha * (1f - (t / 0.82f).coerceIn(0f, 1f))).coerceIn(0f, 1f)
            }
            DismissState.SettlingRestoring -> animScrimAlpha.value.coerceIn(0f, 1f)
            DismissState.Dismissed -> 0f
            else -> 1f
        }

    val contentAlpha: Float
        get() = when (state) {
            DismissState.SettlingDismissing -> {
                val target = targetBoundsProvider?.invoke()
                if (target == null) {
                    val t = returnProgress.value.coerceIn(0f, 1f)
                    (1f - t).coerceIn(0f, 1f)
                } else {
                    1f
                }
            }
            DismissState.Dismissed -> 0f
            else -> animContentAlpha.value.coerceIn(0f, 1f)
        }

    val chromeAlpha: Float
        get() = when (state) {
            DismissState.Dragging -> {
                val p = currentProgress
                ((0.15f - p) / 0.15f).coerceIn(0f, 1f)
            }
            DismissState.SettlingDismissing,
            DismissState.Dismissed -> 0f
            DismissState.SettlingRestoring -> {
                val p = currentProgress
                ((0.15f - p) / 0.15f).coerceIn(0f, 1f)
            }
            else -> 1f
        }

    val shouldClip: Boolean
        get() = state == DismissState.Dragging ||
            state == DismissState.SettlingDismissing ||
            state == DismissState.SettlingRestoring

    val layerScale: Float
        get() {
            val containerW = containerSize.width.toFloat()
            val containerH = containerSize.height.toFloat()
            if (containerW <= 0f || containerH <= 0f) return 1f
            val fitRect = computeFitRect(containerW, containerH, activeItem)
            if (fitRect.width <= 0f) return 1f

            return when (state) {
                DismissState.Dragging -> 1f - (currentProgress * 0.30f)
                DismissState.SettlingDismissing -> {
                    val t = returnProgress.value.coerceIn(0f, 1f)
                    val target = targetBoundsProvider?.invoke()
                    if (target != null) {
                        val cellW = target.width
                        val cellH = target.height
                        val scaleCrop = maxOf(cellW / fitRect.width, cellH / fitRect.height)
                        val finalImageW = fitRect.width * scaleCrop
                        val currentImageW = lerp(initialImageRect.width, finalImageW, t)
                        currentImageW / fitRect.width
                    } else {
                        val startScale = if (fitRect.width > 0f) initialImageRect.width / fitRect.width else 1f
                        lerp(startScale, 0.45f, t)
                    }
                }
                DismissState.SettlingRestoring -> animScale.value
                DismissState.Dismissed -> 0.01f
                else -> 1f
            }
        }

    val layerTranslationX: Float
        get() {
            val containerW = containerSize.width.toFloat()
            val containerH = containerSize.height.toFloat()
            if (containerW <= 0f || containerH <= 0f) return 0f

            return when (state) {
                DismissState.Dragging -> dragX * resistance
                DismissState.SettlingDismissing -> {
                    val t = returnProgress.value.coerceIn(0f, 1f)
                    val target = targetBoundsProvider?.invoke()
                    if (target != null) {
                        val currentCenterX = lerp(initialImageRect.center.x, target.center.x, t)
                        currentCenterX - (containerW / 2f)
                    } else {
                        val startTx = initialImageRect.center.x - (containerW / 2f)
                        lerp(startTx, fallbackTx, t)
                    }
                }
                DismissState.SettlingRestoring -> animOffset.value.x
                DismissState.Dismissed -> 0f
                else -> 0f
            }
        }

    val layerTranslationY: Float
        get() {
            val containerW = containerSize.width.toFloat()
            val containerH = containerSize.height.toFloat()
            if (containerW <= 0f || containerH <= 0f) return 0f

            return when (state) {
                DismissState.Dragging -> dragY * resistance
                DismissState.SettlingDismissing -> {
                    val t = returnProgress.value.coerceIn(0f, 1f)
                    val target = targetBoundsProvider?.invoke()
                    if (target != null) {
                        val currentCenterY = lerp(initialImageRect.center.y, target.center.y, t)
                        currentCenterY - (containerH / 2f)
                    } else {
                        val startTy = initialImageRect.center.y - (containerH / 2f)
                        lerp(startTy, fallbackTy, t)
                    }
                }
                DismissState.SettlingRestoring -> animOffset.value.y
                DismissState.Dismissed -> 0f
                else -> 0f
            }
        }

    fun setActiveMediaItem(item: MediaItem?) {
        activeItem = item
    }

    fun computeFitRect(containerW: Float, containerH: Float, item: MediaItem?): Rect {
        if (containerW <= 0f || containerH <= 0f) return Rect.Zero
        val (effW, effH) = if (item != null) {
            if (item.orientation == 90 || item.orientation == 270) {
                item.height to item.width
            } else {
                item.width to item.height
            }
        } else 0 to 0

        val imageAspect = if (effW > 0 && effH > 0) {
            effW.toFloat() / effH.toFloat()
        } else {
            containerW / containerH
        }

        val containerAspect = containerW / containerH
        val (fitW, fitH) = if (imageAspect > containerAspect) {
            containerW to (containerW / imageAspect)
        } else {
            (containerH * imageAspect) to containerH
        }
        val fitLeft = (containerW - fitW) / 2f
        val fitTop = (containerH - fitH) / 2f
        return Rect(fitLeft, fitTop, fitLeft + fitW, fitTop + fitH)
    }

    /**
     * Computes the exact clip outline in layer-local coordinates.
     * When transformed by [layerScale] and [layerTranslationX]/[layerTranslationY],
     * the on-screen rendered boundary is pixel-identical to the interpolated visible window,
     * removing any letterbox bars during dragging and landing with exact cell crop bounds.
     */
    fun getClipOutline(size: Size, density: Density): Outline {
        val containerW = size.width
        val containerH = size.height
        if (containerW <= 0f || containerH <= 0f) return Outline.Rectangle(Rect.Zero)
        val fitRect = computeFitRect(containerW, containerH, activeItem)
        if (fitRect.width <= 0f) return Outline.Rectangle(Rect(0f, 0f, containerW, containerH))

        return when (state) {
            DismissState.Dragging,
            DismissState.SettlingRestoring -> {
                val cornerRadiusPx = with(density) { (28f * currentProgress).dp.toPx() }
                Outline.Rounded(
                    RoundRect(
                        rect = fitRect,
                        cornerRadius = CornerRadius(cornerRadiusPx)
                    )
                )
            }
            DismissState.SettlingDismissing -> {
                val t = returnProgress.value.coerceIn(0f, 1f)
                val target = targetBoundsProvider?.invoke()
                if (target != null) {
                    val winLeft = lerp(initialWindowRect.left, target.left, t)
                    val winTop = lerp(initialWindowRect.top, target.top, t)
                    val winRight = lerp(initialWindowRect.right, target.right, t)
                    val winBottom = lerp(initialWindowRect.bottom, target.bottom, t)

                    val cellW = target.width
                    val cellH = target.height
                    val scaleCrop = maxOf(cellW / fitRect.width, cellH / fitRect.height)
                    val finalW = fitRect.width * scaleCrop

                    val imgCenterX = lerp(initialImageRect.center.x, target.center.x, t)
                    val imgCenterY = lerp(initialImageRect.center.y, target.center.y, t)
                    val imgW = lerp(initialImageRect.width, finalW, t)
                    val s = (imgW / fitRect.width).coerceAtLeast(0.001f)

                    val localLeft = (winLeft - imgCenterX) / s + containerW / 2f
                    val localTop = (winTop - imgCenterY) / s + containerH / 2f
                    val localRight = (winRight - imgCenterX) / s + containerW / 2f
                    val localBottom = (winBottom - imgCenterY) / s + containerH / 2f
                    val localCorner = lerp(initialCornerRadiusPx, 0f, t) / s

                    Outline.Rounded(
                        RoundRect(
                            rect = Rect(localLeft, localTop, localRight, localBottom),
                            cornerRadius = CornerRadius(localCorner.coerceAtLeast(0f))
                        )
                    )
                } else {
                    val localCorner = lerp(initialCornerRadiusPx, 0f, t)
                    Outline.Rounded(
                        RoundRect(
                            rect = fitRect,
                            cornerRadius = CornerRadius(localCorner.coerceAtLeast(0f))
                        )
                    )
                }
            }
            else -> Outline.Rectangle(Rect(0f, 0f, containerW, containerH))
        }
    }

    fun onDragStart() {
        if (state == DismissState.SettlingDismissing || state == DismissState.Dismissed) return

        animationJob?.cancel()
        state = DismissState.Dragging
        hasTickedThreshold = false
    }

    fun onDrag(deltaX: Float, deltaY: Float) {
        if (state != DismissState.Dragging) return

        dragX += deltaX
        dragY += deltaY

        val p = currentProgress
        if (p >= 0.15f && !hasTickedThreshold) {
            haptics.snap()
            hasTickedThreshold = true
        } else if (p < 0.15f && hasTickedThreshold) {
            hasTickedThreshold = false
        }
    }

    fun onDragEnd(
        velocityX: Float,
        velocityY: Float,
        density: Density,
        targetBoundsProvider: () -> Rect?,
        onRevealThumbnail: () -> Unit = {},
        onDismissComplete: () -> Unit
    ) {
        if (state != DismissState.Dragging) return

        val screenHeight = containerSize.height.toFloat()
        val rawProgress = if (screenHeight > 0f) dragY / screenHeight else 0f
        val flingThresholdPx = with(density) { DISMISS_VELOCITY_DP_PER_S.dp.toPx() }
        val isDownwardDominant = dragY > 0f && abs(dragY) > abs(dragX) * 1.1f
        val hasPositiveVelocity = velocityY > 50f

        val shouldDismiss = (dragY > 0f && rawProgress > DISMISS_DISTANCE_FRACTION) ||
            (velocityY > flingThresholdPx) ||
            (isDownwardDominant && hasPositiveVelocity && rawProgress > RESTORE_MAX_FRACTION)

        if (shouldDismiss) {
            animateDismiss(
                initialVelocityX = velocityX,
                initialVelocityY = velocityY,
                density = density,
                targetBoundsProvider = targetBoundsProvider,
                onRevealThumbnail = onRevealThumbnail,
                onDismissComplete = onDismissComplete
            )
        } else {
            animateBackToOrigin(initialVelocityX = velocityX, initialVelocityY = velocityY)
        }
    }

    // Overload for static Rect? (backwards compatibility / unit tests)
    fun onDragEnd(
        velocityX: Float,
        velocityY: Float,
        density: Density,
        targetBounds: Rect?,
        onDismissComplete: () -> Unit
    ) {
        onDragEnd(
            velocityX = velocityX,
            velocityY = velocityY,
            density = density,
            targetBoundsProvider = { targetBounds },
            onRevealThumbnail = {},
            onDismissComplete = onDismissComplete
        )
    }

    fun onDragCancel(
        velocityX: Float = 0f,
        velocityY: Float = 0f,
        density: Density? = null,
        targetBoundsProvider: (() -> Rect?)? = null,
        onRevealThumbnail: () -> Unit = {},
        onDismissComplete: (() -> Unit)? = null
    ) {
        if (state != DismissState.Dragging) return

        val screenHeight = containerSize.height.toFloat()
        val rawProgress = if (screenHeight > 0f) dragY / screenHeight else 0f
        val flingThresholdPx = density?.let { with(it) { DISMISS_VELOCITY_DP_PER_S.dp.toPx() } } ?: 1000f
        val isDownwardDominant = dragY > 0f && abs(dragY) > abs(dragX) * 1.1f
        val hasPositiveVelocity = velocityY > 50f

        val shouldDismiss = (dragY > 0f && rawProgress > DISMISS_DISTANCE_FRACTION) ||
            (velocityY > flingThresholdPx && dragY > 0f) ||
            (isDownwardDominant && hasPositiveVelocity && rawProgress > RESTORE_MAX_FRACTION)

        if (shouldDismiss && onDismissComplete != null) {
            animateDismiss(
                initialVelocityX = velocityX,
                initialVelocityY = velocityY,
                density = density ?: Density(1f),
                targetBoundsProvider = targetBoundsProvider ?: { null },
                onRevealThumbnail = onRevealThumbnail,
                onDismissComplete = onDismissComplete
            )
        } else {
            animateBackToOrigin(initialVelocityX = velocityX, initialVelocityY = velocityY)
        }
    }

    // Overload for static Rect? cancel
    fun onDragCancel(
        velocityX: Float = 0f,
        velocityY: Float = 0f,
        density: Density? = null,
        targetBounds: Rect? = null,
        onDismissComplete: (() -> Unit)? = null
    ) {
        onDragCancel(
            velocityX = velocityX,
            velocityY = velocityY,
            density = density,
            targetBoundsProvider = { targetBounds },
            onRevealThumbnail = {},
            onDismissComplete = onDismissComplete
        )
    }

    private fun animateBackToOrigin(initialVelocityX: Float, initialVelocityY: Float) {
        if (state == DismissState.SettlingDismissing || state == DismissState.Dismissed) return

        animationJob?.cancel()
        state = DismissState.SettlingRestoring

        val startX = layerTranslationX
        val startY = layerTranslationY
        val startScale = layerScale
        val startAlpha = scrimAlpha

        dragX = 0f
        dragY = 0f

        animationJob = scope.launch {
            try {
                animOffset.snapTo(Offset(startX, startY))
                animScale.snapTo(startScale)
                animScrimAlpha.snapTo(startAlpha)
                animContentAlpha.snapTo(1f)

                val springSpec = spring<Offset>(
                    dampingRatio = 0.85f,
                    stiffness = 400f
                )
                val floatSpring = spring<Float>(
                    dampingRatio = 0.85f,
                    stiffness = 400f
                )

                val jobOffset = launch {
                    animOffset.animateTo(
                        targetValue = Offset.Zero,
                        animationSpec = springSpec,
                        initialVelocity = Offset(initialVelocityX, initialVelocityY)
                    )
                }
                val jobScale = launch {
                    animScale.animateTo(1f, floatSpring)
                }
                val jobScrim = launch {
                    animScrimAlpha.animateTo(1f, floatSpring)
                }

                jobOffset.join()
                jobScale.join()
                jobScrim.join()
            } finally {
                if (state == DismissState.SettlingRestoring) {
                    state = DismissState.Idle
                }
            }
        }
    }

    fun animateDismiss(
        initialVelocityX: Float = 0f,
        initialVelocityY: Float = 0f,
        density: Density = Density(1f),
        targetBoundsProvider: () -> Rect?,
        onRevealThumbnail: () -> Unit = {},
        onDismissComplete: () -> Unit
    ) {
        if (state == DismissState.SettlingDismissing || state == DismissState.Dismissed) return

        animationJob?.cancel()
        state = DismissState.SettlingDismissing
        this.targetBoundsProvider = targetBoundsProvider
        hasRevealedThumbnail = false

        val containerW = containerSize.width.toFloat()
        val containerH = containerSize.height.toFloat()
        val fitRect = computeFitRect(containerW, containerH, activeItem)

        val currentDx = dragX * resistance
        val currentDy = dragY * resistance
        val currentScale = 1f - (currentProgress * 0.30f)
        val currentW = fitRect.width * currentScale
        val currentH = fitRect.height * currentScale
        val currentCenterX = containerW / 2f + currentDx
        val currentCenterY = containerH / 2f + currentDy

        initialWindowRect = Rect(
            currentCenterX - currentW / 2f,
            currentCenterY - currentH / 2f,
            currentCenterX + currentW / 2f,
            currentCenterY + currentH / 2f
        )
        initialImageRect = initialWindowRect
        initialCornerRadiusPx = with(density) { (28f * currentProgress).dp.toPx() }
        initialScrimAlpha = scrimAlpha

        dragX = 0f
        dragY = 0f

        val targetBounds = targetBoundsProvider()
        val initialProgressVelocity = if (targetBounds != null) {
            val distanceY = targetBounds.center.y - initialWindowRect.center.y
            if (abs(distanceY) > 20f) (initialVelocityY / distanceY).coerceIn(-4f, 12f) else 0f
        } else {
            fallbackTx = currentDx * 1.15f
            fallbackTy = (currentDy + (containerH * 0.45f)).coerceAtLeast(containerH * 0.6f)
            0f
        }

        animationJob = scope.launch {
            withContext(NonCancellable) {
                try {
                    returnProgress.snapTo(0f)
                    val returnSpring = spring<Float>(
                        dampingRatio = 0.88f,
                        stiffness = 380f
                    )

                    returnProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = returnSpring,
                        initialVelocity = initialProgressVelocity
                    ) {
                        // Reveal grid thumbnail underneath slightly before landing (~95% progress)
                        if (value >= 0.95f && !hasRevealedThumbnail) {
                            hasRevealedThumbnail = true
                            onRevealThumbnail()
                        }
                    }
                } finally {
                    if (!hasRevealedThumbnail) {
                        hasRevealedThumbnail = true
                        onRevealThumbnail()
                    }
                    state = DismissState.Dismissed
                    onDismissComplete()
                    reset()
                }
            }
        }
    }

    // Overload for static Rect? (backwards compatibility / unit tests)
    fun animateDismiss(
        initialVelocityX: Float = 0f,
        initialVelocityY: Float = 0f,
        targetBounds: Rect?,
        onDismissComplete: () -> Unit
    ) {
        animateDismiss(
            initialVelocityX = initialVelocityX,
            initialVelocityY = initialVelocityY,
            density = Density(1f),
            targetBoundsProvider = { targetBounds },
            onRevealThumbnail = {},
            onDismissComplete = onDismissComplete
        )
    }

    // Predictive back gesture integration
    fun onPredictiveBackProgress(progress: Float, swipeEdge: Int) {
        if (state == DismissState.SettlingDismissing || state == DismissState.Dismissed) return

        val containerW = containerSize.width.toFloat()
        val containerH = containerSize.height.toFloat()
        val p = progress.coerceIn(0f, 1f)

        val dirX = if (swipeEdge == 0) 1f else -1f
        val driftX = dirX * (containerW * 0.08f * p)
        val driftY = containerH * 0.04f * p

        scope.launch {
            animOffset.snapTo(Offset(driftX, driftY))
            animScale.snapTo(1f - (p * 0.15f))
            animScrimAlpha.snapTo(((1f - p) * (1f - p)).coerceIn(0f, 1f))
        }
    }

    fun onPredictiveBackCancelled() {
        if (state == DismissState.SettlingDismissing || state == DismissState.Dismissed) return
        animateBackToOrigin(0f, 0f)
    }

    fun reset() {
        animationJob?.cancel()
        dragX = 0f
        dragY = 0f
        state = DismissState.Idle
        hasTickedThreshold = false
        hasRevealedThumbnail = false
        targetBoundsProvider = null
        initialWindowRect = Rect.Zero
        initialImageRect = Rect.Zero
        scope.launch {
            animOffset.snapTo(Offset.Zero)
            animScale.snapTo(1f)
            animScrimAlpha.snapTo(1f)
            animContentAlpha.snapTo(1f)
            returnProgress.snapTo(0f)
        }
    }
}

private inline fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

@Composable
fun rememberViewerDismissController(
    haptics: HapticController
): ViewerDismissController {
    val scope = rememberCoroutineScope()
    return remember(haptics) {
        ViewerDismissController(scope, haptics)
    }
}
