package com.flykpicks.android.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.FlykType

/**
 * The three tabs (iOS `RootView`). Each is a nested nav graph at [graphRoute] whose first
 * screen is [startRoute]. iOS labels the search tab "Discover".
 */
enum class MainTab(val label: String, val icon: ImageVector, val graphRoute: String, val startRoute: String) {
    Library("Library", Icons.Filled.VideoLibrary, "tab/library", NavRoutes.Library.route),
    Search("Discover", Icons.Filled.Search, "tab/search", NavRoutes.Search.route),
    Profile("Profile", Icons.Filled.AccountBox, "tab/profile", NavRoutes.Profile.route),
}

/**
 * Ink tab bar with brass items, as configured in iOS `FlykPicks_iOSApp.init` (the blueprint's navy
 * background and cream unselected items aren't what iOS does): opaque ink, a brass-25% top
 * hairline, selected brass, unselected brass at 45%. 49dp like the iOS tab bar, plus the
 * navigation bar inset.
 */
@Composable
fun FlykBottomBar(selected: MainTab?, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(FlykColors.Ink)
            .drawBehind {
                drawRect(FlykColors.Brass.copy(alpha = 0.25f), Offset.Zero, Size(size.width, 1.dp.toPx()))
            }
            .navigationBarsPadding()
            .height(49.dp)
            .selectableGroup(),
    ) {
        MainTab.entries.forEach { tab ->
            val tint = if (tab == selected) FlykColors.Brass else FlykColors.Brass.copy(alpha = 0.45f)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected = tab == selected, role = Role.Tab, onClick = { onSelect(tab) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                Text(tab.label, style = FlykType.Caption2.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium), color = tint)
            }
        }
    }
}

@Preview
@Composable
private fun FlykBottomBarPreview() {
    FlykPicksTheme { FlykBottomBar(selected = MainTab.Library, onSelect = {}) }
}
