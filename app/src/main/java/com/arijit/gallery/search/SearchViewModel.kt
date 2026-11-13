package com.arijit.gallery.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.SearchMediaFilter
import com.arijit.gallery.core.domain.model.SortOrder
import com.arijit.gallery.core.domain.repository.AlbumRepository
import com.arijit.gallery.core.domain.repository.MediaRepository
import com.arijit.gallery.core.domain.repository.SettingsRepository
import com.arijit.gallery.viewer.ViewerLaunchCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchSortMode(val label: String) {
    RELEVANCE("Relevance"),
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first")
}

@Immutable
data class SearchUiState(
    val query: String = "",
    val selectedFilter: SearchMediaFilter? = null,
    val sortMode: SearchSortMode = SearchSortMode.RELEVANCE,
    val recentSearches: ImmutableList<String> = persistentListOf(),
    val popularPlaces: ImmutableList<PlaceSummary> = persistentListOf(),
    val availableYears: ImmutableList<Int> = persistentListOf(),
    val results: ImmutableList<MediaItem> = persistentListOf(),
    val isSearching: Boolean = false,
    val hasActiveQuery: Boolean = false,
    val gridColumns: Int = 4,
    val sortOrder: SortOrder = SortOrder.DATE_TAKEN_DESC
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository,
    private val searchIndexManager: SearchIndexManager,
    private val viewerLaunchCache: ViewerLaunchCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            albumRepository.observeRecentSearches().collect { recents ->
                _uiState.update { it.copy(recentSearches = recents.toImmutableList()) }
            }
        }

        viewModelScope.launch {
            settingsRepository.userSettings.collect { settings ->
                _uiState.update {
                    it.copy(
                        gridColumns = settings.gridColumns,
                        sortOrder = settings.sortOrder
                    )
                }
            }
        }

        viewModelScope.launch {
            loadBrowseData()
        }

        viewModelScope.launch {
            combine(
                _uiState.map { Triple(it.query, it.selectedFilter, it.sortMode) }.distinctUntilChanged().debounce(150L),
                _uiState.map { it.sortOrder }.distinctUntilChanged(),
                mediaRepository.observeMediaChanges().onStart { emit(Unit) }
            ) { (query, filter, sortMode), baseSortOrder, _ ->
                val sortOrderToUse = when (sortMode) {
                    SearchSortMode.RELEVANCE -> SortOrder.DATE_TAKEN_DESC
                    SearchSortMode.DATE_DESC -> SortOrder.DATE_TAKEN_DESC
                    SearchSortMode.DATE_ASC -> SortOrder.DATE_TAKEN_ASC
                }
                Triple(query, filter, sortOrderToUse)
            }.collect { (query, filter, sortOrderToUse) ->
                val active = query.isNotBlank() || filter != null
                if (!active) {
                    _uiState.update {
                        it.copy(
                            results = persistentListOf(),
                            isSearching = false,
                            hasActiveQuery = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isSearching = true, hasActiveQuery = true) }
                    val matches = albumRepository.searchMedia(
                        query = query,
                        filter = filter,
                        sortOrder = sortOrderToUse
                    )
                    _uiState.update {
                        it.copy(
                            results = matches.toImmutableList(),
                            isSearching = false,
                            hasActiveQuery = true
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadBrowseData() {
        val places = searchIndexManager.getTopPlaces()
        val years = searchIndexManager.getAvailableYears()
        _uiState.update {
            it.copy(
                popularPlaces = places.toImmutableList(),
                availableYears = years.toImmutableList()
            )
        }
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun toggleFilter(filter: SearchMediaFilter) {
        _uiState.update { state ->
            val next = if (state.selectedFilter == filter) null else filter
            state.copy(selectedFilter = next)
        }
    }

    fun setSortMode(mode: SearchSortMode) {
        _uiState.update { it.copy(sortMode = mode) }
    }

    fun applyRecentSearch(term: String) {
        _uiState.update { it.copy(query = term) }
        submitSearch()
    }

    fun submitSearch() {
        val current = _uiState.value.query.trim()
        if (current.isEmpty()) return
        viewModelScope.launch {
            albumRepository.recordSearch(current)
        }
    }

    fun clearQuery() {
        _uiState.update { it.copy(query = "") }
    }

    fun deleteRecentSearch(term: String) {
        viewModelScope.launch {
            albumRepository.removeRecentSearch(term)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            albumRepository.clearSearchHistory()
        }
    }

    fun prepareViewerLaunch(item: MediaItem, thumbnailSizePx: Int) {
        viewerLaunchCache.put(
            item = item,
            thumbnailSizePx = thumbnailSizePx,
            sortOrder = _uiState.value.sortOrder,
            itemsOverride = _uiState.value.results
        )
    }
}
