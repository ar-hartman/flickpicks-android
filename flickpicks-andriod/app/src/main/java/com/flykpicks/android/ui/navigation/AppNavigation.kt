package com.flykpicks.android.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.flykpicks.android.ui.components.NavigationBarIcons
import com.flykpicks.android.ui.components.ToastHost
import com.flykpicks.android.ui.components.ToastMessage
import com.flykpicks.android.ui.components.ToastStore
import com.flykpicks.android.ui.screens.auth.AuthViewModel
import com.flykpicks.android.ui.screens.auth.BackupCodeAlert
import com.flykpicks.android.ui.screens.auth.ForgotPasswordFlow
import com.flykpicks.android.ui.screens.auth.LoginScreen
import com.flykpicks.android.ui.screens.auth.MFAChallengeScreen
import com.flykpicks.android.ui.screens.auth.RegistrationScreen
import com.flykpicks.android.ui.theme.FlykColors

/**
 * Blueprint §4.1 / §4.4, iOS `RootView`: the auth graph while signed out, the tabbed app while
 * signed in. Each side owns its NavController, so switching discards the other's back stack;
 * signing in always starts on the Library tab.
 */
@Composable
fun RootNavigation(authViewModel: AuthViewModel, toastStore: ToastStore) {
    val auth by authViewModel.uiState.collectAsStateWithLifecycle()
    val toast by toastStore.current.collectAsStateWithLifecycle()
    if (auth.isAuthenticated) {
        MainScaffold(
            pendingTab = auth.pendingTabSelection,
            onTabRequestHandled = authViewModel::onTabRequestHandled,
            backupCodeAlert = auth.backupCodeAlert,
            onBackupCodeAlertDismiss = authViewModel::dismissBackupCodeAlert,
            onBackupCodeAlertGoToSecurity = authViewModel::openSecurityFromBackupCodeAlert,
            toast = toast,
        ) { nav ->
            libraryGraph(nav)
            searchGraph(nav)
            profileGraph(
                nav,
                ShellPlaceholderActions(signOut = { authViewModel.logout() }, showToast = toastStore::show),
            )
        }
    } else {
        AuthNavHost(authViewModel)
    }
}

/**
 * Login and the screens it leads to (blueprint §10.1–10.4). An MFA challenge opening pushes the
 * MFA screen and its ending pops it, so back from MFA cancels the challenge. Cream at the
 * bottom, so dark navigation bar icons.
 */
@Composable
fun AuthNavHost(authViewModel: AuthViewModel, navController: NavHostController = rememberNavController()) {
    NavigationBarIcons(light = false)
    val auth by authViewModel.uiState.collectAsStateWithLifecycle()
    val hasChallenge = auth.pendingMFA != null
    LaunchedEffect(hasChallenge) {
        val onMfa = navController.currentDestination?.route == NavRoutes.MFAChallenge.route
        if (hasChallenge && !onMfa) navController.navigate(NavRoutes.MFAChallenge.route) { launchSingleTop = true }
        if (!hasChallenge && onMfa) navController.popBackStack()
    }
    val backToLogin: () -> Unit = { navController.popBackStack(NavRoutes.Login.route, inclusive = false) }

    NavHost(
        navController = navController,
        startDestination = NavRoutes.Login.route,
        enterTransition = pushEnter,
        exitTransition = pushExit,
        popEnterTransition = popEnter,
        popExitTransition = popExit,
    ) {
        composable(NavRoutes.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onCreateAccount = { navController.navigate(NavRoutes.Register.route) },
                onForgotPassword = { navController.navigate(NavRoutes.ForgotPassword.route) },
            )
        }
        composable(NavRoutes.Register.route) {
            RegistrationScreen(
                viewModel = hiltViewModel(),
                onBackToSignIn = { email ->
                    if (email != null) authViewModel.loginEmail = email
                    backToLogin()
                },
            )
        }
        composable(NavRoutes.ForgotPassword.route) {
            ForgotPasswordFlow(viewModel = hiltViewModel(), onClose = backToLogin)
        }
        composable(NavRoutes.MFAChallenge.route) { MFAChallengeScreen(authViewModel) }
    }
}

/**
 * The signed-in app (iOS `RootView`'s TabView): one NavHost holding a nested graph per tab,
 * the ink bottom bar, the toast above it, and the backup-code alert.
 *
 * Tabs keep their back stacks, except Search, which iOS resets whenever you leave it.
 * Re-selecting the current tab pops it to its first screen. System back pops within the tab,
 * then returns to Library, then leaves the app.
 *
 * [tabGraphs] adds the three tabs' graphs. The shell doesn't know what is in them, which lets
 * its tests run it over stand-in screens.
 */
@Composable
fun MainScaffold(
    pendingTab: MainTab?,
    onTabRequestHandled: () -> Unit,
    backupCodeAlert: BackupCodeAlert?,
    onBackupCodeAlertDismiss: () -> Unit,
    onBackupCodeAlertGoToSecurity: () -> Unit,
    toast: ToastMessage?,
    navController: NavHostController = rememberNavController(),
    tabGraphs: NavGraphBuilder.(NavController) -> Unit,
) {
    NavigationBarIcons(light = true)
    val entry by navController.currentBackStackEntryAsState()
    val currentTab = entry?.destination?.tab()

    LaunchedEffect(pendingTab) {
        if (pendingTab != null) {
            navController.navigateToTab(pendingTab)
            onTabRequestHandled()
        }
    }

    Scaffold(
        bottomBar = { FlykBottomBar(selected = currentTab, onSelect = navController::navigateToTab) },
        containerColor = FlykColors.Canvas,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = MainTab.Library.graphRoute,
                enterTransition = pushEnter,
                exitTransition = pushExit,
                popEnterTransition = popEnter,
                popExitTransition = popExit,
            ) {
                tabGraphs(navController)
            }
            // iOS: 80pt above the safe area, i.e. 31pt above its 49pt tab bar.
            ToastHost(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = 31.dp))
        }
    }

    if (backupCodeAlert != null) {
        BackupCodeAlertDialog(backupCodeAlert, onBackupCodeAlertDismiss, onBackupCodeAlertGoToSecurity)
    }
}

/** Switches tabs the way iOS's TabView does (see [MainScaffold]). */
fun NavController.navigateToTab(tab: MainTab) {
    val current = currentDestination?.tab()
    if (current == tab) {
        popBackStack(tab.startRoute, inclusive = false)
        return
    }
    navigate(tab.graphRoute) {
        popUpTo(graph.findStartDestination().id) { saveState = current != MainTab.Search }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BackupCodeAlertDialog(alert: BackupCodeAlert, onDismiss: () -> Unit, onGoToSecurity: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup Code Used") },
        text = { Text(alert.message) },
        confirmButton = {
            if (alert.offersSecurityLink) TextButton(onClick = onGoToSecurity) { Text("Go to Security") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Dismiss") } },
    )
}
