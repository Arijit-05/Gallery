package com.arijit.gallery.photos

import android.content.IntentSender
import androidx.compose.runtime.Immutable
import com.arijit.gallery.core.domain.model.ThemeMode
import com.arijit.gallery.core.domain.model.TimelineDateAnchor
import com.arijit.gallery.core.domain.model.TimelineGranularity
import com.arijit.gallery.core.domain.model.TimelineUiItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Immutable
data class PhotosUiState(
    val gridColumns: Int = 4,
    val granularity: TimelineGranularity = TimelineGranularity.DAY,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val selectedIds: ImmutableSet<Long> = persistentSetOf(),
    val snapshotItems: ImmutableList<TimelineUiItem> = persistentListOf(),
    val dateAnchors: ImmutableList<TimelineDateAnchor> = persistentListOf(),
    val totalMediaCount: Int = 0,
    val albumsForTransfer: ImmutableList<com.arijit.gallery.core.domain.model.Album> = persistentListOf(),
    val pendingIntentSender: IntentSender? = null,
    val statusMessage: String? = null
) {
    val isSelectionMode: Boolean
        get() = selectedIds.isNotEmpty()
}

