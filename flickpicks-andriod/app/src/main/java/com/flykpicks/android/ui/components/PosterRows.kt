package com.flykpicks.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flykpicks.android.model.LibraryItemImpl
import com.flykpicks.android.model.MediaItemImpl
import com.flykpicks.android.model.enums.ItemCondition
import com.flykpicks.android.model.enums.MediaFormat
import com.flykpicks.android.model.enums.MediaType
import com.flykpicks.android.ui.theme.DS
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.FlykShapes
import com.flykpicks.android.ui.theme.FlykType

// iOS SearchRowPosterCard / LibraryRowPosterCard / SearchGenreRow / LibraryGenreRow
// (blueprint §11.11–11.13). iOS takes a posterHeight too but never uses it: the poster is 2:3.

/** Poster, title and release year; [posterWidth] sets the card width. */
@Composable
fun SearchRowPosterCard(
    item: MediaItemImpl,
    posterWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PosterCard(
        posterUrl = item.posterUrl,
        flyksId = item.flyksId,
        title = item.title,
        year = item.releaseDate.take(4).ifBlank { null },
        width = posterWidth,
        modifier = modifier.clickable(onClick = onClick),
    )
}

/** Poster, title and year (when known). With [onRemove], a long press offers "Remove from library". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryRowPosterCard(
    item: LibraryItemImpl,
    posterWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box(modifier) {
        PosterCard(
            posterUrl = item.posterUrl,
            flyksId = item.titleId,
            title = item.title,
            year = item.year.takeIf { it > 0 }?.toString(),
            width = posterWidth,
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClickLabel = if (onRemove != null) "Show options" else null,
                onLongClick = onRemove?.let {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    }
                },
            ),
        )
        if (onRemove != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Remove from library") },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    colors = MenuDefaults.itemColors(
                        textColor = FlykColors.DangerRed,
                        leadingIconColor = FlykColors.DangerRed,
                    ),
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
private fun PosterCard(
    posterUrl: String?,
    flyksId: String?,
    title: String,
    year: String?,
    width: Dp,
    modifier: Modifier,
) {
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AsyncPosterImage(
            posterUrl = posterUrl,
            flyksId = flyksId,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .dropShadow(FlykShapes.small, Shadow(radius = 6.dp, color = FlykColors.Ink.copy(alpha = 0.12f), offset = DpOffset(0.dp, 3.dp)))
                .clip(FlykShapes.small),
        )
        Text(
            text = title,
            style = FlykType.Caption.copy(fontWeight = FontWeight.Medium),
            color = FlykColors.Ink.copy(alpha = 0.80f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (year != null) {
            Text(year, style = FlykType.Caption2, color = FlykColors.Ink.copy(alpha = 0.45f))
        }
    }
}

/** A titled, horizontally scrolling row of search results. Defaults to the iOS search poster size. */
@Composable
fun SearchGenreRow(
    title: String,
    items: List<MediaItemImpl>,
    onItemClick: (MediaItemImpl) -> Unit,
    modifier: Modifier = Modifier,
    posterWidth: Dp = DS.PosterWidthLarge,
) {
    GenreRow(title, items.size, modifier) {
        items(items) { item ->
            SearchRowPosterCard(item, posterWidth, onClick = { onItemClick(item) })
        }
    }
}

/** A titled, horizontally scrolling row of library items. Defaults to the iOS library poster size. */
@Composable
fun LibraryGenreRow(
    title: String,
    items: List<LibraryItemImpl>,
    onItemClick: (LibraryItemImpl) -> Unit,
    modifier: Modifier = Modifier,
    posterWidth: Dp = DS.PosterWidthLibrary,
    onRemove: ((LibraryItemImpl) -> Unit)? = null,
) {
    GenreRow(title, items.size, modifier) {
        items(items) { item ->
            LibraryRowPosterCard(
                item = item,
                posterWidth = posterWidth,
                onClick = { onItemClick(item) },
                onRemove = onRemove?.let { remove -> { remove(item) } },
            )
        }
    }
}

/**
 * Header ("GENRE · N titles … See All"), brass rule, then the scrolling posters over another rule.
 * "See All" is a plain label, as on iOS (ios-gaps.md G18).
 */
@Composable
private fun GenreRow(
    title: String,
    count: Int,
    modifier: Modifier,
    content: LazyListScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .dropShadow(RectangleShape, Shadow(radius = 10.dp, color = Color.Black.copy(alpha = 0.05f), offset = DpOffset(0.dp, 14.dp)))
            .background(FlykColors.Canvas),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).alignByBaseline(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title.uppercase(),
                    modifier = Modifier.alignByBaseline().weight(1f, fill = false),
                    style = FlykType.Headline.copy(letterSpacing = 1.4.sp),
                    color = FlykColors.Ink,
                )
                Text("·", Modifier.alignByBaseline(), style = FlykType.Body, color = FlykColors.Brass)
                Text(
                    text = if (count == 1) "1 title" else "$count titles",
                    modifier = Modifier.alignByBaseline(),
                    style = FlykType.Subheadline,
                    color = FlykColors.Ink.copy(alpha = 0.5f),
                )
            }
            Text("See All", Modifier.alignByBaseline(), style = FlykType.Subheadline, color = FlykColors.Brass)
        }
        BrassRule()
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // iOS hangs this rule 6pt below the scroller's padded bottom edge.
                    drawRect(
                        color = FlykColors.Brass.copy(alpha = 0.35f),
                        topLeft = Offset(0f, size.height + 5.dp.toPx()),
                        size = Size(size.width, 1.dp.toPx()),
                    )
                }
                .padding(vertical = 12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Top,
            content = content,
        )
    }
}

internal val PreviewMediaItems = listOf("The Third Man", "Brazil", "Paris, Texas", "Chungking Express").mapIndexed { i, title ->
    MediaItemImpl(
        id = "m$i", flyksId = "m$i", title = title, mediaType = MediaType.FILM, originalLanguage = "en",
        genre = "Drama", popularity = 0.0, releaseDate = "19${49 + i * 12}-01-01", voteAverage = 0.0,
        voteCount = 0, posterUrl = null, overview = "",
    )
}

internal val PreviewLibraryItems = listOf("Twin Peaks", "The Wire", "Columbo").mapIndexed { i, title ->
    LibraryItemImpl(
        id = "u$i", titleId = "t$i", title = title, mediaType = MediaType.TV, year = 1990 + i * 6,
        genre = "Crime", posterUrl = null, season = null, ownedSeasons = null, format = MediaFormat.BLURAY,
        condition = ItemCondition.NEW, appraisalValue = 0.0, appraisalSource = "", dateAdded = null, overview = "",
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F0, widthDp = 400)
@Composable
private fun GenreRowsPreview() {
    FlykPicksTheme {
        Column(Modifier.padding(vertical = DS.SpacingXL), verticalArrangement = Arrangement.spacedBy(DS.SpacingXXL)) {
            SearchGenreRow("Drama", PreviewMediaItems, onItemClick = {}, modifier = Modifier.padding(horizontal = 16.dp))
            LibraryGenreRow("Crime", PreviewLibraryItems, onItemClick = {}, modifier = Modifier.padding(horizontal = 16.dp), onRemove = {})
        }
    }
}
