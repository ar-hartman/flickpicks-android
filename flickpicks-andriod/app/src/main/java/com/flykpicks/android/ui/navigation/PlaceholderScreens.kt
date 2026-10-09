package com.flykpicks.android.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.flykpicks.android.ui.components.DecoSectionHeader
import com.flykpicks.android.ui.components.DecoTopBar
import com.flykpicks.android.ui.components.NavRow
import com.flykpicks.android.ui.components.TopBand
import com.flykpicks.android.ui.components.TopBandDefaults
import com.flykpicks.android.ui.components.WordmarkHeader
import com.flykpicks.android.ui.components.decoCard
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykType
import com.flykpicks.android.ui.theme.textileBackground

// Temporary stand-ins so every route can be reached before its real screen exists. Each
// phase that builds a screen replaces its placeholder (IMPLEMENTATION_PLAN.md Phases 4–11).

data class PlaceholderLink(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
)

/** A tab's first screen: textile, TopBand and wordmark like the iOS tab roots. */
@Composable
fun TabRootPlaceholder(
    title: String,
    phase: Int,
    links: List<PlaceholderLink>,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Box(Modifier.fillMaxSize().textileBackground()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(top = TopBandDefaults.ContentTopPadding, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            WordmarkHeader(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp))
            PlaceholderBody(title, phase, args = emptyList(), links = links, extra = extra)
        }
        TopBand()
    }
}

/** A pushed screen: back bar, then the route's arguments and links to its children. */
@Composable
fun PushedPlaceholder(
    title: String,
    phase: Int,
    onBack: () -> Unit,
    args: List<Pair<String, String?>> = emptyList(),
    links: List<PlaceholderLink> = emptyList(),
) {
    Column(Modifier.fillMaxSize().textileBackground()) {
        DecoTopBar(title, onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PlaceholderBody(title, phase, args, links)
        }
    }
}

@Composable
private fun PlaceholderBody(
    title: String,
    phase: Int,
    args: List<Pair<String, String?>>,
    links: List<PlaceholderLink>,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DecoSectionHeader(title, subtitle = "Placeholder · built in Phase $phase")
        args.forEach { (name, value) ->
            Text("$name = ${value ?: "—"}", style = FlykType.Footnote, color = FlykColors.InkMuted)
        }
        if (links.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().decoCard()) {
                links.forEach { NavRow(it.icon, it.label, onClick = it.onClick) }
            }
        }
        extra()
    }
}
