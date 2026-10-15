package com.flykpicks.android.ui.screens.search

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flykpicks.android.model.MediaItemImpl
import com.flykpicks.android.store.RecommendationSection
import com.flykpicks.android.store.RecommendationSectionState
import com.flykpicks.android.ui.components.PosterLazyRow
import com.flykpicks.android.ui.components.PosterRowFrame
import com.flykpicks.android.ui.components.PreviewMediaItems
import com.flykpicks.android.ui.components.SearchRowPosterCard
import com.flykpicks.android.ui.components.titleCount
import com.flykpicks.android.ui.theme.DS
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.FlykShapes
import com.flykpicks.android.ui.theme.FlykType

/**
 * One Discover row (iOS `SearchView.RecommendationRow`): pulsing placeholders until its first
 * answer, then its posters, "Nothing to show yet.", or a failure with "Retry". A row that fails
 * to refresh keeps the posters it had.
 *
 * Not ported: iOS's "Based on what's trending" caption, which iOS never shows (its
 * `isFallback` is always false). Impressions come in Phase 13.
 */
@Composable
internal fun RecommendationRow(
    section: RecommendationSection,
    state: RecommendationSectionState,
    onRetry: () -> Unit,
    onItemClick: (MediaItemImpl) -> Unit,
    modifier: Modifier = Modifier,
    posterWidth: Dp = DS.PosterWidthLarge,
) {
    val waiting = state.isLoading && state.items.isEmpty()
    PosterRowFrame(
        title = section.displayTitle,
        countLabel = if (waiting) "Loading…" else titleCount(state.items.size),
        showSeeAll = state.items.isNotEmpty(),
        modifier = modifier,
    ) {
        when {
            waiting -> SkeletonRow(posterWidth)
            state.items.isEmpty() && state.errorMessage != null -> RowNotice(posterWidth) {
                Text("Couldn't load this section.", style = FlykType.Subheadline, color = FlykColors.Ink.copy(alpha = 0.5f))
                Text(
                    "Retry",
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(role = Role.Button, onClick = onRetry)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    style = FlykType.Subheadline.copy(fontWeight = FontWeight.Medium),
                    color = FlykColors.Brass,
                )
            }
            state.items.isEmpty() -> RowNotice(posterWidth) {
                Text("Nothing to show yet.", style = FlykType.Subheadline, color = FlykColors.Ink.copy(alpha = 0.35f))
            }
            else -> PosterLazyRow {
                items(state.items) { item ->
                    SearchRowPosterCard(item, posterWidth, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

/** A message in place of the posters, as tall as they would be. */
@Composable
private fun RowNotice(posterWidth: Dp, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(posterWidth * 1.5f).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Five placeholder cards fading in and out (iOS `SkeletonPosterCard`, redacted). */
@Composable
private fun SkeletonRow(posterWidth: Dp) {
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    PosterLazyRow(
        Modifier
            .graphicsLayer { this.alpha = alpha }
            .semantics { contentDescription = "Loading" },
        userScrollEnabled = false,
    ) {
        items(5) { SkeletonPosterCard(posterWidth) }
    }
}

@Composable
private fun SkeletonPosterCard(width: Dp) {
    val fill = FlykColors.Ink.copy(alpha = 0.12f)
    val bar = RoundedCornerShape(3.dp)
    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(fill, FlykShapes.small))
        Box(Modifier.fillMaxWidth(0.85f).height(12.dp).background(fill, bar))
        Box(Modifier.width(32.dp).height(10.dp).background(fill, bar))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F0, widthDp = 400)
@Composable
private fun RecommendationRowsPreview() {
    FlykPicksTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            RecommendationRow(RecommendationSection.POPULAR_MOVIES, RecommendationSectionState(items = PreviewMediaItems), {}, {})
            RecommendationRow(RecommendationSection.INVENTORY_MOVIES, RecommendationSectionState(isLoading = true), {}, {})
            RecommendationRow(RecommendationSection.SEARCH_HISTORY_TV, RecommendationSectionState(errorMessage = "Offline"), {}, {})
            RecommendationRow(RecommendationSection.INVENTORY_TV, RecommendationSectionState(), {}, {})
        }
    }
}
