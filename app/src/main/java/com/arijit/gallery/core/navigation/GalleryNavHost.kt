package com.arijit.gallery.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.PhotoAlbum
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.paging.compose.collectAsLazyPagingItems
import com.arijit.gallery.albums.AlbumDetailScreen
import com.arijit.gallery.albums.AlbumDetailViewModel
import com.arijit.gallery.albums.AlbumsScreen
import com.arijit.gallery.albums.AlbumsViewModel
import com.arijit.gallery.core.common.rememberHapticController
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.permissions.MediaAccessLevel
import com.arijit.gallery.core.presentation.LocalItemBoundsRegistry
import com.arijit.gallery.core.presentation.rememberItemBoundsRegistry
import com.arijit.gallery.memories.MemoriesViewModel
import com.arijit.gallery.memories.MemoryStoryViewerScreen
import com.arijit.gallery.photos.PhotosScreen
import com.arijit.gallery.photos.PhotosViewModel
import com.arijit.gallery.search.SearchScreen
import com.arijit.gallery.search.SearchViewModel
import com.arijit.gallery.settings.SettingsScreen
import com.arijit.gallery.settings.SettingsViewModel
import com.arijit.gallery.trash.TrashScreen
import com.arijit.gallery.trash.TrashViewModel
import com.arijit.gallery.vault.VaultScreen
import com.arijit.gallery.vault.VaultViewerScreen
import com.arijit.gallery.vault.VaultViewModel
import com.arijit.gallery.viewer.ViewerScreen
import com.arijit.gallery.viewer.ViewerViewModel
import kotlinx.coroutines.launch

enum class GalleryTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Photos(
        route = "photos",
        label = "Media",
        selectedIcon = Icons.Rounded.PhotoLibrary,
        unselectedIcon = Icons.Outlined.PhotoLibrary
    ),
    Albums(
        route = "albums",
        label = "Albums",
        selectedIcon = Icons.Rounded.PhotoAlbum,
        unselectedIcon = Icons.Outlined.PhotoAlbum
    ),
    Search(
        route = "search",
        label = "Search",
        selectedIcon = Icons.Rounded.Search,
        unselectedIcon = Icons.Outlined.Search
    )
}

const val ROUTE_MAIN_TABS = "main_tabs"
const val ROUTE_VIEWER = "viewer/{mediaId}"
const val ROUTE_ALBUM_DETAIL = "album_detail/{albumKey}"
const val ROUTE_TRASH = "trash"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_VAULT = "vault"
const val ROUTE_VAULT_VIEWER = "vault_viewer/{vaultItemId}"
const val ROUTE_STORY_VIEWER = "story_viewer/{storyId}"

@Composable
fun GalleryRoot(
    accessLevel: MediaAccessLevel,
    onRequestFullAccess: () -> Unit,
    photosViewModel: PhotosViewModel = hiltViewModel(),
    initialMediaId: Long? = null,
    isPickerMode: Boolean = false,
    allowMultiPick: Boolean = false,
    onPickMediaItems: ((List<MediaItem>) -> Unit)? = null,
    onCancelPicker: (() -> Unit)? = null
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val photosState by photosViewModel.uiState.collectAsStateWithLifecycle()
    val pagingItems = photosViewModel.timelinePagingFlow.collectAsLazyPagingItems()

    val vaultViewModel: VaultViewModel = hiltViewModel()
    val viewerViewModel: ViewerViewModel = hiltViewModel()
    val viewerSession by viewerViewModel.session.collectAsStateWithLifecycle()
    val viewerState by viewerViewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberHapticController()
    val boundsRegistry = rememberItemBoundsRegistry()

    var selectedTab by rememberSaveable { mutableStateOf(GalleryTab.Photos) }
    var visitedTabs by remember { mutableStateOf(setOf(GalleryTab.Photos)) }

    LaunchedEffect(initialMediaId) {
        if (initialMediaId != null && initialMediaId > 0L) {
            viewerViewModel.openSessionById(initialMediaId)
        }
    }

    val isMainTabsRoute = currentDestination?.hierarchy?.any { it.route == ROUTE_MAIN_TABS } == true

    BackHandler(enabled = isMainTabsRoute && selectedTab == GalleryTab.Photos && photosState.isSelectionMode) {
        photosViewModel.clearSelection()
    }

    BackHandler(enabled = isMainTabsRoute && selectedTab != GalleryTab.Photos) {
        selectedTab = GalleryTab.Photos
    }

    val showBottomBar = isMainTabsRoute && !(selectedTab == GalleryTab.Photos && photosState.isSelectionMode)

    CompositionLocalProvider(LocalItemBoundsRegistry provides boundsRegistry) {
        Scaffold { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = ROUTE_MAIN_TABS,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(
                    route = ROUTE_MAIN_TABS,
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None }
                ) {
                    val albumsViewModel: AlbumsViewModel = hiltViewModel()
                    val searchViewModel: SearchViewModel = hiltViewModel()

                    Box(modifier = Modifier.fillMaxSize()) {
                        TabVisibilitySlot(visible = selectedTab == GalleryTab.Photos) {
                            PhotosScreen(
                                uiState = photosState,
                                pagingItems = pagingItems,
                                accessLevel = accessLevel,
                                onRequestFullAccess = onRequestFullAccess,
                                onSetGranularity = photosViewModel::setGranularity,
                                onSetThemeMode = photosViewModel::setThemeMode,
                                onPinchZoomStep = photosViewModel::onPinchZoomStep,
                                onToggleSelection = photosViewModel::toggleSelection,
                                onSelectIds = photosViewModel::selectIds,
                                onClearSelection = photosViewModel::clearSelection,
                                onToggleSelectGroup = photosViewModel::toggleSelectGroup,
                                onFavoriteSelected = photosViewModel::favoriteSelectedItems,
                                onTrashSelected = photosViewModel::trashSelectedItems,
                                onIntentSenderFinished = photosViewModel::onIntentSenderFinished,
                                onConsumeStatusMessage = photosViewModel::consumeStatusMessage,
                                onMediaClick = { item, thumbSizePx ->
                                    if (isPickerMode) {
                                        if (allowMultiPick) {
                                            photosViewModel.toggleSelection(item.id)
                                        } else {
                                            onPickMediaItems?.invoke(listOf(item))
                                        }
                                    } else {
                                        photosViewModel.prepareViewerLaunch(item, thumbSizePx)
                                        viewerViewModel.openSession(item, thumbSizePx)
                                    }
                                },
                                onOpenSettings = {
                                    navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
                                },
                                onCopySelectedToAlbum = photosViewModel::copySelectedItemsToAlbum,
                                onMoveSelectedToAlbum = photosViewModel::moveSelectedItemsToAlbum,
                                onMoveSelectedToVault = {
                                    scope.launch {
                                        val selected = photosViewModel.getSelectedMediaItems()
                                        vaultViewModel.addMediaItemsToVault(selected) {
                                            photosViewModel.clearSelection()
                                        }
                                    }
                                },
                                isPickerMode = isPickerMode,
                                allowMultiPick = allowMultiPick,
                                onConfirmMultiPick = {
                                    scope.launch {
                                        val selected = photosViewModel.getSelectedMediaItems()
                                        onPickMediaItems?.invoke(selected)
                                    }
                                }
                            )
                        }

                        if (GalleryTab.Albums in visitedTabs) {
                            val albumsState by albumsViewModel.uiState.collectAsStateWithLifecycle()
                            TabVisibilitySlot(visible = selectedTab == GalleryTab.Albums) {
                                AlbumsScreen(
                                    uiState = albumsState,
                                    onOpenAlbum = { albumId ->
                                        navController.navigate("album_detail/$albumId") { launchSingleTop = true }
                                    },
                                    onOpenSystemAlbum = { type ->
                                        navController.navigate("album_detail/${type.routeKey}") { launchSingleTop = true }
                                    },
                                    onOpenTrash = {
                                        navController.navigate(ROUTE_TRASH) { launchSingleTop = true }
                                    },
                                    onOpenVault = {
                                        navController.navigate(ROUTE_VAULT) { launchSingleTop = true }
                                    },
                                    onOpenSettings = {
                                        navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
                                    },
                                    onCreateAlbum = albumsViewModel::createAlbum,
                                    onRenameAlbum = albumsViewModel::renameAlbum,
                                    onTogglePin = albumsViewModel::togglePin,
                                    onToggleHideFromTimeline = albumsViewModel::toggleHideFromTimeline,
                                    onMoveOrder = albumsViewModel::moveAlbumOrder,
                                    onClearStatusMessage = albumsViewModel::clearStatusMessage,
                                    isPickerMode = isPickerMode,
                                    onCancelPicker = onCancelPicker
                                )
                            }
                        }

                        if (GalleryTab.Search in visitedTabs) {
                            val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
                            TabVisibilitySlot(visible = selectedTab == GalleryTab.Search) {
                                SearchScreen(
                                    uiState = searchState,
                                    onQueryChange = searchViewModel::updateQuery,
                                    onSubmitSearch = searchViewModel::submitSearch,
                                    onClearQuery = searchViewModel::clearQuery,
                                    onToggleFilter = searchViewModel::toggleFilter,
                                    onSortModeChange = searchViewModel::setSortMode,
                                    onApplyRecentSearch = searchViewModel::applyRecentSearch,
                                    onDeleteRecentSearch = searchViewModel::deleteRecentSearch,
                                    onClearHistory = searchViewModel::clearHistory,
                                    onOpenSettings = {
                                        navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
                                    },
                                    onOpenMedia = { item, thumbSizePx ->
                                        if (isPickerMode) {
                                            onPickMediaItems?.invoke(listOf(item))
                                        } else {
                                            searchViewModel.prepareViewerLaunch(item, thumbSizePx)
                                            viewerViewModel.openSession(item, thumbSizePx, searchState.results)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                composable(
                    route = ROUTE_ALBUM_DETAIL,
                    arguments = listOf(navArgument("albumKey") { type = NavType.StringType }),
                    enterTransition = {
                        fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                            slideInHorizontally(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) { it / 5 }
                    },
                    exitTransition = { fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) },
                    popEnterTransition = { fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) },
                    popExitTransition = {
                        fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                            slideOutHorizontally(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) { it / 5 }
                    }
                ) {
                    val detailViewModel: AlbumDetailViewModel = hiltViewModel()
                    val detailState by detailViewModel.uiState.collectAsStateWithLifecycle()
                    AlbumDetailScreen(
                        uiState = detailState,
                        onBack = { navController.popBackStack() },
                        onOpenMedia = { item, thumbSizePx ->
                            detailViewModel.prepareViewerLaunch(item, thumbSizePx)
                            viewerViewModel.openSession(item, thumbSizePx, detailState.items)
                        },
                        onEnterSelection = detailViewModel::enterSelectionWith,
                        onToggleSelection = detailViewModel::toggleSelection,
                        onSelectAll = detailViewModel::selectAll,
                        onClearSelection = detailViewModel::clearSelection,
                        onFavoriteSelected = detailViewModel::favoriteSelected,
                        onTrashSelected = detailViewModel::trashSelected,
                        onCopySelectedToAlbum = detailViewModel::copySelectedToAlbum,
                        onMoveSelectedToAlbum = detailViewModel::moveSelectedToAlbum,
                        onIntentSenderResult = detailViewModel::onIntentSenderResult,
                        onClearStatusMessage = detailViewModel::clearStatusMessage,
                        isPickerMode = isPickerMode,
                        allowMultiPick = allowMultiPick,
                        onPickItems = onPickMediaItems
                    )
                }

                composable(
                    route = ROUTE_TRASH,
                    enterTransition = {
                        fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                            slideInHorizontally(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) { it / 5 }
                    },
                    exitTransition = { fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) },
                    popEnterTransition = { fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) },
                    popExitTransition = {
                        fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                            slideOutHorizontally(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) { it / 5 }
                    }
                ) {
                    val trashViewModel: TrashViewModel = hiltViewModel()
                    val trashState by trashViewModel.uiState.collectAsStateWithLifecycle()
                    TrashScreen(
                        uiState = trashState,
                        onBack = { navController.popBackStack() },
                        onEnterSelection = trashViewModel::enterSelection,
                        onToggleSelection = trashViewModel::toggleSelection,
                        onSelectAll = trashViewModel::selectAll,
                        onClearSelection = trashViewModel::clearSelection,
                        onRestoreSelected = trashViewModel::restoreSelected,
                        onDeleteSelectedPermanently = trashViewModel::deleteSelectedPermanently,
                        onEmptyTrash = trashViewModel::emptyTrash,
                        onIntentSenderResult = trashViewModel::onIntentSenderResult,
                        onClearStatusMessage = trashViewModel::clearStatusMessage
                    )
                }

                composable(
                    route = ROUTE_SETTINGS,
                    enterTransition = {
                        fadeIn(tween(140)) + slideInHorizontally(tween(140)) { it / 6 }
                    },
                    exitTransition = { fadeOut(tween(100)) },
                    popEnterTransition = { fadeIn(tween(100)) },
                    popExitTransition = {
                        fadeOut(tween(100)) + slideOutHorizontally(tween(100)) { it / 6 }
                    }
                ) {
                    val settingsViewModel: SettingsViewModel = hiltViewModel()
                    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
                    SettingsScreen(
                        uiState = settingsState,
                        onBack = { navController.popBackStack() },
                        onSetThemeMode = settingsViewModel::setThemeMode,
                        onSetDynamicColor = settingsViewModel::setDynamicColor,
                        onSetGridColumns = settingsViewModel::setGridColumns,
                        onSetGranularity = settingsViewModel::setTimelineGranularity,
                        onSetSortOrder = settingsViewModel::setSortOrder,
                        onSetVideoAutoplay = settingsViewModel::setVideoAutoplay,
                        onToggleAlbumHidden = settingsViewModel::toggleAlbumHiddenFromTimeline,
                        onOpenTrash = {
                            navController.navigate(ROUTE_TRASH) { launchSingleTop = true }
                        },
                        onOpenVault = {
                            navController.navigate(ROUTE_VAULT) { launchSingleTop = true }
                        },
                        onClearCache = settingsViewModel::clearCaches,
                        onClearStatusMessage = settingsViewModel::clearStatusMessage
                    )
                }

                composable(
                    route = ROUTE_VAULT,
                    enterTransition = {
                        fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                            slideInHorizontally(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) { it / 5 }
                    },
                    exitTransition = { fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) },
                    popEnterTransition = { fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) },
                    popExitTransition = {
                        fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                            slideOutHorizontally(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) { it / 5 }
                    }
                ) {
                    val vaultState by vaultViewModel.uiState.collectAsStateWithLifecycle()
                    VaultScreen(
                        uiState = vaultState,
                        viewModel = vaultViewModel,
                        onBack = { navController.popBackStack() },
                        onOpenVaultItem = { itemId ->
                            navController.navigate("vault_viewer/$itemId") { launchSingleTop = true }
                        }
                    )
                }

                composable(
                    route = ROUTE_VAULT_VIEWER,
                    arguments = listOf(navArgument("vaultItemId") { type = NavType.StringType }),
                    enterTransition = { fadeIn(tween(140)) },
                    exitTransition = { fadeOut(tween(100)) }
                ) { backStackEntry ->
                    val itemId = backStackEntry.arguments?.getString("vaultItemId") ?: ""
                    VaultViewerScreen(
                        vaultItemId = itemId,
                        viewModel = vaultViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = ROUTE_STORY_VIEWER,
                    arguments = listOf(navArgument("storyId") { type = NavType.StringType }),
                    enterTransition = { fadeIn(tween(140)) },
                    exitTransition = { fadeOut(tween(100)) }
                ) { backStackEntry ->
                    val memoriesViewModel: MemoriesViewModel = hiltViewModel()
                    val storyId = backStackEntry.arguments?.getString("storyId") ?: ""
                    val currentContext = androidx.compose.ui.platform.LocalContext.current
                    MemoryStoryViewerScreen(
                        storyId = storyId,
                        viewModel = memoriesViewModel,
                        onBack = { navController.popBackStack() },
                        onOpenMediaInViewer = { mediaId ->
                            viewerViewModel.openSessionById(mediaId)
                        },
                        onShareMedia = { mediaId ->
                            scope.launch {
                                val item = photosViewModel.getSelectedMediaItems().find { it.id == mediaId }
                                    ?: memoriesViewModel.getStory(storyId)?.items?.find { it.id == mediaId }
                                if (item != null) {
                                    val sendIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_STREAM, item.contentUri.toUri())
                                        type = item.mimeType.ifBlank { "image/jpeg" }
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    currentContext.startActivity(android.content.Intent.createChooser(sendIntent, "Share"))
                                }
                            }
                        }
                    )
                }
            }

            // ColorOS-inspired floating bottom navigation pill with spring physics
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { it } + fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
                exit = slideOutVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) { it } + fadeOut(spring(stiffness = Spring.StiffnessMediumLow)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, RoundedCornerShape(28.dp))
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0)
                    ) {
                        GalleryTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        haptics.tabSwitch()
                                    }
                                    visitedTabs = visitedTabs + tab
                                    selectedTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // High-performance 120Hz Viewer overlay: sits above the grid and bottom bar,
            // keeping PhotosScreen / AlbumDetailScreen composed and alive underneath.
            viewerSession?.let { session ->
                key(session.sessionId) {
                    ViewerScreen(
                        session = session,
                        uiState = viewerState,
                        onBack = { viewerViewModel.closeSession() },
                        onToggleImmersive = viewerViewModel::toggleImmersive,
                        onToggleSlideshow = viewerViewModel::toggleSlideshow,
                        onStopSlideshow = viewerViewModel::stopSlideshow,
                        onToggleFavorite = viewerViewModel::toggleFavorite,
                        onTrashItem = viewerViewModel::trashItem,
                        onRenameItem = viewerViewModel::renameItem,
                        onCopyToAlbum = viewerViewModel::copyToAlbum,
                        onLoadDetails = viewerViewModel::loadDetails,
                        onClearDetails = viewerViewModel::clearDetails,
                        onSaveVideoPosition = viewerViewModel::saveVideoPosition,
                        resolveMotionPhotoUri = viewerViewModel::resolveMotionPhotoVideoUri,
                        onIntentSenderFinished = viewerViewModel::onIntentSenderFinished,
                        onConsumeStatusMessage = viewerViewModel::consumeStatusMessage,
                        onMoveToVault = { item ->
                            vaultViewModel.addMediaItemsToVault(listOf(item)) {
                                viewerViewModel.closeSession()
                            }
                        }
                    )
                }
            }
        }
    }
}
}

@Composable
private fun TabVisibilitySlot(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
            scaleIn(
                initialScale = 0.98f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
    ) {
        content()
    }
}
