package com.flykpicks.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.FlykType

/**
 * Bar for pushed screens: back arrow and a centered title on cream, like the inline iOS
 * navigation bar the profile screens use. All titles are inline; iOS's large titles on a few
 * screens aren't reproduced (plan D22). Sets dark status bar icons.
 */
@Composable
fun DecoTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    StatusBarIcons(light = false)
    Box(
        modifier
            .fillMaxWidth()
            .background(FlykColors.Canvas)
            .drawBehind {
                val hairline = 1.dp.toPx()
                drawRect(FlykColors.Ink.copy(alpha = 0.08f), Offset(0f, size.height - hairline), Size(size.width, hairline))
            }
            .statusBarsPadding()
            .height(48.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FlykColors.Ink)
        }
        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp)
                .semantics { heading() },
            style = FlykType.Headline,
            color = FlykColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun DecoTopBarPreview() {
    FlykPicksTheme { DecoTopBar("Privacy & Security", onBack = {}) }
}
