package com.flykpicks.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.flykpicks.android.model.enums.ItemCondition
import com.flykpicks.android.model.enums.MediaFormat
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.FlykType
import com.flykpicks.android.ui.theme.SerifFamily

/**
 * Everything [EditLibraryItemSheet] shows. The screen's ViewModel holds it, so a save in flight
 * survives rotation.
 */
data class EditLibraryItemState(
    val title: String,
    /** Null shows the placeholder, as iOS does when editing from a season. */
    val posterUrl: String?,
    val currentFormat: MediaFormat,
    val currentCondition: ItemCondition,
    val format: MediaFormat = currentFormat,
    val condition: ItemCondition = currentCondition,
    /** Whether the sheet offers "Remove from library". */
    val canRemove: Boolean = false,
    val isSaving: Boolean = false,
    val isRemoving: Boolean = false,
    val errorMessage: String? = null,
) {
    val hasChanges: Boolean get() = format != currentFormat || condition != currentCondition
    val isBusy: Boolean get() = isSaving || isRemoving
}

private val SheetCardShape = RoundedCornerShape(14.dp)

/**
 * Change a copy's format or condition, or remove it (iOS `EditLibraryItemSheet`, blueprint
 * §11.18). [onRemove] is called once the user has confirmed. The sheet can't be dismissed while
 * a save or removal is in flight.
 */
@Composable
fun EditLibraryItemSheet(
    state: EditLibraryItemState,
    onFormatChange: (MediaFormat) -> Unit,
    onConditionChange: (ItemCondition) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    LibraryItemSheet(isBusy = state.isBusy, onDismiss = onCancel) {
        EditLibraryItemContent(state, onFormatChange, onConditionChange, onSave, onCancel, onRemove)
    }
}

/**
 * The modal sheet the library's edit and add sheets sit in: full height, on the grouped gray.
 * It can't be dragged or tapped away while [isBusy].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryItemSheet(isBusy: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val busy by rememberUpdatedState(isBusy)
    // One instance for the sheet's lifetime: the sheet state is keyed on it.
    val staysOpenWhileBusy = remember { { value: SheetValue -> value != SheetValue.Hidden || !busy } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = staysOpenWhileBusy),
        containerColor = FlykColors.GroupedBackground,
        content = content,
    )
}

@Composable
private fun EditLibraryItemContent(
    state: EditLibraryItemState,
    onFormatChange: (MediaFormat) -> Unit,
    onConditionChange: (ItemCondition) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    var confirmingRemoval by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SheetTopBar("Edit Details", onCancel, enabled = !state.isBusy)
        SheetTitleCard(state.title, state.posterUrl, flyksId = null, subtitle = "Update format or condition")

        Column(
            Modifier.fillMaxWidth().sheetCard().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PickerRow("Format", state.format, MediaFormat.entries, { it.displayName }, onFormatChange, enabled = !state.isSaving)
            HorizontalDivider(color = FlykColors.GroupedSeparator.copy(alpha = 0.1f))
            PickerRow("Condition", state.condition, ItemCondition.entries, { it.displayName }, onConditionChange, enabled = !state.isSaving)
        }

        state.errorMessage?.let { SheetErrorCard(it) }

        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DecoButton(onClick = onSave, enabled = state.hasChanges && !state.isBusy) {
                if (state.isSaving) CircularProgressIndicator(Modifier.size(14.dp), color = FlykColors.Ink, strokeWidth = 2.dp)
                Text(if (state.isSaving) "Saving..." else "Save")
            }

            if (state.canRemove) {
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .alpha(if (state.isBusy && !state.isRemoving) 0.5f else 1f)
                        .clip(shape)
                        .background(FlykColors.DangerRed.copy(alpha = 0.10f))
                        .clickable(enabled = !state.isBusy, role = Role.Button) { confirmingRemoval = true }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.isRemoving) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = FlykColors.DangerRed, strokeWidth = 2.dp)
                    }
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = FlykColors.DangerRed, modifier = Modifier.size(18.dp))
                    Text(
                        if (state.isRemoving) "Removing..." else "Remove from library",
                        style = FlykType.Subheadline.copy(fontWeight = FontWeight.SemiBold),
                        color = FlykColors.DangerRed,
                    )
                }
            }

            TextButton(onClick = onCancel, enabled = !state.isBusy) {
                Text(
                    "Cancel",
                    style = FlykType.Subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = FlykColors.Ink.copy(alpha = if (state.isBusy) 0.3f else 0.7f),
                )
            }
        }
    }

    if (confirmingRemoval) {
        AlertDialog(
            onDismissRequest = { confirmingRemoval = false },
            title = { Text("Remove this item from your library?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingRemoval = false
                    onRemove()
                }) { Text("Remove", color = FlykColors.DangerRed) }
            },
            dismissButton = { TextButton(onClick = { confirmingRemoval = false }) { Text("Cancel") } },
        )
    }
}

internal fun Modifier.sheetCard(shape: RoundedCornerShape = SheetCardShape): Modifier = this
    .clip(shape)
    .background(FlykColors.GroupedCard)
    .border(1.dp, FlykColors.Ink.copy(alpha = 0.10f), shape)

/** "Cancel" on the left and the sheet's title centered (iOS inline navigation title). */
@Composable
internal fun SheetTopBar(title: String, onCancel: () -> Unit, enabled: Boolean) {
    Box(Modifier.fillMaxWidth()) {
        TextButton(onClick = onCancel, enabled = enabled, modifier = Modifier.align(Alignment.CenterStart)) {
            Text("Cancel", style = FlykType.Body, color = FlykColors.Brass.copy(alpha = if (enabled) 1f else 0.4f))
        }
        Text(
            title,
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
            style = FlykType.Headline,
            color = FlykColors.Ink,
        )
    }
}

/** The poster thumbnail and the title, with a line saying what the sheet is for. */
@Composable
internal fun SheetTitleCard(title: String, posterUrl: String?, flyksId: String?, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().sheetCard().padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumbShape = RoundedCornerShape(10.dp)
        AsyncPosterImage(
            posterUrl = posterUrl,
            flyksId = flyksId,
            modifier = Modifier
                .size(width = 54.dp, height = 80.dp)
                .clip(thumbShape)
                .border(1.dp, FlykColors.Ink.copy(alpha = 0.12f), thumbShape),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                style = FlykType.Headline.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Bold),
                color = FlykColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, style = FlykType.Subheadline, color = FlykColors.InkMuted)
        }
    }
}

/** A failed request, in red on a card. */
@Composable
internal fun SheetErrorCard(message: String) {
    Row(
        Modifier.fillMaxWidth().sheetCard(RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = FlykColors.DangerRed, modifier = Modifier.size(18.dp))
        Text(message, style = FlykType.Footnote, color = FlykColors.DangerRed)
    }
}

/** A label and the current value in brass; tapping the value opens a menu of [options]. */
@Composable
internal fun <T> PickerRow(
    label: String,
    selected: T,
    options: List<T>,
    name: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean,
) {
    Row(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FlykType.Subheadline.copy(fontWeight = FontWeight.SemiBold), color = FlykColors.Ink)
        Spacer(Modifier.weight(1f))
        ValueMenu(selected, options, name, onSelect, enabled, clickLabel = "Change ${label.lowercase()}")
    }
}

/** The current value in brass on a gray pill (iOS `Menu` label); tapping it opens a menu of [options]. */
@Composable
internal fun <T> ValueMenu(
    selected: T,
    options: List<T>,
    name: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean,
    clickLabel: String,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        val shape = RoundedCornerShape(10.dp)
        Row(
            Modifier
                .clip(shape)
                .background(FlykColors.GroupedBackground)
                .clickable(enabled = enabled, onClickLabel = clickLabel, role = Role.DropdownList) { open = true }
                .padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name(selected), style = FlykType.Subheadline.copy(fontWeight = FontWeight.SemiBold), color = FlykColors.Brass)
            Icon(
                Icons.Filled.UnfoldMore,
                contentDescription = null,
                tint = FlykColors.Brass.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(name(option)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF2F2F7)
@Composable
private fun EditLibraryItemSheetPreview() {
    FlykPicksTheme {
        Column {
            Spacer(Modifier.height(12.dp).width(1.dp))
            EditLibraryItemContent(
                state = EditLibraryItemState(
                    title = "The Third Man",
                    posterUrl = null,
                    currentFormat = MediaFormat.DVD,
                    currentCondition = ItemCondition.GOOD,
                    format = MediaFormat.BLURAY,
                    canRemove = true,
                    errorMessage = "No internet connection. Check your connection and try again.",
                ),
                onFormatChange = {}, onConditionChange = {}, onSave = {}, onCancel = {}, onRemove = {},
            )
        }
    }
}
