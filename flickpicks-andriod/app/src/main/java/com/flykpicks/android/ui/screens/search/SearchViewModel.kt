package com.flykpicks.android.ui.screens.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flykpicks.android.model.MediaItemImpl
import com.flykpicks.android.store.RecommendationSection
import com.flykpicks.android.store.RecommendationsState
import com.flykpicks.android.store.RecommendationsStore
import com.flykpicks.android.store.SearchResults
import com.flykpicks.android.store.SearchStore
import com.flykpicks.android.util.GenreSection
import com.flykpicks.android.util.groupByPrimaryGenre
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row of search results: the titles whose first genre is [GenreSection.genre]. */
typealias SearchSection = GenreSection<MediaItemImpl>

/** Search results by first genre (iOS `SearchView.itemsByGenre`). */
fun searchSections(items: List<MediaItemImpl>): List<SearchSection> =
    groupByPrimaryGenre(items, genre = { it.genre }, title = { it.title })

/**
 * The Discover tab's first screen (blueprint §9.3, iOS `SearchScreenState`): the query and
 * whether to show results or the recommendation rows. The results live in [SearchStore] and the
 * rows in [RecommendationsStore].
 *
 * iOS resets Discover whenever the user leaves the tab. Here leaving the tab pops this screen,
 * so the reset is this ViewModel ending ([onCleared]); the detail stack goes with it.
 *
 * Actions return their [Job] so tests can wait for them; the UI ignores it.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchStore: SearchStore,
    private val recommendationsStore: RecommendationsStore,
) : ViewModel() {

    val results: StateFlow<SearchResults> = searchStore.state
    val recommendations: StateFlow<RecommendationsState> = recommendationsStore.state

    // Compose state rather than part of a StateFlow so typing stays synchronous.
    var query by mutableStateOf("")
        private set

    /** False until the first search, and again once the field is cleared: the screen shows the rows. */
    var hasSearched by mutableStateOf(false)
        private set

    /** A pull to refresh is in flight. */
    var isRefreshing by mutableStateOf(false)
        private set

    private var searchJob: Job? = null

    /** Emptying the field goes back to the recommendation rows (iOS `updateQuery`). */
    fun updateQuery(text: String) {
        query = text
        if (text.isEmpty()) showRecommendations()
    }

    /** Searches for the trimmed query; a blank one goes back to the rows. Replaces any search in flight. */
    fun search(): Job? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            showRecommendations()
            return null
        }
        searchJob?.cancel()
        hasSearched = true
        return viewModelScope.launch { searchStore.search(trimmed) }.also { searchJob = it }
    }

    /** Every time the screen comes into view, as iOS's `.task` does. Fetches only the first time. */
    fun loadRecommendations(): Job = viewModelScope.launch { recommendationsStore.loadIfNeeded() }

    fun refreshRecommendations(): Job = viewModelScope.launch {
        isRefreshing = true
        try {
            recommendationsStore.refresh()
        } finally {
            isRefreshing = false
        }
    }

    fun retrySection(section: RecommendationSection): Job = recommendationsStore.refreshSection(section)

    override fun onCleared() {
        searchStore.clear()
    }

    private fun showRecommendations() {
        searchJob?.cancel()
        searchJob = null
        searchStore.clear()
        hasSearched = false
    }
}
