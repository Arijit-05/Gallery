package com.arijit.gallery.viewer

import android.content.IntentSender
import androidx.compose.runtime.Immutable
import com.arijit.gallery.core.domain.model.MediaDetails
import com.arijit.gallery.core.domain.model.MediaItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@Immutable
data class ViewerUiState(
    val items: ImmutableList<MediaItem> = persistentListOf(),
    val initialPageIndex: Int = 0,
    val heroItem: MediaItem? = null,
    val heroThumbnailKey: String? = null,
    val thumbnailSizePx: Int = 0,
    val isLoading: Boolean = true,
    val isImmersive: Boolean = false,
    val isSlideshowActive: Boolean = false,
    val details: MediaDetails? = null,
    val isDetailsLoading: Boolean = false,
    val videoPositionsMs: ImmutableMap<Long, Long> = persistentMapOf(),
    val videoAutoplay: Boolean = true,
    val pendingIntentSender: IntentSender? = null,
    val statusMessage: String? = null
)

