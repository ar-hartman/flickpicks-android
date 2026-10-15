package com.flykpicks.android.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleRight
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flykpicks.android.api.endpoints.FlykSource
import com.flykpicks.android.model.MediaItemImpl
import com.flykpicks.android.store.RecommendationSection
import com.flykpicks.android.store.RecommendationSectionState
import com.flykpicks.android.store.RecommendationsState
import com.flykpicks.android.store.SearchResults
import com.flykpicks.android.ui.components.DecoSearchField
import com.flykpicks.android.ui.components.EmptyNotice
import com.flykpicks.android.ui.components.LoadErrorNotice
import com.flykpicks.android.ui.components.PreviewMediaItems
import com.flykpicks.android.ui.components.SearchGenreRow
import com.flykpicks.android.ui.components.TopBand
import com.flykpicks.android.ui.components.TopBandDefaults
import com.flykpicks.android.ui.components.WordmarkHeader
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.FlykType
import com.flykpicks.android.ui.theme.textileBackground

/**
 * The Discover tab (blueprint §10.5, iOS `SearchView`): recommendation rows until the user
 * searches, then the results by genre. [onOpenTitle] takes the title and the `X-Flyk-Source`
 * of the row it was tapped in.
 */
@Composable
fun SearchScreen(viewModel: SearchViewModel, onOpenTitle: (MediaItemImpl, String) -> Unit) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadRecommendations() }

    SearchContent(
        query = viewModel.query,
        onQueryChange = viewModel::updateQuery,
        onSearch = { viewModel.search() },
        hasSearched = viewModel.hasSearched,
        results = results,
        recommendations = recommendations,
        isRefreshing = viewModel.isRefreshing,
        onRefresh = { viewModel.refreshRecommendations() },
        onRetrySection = { viewModel.retrySection(it) },
        onOpenTitle = onOpenTitle,
    )
}

/** The wordmark and search field stay put; the rows scroll beneath them. */
@Composable
internal fun SearchContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    hasSearched: Boolean,
    results: SearchResults,
    recommendations: RecommendationsState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onRetrySection: (RecommendationSection) -> Unit,
    onOpenTitle: (MediaItemImpl, String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onSearch()
    }

    Box(Modifier.fillMaxSize().textileBackground()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = TopBandDefaults.ContentTopPadding)) {
            WordmarkHeader(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp))
            // The barcode scanner button joins these in Phase 12.
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DecoSearchField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = "Search",
                    onSearch = submit,
                    modifier = Modifier.weight(1f).padding(end = 2.dp),
                )
                IconButton(onClick = submit) {
                    Icon(Icons.Filled.ArrowCircleRight, contentDescription = "Search", tint = FlykColors.Brass, modifier = Modifier.size(28.dp))
                }
            }

            when {
                results.isLoading -> Searching()
                !hasSearched -> RecommendationsFeed(recommendations, isRefreshing, onRefresh, onRetrySection, onOpenTitle)
                // iOS shows "No results found" for a failed search too (ios-gaps.md G5).
                results.errorMessage != null -> LoadErrorNotice(results.errorMessage, onRetry = submit)
                results.items.isEmpty() -> EmptyNotice(Icons.Outlined.SearchOff, "No results found", "Try a different title")
                // A new search starts at the top, as iOS's `.id(searchStore.generation)` does.
                else -> key(results.generation) { SearchResultRows(results.items, onOpenTitle) }
            }
        }
        TopBand()
    }
}

@Composable
private fun ColumnScope.Searching() {
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(28.dp), color = FlykColors.Brass, strokeWidth = 2.5.dp)
            Text("Searching…", style = FlykType.Subheadline, color = FlykColors.Ink.copy(alpha = 0.5f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecommendationsFeed(
    recommendations: RecommendationsState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onRetrySection: (RecommendationSection) -> Unit,
    onOpenTitle: (MediaItemImpl, String) -> Unit,
) {
    val refreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        state = refreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = refreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = FlykColors.Surface,
                color = FlykColors.Brass,
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            items(RecommendationSection.entries, key = { it.name }) { section ->
                RecommendationRow(
                    section = section,
                    state = recommendations.section(section),
                    onRetry = { onRetrySection(section) },
                    onItemClick = { onOpenTitle(it, section.source) },
                )
            }
        }
    }
}

@Composable
private fun SearchResultRows(items: List<MediaItemImpl>, onOpenTitle: (MediaItemImpl, String) -> Unit) {
    val sections = remember(items) { searchSections(items) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        items(sections, key = { it.genre }) { section ->
            SearchGenreRow(section.genre, section.items, onItemClick = { onOpenTitle(it, FlykSource.SEARCH_RESULTS) })
        }
    }
}

@Preview(device = "spec:width=411dp,height=891dp")
@Composable
private fun DiscoverPreview() {
    FlykPicksTheme {
        SearchContent(
            query = "", onQueryChange = {}, onSearch = {}, hasSearched = false, results = SearchResults(),
            recommendations = RecommendationsState(
                RecommendationSection.entries.associateWith {
                    if (it == RecommendationSection.INVENTORY_MOVIES) RecommendationSectionState(isLoading = true)
                    else RecommendationSectionState(items = PreviewMediaItems)
                },
            ),
            isRefreshing = false, onRefresh = {}, onRetrySection = {}, onOpenTitle = { _, _ -> },
        )
    }
}

@Preview(device = "spec:width=411dp,height=891dp")
@Composable
private fun SearchResultsPreview() {
    FlykPicksTheme {
        SearchContent(
            query = "third", onQueryChange = {}, onSearch = {}, hasSearched = true,
            results = SearchResults("third", PreviewMediaItems), recommendations = RecommendationsState(),
            isRefreshing = false, onRefresh = {}, onRetrySection = {}, onOpenTitle = { _, _ -> },
        )
    }
}
