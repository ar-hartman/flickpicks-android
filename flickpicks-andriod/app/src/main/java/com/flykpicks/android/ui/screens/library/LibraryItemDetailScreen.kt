package com.flykpicks.android.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flykpicks.android.model.LibraryItemImpl
import com.flykpicks.android.model.SeasonSummaryDTO
import com.flykpicks.android.model.enums.ItemCondition
import com.flykpicks.android.model.enums.MediaFormat
import com.flykpicks.android.model.enums.MediaType
import com.flykpicks.android.store.LibraryState
import com.flykpicks.android.ui.components.DetailHeroBanner
import com.flykpicks.android.ui.components.EditLibraryItemSheet
import com.flykpicks.android.ui.components.GroupedCard
import com.flykpicks.android.ui.components.GroupedDivider
import com.flykpicks.android.ui.components.LoadErrorNotice
import com.flykpicks.android.ui.components.TopBandDefaults
import com.flykpicks.android.ui.components.TopBandWithBack
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.FlykType
import com.flykpicks.android.ui.theme.SerifFamily
import com.flykpicks.android.util.DateFormatting
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * One owned title: what it is, the copies of it, and for a series its seasons (iOS
 * `LibraryItemDetailView`; plan Q5). Closes itself when the title has left the library.
 */
@Composable
fun LibraryItemDetailScreen(
    viewModel: LibraryItemDetailViewModel,
    onBack: () -> Unit,
    onOpenSeason: (Int) -> Unit,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()

    // iOS waits on a spinner forever once a title's last copy is gone (ios-gaps.md G27).
    val gone = library.hasLoaded && library.items.none { it.titleId == viewModel.titleId } && !viewModel.isBusy
    LaunchedEffect(gone) { if (gone) onBack() }

    LibraryItemDetailContent(
        titleId = viewModel.titleId,
        library = library,
        seasons = viewModel.seasons,
        seasonsLoading = viewModel.seasonsLoading,
        isUpdating = viewModel.isUpdating,
        onBack = onBack,
        onRetry = { viewModel.load() },
        onEdit = { viewModel.startEditing(it) },
        onOpenSeason = onOpenSeason,
        onAddAllSeasons = { viewModel.addAllSeasons(it) },
    )

    viewModel.edit?.let { edit ->
        EditLibraryItemSheet(
            state = edit.sheet,
            onFormatChange = viewModel::selectFormat,
            onConditionChange = viewModel::selectCondition,
            onSave = { viewModel.saveEdit() },
            onCancel = viewModel::cancelEdit,
            onRemove = { viewModel.removeEdited() },
        )
    }

    viewModel.updateError?.let { message -> SeasonUpdateErrorDialog(message, viewModel::dismissUpdateError) }
}

@Composable
internal fun SeasonUpdateErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Couldn't update seasons") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
internal fun LibraryItemDetailContent(
    titleId: String,
    library: LibraryState,
    seasons: List<SeasonSummaryDTO>,
    seasonsLoading: Boolean,
    isUpdating: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEdit: (LibraryItemImpl) -> Unit,
    onOpenSeason: (Int) -> Unit,
    onAddAllSeasons: (LibraryItemImpl) -> Unit,
) {
    val copies = remember(library.items, titleId) { library.items(titleId) }
    val title = copies.firstOrNull()

    Box(Modifier.fillMaxSize().background(FlykColors.GroupedBackground)) {
        when {
            title != null -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(top = TopBandDefaults.ContentTopPadding, bottom = 24.dp),
            ) {
                DetailHeroBanner(titleId)
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TitleSummary(title)
                    CopiesSection(copies.sortedBy { it.format.displayName }, onEdit)
                    if (title.mediaType == MediaType.TV) {
                        SeasonsSection(
                            seasons = seasons,
                            loading = seasonsLoading,
                            isOwned = { library.isSeasonOwned(it, titleId) },
                            copies = copies,
                            isUpdating = isUpdating,
                            onOpenSeason = onOpenSeason,
                            onAddAllSeasons = onAddAllSeasons,
                        )
                    }
                }
            }
            // Only reachable when the library itself couldn't load, e.g. reopened offline.
            library.errorMessage != null && !library.isLoading ->
                LoadErrorNotice(library.errorMessage, onRetry, Modifier.statusBarsPadding().padding(top = TopBandDefaults.ContentTopPadding))
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
private fun TitleSummary(title: LibraryItemImpl) {
    Text(
        title.title,
        modifier = Modifier.semantics { heading() },
        style = FlykType.LargeTitle.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Bold),
        color = FlykColors.Ink,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        // The year is 0 when the server had no release date.
        listOfNotNull(title.year.takeIf { it > 0 }?.toString(), title.genre).joinToString(" • "),
        style = FlykType.Headline,
        color = FlykColors.InkMuted,
    )
    Text(
        title.mediaType.displayName,
        modifier = Modifier.clip(CircleShape).background(FlykColors.GroupedCard).padding(horizontal = 10.dp, vertical = 6.dp),
        style = FlykType.Subheadline.copy(fontWeight = FontWeight.SemiBold),
        color = FlykColors.Ink,
    )
    GroupedDivider(Modifier.padding(vertical = 6.dp))
    SectionTitle("Overview")
    Text(title.overview, style = FlykType.Body, color = FlykColors.Ink)
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { heading() }, style = FlykType.Headline, color = FlykColors.InkMuted)
}

@Composable
private fun CopiesSection(copies: List<LibraryItemImpl>, onEdit: (LibraryItemImpl) -> Unit) {
    GroupedDivider(Modifier.padding(vertical = 6.dp))
    SectionTitle("Copies")
    GroupedCard(cornerRadius = 8.dp) {
        copies.forEachIndexed { index, copy ->
            CopyRow(copy, onEdit = { onEdit(copy) })
            if (index < copies.lastIndex) GroupedDivider(Modifier.padding(start = 14.dp))
        }
    }
}

@Composable
private fun CopyRow(copy: LibraryItemImpl, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(copy.format.displayName, style = FlykType.Subheadline.copy(fontWeight = FontWeight.SemiBold), color = FlykColors.Ink)
            Text(copy.condition.displayName, style = FlykType.Caption, color = FlykColors.Ink.copy(alpha = 0.55f))
            if (copy.dateAdded != null) {
                Text(
                    "Added ${DateFormatting.displayDate(copy.dateAdded)}",
                    style = FlykType.Caption,
                    color = FlykColors.Ink.copy(alpha = 0.4f),
                )
            }
            // DEFAULT is the server's stand-in for "not appraised".
            if (copy.appraisalSource != "DEFAULT") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocalOffer, contentDescription = null, tint = FlykColors.Brass, modifier = Modifier.size(11.dp))
                    Text(
                        formatUsd(copy.appraisalValue),
                        style = FlykType.Caption.copy(fontWeight = FontWeight.Medium),
                        color = FlykColors.Ink.copy(alpha = 0.7f),
                    )
                    Text("·", style = FlykType.Caption, color = FlykColors.Ink.copy(alpha = 0.3f))
                    Text(appraisalSourceLabel(copy.appraisalSource), style = FlykType.Caption, color = FlykColors.Ink.copy(alpha = 0.45f))
                }
            }
        }
        PillButton("Edit", onEdit, icon = Icons.Outlined.Edit)
    }
}

@Composable
private fun SeasonsSection(
    seasons: List<SeasonSummaryDTO>,
    loading: Boolean,
    isOwned: (Int) -> Boolean,
    copies: List<LibraryItemImpl>,
    isUpdating: Boolean,
    onOpenSeason: (Int) -> Unit,
    onAddAllSeasons: (LibraryItemImpl) -> Unit,
) {
    GroupedDivider(Modifier.padding(vertical = 6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle("Seasons")
        Spacer(Modifier.weight(1f))
        if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = FlykColors.InkMuted, strokeWidth = 2.dp)
    }
    when {
        seasons.isNotEmpty() -> {
            GroupedCard(cornerRadius = 8.dp) {
                seasons.forEachIndexed { index, season ->
                    SeasonRow(season, owned = isOwned(season.seasonNumber), onClick = { onOpenSeason(season.seasonNumber) })
                    if (index < seasons.lastIndex) GroupedDivider()
                }
            }
            if (!seasons.all { isOwned(it.seasonNumber) }) {
                copies.forEach { copy ->
                    Row(
                        Modifier
                            .alpha(if (isUpdating) 0.4f else 1f)
                            .clip(CircleShape)
                            .background(FlykColors.GroupedCard)
                            .clickable(enabled = !isUpdating, role = Role.Button) { onAddAllSeasons(copy) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, tint = FlykColors.Brass, modifier = Modifier.size(15.dp))
                        Text(
                            "Add All Seasons — ${copy.format.displayName}",
                            style = FlykType.Subheadline.copy(fontWeight = FontWeight.SemiBold),
                            color = FlykColors.Brass,
                        )
                    }
                }
            }
        }
        !loading -> Text(
            "Season information unavailable.",
            modifier = Modifier.padding(vertical = 4.dp),
            style = FlykType.Subheadline,
            color = FlykColors.Ink.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun SeasonRow(season: SeasonSummaryDTO, owned: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .alpha(if (owned) 1f else 0.45f)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "Season ${season.seasonNumber}",
                style = FlykType.Subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = FlykColors.Ink,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${season.episodeCount} ep", style = FlykType.Caption, color = FlykColors.Ink.copy(alpha = 0.5f))
                season.airYear?.let { year ->
                    Text("·", style = FlykType.Caption, color = FlykColors.Ink.copy(alpha = 0.3f))
                    Text(year, style = FlykType.Caption, color = FlykColors.Ink.copy(alpha = 0.5f))
                }
            }
        }
        if (owned) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(FlykColors.Brass, CircleShape))
                Text("Owned", style = FlykType.Caption.copy(fontWeight = FontWeight.Medium), color = FlykColors.Brass)
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = FlykColors.Ink.copy(alpha = if (owned) 0.3f else 0.15f),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** The year a season first aired, when the server knows it. */
internal val SeasonSummaryDTO.airYear: String? get() = airDate?.take(4)?.ifEmpty { null }

internal fun formatUsd(amount: Double, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance("USD") }.format(amount)

/** Where an appraisal came from, as shown beside it. Unknown sources are title-cased. */
internal fun appraisalSourceLabel(source: String): String = when (source) {
    "EBAY" -> "eBay"
    "PRICECHARTING" -> "PriceCharting"
    "MANUAL" -> "Manual"
    "INTERNAL" -> "FlykPicks"
    else -> source.lowercase(Locale.ROOT).replace(Regex("(^|[^a-z])([a-z])")) { it.groupValues[1] + it.groupValues[2].uppercase(Locale.ROOT) }
}

private val PreviewSeries = listOf(MediaFormat.BLURAY to listOf(1, 2), MediaFormat.DVD to null).mapIndexed { i, (format, seasons) ->
    LibraryItemImpl(
        id = "u$i", titleId = "t1", title = "Twin Peaks", mediaType = MediaType.TV, year = 1990,
        genre = "Drama • Mystery", posterUrl = null, season = seasons?.joinToString(", "), ownedSeasons = seasons,
        format = format, condition = ItemCondition.VERY_GOOD, appraisalValue = 42.5,
        appraisalSource = if (i == 0) "EBAY" else "DEFAULT", dateAdded = "2026-02-01T00:00:00Z",
        overview = "An idiosyncratic FBI agent investigates the murder of a young woman in the even more idiosyncratic town of Twin Peaks.",
    )
}

@Preview(device = "spec:width=411dp,height=1100dp")
@Composable
private fun LibraryItemDetailPreview() {
    FlykPicksTheme {
        LibraryItemDetailContent(
            titleId = "t1",
            library = LibraryState(items = PreviewSeries.take(1), hasLoaded = true),
            seasons = listOf(
                SeasonSummaryDTO(1, 8, "1990-04-08", null, 8.3),
                SeasonSummaryDTO(2, 22, "1990-09-30", null, 7.9),
                SeasonSummaryDTO(3, 18, "2017-05-21", null, 8.5),
            ),
            seasonsLoading = false, isUpdating = false,
            onBack = {}, onRetry = {}, onEdit = {}, onOpenSeason = {}, onAddAllSeasons = {},
        )
    }
}
