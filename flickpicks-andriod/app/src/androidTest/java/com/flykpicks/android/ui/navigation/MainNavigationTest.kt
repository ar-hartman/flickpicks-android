package com.flykpicks.android.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import androidx.test.espresso.Espresso
import com.flykpicks.android.ui.navigation.NavRoutes.Companion.ARG_FLYKS_ID
import com.flykpicks.android.ui.navigation.NavRoutes.Companion.ARG_SEASON
import com.flykpicks.android.ui.navigation.NavRoutes.Companion.ARG_SOURCE
import com.flykpicks.android.ui.navigation.NavRoutes.Companion.ARG_TITLE_ID
import com.flykpicks.android.ui.theme.FlykPicksTheme
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tab and back-stack behavior of the signed-in shell, driven through placeholder screens. The
 * Library and Discover tabs' real screens need Hilt and a server, so stand-in graphs with the
 * same routes take their place here.
 */
class MainNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private var pendingTab by mutableStateOf<MainTab?>(null)

    @Before
    fun setUp() {
        compose.setContent {
            FlykPicksTheme {
                MainScaffold(
                    pendingTab = pendingTab,
                    onTabRequestHandled = { pendingTab = null },
                    backupCodeAlert = null,
                    onBackupCodeAlertDismiss = {},
                    onBackupCodeAlertGoToSecurity = {},
                    toast = null,
                ) { nav ->
                    placeholderLibraryGraph(nav)
                    placeholderSearchGraph(nav)
                    profileGraph(nav, ShellPlaceholderActions(signOut = {}, showToast = {}))
                }
            }
        }
    }

    private fun tab(label: String): SemanticsNodeInteraction = compose.onNode(hasText(label) and isSelectable())

    private fun open(label: String) {
        compose.onNodeWithText(label).performClick()
    }

    // Section headers are uppercase, so these never match a tab label or top bar title.
    private fun assertShowing(text: String) {
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun startsOnLibrary() {
        tab("Library").assertIsSelected()
        assertShowing("LIBRARY")
    }

    @Test
    fun tabsKeepTheirBackStacks_exceptDiscover_whichResetsLikeIos() {
        open("The Third Man")
        assertShowing("titleId = title-1")
        tab("Profile").performClick()
        open("Privacy & Security")
        assertShowing("SECURITY")
        tab("Discover").performClick()
        open("Brazil (popular movies)")
        assertShowing("source = recommendation.popularMovies")

        tab("Library").performClick()
        assertShowing("titleId = title-1")
        tab("Profile").performClick()
        assertShowing("SECURITY")
        tab("Discover").performClick()
        assertShowing("DISCOVER")
    }

    @Test
    fun reselectingTheCurrentTab_popsToItsFirstScreen() {
        open("Twin Peaks")
        open("Season 2")
        assertShowing("seasonNumber = 2")

        tab("Library").performClick()

        assertShowing("LIBRARY")
    }

    @Test
    fun systemBack_popsWithinTheTab_thenReturnsToLibrary() {
        tab("Profile").performClick()
        open("Disputes")
        open("Dispute #77")
        assertShowing("disputeId = dispute-77")

        Espresso.pressBack()
        assertShowing("DISPUTES")
        Espresso.pressBack()
        assertShowing("PROFILE")
        Espresso.pressBack()
        assertShowing("LIBRARY")
        tab("Library").assertIsSelected()
    }

    @Test
    fun tabRequest_switchesTabs_andIsMarkedHandled() {
        compose.runOnIdle { pendingTab = MainTab.Profile }

        assertShowing("PROFILE")
        tab("Profile").assertIsSelected()
        compose.runOnIdle { assertNull(pendingTab) }
    }

    @Test
    fun routeArguments_roundTripThroughEncoding() {
        tab("Discover").performClick()
        open("Columbo (search result)")

        assertShowing("flyksId = t 1/x")
        assertShowing("source = search.results")
    }

    @Test
    fun optionalQueryArgument_isNullWhenOmitted() {
        tab("Profile").performClick()
        open("Shipping Addresses")
        open("New Address")
        assertShowing("id = —")
        Espresso.pressBack()
        open("Edit 1 Main St")
        assertShowing("id = addr-1")
    }
}

private fun NavGraphBuilder.placeholderLibraryGraph(nav: NavController) {
    navigation(startDestination = MainTab.Library.startRoute, route = MainTab.Library.graphRoute) {
        composable(NavRoutes.Library.route) {
            TabRootPlaceholder(
                title = "Library",
                phase = 5,
                links = listOf(
                    PlaceholderLink("The Third Man", { nav.navigate(NavRoutes.LibraryDetail.build("title-1")) }),
                    PlaceholderLink("Twin Peaks", { nav.navigate(NavRoutes.LibraryDetail.build("title-2")) }),
                ),
            )
        }
        composable(NavRoutes.LibraryDetail.route) { entry ->
            val titleId = entry.arguments?.getString(ARG_TITLE_ID)
            PushedPlaceholder(
                title = "Library Item",
                phase = 5,
                onBack = nav::popBackStack,
                args = listOf(ARG_TITLE_ID to titleId),
                links = listOf(
                    PlaceholderLink("Season 1", { nav.navigate(NavRoutes.TVSeasonDetail.build(titleId.orEmpty(), 1)) }),
                    PlaceholderLink("Season 2", { nav.navigate(NavRoutes.TVSeasonDetail.build(titleId.orEmpty(), 2)) }),
                ),
            )
        }
        composable(
            NavRoutes.TVSeasonDetail.route,
            arguments = listOf(navArgument(ARG_SEASON) { type = NavType.IntType }),
        ) { entry ->
            val season = entry.arguments?.getInt(ARG_SEASON)
            PushedPlaceholder(
                title = "Season $season",
                phase = 5,
                onBack = nav::popBackStack,
                args = listOf(ARG_TITLE_ID to entry.arguments?.getString(ARG_TITLE_ID), ARG_SEASON to season?.toString()),
            )
        }
    }
}

private fun NavGraphBuilder.placeholderSearchGraph(nav: NavController) {
    navigation(startDestination = MainTab.Search.startRoute, route = MainTab.Search.graphRoute) {
        composable(NavRoutes.Search.route) {
            TabRootPlaceholder(
                title = "Discover",
                phase = 6,
                links = listOf(
                    PlaceholderLink("Brazil (popular movies)", { nav.navigate(NavRoutes.SearchDetail.build("m1", "recommendation.popularMovies")) }),
                    PlaceholderLink("Columbo (search result)", { nav.navigate(NavRoutes.SearchDetail.build("t 1/x", "search.results")) }),
                ),
            )
        }
        composable(NavRoutes.SearchDetail.route) { entry ->
            PushedPlaceholder(
                title = "Title Detail",
                phase = 6,
                onBack = nav::popBackStack,
                args = listOf(ARG_FLYKS_ID to entry.arguments?.getString(ARG_FLYKS_ID), ARG_SOURCE to entry.arguments?.getString(ARG_SOURCE)),
            )
        }
    }
}
