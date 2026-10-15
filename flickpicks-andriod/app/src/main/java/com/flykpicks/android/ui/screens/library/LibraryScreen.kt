package com.flykpicks.android.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flykpicks.android.model.LibraryItemImpl
import com.flykpicks.android.model.enums.MediaType
import com.flykpicks.android.store.LibraryState
import com.flykpicks.android.ui.components.DecoSearchField
import com.flykpicks.android.ui.components.EmptyNotice
import com.flykpicks.android.ui.components.HeaderStats
import com.flykpicks.android.ui.components.LibraryGenreRow
import com.flykpicks.android.ui.components.LoadErrorNotice
import com.flykpicks.android.ui.components.PreviewLibraryItems
import com.flykpicks.android.ui.components.TopBand
import com.flykpicks.android.ui.components.TopBandDefaults
import com.flykpicks.android.ui.components.WordmarkHeader
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.textileBackground

/** Blueprint §10.7, iOS `LibraryView`. [onOpenTitle] takes the catalog title id. */
@Composable
fun LibraryScreen(viewModel: LibraryViewModel, onOpenTitle: (String) -> Unit) {
    val library by viewModel.library.collectAsStateWithLifecycle()

    // Every time the screen comes back into view, as iOS's `.task` does.
    LaunchedEffect(Unit) { viewModel.load() }

    LibraryContent(
        library = library,
        query = viewModel.query,
        onQueryChange = { viewModel.query = it },
        onRetry = { viewModel.load() },
        onOpenTitle = onOpenTitle,
        onRemove = viewModel::requestRemoval,
    )

    viewModel.pendingRemoval?.let { item ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRemoval,
            title = { Text("Remove this item from your library?") },
            text = { Text("${item.title} will be removed.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRemoval() }) { Text("Remove", color = FlykColors.DangerRed) }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelRemoval) { Text("Cancel") } },
        )
    }

    viewModel.removalError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRemovalError,
            title = { Text("Couldn't remove") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissRemovalError) { Text("OK") } },
        )
    }
}

/** The wordmark, filter field and counts stay put; the genre rows scroll beneath them. */
@Composable
internal fun LibraryContent(
    library: LibraryState,
    query: String,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenTitle: (String) -> Unit,
    onRemove: (LibraryItemImpl) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val sections = remember(library.items, query) { librarySections(library.items, query) }

    Box(Modifier.fillMaxSize().textileBackground()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = TopBandDefaults.ContentTopPadding)) {
            WordmarkHeader(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp))
            DecoSearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = "Search your collection",
                onSearch = { focusManager.clearFocus() },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            )
            // The counts are for the whole library, whatever the filter shows.
            HeaderStats(
                totalTitles = library.items.size,
                films = library.items.count { it.mediaType == MediaType.FILM },
                tvShows = library.items.count { it.mediaType == MediaType.TV },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                when {
                    library.items.isNotEmpty() && sections.isEmpty() -> item(key = "no-match") {
                        EmptyNotice(Icons.Outlined.SearchOff, "No results found", "Try a different title")
                    }
                    library.items.isNotEmpty() -> items(sections, key = { it.genre }) { section ->
                        LibraryGenreRow(
                            title = section.genre,
                            items = section.items,
                            onItemClick = { onOpenTitle(it.titleId) },
                            onRemove = onRemove,
                        )
                    }
                    // Nothing to show yet. One of these at a time: iOS stacks "Your library is
                    // empty" under a failed load (ios-gaps.md G26).
                    library.errorMessage != null -> item(key = "error") {
                        LoadErrorNotice(library.errorMessage, onRetry)
                    }
                    library.hasLoaded -> item(key = "empty") {
                        EmptyNotice(
                            Icons.Outlined.VideoLibrary,
                            "Your library is empty",
                            "Search for a title and add it to your collection.",
                        )
                    }
                    else -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp), color = FlykColors.InkMuted, strokeWidth = 2.5.dp)
                        }
                    }
                }
            }
        }
        TopBand()
    }
}

@Preview(device = "spec:width=411dp,height=891dp")
@Composable
private fun LibraryPreview() {
    FlykPicksTheme {
        LibraryContent(
            library = LibraryState(items = PreviewLibraryItems, hasLoaded = true),
            query = "", onQueryChange = {}, onRetry = {}, onOpenTitle = {}, onRemove = {},
        )
    }
}

@Preview(device = "spec:width=411dp,height=891dp")
@Composable
private fun LibraryEmptyPreview() {
    FlykPicksTheme {
        LibraryContent(
            library = LibraryState(hasLoaded = true),
            query = "", onQueryChange = {}, onRetry = {}, onOpenTitle = {}, onRemove = {},
        )
    }
}
