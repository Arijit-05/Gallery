package com.arijit.gallery.photos.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.arijit.gallery.core.common.Formatters
import com.arijit.gallery.core.designsystem.HeartRed
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.media.MediaThumbnailRequest
import com.arijit.gallery.core.presentation.LocalItemBoundsRegistry

@Composable
fun MediaGridTile(
    item: MediaItem,
    thumbnailSizePx: Int,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val thumbnailRequest = remember(item.id, item.dateModifiedSec, thumbnailSizePx) {
        MediaThumbnailRequest(
            id = item.id,
            uri = item.contentUri.toUri(),
            dateModifiedSec = item.dateModifiedSec,
            sizePx = thumbnailSizePx
        )
    }

    val durationText = remember(item.durationMs) {
        if (item.isVideo) Formatters.formatDuration(item.durationMs) else ""
    }

    val boundsRegistry = LocalItemBoundsRegistry.current

    DisposableEffect(item.id) {
        onDispose {
            boundsRegistry.unregisterBounds(item.id)
        }
    }

    // Spring-animated selection scale (0.86f when selected, 1f when normal)
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tileSelectionScale"
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .onGloballyPositioned { coordinates ->
                boundsRegistry.registerBounds(item.id, coordinates.boundsInRoot())
            }
    ) {
        AsyncImage(
            model = thumbnailRequest,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (boundsRegistry.hiddenThumbnailId == item.id) 0f else 1f
                    scaleX = selectionScale
                    scaleY = selectionScale
                    clip = isSelected
                    shape = SelectedTileShape
                }
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = selectionScale
                        scaleY = selectionScale
                        clip = true
                        shape = SelectedTileShape
                    }
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            )
        }

        if (item.isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .background(BadgeScrim, BadgeShape)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = durationText,
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 12.sp
                )
            }
        }

        if (item.isFavorite) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = "Favorite",
                tint = HeartRed,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(15.dp)
            )
        }

        if (!isSelectionMode && (item.isGif || item.isMotionPhoto)) {
            Text(
                text = if (item.isGif) "GIF" else "LIVE",
                color = Color.White,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .background(BadgeScrim, SmallBadgeShape)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }

        // Selection circle indicator with enter/exit spring animation
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleIn(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleOut(spring(stiffness = Spring.StiffnessMediumLow)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
        ) {
            val ringColor = if (isSelected) MaterialTheme.colorScheme.primary else UnselectedRingColor
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        clip = true
                        shape = CircleShape
                    }
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else UnselectedFillColor
                    )
                    .border(width = 1.6.dp, color = ringColor, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

private val SelectedTileShape = RoundedCornerShape(12.dp)
private val BadgeShape = RoundedCornerShape(6.dp)
private val SmallBadgeShape = RoundedCornerShape(5.dp)
private val BadgeScrim = Color.Black.copy(alpha = 0.58f)
private val UnselectedRingColor = Color.White.copy(alpha = 0.85f)
private val UnselectedFillColor = Color.Black.copy(alpha = 0.30f)
