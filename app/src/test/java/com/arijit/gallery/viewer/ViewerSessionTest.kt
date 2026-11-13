package com.arijit.gallery.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.MediaType
import com.arijit.gallery.core.domain.model.SortOrder
import com.arijit.gallery.core.domain.model.ThemeMode
import com.arijit.gallery.core.domain.model.TimelineDateAnchor
import com.arijit.gallery.core.domain.model.TimelineGranularity
import com.arijit.gallery.core.domain.model.UserSettings
import com.arijit.gallery.core.domain.repository.MediaRepository
import com.arijit.gallery.core.domain.repository.SettingsRepository
import com.arijit.gallery.core.media.MediaDetailsDataSource
import com.arijit.gallery.core.media.MediaMutationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeMediaRepository(
    var items: List<MediaItem> = emptyList()
) : MediaRepository {
    override fun observeMediaChanges(): Flow<Unit> = emptyFlow()
    override fun notifyMediaChanged() {}
    override fun getTimelinePagingFlow(sortOrder: SortOrder, hiddenBuckets: Set<String>): Flow<PagingData<MediaItem>> = emptyFlow()
    override fun getCachedSnapshot(): Flow<List<MediaItem>> = flowOf(items)
    override suspend fun refreshSnapshot(sortOrder: SortOrder, hiddenBuckets: Set<String>) {}
    override suspend fun getTimelineDateAnchors(granularity: TimelineGranularity, sortOrder: SortOrder, hiddenBuckets: Set<String>): List<TimelineDateAnchor> = emptyList()
    override suspend fun getMediaIdsForGroup(groupKey: String, granularity: TimelineGranularity, hiddenBuckets: Set<String>): List<Long> = emptyList()
    override suspend fun getMediaCount(hiddenBuckets: Set<String>): Int = items.size
    override suspend fun getAllMedia(sortOrder: SortOrder, hiddenBuckets: Set<String>): List<MediaItem> = items
    override fun getCachedMediaIfFresh(sortOrder: SortOrder, hiddenBuckets: Set<String>): List<MediaItem> = items
    override suspend fun getMediaByIds(ids: Collection<Long>): List<MediaItem> = items.filter { it.id in ids }
}

class FakeSettingsRepository : SettingsRepository {
    override val userSettings: Flow<UserSettings> = flowOf(UserSettings())
    override suspend fun setThemeMode(mode: ThemeMode) {}
    override suspend fun setDynamicColor(enabled: Boolean) {}
    override suspend fun setGridColumns(columns: Int) {}
    override suspend fun setTimelineGranularity(granularity: TimelineGranularity) {}
    override suspend fun setSortOrder(sortOrder: SortOrder) {}
    override suspend fun setHiddenBuckets(buckets: Set<String>) {}
    override suspend fun setVideoAutoplay(enabled: Boolean) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class ViewerSessionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val photoA = MediaItem(
        id = 101L,
        contentUri = "content://media/external/images/media/101",
        displayName = "Photo_A.jpg",
        mimeType = "image/jpeg",
        mediaType = MediaType.IMAGE,
        dateTakenMs = 1700000001000L,
        dateAddedSec = 1700000001L,
        dateModifiedSec = 1700000001L,
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

    private val photoB = MediaItem(
        id = 102L,
        contentUri = "content://media/external/images/media/102",
        displayName = "Photo_B.jpg",
        mimeType = "image/jpeg",
        mediaType = MediaType.IMAGE,
        dateTakenMs = 1700000002000L,
        dateAddedSec = 1700000002L,
        dateModifiedSec = 1700000002L,
        sizeBytes = 2048576L,
        width = 4000,
        height = 3000,
        durationMs = 0L,
        bucketId = "camera",
        bucketName = "Camera",
        relativePath = "DCIM/Camera/",
        isFavorite = true,
        orientation = 0
    )

    private val photoC = MediaItem(
        id = 103L,
        contentUri = "content://media/external/images/media/103",
        displayName = "Photo_C.jpg",
        mimeType = "image/jpeg",
        mediaType = MediaType.IMAGE,
        dateTakenMs = 1700000003000L,
        dateAddedSec = 1700000003L,
        dateModifiedSec = 1700000003L,
        sizeBytes = 3048576L,
        width = 3000,
        height = 3000,
        durationMs = 0L,
        bucketId = "camera",
        bucketName = "Camera",
        relativePath = "DCIM/Camera/",
        isFavorite = false,
        orientation = 0
    )

    private val allMedia = listOf(photoA, photoB, photoC)

    private lateinit var mediaRepository: FakeMediaRepository
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var launchCache: ViewerLaunchCache
    private lateinit var viewModel: ViewerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mediaRepository = FakeMediaRepository(allMedia)
        settingsRepository = FakeSettingsRepository()
        launchCache = ViewerLaunchCache()

        viewModel = ViewerViewModel(
            savedStateHandle = SavedStateHandle(),
            mediaRepository = mediaRepository,
            settingsRepository = settingsRepository,
            detailsDataSource = MediaDetailsDataSource(),
            mutationManager = MediaMutationManager(),
            motionPhotoExtractor = MotionPhotoExtractor(),
            launchCache = launchCache
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasNoActiveSession() {
        assertNull(viewModel.session.value)
        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertNull(viewModel.uiState.value.heroItem)
    }

    @Test
    fun openSession_createsFreshSessionWithNewId_andEmitsTappedPhotoImmediately() {
        val sessionA = viewModel.openSession(photoA, thumbnailSizePx = 512)

        // Session must be non-null immediately
        val currentSession = viewModel.session.value
        assertNotNull(currentSession)
        assertEquals(sessionA.sessionId, currentSession?.sessionId)
        assertEquals(photoA.id, currentSession?.mediaId)
        assertEquals(photoA, currentSession?.heroItem)
        assertEquals(0, currentSession?.initialIndex)
        assertEquals("ms_thumb_101_1700000001_512", currentSession?.heroThumbnailKey)

        // UiState must be updated in the exact same atomic pass
        val uiState = viewModel.uiState.value
        assertEquals(photoA, uiState.heroItem)
        assertEquals(0, uiState.initialPageIndex)
        assertEquals(3, uiState.items.size)
        assertEquals(photoA.id, uiState.items[0].id)
    }

    @Test
    fun closeSession_completelyResetsSessionAndUiState() {
        viewModel.openSession(photoA, thumbnailSizePx = 512)
        assertNotNull(viewModel.session.value)

        viewModel.closeSession()

        // Everything must be wiped on dismiss
        assertNull(viewModel.session.value)
        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertNull(viewModel.uiState.value.heroItem)
    }

    @Test
    fun openDifferentPhoto_hasBrandNewSessionId_andShowsNewPhotoOnFirstFrame() {
        // 1. Open photo A and dismiss
        val sessionA = viewModel.openSession(photoA, thumbnailSizePx = 512)
        assertEquals(photoA.id, viewModel.session.value?.mediaId)
        viewModel.closeSession()
        assertNull(viewModel.session.value)

        // 2. Open photo B
        val sessionB = viewModel.openSession(photoB, thumbnailSizePx = 512)

        // Must have a brand new sessionId to force key(session.sessionId) recreation
        assertNotEquals(sessionA.sessionId, sessionB.sessionId)
        assertTrue(sessionB.sessionId > sessionA.sessionId)

        // Must immediately emit photo B
        val currentSession = viewModel.session.value
        assertNotNull(currentSession)
        assertEquals(photoB.id, currentSession?.mediaId)
        assertEquals(photoB, currentSession?.heroItem)
        assertEquals(1, currentSession?.initialIndex) // Index of photoB in allMedia

        val uiState = viewModel.uiState.value
        assertEquals(photoB, uiState.heroItem)
        assertEquals(1, uiState.initialPageIndex)
        assertEquals(photoB.id, uiState.items[1].id)
    }

    @Test
    fun swipingPagerThenDismissing_doesNotAffectNextOpen() {
        // Open photo A (index 0)
        viewModel.openSession(photoA, thumbnailSizePx = 512)
        // User swipes to Photo C in the pager, then dismisses
        viewModel.closeSession()

        // Next open is Photo B (index 1)
        val sessionB = viewModel.openSession(photoB, thumbnailSizePx = 512)

        // Session B must initialize directly to Photo B (index 1), NEVER Photo C or Photo A!
        assertEquals(photoB.id, sessionB.mediaId)
        assertEquals(1, sessionB.initialIndex)
        assertEquals(photoB, viewModel.uiState.value.heroItem)
        assertEquals(1, viewModel.uiState.value.initialPageIndex)
    }

    @Test
    fun rapidOpenDismissSequence_10Times_alwaysShowsTappedPhoto() {
        val testPhotos = listOf(photoA, photoB, photoC)
        var lastSessionId = 0L

        for (i in 0 until 10) {
            val targetPhoto = testPhotos[i % testPhotos.size]
            val session = viewModel.openSession(targetPhoto, thumbnailSizePx = 512)

            // Session ID must strictly increase
            assertTrue(session.sessionId > lastSessionId)
            lastSessionId = session.sessionId

            // Session and UI state must match targetPhoto with zero leftover from previous iteration
            assertEquals(targetPhoto.id, session.mediaId)
            assertEquals(targetPhoto, session.heroItem)
            assertEquals(i % testPhotos.size, session.initialIndex)

            val currentUi = viewModel.uiState.value
            assertEquals(targetPhoto, currentUi.heroItem)
            assertEquals(i % testPhotos.size, currentUi.initialPageIndex)

            // Dismiss
            viewModel.closeSession()
            assertNull(viewModel.session.value)
            assertTrue(viewModel.uiState.value.items.isEmpty())
        }
    }
}

