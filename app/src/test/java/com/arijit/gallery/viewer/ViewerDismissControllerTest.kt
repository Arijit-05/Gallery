package com.arijit.gallery.viewer

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.arijit.gallery.core.common.HapticController
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImmediateFrameClock : MonotonicFrameClock {
    private var nanoTime = 1_000_000_000L
    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
        nanoTime += 16_000_000L
        return onFrame(nanoTime)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ViewerDismissControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = ImmediateFrameClock()
    private val testScope = TestScope(testDispatcher + clock)
    private val haptics = HapticController()
    private val density = Density(density = 2.75f, fontScale = 1.0f)

    private lateinit var controller: ViewerDismissController

    private val sampleItem = MediaItem(
        id = 101L,
        contentUri = "content://media/external/images/media/101",
        displayName = "IMG_2026.jpg",
        mimeType = "image/jpeg",
        mediaType = MediaType.IMAGE,
        dateTakenMs = 1700000000000L,
        dateAddedSec = 1700000000L,
        dateModifiedSec = 1700000000L,
        sizeBytes = 2048576L,
        width = 3000,
        height = 4000,
        durationMs = 0L,
        bucketId = "camera",
        bucketName = "Camera",
        relativePath = "DCIM/Camera/",
        isFavorite = false,
        orientation = 0
    )

    @Before
    fun setUp() {
        controller = ViewerDismissController(testScope, haptics)
        controller.containerSize = IntSize(width = 1080, height = 2400)
        controller.setActiveMediaItem(sampleItem)
    }

    @Test
    fun initialState_isIdle() {
        assertEquals(DismissState.Idle, controller.state)
        assertFalse(controller.isDragging)
        assertFalse(controller.isDismissing)
        assertEquals(0f, controller.dragX, 0.001f)
        assertEquals(0f, controller.dragY, 0.001f)
        assertEquals(1f, controller.scale, 0.001f)
        assertEquals(1f, controller.scrimAlpha, 0.001f)
        assertEquals(1f, controller.chromeAlpha, 0.001f)
    }

    @Test
    fun onDragStart_transitionsToDragging() {
        controller.onDragStart()
        assertEquals(DismissState.Dragging, controller.state)
        assertTrue(controller.isDragging)
        assertFalse(controller.isDismissing)
    }

    @Test
    fun dragToBottomEdge_lowVelocity_commitsDismiss() = testScope.runTest {
        var dismissCompleted = false
        controller.onDragStart()
        // Drag 1500px down (1500 / 2400 = 0.625 > 0.08 threshold)
        controller.onDrag(deltaX = 20f, deltaY = 1500f)

        // Release at the bottom edge with 0 velocity (finger held still)
        controller.onDragEnd(
            velocityX = 0f,
            velocityY = 0f,
            density = density,
            targetBounds = Rect(100f, 200f, 300f, 400f),
            onDismissComplete = { dismissCompleted = true }
        )

        testScope.advanceUntilIdle()

        // Must commit to dismiss, NEVER spring back
        assertTrue(dismissCompleted)
        assertEquals(DismissState.Idle, controller.state) // reset() called in finally
    }

    @Test
    fun dragCancel_atBottomEdge_commitsDismiss() = testScope.runTest {
        var dismissCompleted = false
        controller.onDragStart()
        // Drag 1200px down past 8% threshold
        controller.onDrag(deltaX = 0f, deltaY = 1200f)

        // Cancellation arrives at the bottom edge (system gesture navigation touch steal)
        controller.onDragCancel(
            velocityX = 0f,
            velocityY = 0f,
            density = density,
            targetBounds = null,
            onDismissComplete = { dismissCompleted = true }
        )

        testScope.advanceUntilIdle()

        // Must commit dismiss even on cancel when past threshold
        assertTrue(dismissCompleted)
    }

    @Test
    fun dragTinyDistance_lowVelocity_springsBackToOrigin() = testScope.runTest {
        var dismissCompleted = false
        controller.onDragStart()
        // Drag only 40px (40 / 2400 = 0.016 <= RESTORE_MAX_FRACTION 0.03) with 0 velocity
        controller.onDrag(deltaX = 0f, deltaY = 40f)

        controller.onDragEnd(
            velocityX = 0f,
            velocityY = 0f,
            density = density,
            targetBounds = null,
            onDismissComplete = { dismissCompleted = true }
        )

        testScope.advanceUntilIdle()

        // Must NOT dismiss; must restore to Idle
        assertFalse(dismissCompleted)
        assertEquals(DismissState.Idle, controller.state)
    }

    @Test
    fun lightSwipe_over8Percent_commitsDismiss() = testScope.runTest {
        var dismissCompleted = false
        controller.onDragStart()
        // Drag 250px (250 / 2400 = 0.104 > DISMISS_DISTANCE_FRACTION 0.08) with 0 velocity
        controller.onDrag(deltaX = 5f, deltaY = 250f)

        controller.onDragEnd(
            velocityX = 0f,
            velocityY = 0f,
            density = density,
            targetBounds = Rect(100f, 100f, 460f, 460f),
            onDismissComplete = { dismissCompleted = true }
        )

        testScope.advanceUntilIdle()

        // Light swipe must dismiss!
        assertTrue(dismissCompleted)
    }

    @Test
    fun tinyQuickFlick_commitsDismiss() = testScope.runTest {
        var dismissCompleted = false
        controller.onDragStart()
        // Tiny drag (50px), but downward velocity > 400 dp/s (approx 1100 px/s)
        controller.onDrag(deltaX = 0f, deltaY = 50f)

        controller.onDragEnd(
            velocityX = 0f,
            velocityY = 1500f,
            density = density,
            targetBounds = null,
            onDismissComplete = { dismissCompleted = true }
        )

        testScope.advanceUntilIdle()

        assertTrue(dismissCompleted)
    }

    @Test
    fun downwardDominantPositiveVelocity_commitsDismiss() = testScope.runTest {
        var dismissCompleted = false
        controller.onDragStart()
        // 90px drag (> 3% restore threshold), downward-dominant, small positive velocity (80 px/s)
        controller.onDrag(deltaX = 10f, deltaY = 90f)

        controller.onDragEnd(
            velocityX = 0f,
            velocityY = 80f,
            density = density,
            targetBounds = null,
            onDismissComplete = { dismissCompleted = true }
        )

        testScope.advanceUntilIdle()

        assertTrue(dismissCompleted)
    }

    @Test
    fun nullTargetBounds_smoothContinuedMotionFallback() = testScope.runTest {
        var dismissCompleted = false
        controller.onDragStart()
        controller.onDrag(deltaX = 10f, deltaY = 1000f)

        // Dismiss with null targetBounds (e.g. thumbnail off-screen)
        controller.onDragEnd(
            velocityX = 0f,
            velocityY = 0f,
            density = density,
            targetBounds = null,
            onDismissComplete = { dismissCompleted = true }
        )

        testScope.advanceUntilIdle()

        assertTrue(dismissCompleted)
    }

    @Test
    fun returnAnimation_revealsThumbnailSlightlyBeforeLanding() = testScope.runTest {
        var thumbnailRevealed = false
        var dismissCompleted = false

        controller.onDragStart()
        controller.onDrag(deltaX = 0f, deltaY = 500f)

        controller.onDragEnd(
            velocityX = 0f,
            velocityY = 0f,
            density = density,
            targetBoundsProvider = { Rect(100f, 200f, 456f, 556f) },
            onRevealThumbnail = { thumbnailRevealed = true },
            onDismissComplete = { dismissCompleted = true }
        )

        testScope.advanceUntilIdle()

        assertTrue(thumbnailRevealed)
        assertTrue(dismissCompleted)
    }

    @Test
    fun onceSettlingDismissing_dragInputsAreIgnored() = testScope.runTest {
        var dismissCompleted = false
        controller.animateDismiss(
            targetBounds = null,
            onDismissComplete = { dismissCompleted = true }
        )

        // Attempting to drag while dismissing must be ignored
        controller.onDragStart()
        assertFalse(controller.isDragging)

        testScope.advanceUntilIdle()
        assertTrue(dismissCompleted)
    }

    @Test
    fun reset_restoresInitialValues() {
        controller.onDragStart()
        controller.onDrag(50f, 500f)
        controller.reset()

        assertEquals(DismissState.Idle, controller.state)
        assertEquals(0f, controller.dragX, 0.001f)
        assertEquals(0f, controller.dragY, 0.001f)
    }
}
