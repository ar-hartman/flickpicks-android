package com.flykpicks.android.ui.screens.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.flykpicks.android.model.MediaItemImpl
import com.flykpicks.android.model.SeasonSummaryDTO
import com.flykpicks.android.model.enums.ItemCondition
import com.flykpicks.android.model.enums.MediaFormat
import com.flykpicks.android.model.enums.MediaType
import com.flykpicks.android.store.RecommendationSection
import com.flykpicks.android.store.RecommendationSectionState
import com.flykpicks.android.store.RecommendationsState
import com.flykpicks.android.store.SearchResults
import com.flykpicks.android.ui.components.AddToLibraryContent
import com.flykpicks.android.ui.components.AddToLibraryState
import com.flykpicks.android.ui.theme.FlykPicksTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** The Discover screens' layouts and wiring, over fixed state. Their logic is covered by the JVM tests. */
class SearchScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private fun title(id: String, title: String, genre: String, tv: Boolean = false) = MediaItemImpl(
        id = id, flyksId = id, title = title, mediaType = if (tv) MediaType.TV else MediaType.FILM, originalLanguage = "en",
        genre = genre, popularity = 0.0, releaseDate = "1995-12-15", voteAverage = 7.0, voteCount = 10, posterUrl = null,
        overview = "A crew of thieves.",
    )

    private val heat = title("heat", "Heat", "Crime • Drama")
    private val ronin = title("ronin", "Ronin", "Thriller")
    private val peaks = title("peaks", "Twin Peaks", "Mystery", tv = true)

    // MARK: Discover

    private var query by mutableStateOf("")
    private var searches = 0
    private var opened: Pair<String, String>? = null
    private var retried: RecommendationSection? = null

    private fun showDiscover(
        hasSearched: Boolean = false,
        results: SearchResults = SearchResults(),
        recommendations: RecommendationsState = RecommendationsState(),
    ) {
        compose.setContent {
            FlykPicksTheme {
                SearchContent(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { searches++ },
                    hasSearched = hasSearched,
                    results = results,
                    recommendations = recommendations,
                    isRefreshing = false,
                    onRefresh = {},
                    onRetrySection = { retried = it },
                    onOpenTitle = { item, source -> opened = item.flyksId to source },
                )
            }
        }
    }

    @Test
    fun beforeASearch_everyRowIsListed_andATitleOpensWithItsRowsSource() {
        showDiscover(
            recommendations = RecommendationsState(
                RecommendationSection.entries.associateWith { RecommendationSectionState() } +
                    (RecommendationSection.TOP_RATED_TV to RecommendationSectionState(items = listOf(peaks))),
            ),
        )

        // The rows' own lists scroll sideways; this is the one that scrolls down.
        val feed = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        for (section in RecommendationSection.entries) {
            feed.performScrollToNode(hasText(section.displayTitle.uppercase()))
            compose.onNodeWithText(section.displayTitle.uppercase()).assertIsDisplayed()
        }
        compose.onNodeWithText("Twin Peaks").performClick()

        assertEquals("peaks" to "recommendation.topRatedTV", opened)
    }

    @Test
    fun typingAndSubmitting_searches_fromTheKeyboardOrTheArrow() {
        showDiscover()

        compose.onNode(hasSetTextAction()).performTextInput("heat")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithContentDescription("Search").performClick()

        assertEquals("heat", query)
        assertEquals(2, searches)
    }

    @Test
    fun results_showByGenre_andOpenAsSearchResults() {
        showDiscover(hasSearched = true, results = SearchResults("heat", listOf(ronin, heat)))

        compose.onNodeWithText("CRIME").assertIsDisplayed()
        compose.onNodeWithText("THRILLER").assertIsDisplayed()
        compose.onNodeWithText("BECAUSE YOU OWN IT · MOVIES").assertDoesNotExist()
        compose.onNodeWithText("Heat").performClick()

        assertEquals("heat" to "search.results", opened)
    }

    @Test
    fun whileSearching_aSpinnerSaysSo() {
        showDiscover(hasSearched = true, results = SearchResults("heat", isLoading = true))

        compose.onNodeWithText("Searching…").assertIsDisplayed()
    }

    @Test
    fun noResults_andAFailure_areToldApart() {
        var results by mutableStateOf(SearchResults("zzz"))
        compose.setContent {
            FlykPicksTheme {
                SearchContent(
                    query = "zzz", onQueryChange = {}, onSearch = { searches++ }, hasSearched = true, results = results,
                    recommendations = RecommendationsState(), isRefreshing = false, onRefresh = {}, onRetrySection = {},
                    onOpenTitle = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText("No results found").assertIsDisplayed()

        compose.runOnIdle { results = SearchResults("zzz", errorMessage = "No internet connection. Check your connection and try again.") }
        compose.onNodeWithText("No internet connection. Check your connection and try again.").assertIsDisplayed()
        compose.onNodeWithText("No results found").assertDoesNotExist()
        compose.onNodeWithText("Try Again").performClick()
        assertEquals(1, searches)
    }

    // MARK: A row

    private fun showRow(state: RecommendationSectionState) {
        compose.setContent {
            FlykPicksTheme {
                RecommendationRow(RecommendationSection.INVENTORY_TV, state, onRetry = { retried = RecommendationSection.INVENTORY_TV }, onItemClick = {})
            }
        }
    }

    @Test
    fun aRowWaitingForItsFirstAnswer_saysLoading() {
        showRow(RecommendationSectionState(isLoading = true))

        compose.onNodeWithText("Loading…").assertIsDisplayed()
        compose.onNodeWithContentDescription("Loading").assertIsDisplayed()
        compose.onNodeWithText("See All").assertDoesNotExist()
    }

    @Test
    fun aRowThatFailed_offersRetry() {
        showRow(RecommendationSectionState(errorMessage = "Offline"))

        compose.onNodeWithText("Couldn't load this section.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()

        assertEquals(RecommendationSection.INVENTORY_TV, retried)
    }

    @Test
    fun anEmptyRow_saysSo_andARefreshingRowKeepsItsPosters() {
        var state by mutableStateOf(RecommendationSectionState())
        compose.setContent {
            FlykPicksTheme { RecommendationRow(RecommendationSection.INVENTORY_TV, state, onRetry = {}, onItemClick = {}) }
        }

        compose.onNodeWithText("Nothing to show yet.").assertIsDisplayed()
        compose.onNodeWithText("0 titles").assertIsDisplayed()

        compose.runOnIdle { state = RecommendationSectionState(items = listOf(peaks), isLoading = true) }
        compose.onNodeWithText("Twin Peaks").assertIsDisplayed()
        compose.onNodeWithText("1 title").assertIsDisplayed()
        compose.onNodeWithText("See All").assertIsDisplayed()
    }

    // MARK: Detail

    @Test
    fun theDetail_showsTheTitle_saysWhenItsOwned_andOffersToAddIt() {
        var adds = 0
        var inLibrary by mutableStateOf(false)
        compose.setContent {
            FlykPicksTheme {
                SearchItemDetailContent(heat, "search.results", inLibrary, loadError = null, onBack = {}, onRetry = {}, onAdd = { adds++ })
            }
        }

        compose.onNodeWithText("Heat").assertIsDisplayed()
        compose.onNodeWithText("1995-12-15 • Crime • Drama").assertIsDisplayed()
        compose.onNodeWithText("Film").assertIsDisplayed()
        compose.onNodeWithText("In Your Library").assertDoesNotExist()

        compose.runOnIdle { inLibrary = true }
        compose.onNodeWithText("In Your Library").assertIsDisplayed()
        compose.onNodeWithText("Add To Inventory").performScrollTo().performClick()
        assertEquals(1, adds)
    }

    @Test
    fun aRestoredDetail_thatCouldntLoad_offersTryAgain() {
        var retries = 0
        compose.setContent {
            FlykPicksTheme {
                SearchItemDetailContent(null, "search.results", false, loadError = "No media item found.", onBack = {}, onRetry = { retries++ }, onAdd = {})
            }
        }

        compose.onNodeWithText("No media item found.").assertIsDisplayed()
        compose.onNodeWithText("Try Again").performClick()
        assertEquals(1, retries)
    }

    // MARK: Add sheet

    private val seasons = listOf(SeasonSummaryDTO(1, 8, null, null, 0.0), SeasonSummaryDTO(2, 22, null, null, 0.0))

    private var sheet by mutableStateOf(AddToLibraryState(isTV = true))

    private fun showSheet(onSave: () -> Unit = {}) {
        compose.setContent {
            FlykPicksTheme {
                AddToLibraryContent(
                    title = "Twin Peaks", posterUrl = null, flyksId = null, state = sheet, seasons = seasons,
                    seasonsLoading = false, onEdit = { sheet = it(sheet) }, onSave = onSave, onCancel = {},
                )
            }
        }
    }

    @Test
    fun aWholeSeries_savesInOneFormatAndCondition() {
        var saves = 0
        showSheet { saves++ }

        compose.onNodeWithText("Format").assertIsDisplayed()
        compose.onNodeWithText("Blu-Ray").performClick()
        compose.onNodeWithText("4K UHD").performClick()
        compose.onNodeWithText("Save").assertIsEnabled().performClick()

        assertEquals(MediaFormat.UHD_4K, sheet.format)
        assertEquals(1, saves)
    }

    @Test
    fun pickingSeasons_needsAConfirmation_thenEachSeasonGetsItsOwnCopy() {
        showSheet()

        // With "All Seasons" on, its switch is the only toggle in the sheet.
        compose.onNode(isToggleable()).assertIsOn().performClick()
        compose.onNodeWithText("Format").assertDoesNotExist()
        compose.onNodeWithText("Select at least one season to continue.").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsNotEnabled()

        compose.onNodeWithText("Season 1").performClick()
        compose.onNodeWithText("Confirm your season selection above to continue.").assertIsDisplayed()
        compose.onNodeWithText("Confirm 1 Season →").performClick()

        compose.onNodeWithText("1 Season Selected").assertIsDisplayed()
        compose.onNodeWithText("Blu-Ray").performClick()
        compose.onNodeWithText("DVD").performClick()
        compose.onNodeWithText("Save").assertIsEnabled()

        assertEquals(setOf(1), sheet.selectedSeasons)
        assertEquals(MediaFormat.DVD, sheet.formatOf(1))
        assertEquals(ItemCondition.VERY_GOOD, sheet.conditionOf(1))
        assertNull(sheet.errorMessage)
    }
}
