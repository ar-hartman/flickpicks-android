package com.flykpicks.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flykpicks.android.model.SeasonSummaryDTO
import com.flykpicks.android.model.enums.ItemCondition
import com.flykpicks.android.model.enums.MediaFormat
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.FlykType

/** One add request: a copy in [format] and [condition]. [seasons] null means a film, or every season. */
data class AddToLibraryRequest(val format: MediaFormat, val condition: ItemCondition, val seasons: List<Int>?)

/**
 * Everything [AddToLibrarySheet] shows (iOS `AddToLibrarySheet`'s state). The screen's ViewModel
 * holds it, so a save in flight survives rotation.
 *
 * A film or a whole series is one copy in one format and condition. With "All Seasons" off, the
 * user picks seasons, confirms them, and then gives each its own format and condition.
 */
data class AddToLibraryState(
    val isTV: Boolean,
    val format: MediaFormat = DEFAULT_FORMAT,
    val condition: ItemCondition = DEFAULT_CONDITION,
    val allSeasons: Boolean = true,
    val selectedSeasons: Set<Int> = emptySet(),
    /** The picks are confirmed and each selected season shows its own format and condition. */
    val seasonsConfirmed: Boolean = false,
    val seasonFormats: Map<Int, MediaFormat> = emptyMap(),
    val seasonConditions: Map<Int, ItemCondition> = emptyMap(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSave: Boolean get() = !isTV || allSeasons || (seasonsConfirmed && selectedSeasons.isNotEmpty())

    fun formatOf(season: Int): MediaFormat = seasonFormats[season] ?: DEFAULT_FORMAT

    fun conditionOf(season: Int): ItemCondition = seasonConditions[season] ?: DEFAULT_CONDITION

    /** Turning "All Seasons" back on forgets the picks, as on iOS. */
    fun withAllSeasons(on: Boolean): AddToLibraryState =
        if (on) copy(allSeasons = true, selectedSeasons = emptySet(), seasonsConfirmed = false, seasonFormats = emptyMap(), seasonConditions = emptyMap())
        else copy(allSeasons = false)

    fun toggling(season: Int): AddToLibraryState =
        copy(selectedSeasons = if (season in selectedSeasons) selectedSeasons - season else selectedSeasons + season)

    fun withSeasonFormat(season: Int, format: MediaFormat) = copy(seasonFormats = seasonFormats + (season to format))

    fun withSeasonCondition(season: Int, condition: ItemCondition) = copy(seasonConditions = seasonConditions + (season to condition))

    /**
     * What Save sends, as iOS does: one copy for a film or a whole series; otherwise one copy per
     * format and condition, holding the seasons picked with them, lowest season first.
     */
    fun requests(): List<AddToLibraryRequest> =
        if (!isTV || allSeasons) {
            listOf(AddToLibraryRequest(format, condition, seasons = null))
        } else {
            selectedSeasons.sorted()
                .groupBy { formatOf(it) to conditionOf(it) }
                .map { (copy, seasons) -> AddToLibraryRequest(copy.first, copy.second, seasons) }
        }

    companion object {
        val DEFAULT_FORMAT = MediaFormat.BLURAY
        val DEFAULT_CONDITION = ItemCondition.VERY_GOOD
    }
}

/**
 * Add a title from Discover to the library (iOS `AddToLibrarySheet`, blueprint §11.17). For a
 * series, [seasons] lists every season the catalog has. The sheet can't be dismissed while
 * saving.
 */
@Composable
fun AddToLibrarySheet(
    title: String,
    posterUrl: String?,
    flyksId: String,
    state: AddToLibraryState,
    seasons: List<SeasonSummaryDTO>,
    seasonsLoading: Boolean,
    onEdit: ((AddToLibraryState) -> AddToLibraryState) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    LibraryItemSheet(isBusy = state.isSaving, onDismiss = onCancel) {
        AddToLibraryContent(title, posterUrl, flyksId, state, seasons, seasonsLoading, onEdit, onSave, onCancel)
    }
}

@Composable
internal fun AddToLibraryContent(
    title: String,
    posterUrl: String?,
    flyksId: String?,
    state: AddToLibraryState,
    seasons: List<SeasonSummaryDTO>,
    seasonsLoading: Boolean,
    onEdit: ((AddToLibraryState) -> AddToLibraryState) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val enabled = !state.isSaving
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SheetTopBar("Add to Inventory", onCancel, enabled)
        SheetTitleCard(title, posterUrl, flyksId, subtitle = "Choose details for your copy")

        if (!state.isTV || state.allSeasons) {
            Column(
                Modifier.fillMaxWidth().sheetCard().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // iOS labels this menu with the raw value ("BLURAY", "FOUR_K_ULTRA_HD"); ios-gaps.md G33.
                PickerRow("Format", state.format, MediaFormat.entries, { it.displayName }, { f -> onEdit { it.copy(format = f) } }, enabled)
                CardDivider()
                PickerRow("Condition", state.condition, ItemCondition.entries, { it.displayName }, { c -> onEdit { it.copy(condition = c) } }, enabled)
            }
        }

        if (state.isTV) SeasonsCard(state, seasons, seasonsLoading, onEdit)

        state.errorMessage?.let { SheetErrorCard(it) }

        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.isTV && !state.allSeasons) {
                when {
                    state.selectedSeasons.isEmpty() -> Hint("Select at least one season to continue.", FlykColors.DangerRed.copy(alpha = 0.75f))
                    !state.seasonsConfirmed -> Hint("Confirm your season selection above to continue.", FlykColors.Ink.copy(alpha = 0.5f))
                }
            }
            DecoButton(onClick = onSave, enabled = enabled && state.canSave) {
                if (state.isSaving) CircularProgressIndicator(Modifier.size(14.dp), color = FlykColors.Ink, strokeWidth = 2.dp)
                Text(if (state.isSaving) "Saving..." else "Save")
            }
            TextButton(onClick = onCancel, enabled = enabled) {
                Text(
                    "Not now",
                    style = FlykType.Subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = FlykColors.Ink.copy(alpha = if (enabled) 0.7f else 0.3f),
                )
            }
        }
    }
}

/** "All Seasons", or the seasons to pick from, or the picked seasons' formats and conditions. */
@Composable
private fun SeasonsCard(
    state: AddToLibraryState,
    seasons: List<SeasonSummaryDTO>,
    seasonsLoading: Boolean,
    onEdit: ((AddToLibraryState) -> AddToLibraryState) -> Unit,
) {
    val enabled = !state.isSaving
    Column(
        Modifier.fillMaxWidth().sheetCard().padding(14.dp).alpha(if (enabled) 1f else 0.5f),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Seasons",
                    style = FlykType.Caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                    color = FlykColors.Ink.copy(alpha = 0.5f),
                )
                Text("All Seasons", style = FlykType.Subheadline.copy(fontWeight = FontWeight.SemiBold), color = FlykColors.Ink)
            }
            Switch(
                checked = state.allSeasons,
                onCheckedChange = { on -> onEdit { it.withAllSeasons(on) } },
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = FlykColors.Brass,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = FlykColors.Ink.copy(alpha = 0.12f),
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }

        if (!state.allSeasons) {
            CardDivider()
            when {
                seasons.isEmpty() -> Text(
                    if (seasonsLoading) "Loading seasons…" else "Season list unavailable. Enable All Seasons to proceed.",
                    style = FlykType.Caption,
                    color = FlykColors.Ink.copy(alpha = 0.45f),
                )
                !state.seasonsConfirmed -> {
                    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        seasons.forEachIndexed { index, season ->
                            SeasonToggleRow(season, selected = season.seasonNumber in state.selectedSeasons, enabled) {
                                onEdit { it.toggling(season.seasonNumber) }
                            }
                            if (index < seasons.lastIndex) {
                                HorizontalDivider(Modifier.padding(start = 36.dp), color = separator(opacity = 0.2f))
                            }
                        }
                    }
                    if (state.selectedSeasons.isNotEmpty()) {
                        CardDivider()
                        val count = state.selectedSeasons.size
                        BrassLink(
                            "Confirm $count Season${if (count == 1) "" else "s"} →",
                            enabled,
                            Modifier.fillMaxWidth(),
                        ) { onEdit { it.copy(seasonsConfirmed = true) } }
                    }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val count = state.selectedSeasons.size
                        Text(
                            "$count Season${if (count == 1) "" else "s"} Selected",
                            modifier = Modifier.weight(1f),
                            style = FlykType.Subheadline,
                            color = FlykColors.Ink.copy(alpha = 0.6f),
                        )
                        BrassLink("Change", enabled, small = true) { onEdit { it.copy(seasonsConfirmed = false) } }
                    }
                    state.selectedSeasons.sorted().forEach { season ->
                        CardDivider()
                        SeasonConfigRow(season, state, enabled, onEdit)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonToggleRow(season: SeasonSummaryDTO, selected: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (selected) FlykColors.Brass else FlykColors.Ink.copy(alpha = 0.25f),
            modifier = Modifier.size(22.dp),
        )
        Text("Season ${season.seasonNumber}", style = FlykType.Subheadline, color = FlykColors.Ink)
        Text("· ${season.episodeCount} ep", style = FlykType.Caption, color = FlykColors.Ink.copy(alpha = 0.45f))
    }
}

@Composable
private fun SeasonConfigRow(
    season: Int,
    state: AddToLibraryState,
    enabled: Boolean,
    onEdit: ((AddToLibraryState) -> AddToLibraryState) -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Season $season", style = FlykType.Subheadline.copy(fontWeight = FontWeight.SemiBold), color = FlykColors.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ValueMenu(
                state.formatOf(season), MediaFormat.entries, { it.displayName },
                { f -> onEdit { it.withSeasonFormat(season, f) } }, enabled, clickLabel = "Change season $season format",
            )
            ValueMenu(
                state.conditionOf(season), ItemCondition.entries, { it.displayName },
                { c -> onEdit { it.withSeasonCondition(season, c) } }, enabled, clickLabel = "Change season $season condition",
            )
        }
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(color = separator(opacity = 0.35f))
}

/** iOS `Divider().opacity(x)`: the separator color, which is translucent already, faded further. */
private fun separator(opacity: Float) = FlykColors.GroupedSeparator.copy(alpha = FlykColors.GroupedSeparator.alpha * opacity)

@Composable
private fun Hint(text: String, color: Color) {
    Text(text, style = FlykType.Caption, color = color, textAlign = TextAlign.Center)
}

/** A text button in brass, centered in [modifier]. */
@Composable
private fun BrassLink(text: String, enabled: Boolean, modifier: Modifier = Modifier, small: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        style = (if (small) FlykType.Caption else FlykType.Subheadline).copy(fontWeight = FontWeight.SemiBold),
        color = FlykColors.Brass,
        textAlign = TextAlign.Center,
    )
}

private val PreviewSeasons = listOf(SeasonSummaryDTO(1, 8, null, null, 0.0), SeasonSummaryDTO(2, 22, null, null, 0.0), SeasonSummaryDTO(3, 18, null, null, 0.0))

@Preview(showBackground = true, backgroundColor = 0xFFF2F2F7, heightDp = 900)
@Composable
private fun AddToLibraryPickingPreview() {
    FlykPicksTheme {
        Column {
            Spacer(Modifier.size(12.dp))
            AddToLibraryContent(
                title = "Twin Peaks", posterUrl = null, flyksId = null,
                state = AddToLibraryState(isTV = true, allSeasons = false, selectedSeasons = setOf(1, 3)),
                seasons = PreviewSeasons, seasonsLoading = false, onEdit = {}, onSave = {}, onCancel = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF2F2F7, heightDp = 900)
@Composable
private fun AddToLibraryConfirmedPreview() {
    FlykPicksTheme {
        Column {
            Spacer(Modifier.size(12.dp))
            AddToLibraryContent(
                title = "Twin Peaks", posterUrl = null, flyksId = null,
                state = AddToLibraryState(
                    isTV = true, allSeasons = false, selectedSeasons = setOf(1, 3), seasonsConfirmed = true,
                    seasonFormats = mapOf(3 to MediaFormat.DVD), errorMessage = "Added Season 1. No internet connection. Check your connection and try again.",
                ),
                seasons = PreviewSeasons, seasonsLoading = false, onEdit = {}, onSave = {}, onCancel = {},
            )
        }
    }
}
