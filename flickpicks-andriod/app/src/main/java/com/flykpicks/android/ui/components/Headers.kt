package com.flykpicks.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.FlykType
import com.flykpicks.android.ui.theme.SerifFamily
import com.flykpicks.android.ui.theme.inkPaper

/** "FLICK PICKS" in letter-spaced serif over a brass rule (iOS `WordmarkHeader`). */
@Composable
fun WordmarkHeader(modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "FLICK PICKS",
            modifier = Modifier.semantics { heading() },
            style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = 2.6.sp),
            color = FlykColors.Ink,
        )
        Box(
            Modifier
                .widthIn(max = 260.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(FlykColors.Brass.copy(alpha = 0.85f)),
        )
    }
}

object TopBandDefaults {
    /** Navy below the status bar. iOS draws 84pt from the screen top, which leaves ~25pt below the status bar on current iPhones. */
    val BandHeight = 28.dp

    /**
     * Where content under a [TopBand] starts, measured below the status bar
     * (iOS `topBandHeight` = 48 + 1 + 2 + 1, applied from the safe area).
     */
    val ContentTopPadding = 52.dp
}

/**
 * Navy ink-paper band behind the status bar, closed off by ink / brass / ink rules (iOS `TopBand`).
 * Place it last in a Box so it sits above scrolling content. Sets light status bar icons.
 */
@Composable
fun TopBand(modifier: Modifier = Modifier) {
    StatusBarIcons(light = true)
    Column(
        modifier
            .fillMaxWidth()
            .dropShadow(RectangleShape, Shadow(radius = 6.dp, color = FlykColors.Ink.copy(alpha = 0.20f), offset = DpOffset(0.dp, 3.dp))),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(FlykColors.NavBar)
                .inkPaper()
                .statusBarsPadding()
                .height(TopBandDefaults.BandHeight),
        )
        Rule(FlykColors.Ink.copy(alpha = 0.35f), 1)
        Rule(FlykColors.Brass.copy(alpha = 0.9f), 2)
        Rule(FlykColors.Ink.copy(alpha = 0.25f), 1)
    }
}

@Composable
private fun Rule(color: Color, heightDp: Int) {
    Box(Modifier.fillMaxWidth().height(heightDp.dp).background(color))
}

/**
 * A [TopBand] with a brass "Back" in it, for the pushed screens that keep the band instead of a
 * [DecoTopBar] (the iOS library detail screens). The tap target extends below the band so it
 * stays 48dp tall.
 */
@Composable
fun TopBandWithBack(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        TopBand()
        Box(
            Modifier
                .statusBarsPadding()
                .padding(start = 4.dp)
                .clickable(onClickLabel = "Go back", role = Role.Button, onClick = onBack)
                .height(48.dp)
                .padding(horizontal = 12.dp),
        ) {
            Row(
                Modifier.height(TopBandDefaults.BandHeight),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = FlykColors.Brass,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "Back",
                    style = FlykType.Caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                    color = FlykColors.Brass,
                )
            }
        }
    }
}

/** Titles / Films / TV Shows counts between brass rules (iOS `HeaderStats`). */
@Composable
fun HeaderStats(totalTitles: Int, films: Int, tvShows: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        BrassRule()
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            StatColumn(totalTitles, "Titles")
            StatDivider()
            StatColumn(films, "Films")
            StatDivider()
            StatColumn(tvShows, "TV Shows")
        }
        BrassRule()
    }
}

@Composable
private fun RowScope.StatColumn(value: Int, label: String) {
    Column(
        Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("$value", style = FlykType.Title3.copy(fontWeight = FontWeight.SemiBold), color = FlykColors.Ink)
        Text(
            label.uppercase(),
            style = FlykType.Caption2.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
            color = FlykColors.Brass,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(28.dp).background(FlykColors.Brass.copy(alpha = 0.4f)))
}

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F0)
@Composable
private fun HeadersPreview() {
    FlykPicksTheme {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            TopBand()
            WordmarkHeader(Modifier.align(Alignment.CenterHorizontally))
            HeaderStats(totalTitles = 128, films = 97, tvShows = 31, modifier = Modifier.padding(horizontal = 20.dp))
        }
    }
}
