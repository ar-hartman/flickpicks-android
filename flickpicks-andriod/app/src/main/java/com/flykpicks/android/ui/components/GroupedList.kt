package com.flykpicks.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flykpicks.android.ui.theme.FlykColors

// The detail screens use iOS's grouped-list look: white cards of rows on a gray page (plan D27).

/** A white card of rows on the grouped page. */
@Composable
fun GroupedCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(cornerRadius)).background(FlykColors.GroupedCard),
        content = content,
    )
}

@Composable
fun GroupedDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 0.5.dp, color = FlykColors.GroupedSeparator)
}
