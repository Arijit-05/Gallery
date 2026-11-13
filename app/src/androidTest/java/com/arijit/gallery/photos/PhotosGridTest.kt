package com.arijit.gallery.photos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.paging.PagingData
import com.arijit.gallery.core.designsystem.GalleryTheme
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.MediaType
import com.arijit.gallery.core.domain.model.TimelineUiItem
import com.arijit.gallery.core.permissions.MediaAccessLevel
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class PhotosGridTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun photosScreen_displaysSnapshotHeaderAndItemCount() {
        val sample = MediaItem(
            id = 101L,
            contentUri = "content://media/external/images/media/101",
            displayName = "IMG_101.jpg",
            mimeType = "image/jpeg",
            mediaType = MediaType.VIDEO,
            dateTakenMs = 1_762_944_000_000L,
            dateAddedSec = 1_762_944_000L,
            dateModifiedSec = 1_762_944_000L,
            sizeBytes = 4096L,
            width = 1920,
            height = 1080,
            durationMs = 14_000L,
            bucketId = "1",
            bucketName = "Camera",
            relativePath = "DCIM/Camera/",
            isFavorite = true,
            orientation = 0
        )

        val snapshot = persistentListOf<TimelineUiItem>(
            TimelineUiItem.Header(groupKey = "2025-11-12", title = "Nov 12, 2025", timestampMs = 1_762_944_000_000L),
            TimelineUiItem.Media(item = sample, groupKey = "2025-11-12")
        )

        composeRule.setContent {
            val pagingItems = flowOf(PagingData.from(snapshot.toList())).collectAsLazyPagingItems()
            GalleryTheme {
                PhotosScreen(
                    uiState = PhotosUiState(
                        snapshotItems = snapshot,
                        totalMediaCount = 1
                    ),
                    pagingItems = pagingItems,
                    accessLevel = MediaAccessLevel.FULL,
                    onRequestFullAccess = {},
                    onSetGranularity = {},
                    onSetThemeMode = {},
                    onPinchZoomStep = {},
                    onToggleSelection = {},
                    onSelectIds = {},
                    onClearSelection = {},
                    onToggleSelectGroup = {},
                    onFavoriteSelected = {},
                    onTrashSelected = {},
                    onIntentSenderFinished = {},
                    onConsumeStatusMessage = {},
                    onMediaClick = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Photos").assertIsDisplayed()
        composeRule.onNodeWithText("1 items").assertIsDisplayed()
        composeRule.onNodeWithText("Nov 12, 2025").assertIsDisplayed()
        composeRule.onNodeWithText("0:14").assertIsDisplayed()
    }
}

