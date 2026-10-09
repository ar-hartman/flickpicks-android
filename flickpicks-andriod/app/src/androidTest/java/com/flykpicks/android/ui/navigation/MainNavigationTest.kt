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
import androidx.test.espresso.Espresso
import com.flykpicks.android.ui.theme.FlykPicksTheme
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Tab and back-stack behavior of the signed-in shell, driven through the placeholder screens. */
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
                    placeholderActions = ShellPlaceholderActions(signOut = {}, showToast = {}),
                )
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
