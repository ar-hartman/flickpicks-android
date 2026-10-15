package com.flykpicks.android.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flykpicks.android.api.endpoints.FlykSource
import com.flykpicks.android.model.MediaItemImpl
import com.flykpicks.android.ui.components.AddToLibrarySheet
import com.flykpicks.android.ui.components.DecoButton
import com.flykpicks.android.ui.components.DetailHeroBanner
import com.flykpicks.android.ui.components.GroupedDivider
import com.flykpicks.android.ui.components.LoadErrorNotice
import com.flykpicks.android.ui.components.PreviewMediaItems
import com.flykpicks.android.ui.components.TopBandDefaults
import com.flykpicks.android.ui.components.TopBandWithBack
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.FlykType
import com.flykpicks.android.ui.theme.SerifFamily

/**
 * A title opened from Discover (blueprint §10.6, iOS `SearchItemDetailView`): backdrop, title,
 * overview and "Add To Inventory". A series' seasons are offered in the add sheet.
 */
@Composable
fun SearchItemDetailScreen(viewModel: SearchItemDetailViewModel, onBack: () -> Unit) {
    val library by viewModel.library.collectAsStateWithLifecycle()

    SearchItemDetailContent(
        item = viewModel.item,
        source = viewModel.source,
        inLibrary = library.items(viewModel.flyksId).isNotEmpty(),
        loadError = viewModel.loadError,
        onBack = onBack,
        onRetry = { viewModel.retry() },
        onAdd = viewModel::openAddSheet,
    )

    val item = viewModel.item
    val sheet = viewModel.addSheet
    if (item != null && sheet != null) {
        AddToLibrarySheet(
            title = item.title,
            posterUrl = item.posterUrl,
            flyksId = item.flyksId,
            state = sheet,
            seasons = viewModel.seasons,
            seasonsLoading = viewModel.seasonsLoading,
            onEdit = viewModel::editAddSheet,
            onSave = { viewModel.saveToLibrary() },
            onCancel = viewModel::cancelAddSheet,
        )
    }
}

@Composable
internal fun SearchItemDetailContent(
    item: MediaItemImpl?,
    source: String,
    inLibrary: Boolean,
    loadError: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAdd: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(FlykColors.GroupedBackground)) {
        when {
            item != null -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(top = TopBandDefaults.ContentTopPadding, bottom = 24.dp),
            ) {
                DetailHeroBanner(item.flyksId, source = source)
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TitleSummary(item, inLibrary)
                    GroupedDivider(Modifier.padding(vertical = 6.dp))
                    Text("Overview:", Modifier.semantics { heading() }, style = FlykType.Headline, color = FlykColors.InkMuted)
                    Text(item.overview, style = FlykType.Body, color = FlykColors.Ink)
                    GroupedDivider(Modifier.padding(vertical = 6.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        DecoButton(onClick = onAdd) { Text("Add To Inventory") }
                    }
                }
            }
            // Only after process death, when the title had to come from the server and didn't.
            loadError != null ->
                LoadErrorNotice(loadError, onRetry, Modifier.statusBarsPadding().padding(top = TopBandDefaults.ContentTopPadding))
            else -> CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(28.dp),
                color = FlykColors.InkMuted,
                strokeWidth = 2.5.dp,
            )
        }
        TopBandWithBack(onBack)
    }
}

@Composable
private fun TitleSummary(item: MediaItemImpl, inLibrary: Boolean) {
    Text(
        item.title,
        modifier = Modifier.semantics { heading() },
        style = FlykType.LargeTitle.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Bold),
        color = FlykColors.Ink,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    // iOS shows the full release date here, not just the year.
    Text(
        listOf(item.releaseDate, item.genre).filter { it.isNotBlank() }.joinToString(" • "),
        style = FlykType.Headline,
        color = FlykColors.InkMuted,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(item.mediaType.displayName)
        item.season?.let { Chip("Season $it") }
        // Not on iOS, which gives no sign that the title is already owned.
        if (inLibrary) Chip("In Your Library", FlykColors.Brass, check = true)
    }
}

@Composable
private fun Chip(text: String, color: Color = FlykColors.Ink, check: Boolean = false) {
    Row(
        Modifier.clip(CircleShape).background(FlykColors.GroupedCard).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (check) Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = FlykType.Subheadline.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}

@Preview(device = "spec:width=411dp,height=891dp")
@Composable
private fun SearchItemDetailPreview() {
    FlykPicksTheme {
        SearchItemDetailContent(
            item = PreviewMediaItems.first().copy(
                genre = "Mystery • Thriller",
                overview = "Pulp novelist Holly Martins arrives in post-war Vienna to find that his friend Harry Lime has died.",
            ),
            source = FlykSource.SEARCH_RESULTS, inLibrary = true, loadError = null, onBack = {}, onRetry = {}, onAdd = {},
        )
    }
}
