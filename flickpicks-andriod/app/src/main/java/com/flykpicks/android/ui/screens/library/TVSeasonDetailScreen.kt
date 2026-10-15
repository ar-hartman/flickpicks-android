package com.flykpicks.android.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flykpicks.android.model.LibraryItemImpl
import com.flykpicks.android.model.SeasonSummaryDTO
import com.flykpicks.android.model.enums.ItemCondition
import com.flykpicks.android.model.enums.MediaFormat
import com.flykpicks.android.model.enums.MediaType
import com.flykpicks.android.ui.components.EditLibraryItemSheet
import com.flykpicks.android.ui.components.GroupedCard
import com.flykpicks.android.ui.components.GroupedDivider
import com.flykpicks.android.ui.components.TopBandDefaults
import com.flykpicks.android.ui.components.TopBandWithBack
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.FlykType
import com.flykpicks.android.ui.theme.SerifFamily
import java.util.Locale

/**
 * One season of an owned series: which copies include it, and adding it to or taking it off a
 * copy (iOS `TVSeasonDetailView`). Shares the title screen's [viewModel].
 */
@Composable
fun TVSeasonDetailScreen(viewModel: LibraryItemDetailViewModel, seasonNumber: Int, onBack: () -> Unit) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val copies = remember(library.items) { library.items(viewModel.titleId) }
    val seriesTitle = copies.firstOrNull()?.title.orEmpty()

    // Leave once a copy was removed from here (as iOS does), or when the series is gone altogether.
    val gone = library.hasLoaded && copies.isEmpty() && !viewModel.isBusy
    val closing = gone || viewModel.seasonScreenShouldClose
    LaunchedEffect(closing) {
        if (closing) {
            viewModel.onSeasonScreenClosed()
            onBack()
        }
    }

    TVSeasonDetailContent(
        seriesTitle = seriesTitle,
        seasonNumber = seasonNumber,
        season = viewModel.seasons.firstOrNull { it.seasonNumber == seasonNumber },
        copies = copies,
        isUpdating = viewModel.isUpdating,
        onBack = onBack,
        onEdit = { copy ->
            viewModel.startEditing(copy, title = "$seriesTitle — Season $seasonNumber", posterUrl = null, canRemove = false)
        },
        onAddSeason = { viewModel.addSeason(it, seasonNumber) },
        onRemoveSeason = { viewModel.removeSeason(it, seasonNumber) },
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

    if (viewModel.pendingCopyRemoval != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelCopyRemoval,
            title = { Text("Remove this copy?") },
            text = { Text("This will remove the entire copy from your library since it only owns this season.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmCopyRemoval() }) { Text("Remove", color = FlykColors.DangerRed) }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelCopyRemoval) { Text("Cancel") } },
        )
    }

    viewModel.updateError?.let { message -> SeasonUpdateErrorDialog(message, viewModel::dismissUpdateError) }
}

/** [season] is null until the series' seasons have loaded (and if they couldn't be). */
@Composable
internal fun TVSeasonDetailContent(
    seriesTitle: String,
    seasonNumber: Int,
    season: SeasonSummaryDTO?,
    copies: List<LibraryItemImpl>,
    isUpdating: Boolean,
    onBack: () -> Unit,
    onEdit: (LibraryItemImpl) -> Unit,
    onAddSeason: (LibraryItemImpl) -> Unit,
    onRemoveSeason: (LibraryItemImpl) -> Unit,
) {
    val owning = copies.filter { it.ownsSeason(seasonNumber) }
    val addable = copies.filterNot { it.ownsSeason(seasonNumber) }

    Box(Modifier.fillMaxSize().background(FlykColors.GroupedBackground)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(top = TopBandDefaults.ContentTopPadding + 16.dp, bottom = 32.dp),
        ) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    seriesTitle.uppercase(),
                    style = FlykType.Caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
                    color = FlykColors.Brass,
                )
                Text(
                    "Season $seasonNumber",
                    modifier = Modifier.semantics { heading() },
                    style = FlykType.LargeTitle.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Bold),
                    color = FlykColors.Ink,
                )
                if (season != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatBadge(Icons.Outlined.Movie, "${season.episodeCount} episodes")
                        season.airYear?.let { StatBadge(Icons.Outlined.CalendarToday, it) }
                        if (season.voteAverage > 0) {
                            StatBadge(Icons.Filled.Star, formatRating(season.voteAverage))
                        }
                    }
                    if (!season.overview.isNullOrEmpty()) {
                        Text(
                            season.overview,
                            modifier = Modifier.padding(top = 4.dp),
                            style = FlykType.Body,
                            color = FlykColors.Ink.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            SectionHeader("Your Copies")
            if (owning.isEmpty()) {
                Text(
                    "You don't own this season.",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    style = FlykType.Subheadline,
                    color = FlykColors.Ink.copy(alpha = 0.4f),
                )
            } else {
                GroupedCard(Modifier.padding(horizontal = 16.dp)) {
                    owning.forEachIndexed { index, copy ->
                        SeasonCopyRow(copy, note = "Owns full series".takeIf { copy.ownedSeasons == null }) {
                            PillButton("Edit", { onEdit(copy) }, icon = Icons.Outlined.Edit, enabled = !isUpdating)
                            // A copy that owns the whole series has no season list to take one off.
                            if (copy.ownedSeasons != null) {
                                PillButton("Remove", { onRemoveSeason(copy) }, color = FlykColors.DangerRed, enabled = !isUpdating)
                            }
                        }
                        if (index < owning.lastIndex) GroupedDivider(Modifier.padding(start = 16.dp))
                    }
                }
            }

            if (addable.isNotEmpty()) {
                SectionHeader("Add to a Copy")
                GroupedCard(Modifier.padding(horizontal = 16.dp)) {
                    addable.forEachIndexed { index, copy ->
                        SeasonCopyRow(copy, note = null) {
                            PillButton("Add Season $seasonNumber", { onAddSeason(copy) }, enabled = !isUpdating)
                        }
                        if (index < addable.lastIndex) GroupedDivider(Modifier.padding(start = 16.dp))
                    }
                }
            }

            // iOS also shows users a note about pending backend work here (ios-gaps.md G29).
            SectionHeader("Episodes")
            ComingSoonCard(Icons.Outlined.Subscriptions, "Episode list coming soon")
            SectionHeader("Cast")
            ComingSoonCard(Icons.Outlined.People, "Cast information coming soon")
        }
        TopBandWithBack(onBack)
    }
}

/** A 0–10 rating to one decimal place, e.g. "7.9". */
internal fun formatRating(rating: Double, locale: Locale = Locale.getDefault()): String = String.format(locale, "%.1f", rating)

@Composable
private fun StatBadge(icon: ImageVector, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = FlykColors.Brass, modifier = Modifier.size(13.dp))
        Text(label, style = FlykType.Caption.copy(fontWeight = FontWeight.Medium), color = FlykColors.Ink.copy(alpha = 0.6f))
    }
}

/** An uppercase section label, set 24dp below whatever precedes it. */
@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 10.dp).semantics { heading() },
        style = FlykType.Headline.copy(letterSpacing = 1.4.sp),
        color = FlykColors.Ink.copy(alpha = 0.5f),
    )
}

/** A copy's format and condition, an optional brass [note], and its buttons. */
@Composable
private fun SeasonCopyRow(copy: LibraryItemImpl, note: String?, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(copy.format.displayName, style = FlykType.Subheadline.copy(fontWeight = FontWeight.SemiBold), color = FlykColors.Ink)
            Text(copy.condition.displayName, style = FlykType.Caption, color = FlykColors.Ink.copy(alpha = 0.5f))
            if (note != null) {
                Text(note, style = FlykType.Caption.copy(fontWeight = FontWeight.Medium), color = FlykColors.Brass.copy(alpha = 0.8f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
private fun ComingSoonCard(icon: ImageVector, title: String) {
    GroupedCard(Modifier.padding(horizontal = 16.dp)) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = FlykColors.Brass.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
            }
            Text(title, style = FlykType.Subheadline.copy(fontWeight = FontWeight.SemiBold), color = FlykColors.Ink.copy(alpha = 0.6f))
            Spacer(Modifier.height(1.dp).weight(1f))
        }
    }
}

@Preview(device = "spec:width=411dp,height=1000dp")
@Composable
private fun TVSeasonDetailPreview() {
    val copies = listOf(MediaFormat.BLURAY to listOf(1, 2), MediaFormat.DVD to listOf(1), MediaFormat.VHS to null)
        .mapIndexed { i, (format, seasons) ->
            LibraryItemImpl(
                id = "u$i", titleId = "t1", title = "Twin Peaks", mediaType = MediaType.TV, year = 1990, genre = "Drama",
                posterUrl = null, season = null, ownedSeasons = seasons, format = format, condition = ItemCondition.GOOD,
                appraisalValue = 0.0, appraisalSource = "DEFAULT", dateAdded = null, overview = "",
            )
        }
    FlykPicksTheme {
        TVSeasonDetailContent(
            seriesTitle = "Twin Peaks",
            seasonNumber = 2,
            season = SeasonSummaryDTO(2, 22, "1990-09-30", "Cooper stays in town as the investigation widens.", 7.9),
            copies = copies,
            isUpdating = false,
            onBack = {}, onEdit = {}, onAddSeason = {}, onRemoveSeason = {},
        )
    }
}
