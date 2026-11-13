package com.arijit.gallery.core.presentation

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveGridInfo(
    val gridState: LazyGridState,
    val getGridBoundsInRoot: () -> Rect?
)

/**
 * Tracks root-coordinate bounds of visible media items in grids (PhotosScreen, AlbumDetailScreen)
 * to orchestrate seamless, zero-snap shared element return animations for the viewer.
 *
 * Supports live bounds resolution from active [LazyGridState]s so flinging or scrolling grids
 * always resolve up-to-date target cell bounds on every frame without relying on stale cached values.
 */
@Singleton
class ItemBoundsRegistry @Inject constructor() {
    private val boundsMap = ConcurrentHashMap<Long, Rect>()
    private val activeGrids = CopyOnWriteArrayList<ActiveGridInfo>()

    var hiddenThumbnailId by mutableStateOf<Long?>(null)

    fun registerGrid(gridState: LazyGridState, getGridBoundsInRoot: () -> Rect?) {
        activeGrids.add(ActiveGridInfo(gridState, getGridBoundsInRoot))
    }

    fun unregisterGrid(gridState: LazyGridState) {
        activeGrids.removeAll { it.gridState === gridState }
    }

    fun registerBounds(mediaId: Long, boundsInRoot: Rect) {
        boundsMap[mediaId] = boundsInRoot
    }

    fun unregisterBounds(mediaId: Long) {
        boundsMap.remove(mediaId)
    }

    /**
     * Resolves the actual live root bounds of the cell for [mediaId].
     * First checks live [LazyGridState.layoutInfo.visibleItemsInfo] across active grids.
     * If the cell is visible, computes its exact bounds relative to the grid container and
     * clips to the grid viewport so the return never targets an area off-screen.
     * Falls back to [boundsMap] if the grid is not actively registered.
     */
    fun getLiveBounds(mediaId: Long): Rect? {
        // 1. Check live active grids first
        for (grid in activeGrids) {
            val gridBounds = grid.getGridBoundsInRoot() ?: continue
            val visibleItem = grid.gridState.layoutInfo.visibleItemsInfo.firstOrNull {
                it.key == "media_$mediaId" || it.key == mediaId
            }
            if (visibleItem != null) {
                val itemLeft = gridBounds.left + visibleItem.offset.x
                val itemTop = gridBounds.top + visibleItem.offset.y
                val itemRight = itemLeft + visibleItem.size.width
                val itemBottom = itemTop + visibleItem.size.height
                val cellRect = Rect(itemLeft, itemTop, itemRight, itemBottom)

                // Ensure it does not exceed the grid container bounds
                val clippedRect = Rect(
                    left = maxOf(cellRect.left, gridBounds.left),
                    top = maxOf(cellRect.top, gridBounds.top),
                    right = minOf(cellRect.right, gridBounds.right),
                    bottom = minOf(cellRect.bottom, gridBounds.bottom)
                )
                // If cell is actually visible inside the grid viewport
                if (clippedRect.width > 10f && clippedRect.height > 10f) {
                    return cellRect
                } else {
                    // Cell scrolled completely outside grid viewport
                    return null
                }
            }
        }

        // 2. Fall back to boundsMap
        return boundsMap[mediaId]
    }

    fun getBounds(mediaId: Long): Rect? = getLiveBounds(mediaId)

    fun hideThumbnail(mediaId: Long?) {
        hiddenThumbnailId = mediaId
    }

    fun showAllThumbnails() {
        hiddenThumbnailId = null
    }

    fun clear() {
        boundsMap.clear()
        activeGrids.clear()
        hiddenThumbnailId = null
    }
}

val LocalItemBoundsRegistry = staticCompositionLocalOf { ItemBoundsRegistry() }

@Composable
fun rememberItemBoundsRegistry(): ItemBoundsRegistry {
    return remember { ItemBoundsRegistry() }
}
