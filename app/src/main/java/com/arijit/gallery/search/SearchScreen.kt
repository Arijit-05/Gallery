package com.arijit.gallery.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.arijit.gallery.core.common.rememberHapticController
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.SearchMediaFilter
import com.arijit.gallery.photos.components.MediaGridTile

private data class CategoryTile(
    val title: String,
    val icon: ImageVector,
    val filter: SearchMediaFilter?,
    val query: String? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onClearQuery: () -> Unit,
    onToggleFilter: (SearchMediaFilter) -> Unit,
    onSortModeChange: (SearchSortMode) -> Unit,
    onApplyRecentSearch: (String) -> Unit,
    onDeleteRecentSearch: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMedia: (MediaItem, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val haptics = rememberHapticController()
    val gridState = rememberLazyGridState()
    val emptyScrollState = rememberScrollState()
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val categoryTiles = remember {
        listOf(
            CategoryTile("Screenshots", Icons.Rounded.PhoneAndroid, SearchMediaFilter.SCREENSHOTS),
            CategoryTile("Videos", Icons.Rounded.Videocam, SearchMediaFilter.VIDEOS),
            CategoryTile("Selfies", Icons.Rounded.Face, SearchMediaFilter.SELFIES),
            CategoryTile("GIFs", Icons.Rounded.Gif, SearchMediaFilter.GIFS),
            CategoryTile("Favorites", Icons.Rounded.Favorite, SearchMediaFilter.FAVORITES),
            CategoryTile("Places", Icons.Rounded.LocationOn, null, query = "places")
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp, bottom = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    TextField(
                        value = uiState.query,
                        onValueChange = onQueryChange,
                        placeholder = { Text("Search dates, places, folders, items...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (uiState.query.isNotEmpty()) {
                                IconButton(onClick = {
                                    haptics.click()
                                    onClearQuery()
                                }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                haptics.click()
                                focusManager.clearFocus()
                                onSubmitSearch()
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = {
                        haptics.click()
                        onOpenSettings()
                    }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                }

                // Quick filter chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "all") {
                        FilterChip(
                            selected = uiState.selectedFilter == null,
                            onClick = {
                                haptics.click()
                                if (uiState.selectedFilter != null) {
                                    uiState.selectedFilter?.let { onToggleFilter(it) }
                                }
                            },
                            label = { Text("All") }
                        )
                    }
                    items(SearchMediaFilter.entries, key = { it.name }) { filter ->
                        FilterChip(
                            selected = uiState.selectedFilter == filter,
                            onClick = {
                                haptics.click()
                                onToggleFilter(filter)
                            },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val spacingDp = 2.dp
            val totalSpacingPx = with(density) { (spacingDp * (uiState.gridColumns - 1)).roundToPx() }
            val maxWidthPx = with(density) { maxWidth.roundToPx() }
            val thumbSizePx = ((maxWidthPx - totalSpacingPx) / uiState.gridColumns.coerceAtLeast(1))
                .coerceIn(120, 480)

            when {
                !uiState.hasActiveQuery -> {
                    // Empty Search Dashboard
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(emptyScrollState)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // 1. Recent Searches
                        if (uiState.recentSearches.isNotEmpty()) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Recent searches",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = {
                                        haptics.click()
                                        onClearHistory()
                                    }) {
                                        Text("Clear all")
                                    }
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    uiState.recentSearches.forEach { term ->
                                        AssistChip(
                                            onClick = {
                                                haptics.click()
                                                onApplyRecentSearch(term)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.History,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Remove",
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable {
                                                            haptics.click()
                                                            onDeleteRecentSearch(term)
                                                        }
                                                )
                                            },
                                            label = { Text(term) }
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Category Tiles
                        Column {
                            Text(
                                text = "Categories",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            val chunked = categoryTiles.chunked(3)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (row in chunked) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (tile in row) {
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainer,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        haptics.click()
                                                        if (tile.filter != null) {
                                                            onToggleFilter(tile.filter)
                                                        } else if (tile.query != null) {
                                                            onApplyRecentSearch(tile.query)
                                                        }
                                                    }
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = tile.icon,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        text = tile.title,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Places Browse Row
                        if (uiState.popularPlaces.isNotEmpty()) {
                            Column {
                                Text(
                                    text = "Places",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(uiState.popularPlaces, key = { it.name }) { place ->
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.clickable {
                                                haptics.click()
                                                onApplyRecentSearch(place.name)
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.LocationOn,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "${place.name} (${place.count})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Years Browse Row
                        if (uiState.availableYears.isNotEmpty()) {
                            Column {
                                Text(
                                    text = "Years",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(uiState.availableYears, key = { it }) { year ->
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.clickable {
                                                haptics.click()
                                                onApplyRecentSearch(year.toString())
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.CalendarToday,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = year.toString(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }

                uiState.isSearching && uiState.results.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.results.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No matching media found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Try a filename, month/year, place, or phrase like 'videos 2024'",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                else -> {
                    // Result Grid UI
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (uiState.results.size == 1) "1 item found" else "${uiState.results.size} items found",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Sort mode switcher
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.clickable {
                                        haptics.click()
                                        sortMenuExpanded = true
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Sort,
                                            contentDescription = "Sort order",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = uiState.sortMode.label,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false }
                                ) {
                                    SearchSortMode.entries.forEach { mode ->
                                        DropdownMenuItem(
                                            text = { Text(mode.label) },
                                            onClick = {
                                                haptics.click()
                                                onSortModeChange(mode)
                                                sortMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(uiState.gridColumns),
                            horizontalArrangement = Arrangement.spacedBy(spacingDp),
                            verticalArrangement = Arrangement.spacedBy(spacingDp),
                            contentPadding = PaddingValues(bottom = 112.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = uiState.results,
                                key = { it.id }
                            ) { item ->
                                MediaGridTile(
                                    item = item,
                                    thumbnailSizePx = thumbSizePx,
                                    isSelectionMode = false,
                                    isSelected = false,
                                    modifier = Modifier.clickable {
                                        onSubmitSearch()
                                        onOpenMedia(item, thumbSizePx)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
