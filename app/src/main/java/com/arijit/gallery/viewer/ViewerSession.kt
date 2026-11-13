package com.arijit.gallery.viewer

import androidx.compose.runtime.Immutable
import com.arijit.gallery.core.domain.model.MediaItem
import kotlinx.collections.immutable.ImmutableList

/**
 * Immutable session token representing a single distinct opening of the photo viewer.
 *
 * Every open generates a new, unique [sessionId]. The viewer overlay is composed
 * strictly inside key(session.sessionId), ensuring all Compose state (PagerState,
 * ZoomTransformState, Animatable, Coil painters) is destroyed on close and rebuilt
 * completely fresh on open, eliminating any possibility of displaying stale media from
 * previous sessions.
 */
@Immutable
data class ViewerSession(
    val sessionId: Long,
    val mediaId: Long,
    val initialIndex: Int,
    val heroItem: MediaItem,
    val heroThumbnailKey: String?,
    val thumbnailSizePx: Int,
    val items: ImmutableList<MediaItem>
)

