package com.flykpicks.android.ui.components

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Whatever draws behind a system bar decides its icon color: the navy TopBand and ink bottom
// bar want light icons, cream surfaces want dark ones. Each call applies when its composable
// enters composition. During a navigation transition the incoming screen enters last, so it
// wins, and a screen revealed by back re-enters and re-applies its own.

/** Status bar icons: [light] for dark surfaces such as the [TopBand]. */
@Composable
fun StatusBarIcons(light: Boolean) {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    LaunchedEffect(light) {
        WindowCompat.getInsetsController(activity.window, view).isAppearanceLightStatusBars = !light
    }
}

/** Navigation bar icons: [light] over the ink bottom bar, dark over cream. */
@Composable
fun NavigationBarIcons(light: Boolean) {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    LaunchedEffect(light) {
        WindowCompat.getInsetsController(activity.window, view).isAppearanceLightNavigationBars = !light
    }
}
