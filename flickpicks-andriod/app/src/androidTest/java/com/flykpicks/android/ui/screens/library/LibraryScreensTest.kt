package com.flykpicks.android.ui.screens.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.flykpicks.android.model.LibraryItemImpl
import com.flykpicks.android.model.SeasonSummaryDTO
import com.flykpicks.android.model.enums.ItemCondition
import com.flykpicks.android.model.enums.MediaFormat
import com.flykpicks.android.model.enums.MediaType
import com.flykpicks.android.store.LibraryState
import com.flykpicks.android.ui.components.EditLibraryItemSheet
import com.flykpicks.android.ui.components.EditLibraryItemState
import com.flykpicks.android.ui.theme.FlykPicksTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The Library screens' layouts and wiring, over fixed state. Their logic is covered by the JVM tests. */
class LibraryScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private fun copy(
        id: String,
        titleId: String,
        title: String,
        genre: String,
        tv: Boolean = false,
        format: MediaFormat = MediaFormat.DVD,
        seasons: List<Int>? = null,
        appraisalSource: String = "DEFAULT",
    ) = LibraryItemImpl(
        id = id, titleId = titleId, title = title, mediaType = if (tv) MediaType.TV else MediaType.FILM, year = 1995,
        genre = genre, posterUrl = null, season = null, ownedSeasons = seasons, format = format,
        condition = ItemCondition.GOOD, appraisalValue = 12.5, appraisalSource = appraisalSource,
        dateAdded = "2026-02-01T00:00:00Z", overview = "A crew of thieves.",
    )

    private val heat = copy("heat-dvd", "heat", "Heat", "Crime • Drama", appraisalSource = "EBAY")
    private val peaksBluray = copy("peaks-bluray", "peaks", "Twin Peaks", "Mystery", tv = true, format = MediaFormat.BLURAY, seasons = listOf(1))
    private val peaksVhs = copy("peaks-vhs", "peaks", "Twin Peaks", "Mystery", tv = true, format = MediaFormat.VHS)
    private val shelf = LibraryState(items = listOf(heat, peaksBluray), hasLoaded = true)

    private val seasons = listOf(
        SeasonSummaryDTO(1, 8, "1990-04-08", "A body is found.", 8.3),
        SeasonSummaryDTO(2, 22, "1990-09-30", null, 0.0),
    )

    // MARK: Library

    private var query by mutableStateOf("")

    private fun showLibrary(
        library: LibraryState,
        onRetry: () -> Unit = {},
        onOpenTitle: (String) -> Unit = {},
        onRemove: (LibraryItemImpl) -> Unit = {},
    ) {
        compose.setContent {
            FlykPicksTheme {
                LibraryContent(library, query, { query = it }, onRetry, onOpenTitle, onRemove)
            }
        }
    }

    @Test
    fun library_showsARowPerGenre_theCounts_andOpensATitleByItsTitleId() {
        var opened: String? = null
        showLibrary(shelf, onOpenTitle = { opened = it })

        compose.onNodeWithText("CRIME").assertIsDisplayed()
        compose.onNodeWithText("MYSTERY").assertIsDisplayed()
        compose.onNodeWithText("TITLES").assertIsDisplayed()

        compose.onNodeWithText("Twin Peaks").performClick()

        assertEquals("peaks", opened)
    }

    @Test
    fun typingInTheField_filtersTheRows_andSaysWhenNothingMatches() {
        showLibrary(shelf)

        compose.onNode(hasSetTextAction()).performTextInput("twin")
        compose.onNodeWithText("Twin Peaks").assertIsDisplayed()
        compose.onNodeWithText("Heat").assertDoesNotExist()

        compose.onNode(hasSetTextAction()).performTextInput("zzz")
        compose.onNodeWithText("No results found").assertIsDisplayed()
        compose.onNodeWithText("Your library is empty").assertDoesNotExist()
    }

    @Test
    fun longPressingACopy_offersToRemoveIt() {
        var removed: LibraryItemImpl? = null
        showLibrary(shelf, onRemove = { removed = it })

        compose.onNodeWithText("Heat").performTouchInput { longClick() }
        compose.onNodeWithText("Remove from library").performClick()

        assertEquals(heat, removed)
    }

    @Test
    fun anEmptyLibrary_saysSo() {
        showLibrary(LibraryState(hasLoaded = true))

        compose.onNodeWithText("Your library is empty").assertIsDisplayed()
    }

    @Test
    fun aFailedFirstLoad_showsOnlyTheError_andRetries() {
        var retries = 0
        showLibrary(LibraryState(errorMessage = "Couldn't load your library. Please try again."), onRetry = { retries++ })

        compose.onNodeWithText("Couldn't load your library. Please try again.").assertIsDisplayed()
        compose.onNodeWithText("Your library is empty").assertDoesNotExist()
        compose.onNodeWithText("Try Again").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun beforeTheFirstLoadFinishes_nothingClaimsTheLibraryIsEmpty() {
        showLibrary(LibraryState(isLoading = true))

        compose.onNodeWithText("Your library is empty").assertDoesNotExist()
    }

    // MARK: Title

    @Test
    fun aSeries_listsItsCopiesAndSeasons_andWiresEveryAction() {
        var edited: LibraryItemImpl? = null
        var openedSeason: Int? = null
        var addAllTo: LibraryItemImpl? = null
        var backs = 0
        compose.setContent {
            FlykPicksTheme {
                LibraryItemDetailContent(
                    titleId = "peaks",
                    library = LibraryState(items = listOf(heat, peaksBluray), hasLoaded = true),
                    seasons = seasons,
                    seasonsLoading = false,
                    isUpdating = false,
                    onBack = { backs++ },
                    onRetry = {},
                    onEdit = { edited = it },
                    onOpenSeason = { openedSeason = it },
                    onAddAllSeasons = { addAllTo = it },
                )
            }
        }

        compose.onNodeWithText("Twin Peaks").assertIsDisplayed()
        compose.onNodeWithText("1995 • Mystery").assertIsDisplayed()
        compose.onNodeWithText("Heat").assertDoesNotExist()

        compose.onNodeWithText("Edit").performScrollTo().performClick()
        assertEquals(peaksBluray, edited)

        // Season 1 is owned by the Blu-Ray; season 2 by nothing.
        compose.onAllNodesWithText("Owned").assertCountEquals(1)
        compose.onNodeWithText("Season 2").performScrollTo().performClick()
        assertEquals(2, openedSeason)

        compose.onNodeWithText("Add All Seasons — Blu-Ray").performScrollTo().performClick()
        assertEquals(peaksBluray, addAllTo)

        compose.onNodeWithText("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun aFilm_showsItsAppraisal_andNoSeasons() {
        compose.setContent {
            FlykPicksTheme {
                LibraryItemDetailContent(
                    titleId = "heat", library = shelf, seasons = emptyList(), seasonsLoading = false, isUpdating = false,
                    onBack = {}, onRetry = {}, onEdit = {}, onOpenSeason = {}, onAddAllSeasons = {},
                )
            }
        }

        compose.onNodeWithText("Copies").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("eBay").assertExists()
        compose.onNodeWithText("Seasons").assertDoesNotExist()
    }

    // MARK: Season

    @Test
    fun aSeason_separatesCopiesThatOwnIt_fromCopiesItCanBeAddedTo() {
        var added: LibraryItemImpl? = null
        var removedFrom: LibraryItemImpl? = null
        val peaksDvd = copy("peaks-dvd", "peaks", "Twin Peaks", "Mystery", tv = true, format = MediaFormat.DVD, seasons = listOf(1))
        compose.setContent {
            FlykPicksTheme {
                TVSeasonDetailContent(
                    seriesTitle = "Twin Peaks",
                    seasonNumber = 2,
                    season = seasons[1],
                    // Season 2: the Blu-Ray lists it, the VHS owns the whole series, the DVD lacks it.
                    copies = listOf(peaksBluray.copy(ownedSeasons = listOf(1, 2)), peaksVhs, peaksDvd),
                    isUpdating = false,
                    onBack = {},
                    onEdit = {},
                    onAddSeason = { added = it },
                    onRemoveSeason = { removedFrom = it },
                )
            }
        }

        compose.onNodeWithText("TWIN PEAKS").assertIsDisplayed()
        compose.onNodeWithText("22 episodes").assertIsDisplayed()
        compose.onNodeWithText("Owns full series").assertIsDisplayed()
        // Only the copy with a season list can have a season taken off.
        compose.onAllNodesWithText("Remove").assertCountEquals(1)

        compose.onNodeWithText("Remove").performClick()
        assertEquals("peaks-bluray", removedFrom?.id)

        compose.onNodeWithText("Add Season 2").performScrollTo().performClick()
        assertEquals(peaksDvd, added)
    }

    @Test
    fun whileAChangeIsInFlight_theSeasonButtonsAreDisabled() {
        compose.setContent {
            FlykPicksTheme {
                TVSeasonDetailContent(
                    seriesTitle = "Twin Peaks", seasonNumber = 2, season = null, copies = listOf(peaksBluray),
                    isUpdating = true, onBack = {}, onEdit = {}, onAddSeason = {}, onRemoveSeason = {},
                )
            }
        }

        compose.onNodeWithText("You don't own this season.").assertIsDisplayed()
        compose.onNodeWithText("Add Season 2").assertIsNotEnabled()
    }

    // MARK: Edit sheet

    private var sheet by mutableStateOf(
        EditLibraryItemState("Heat", posterUrl = null, currentFormat = MediaFormat.DVD, currentCondition = ItemCondition.GOOD, canRemove = true),
    )

    @Test
    fun theEditSheet_savesOnlyAChange_andConfirmsBeforeRemoving() {
        var saves = 0
        var removals = 0
        compose.setContent {
            FlykPicksTheme {
                EditLibraryItemSheet(
                    state = sheet,
                    onFormatChange = { sheet = sheet.copy(format = it) },
                    onConditionChange = { sheet = sheet.copy(condition = it) },
                    onSave = { saves++ },
                    onCancel = {},
                    onRemove = { removals++ },
                )
            }
        }

        compose.onNodeWithText("Save").assertIsNotEnabled()

        compose.onNodeWithText("DVD").performClick()
        compose.onNodeWithText("Blu-Ray").performClick()
        assertEquals(MediaFormat.BLURAY, sheet.format)
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(1, saves)

        compose.onNodeWithText("Remove from library").performClick()
        assertEquals("removal waits for the confirmation", 0, removals)
        compose.onNodeWithText("Remove").performClick()
        assertEquals(1, removals)
    }

    @Test
    fun theEditSheet_showsAFailure_andLocksItsButtonsWhileSaving() {
        sheet = sheet.copy(format = MediaFormat.VHS, isSaving = true, errorMessage = null)
        var cancels = 0
        compose.setContent {
            FlykPicksTheme {
                EditLibraryItemSheet(sheet, {}, {}, onSave = {}, onCancel = { cancels++ }, onRemove = {})
            }
        }

        compose.onNodeWithText("Saving...").assertIsNotEnabled()
        compose.onNodeWithText("Remove from library").assertIsNotEnabled()

        compose.runOnIdle { sheet = sheet.copy(isSaving = false, errorMessage = "That copy is in an open order.") }

        compose.onNodeWithText("That copy is in an open order.").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsEnabled()
        assertEquals("nothing dismissed the sheet", 0, cancels)
    }
}
