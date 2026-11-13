package com.arijit.gallery.viewer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.arijit.gallery.core.designsystem.GalleryTheme
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.MediaType
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test

class ViewerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun viewerScreen_displaysTopAndBottomActionBars() {
        val sample = MediaItem(
            id = 55L,
            contentUri = "content://media/external/images/media/55",
            displayName = "IMG_55.jpg",
            mimeType = "image/jpeg",
            mediaType = MediaType.IMAGE,
            dateTakenMs = 1_762_944_000_000L,
            dateAddedSec = 1_762_944_000L,
            dateModifiedSec = 1_762_944_000L,
            sizeBytes = 2048L,
            width = 4000,
            height = 3000,
            durationMs = 0L,
            bucketId = "1",
            bucketName = "Camera",
            relativePath = "DCIM/Camera/",
            isFavorite = true,
            orientation = 0
        )

        composeRule.setContent {
            GalleryTheme {
                ViewerScreen(
                    uiState = ViewerUiState(
                        items = persistentListOf(sample),
                        initialPageIndex = 0,
                        isLoading = false
                    ),
                    onBack = {},
                    onToggleImmersive = {},
                    onToggleSlideshow = {},
                    onStopSlideshow = {},
                    onToggleFavorite = {},
                    onTrashItem = {},
                    onRenameItem = { _, _ -> },
                    onCopyToAlbum = { _, _ -> },
                    onLoadDetails = {},
                    onClearDetails = {},
                    onSaveVideoPosition = { _, _ -> },
                    resolveMotionPhotoUri = { null },
                    onIntentSenderFinished = {},
                    onConsumeStatusMessage = {}
                )
            }
        }

        composeRule.onNodeWithText("Camera").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Details").assertIsDisplayed()
        composeRule.onNodeWithText("Share").assertIsDisplayed()
        composeRule.onNodeWithText("Favorite").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
    }
}

