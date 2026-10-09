package com.flykpicks.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flykpicks.android.ui.components.ToastStore
import com.flykpicks.android.ui.navigation.RootNavigation
import com.flykpicks.android.ui.screens.auth.AuthViewModel
import com.flykpicks.android.ui.theme.FlykPicksTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var toastStore: ToastStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent bars. Icon colors are set by whatever each screen draws behind them
        // (`StatusBarIcons` / `NavigationBarIcons`).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            FlykPicksTheme {
                // Activity-scoped: the shell and every auth screen share it.
                val authViewModel: AuthViewModel = hiltViewModel()
                RootNavigation(authViewModel, toastStore)
            }
        }
    }
}
