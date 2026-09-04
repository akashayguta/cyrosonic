package com.example.hunterxmusic.presentation.search

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hunterxmusic.domain.model.Track
import com.example.hunterxmusic.domain.repository.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SearchCategory(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    PLAYLISTS("Playlists")
}

data class SearchState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val recentQueries: List<String> = emptyList(),
    val selectedCategory: SearchCategory = SearchCategory.ALL,
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val nextContinuationToken: String? = null,
    val isLoadingMore: Boolean = false,
    val error: String? = null
)

class SearchViewModel(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _state = mutableStateOf(SearchState())
    val state: State<SearchState> = _state

    private var searchJob: Job? = null

    init {
        loadRecentQueries()
    }

    fun loadRecentQueries() {
        viewModelScope.launch {
            try {
                val recents = musicRepository.getSearchSuggestions("")
                _state.value = _state.value.copy(recentQueries = recents)
            } catch (_: Exception) { }
        }
    }

    fun onQueryChanged(query: String) {
        _state.value = _state.value.copy(query = query)

        if (query.isBlank()) {
            _state.value = _state.value.copy(
                results = emptyList(),
                suggestions = emptyList(),
                hasSearched = false,
                nextContinuationToken = null,
                isLoadingMore = false
            )
            loadRecentQueries()
            return
        }

        // Live suggestions
        val suggestions = musicRepository.getSearchSuggestions(query)
        _state.value = _state.value.copy(suggestions = suggestions)

        // Debounced search (180ms for instant snappy results)
        if (query.trim().length >= 2) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                delay(180)
                performSearch(query.trim(), _state.value.selectedCategory)
            }
        }
    }

    fun onCategorySelected(category: SearchCategory) {
        if (_state.value.selectedCategory == category) return
        _state.value = _state.value.copy(selectedCategory = category)
        val q = _state.value.query.trim()
        if (q.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                performSearch(q, category)
            }
        }
    }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            performSearch(q, _state.value.selectedCategory)
        }
    }

    fun onSuggestionClick(suggestion: String) {
        _state.value = _state.value.copy(query = suggestion, suggestions = emptyList())
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            performSearch(suggestion, _state.value.selectedCategory)
        }
    }

    fun searchByMood(mood: String) {
        _state.value = _state.value.copy(query = mood)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            performSearch("$mood songs", SearchCategory.ALL)
        }
    }

    fun clearSearch() {
        _state.value = SearchState(recentQueries = _state.value.recentQueries)
        loadRecentQueries()
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            try {
                musicRepository.clearSearchHistory()
                _state.value = _state.value.copy(recentQueries = emptyList())
            } catch (_: Exception) { }
        }
    }

    fun loadMore() {
        val token = _state.value.nextContinuationToken ?: return
        if (_state.value.isLoadingMore) return

        _state.value = _state.value.copy(isLoadingMore = true)
        viewModelScope.launch {
            try {
                val (newTracks, nextToken) = musicRepository.searchTracksNextPage(token)
                _state.value = _state.value.copy(
                    results = _state.value.results + newTracks,
                    nextContinuationToken = nextToken,
                    isLoadingMore = false
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    private suspend fun performSearch(query: String, category: SearchCategory) {
        _state.value = _state.value.copy(isSearching = true, error = null)
        try {
            val adjustedQuery = when (category) {
                SearchCategory.ALL -> query
                SearchCategory.SONGS -> "$query song"
                SearchCategory.ARTISTS -> "$query artist songs"
                SearchCategory.ALBUMS -> "$query full album"
                SearchCategory.PLAYLISTS -> "$query playlist"
            }
            val (results, token) = musicRepository.searchTracksPaginated(adjustedQuery)
            musicRepository.saveSearchQuery(query)
            _state.value = _state.value.copy(
                results = results,
                hasSearched = true,
                suggestions = emptyList(),
                nextContinuationToken = token,
                isLoadingMore = false,
                error = null
            )
            loadRecentQueries()
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (_: Exception) {
            _state.value = _state.value.copy(
                results = emptyList(),
                hasSearched = true,
                suggestions = emptyList(),
                nextContinuationToken = null,
                isLoadingMore = false,
                error = "Couldn't reach search. Check your connection and tap Retry."
            )
        } finally {
            _state.value = _state.value.copy(isSearching = false)
        }
    }
}
