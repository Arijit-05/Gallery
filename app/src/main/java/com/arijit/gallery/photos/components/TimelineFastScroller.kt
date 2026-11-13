package com.arijit.gallery.photos.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.arijit.gallery.core.domain.model.TimelineDateAnchor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TimelineFastScroller(
    gridState: LazyGridState,
    totalItemCount: Int,
    dateAnchors: ImmutableList<TimelineDateAnchor>,
    modifier: Modifier = Modifier
) {
    if (totalItemCount < 40) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var isVisible by remember { mutableStateOf(false) }

    val scrollFraction by remember(totalItemCount) {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val visibleCount = layoutInfo.visibleItemsInfo.size
            val maxFirst = (totalItemCount - visibleCount).coerceAtLeast(1)
            (gridState.firstVisibleItemIndex.toFloat() / maxFirst.toFloat()).coerceIn(0f, 1f)
        }
    }

    val activeFraction = if (isDragging) dragFraction else scrollFraction

    val currentBubbleLabel by remember(dateAnchors, totalItemCount) {
        derivedStateOf {
            if (dateAnchors.isEmpty()) return@derivedStateOf ""
            val targetIdx = (activeFraction * totalItemCount).roundToInt()
            var label = dateAnchors.first().label
            for (anchor in dateAnchors) {
                if (anchor.itemOffset <= targetIdx) {
                    label = anchor.label
                } else {
                    break
                }
            }
            label
        }
    }

    LaunchedEffect(gridState.isScrollInProgress, isDragging) {
        if (gridState.isScrollInProgress || isDragging) {
            isVisible = true
        } else if (isVisible) {
            delay(1100L)
            isVisible = false
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val trackHeightPx = with(density) { (maxHeight - 64.dp).toPx().coerceAtLeast(1f) }
        val thumbSizePx = with(density) { 44.dp.toPx() }
        val usableTrackPx = (trackHeightPx - thumbSizePx).coerceAtLeast(1f)
        val thumbOffsetPx = (activeFraction * usableTrackPx).roundToInt()

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .padding(top = 16.dp, bottom = 48.dp, end = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .fillMaxHeight()
                    .pointerInput(totalItemCount, usableTrackPx) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                val f = ((offset.y - thumbSizePx / 2f) / usableTrackPx).coerceIn(0f, 1f)
                                dragFraction = f
                                val targetIndex = (f * (totalItemCount - 1)).roundToInt().coerceIn(0, (totalItemCount - 1).coerceAtLeast(0))
                                scope.launch { gridState.scrollToItem(targetIndex) }
                            },
                            onVerticalDrag = { change, _ ->
                                change.consume()
                                val f = ((change.position.y - thumbSizePx / 2f) / usableTrackPx).coerceIn(0f, 1f)
                                dragFraction = f
                                val targetIndex = (f * (totalItemCount - 1)).roundToInt().coerceIn(0, (totalItemCount - 1).coerceAtLeast(0))
                                scope.launch { gridState.scrollToItem(targetIndex) }
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false }
                        )
                    }
            ) {
                // Floating Date Bubble when dragging
                if (isDragging && currentBubbleLabel.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset { IntOffset(x = with(density) { (-48).dp.roundToPx() }, y = thumbOffsetPx) }
                            .shadow(6.dp, RoundedCornerShape(20.dp))
                    ) {
                        Text(
                            text = currentBubbleLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }

                // Scrubber thumb pill
                Surface(
                    shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                    color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(x = 0, y = thumbOffsetPx) }
                        .height(44.dp)
                        .width(28.dp)
                        .shadow(4.dp, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.UnfoldMore,
                            contentDescription = "Fast scroll",
                            tint = if (isDragging) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

