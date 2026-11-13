package com.arijit.gallery.albums

import androidx.compose.runtime.Immutable
import com.arijit.gallery.core.domain.model.Album
import com.arijit.gallery.core.domain.model.SystemAlbumSummary
import com.arijit.gallery.core.domain.model.SystemAlbumType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@Immutable
data class AlbumsUiState(
    val systemSummaries: ImmutableMap<SystemAlbumType, SystemAlbumSummary> = persistentMapOf(),
    val trashCount: Int = 0,
    val albums: ImmutableList<Album> = persistentListOf(),
    val isLoading: Boolean = true,
    val statusMessage: String? = null
)

