package com.flykpicks.android.ui.screens.search

import androidx.lifecycle.SavedStateHandle
import com.flykpicks.android.api.endpoints.FlykSource
import com.flykpicks.android.model.enums.ItemCondition
import com.flykpicks.android.model.enums.MediaFormat
import com.flykpicks.android.store.RecommendationSection
import com.flykpicks.android.testing.LIBRARY_ADD_PATH
import com.flykpicks.android.testing.PEAKS_BLURAY
import com.flykpicks.android.testing.Routes
import com.flykpicks.android.testing.SEARCH_PATH
import com.flykpicks.android.testing.SEASONS_JSON
import com.flykpicks.android.testing.TestNetwork
import com.flykpicks.android.testing.TestStores
import com.flykpicks.android.testing.errorJson
import com.flykpicks.android.testing.eventually
import com.flykpicks.android.testing.heatJson
import com.flykpicks.android.testing.json
import com.flykpicks.android.testing.libraryListJson
import com.flykpicks.android.testing.mediaListJson
import com.flykpicks.android.testing.movieItemJson
import com.flykpicks.android.testing.path
import com.flykpicks.android.testing.tvItemJson
import com.flykpicks.android.testing.twinPeaksJson
import com.flykpicks.android.ui.components.AddToLibraryRequest
import com.flykpicks.android.ui.components.AddToLibraryState
import com.flykpicks.android.ui.navigation.NavRoutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val HEAT_ITEM_PATH = "v1/media/heat"
private const val PEAKS_ITEM_PATH = "v1/media/peaks"
private const val PEAKS_SEASONS_PATH = "v1/media/tv/peaks/seasons"

/** A title opened from Discover and its add sheet, against the production networking stack. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchItemDetailViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var net: TestNetwork
    private lateinit var stores: TestStores
    private val routes = Routes()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val heat = movieItemJson("heat", "Heat", "[80, 18]")
    private val peaks = tvItemJson("peaks", "Twin Peaks")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer().apply { dispatcher = routes; start() }
        net = TestNetwork(server).apply { signIn() }
        stores = TestStores(net, appScope)
        routes.on(HEAT_ITEM_PATH, json(heat))
        routes.on(PEAKS_ITEM_PATH, json(peaks))
        routes.on(PEAKS_SEASONS_PATH, json(SEASONS_JSON))
    }

    @After
    fun tearDown() {
        appScope.cancel()
        server.close()
        Dispatchers.resetMain()
    }

    /** Opens [flyksId] as a search result, as the Search screen does. */
    private fun openFromSearch(flyksId: String, source: String = FlykSource.SEARCH_RESULTS): SearchItemDetailViewModel {
        routes.on(SEARCH_PATH, json(mediaListJson(heat, peaks)))
        runBlocking { stores.search.search("anything") }
        return viewModel(flyksId, source)
    }

    private fun viewModel(flyksId: String, source: String) = SearchItemDetailViewModel(
        SavedStateHandle(mapOf(NavRoutes.ARG_FLYKS_ID to flyksId, NavRoutes.ARG_SOURCE to source)),
        stores.search,
        stores.recommendations,
        stores.library,
        stores.asset,
        appScope,
    )

    private fun SearchItemDetailViewModel.save() = runBlocking { saveToLibrary().join() }

    // MARK: Showing the title

    @Test
    fun aSearchResult_showsAtOnce_andReportsTheViewWithItsSource() {
        val vm = openFromSearch("heat")

        assertEquals("Heat", vm.item?.title)
        routes.awaitRequests(HEAT_ITEM_PATH, 1)
        assertEquals(FlykSource.SEARCH_RESULTS, routes.requestsTo(HEAT_ITEM_PATH).single().headers[FlykSource.HEADER])
        assertTrue(routes.requestsTo(PEAKS_SEASONS_PATH).isEmpty())
    }

    @Test
    fun aRecommendation_isFoundInItsRow_andAttributedToIt() {
        for (section in RecommendationSection.entries - RecommendationSection.POPULAR_TV) routes.on(section.path, json("[]"))
        routes.on(RecommendationSection.POPULAR_TV.path, json(mediaListJson(peaks)))
        runBlocking { stores.recommendations.loadIfNeeded() }

        val vm = viewModel("peaks", RecommendationSection.POPULAR_TV.source)

        assertEquals("Twin Peaks", vm.item?.title)
        routes.awaitRequests(PEAKS_ITEM_PATH, 1)
        assertEquals("recommendation.popularTV", routes.requestsTo(PEAKS_ITEM_PATH).single().headers[FlykSource.HEADER])
    }

    @Test
    fun aSeries_fetchesItsSeasons_withTheSameSource() {
        val vm = openFromSearch("peaks")

        routes.awaitRequests(PEAKS_SEASONS_PATH, 1)
        assertEquals(FlykSource.SEARCH_RESULTS, routes.requestsTo(PEAKS_SEASONS_PATH).single().headers[FlykSource.HEADER])
        eventually { vm.seasons.size == 3 }
        assertFalse(vm.seasonsLoading)
    }

    @Test
    fun afterProcessDeath_theTitleComesFromTheServer() {
        val vm = viewModel("peaks", FlykSource.SEARCH_RESULTS)

        eventually { vm.item != null }
        assertEquals("Twin Peaks", vm.item?.title)
        assertEquals(1, routes.requestsTo(PEAKS_ITEM_PATH).size)
        routes.awaitRequests(PEAKS_SEASONS_PATH, 1)
    }

    @Test
    fun aRestoredTitleTheServerCantFind_saysWhy_andRetryAsksAgain() {
        routes.on("v1/media/gone", errorJson(404, "not_found", "No media item found."), json(movieItemJson("gone", "Found it")))

        val vm = viewModel("gone", FlykSource.SEARCH_RESULTS)
        eventually { vm.loadError != null }
        assertNull(vm.item)
        assertEquals("No media item found.", vm.loadError)

        runBlocking { vm.retry().join() }

        assertEquals("Found it", vm.item?.title)
        assertNull(vm.loadError)
    }

    // MARK: Adding

    @Test
    fun addingAFilm_sendsOneCopy_closesTheSheet_andTheLibraryHasIt() {
        routes.on(LIBRARY_ADD_PATH, json(libraryListJson(heatJson())))
        val vm = openFromSearch("heat")

        vm.openAddSheet()
        assertEquals(AddToLibraryState(isTV = false), vm.addSheet)
        vm.editAddSheet { it.copy(format = MediaFormat.DVD) }
        vm.save()

        assertEquals(
            """{"mediaItemFlyksID":"heat","format":"DVD","condition":"VERY_GOOD","selectAllSeasons":true}""",
            routes.bodyOf(LIBRARY_ADD_PATH),
        )
        assertEquals(FlykSource.LIBRARY_DETAIL, routes.requestsTo(LIBRARY_ADD_PATH).single().headers[FlykSource.HEADER])
        assertNull(vm.addSheet)
        assertTrue(vm.library.value.items("heat").isNotEmpty())
    }

    @Test
    fun addingAWholeSeries_sendsAllSeasons() {
        routes.on(LIBRARY_ADD_PATH, json(libraryListJson(twinPeaksJson())))
        val vm = openFromSearch("peaks")

        vm.openAddSheet()
        vm.save()

        assertEquals(
            """{"mediaItemFlyksID":"peaks","format":"BLURAY","condition":"VERY_GOOD","selectAllSeasons":true}""",
            routes.bodyOf(LIBRARY_ADD_PATH),
        )
        assertNull(vm.addSheet)
    }

    @Test
    fun pickedSeasons_goOneCopyPerFormatAndCondition_lowestSeasonFirst() {
        routes.on(LIBRARY_ADD_PATH, json(libraryListJson(twinPeaksJson())), json(libraryListJson(twinPeaksJson())))
        val vm = openFromSearch("peaks")

        vm.openAddSheet()
        vm.editAddSheet { it.withAllSeasons(false).toggling(3).toggling(1).toggling(2) }
        assertFalse(vm.addSheet!!.canSave)
        vm.editAddSheet { it.copy(seasonsConfirmed = true).withSeasonFormat(2, MediaFormat.DVD).withSeasonCondition(2, ItemCondition.GOOD) }
        vm.save()

        assertEquals(
            listOf(
                """{"mediaItemFlyksID":"peaks","format":"BLURAY","condition":"VERY_GOOD","ownedSeasons":[1,3],"selectAllSeasons":false}""",
                """{"mediaItemFlyksID":"peaks","format":"DVD","condition":"GOOD","ownedSeasons":[2],"selectAllSeasons":false}""",
            ),
            routes.requestsTo(LIBRARY_ADD_PATH).map { it.body!!.utf8() },
        )
        assertNull(vm.addSheet)
    }

    @Test
    fun whenALaterCopyFails_theSeasonsAlreadyAddedLeaveTheSelection_soSavingAgainSendsOnlyTheRest() {
        routes.on(
            LIBRARY_ADD_PATH,
            json(libraryListJson(twinPeaksJson())),
            errorJson(500, "server_error", "Couldn't add that."),
            json(libraryListJson(twinPeaksJson(PEAKS_BLURAY))),
        )
        val vm = openFromSearch("peaks")
        vm.openAddSheet()
        vm.editAddSheet { it.withAllSeasons(false).toggling(1).toggling(2).copy(seasonsConfirmed = true).withSeasonFormat(2, MediaFormat.DVD) }

        vm.save()

        val sheet = vm.addSheet!!
        assertEquals("Added Season 1. Couldn't add that.", sheet.errorMessage)
        assertEquals(setOf(2), sheet.selectedSeasons)
        assertFalse(sheet.isSaving)

        vm.save()

        assertEquals(3, routes.requestsTo(LIBRARY_ADD_PATH).size)
        assertTrue(routes.requestsTo(LIBRARY_ADD_PATH).last().body!!.utf8().contains(""""ownedSeasons":[2]"""))
        assertNull(vm.addSheet)
    }

    @Test
    fun aFailedAdd_keepsTheSheetOpen_withTheMessage() {
        routes.on(LIBRARY_ADD_PATH, errorJson(400, "bad_request", "Received null media item!"))
        val vm = openFromSearch("heat")
        vm.openAddSheet()

        vm.save()

        assertEquals("Received null media item!", vm.addSheet?.errorMessage)
        assertFalse(vm.library.value.items.any { it.titleId == "heat" })
    }

    @Test
    fun theSheet_cantBeClosedOrChangedWhileSaving() {
        routes.on(LIBRARY_ADD_PATH, json(libraryListJson(heatJson())))
        val inFlight = routes.hold(LIBRARY_ADD_PATH)
        val vm = openFromSearch("heat")
        vm.openAddSheet()

        val save = vm.saveToLibrary()
        routes.awaitRequests(LIBRARY_ADD_PATH, 1)
        vm.cancelAddSheet()
        vm.editAddSheet { it.copy(format = MediaFormat.VHS) }

        assertNotNull(vm.addSheet)
        assertTrue(vm.addSheet!!.isSaving)
        assertEquals(MediaFormat.BLURAY, vm.addSheet!!.format)
        inFlight.countDown()
        runBlocking { save.join() }
        assertNull(vm.addSheet)
    }

    // MARK: Sheet state

    @Test
    fun turningAllSeasonsBackOn_forgetsThePicks() {
        val picked = AddToLibraryState(isTV = true).withAllSeasons(false).toggling(1).copy(seasonsConfirmed = true)
            .withSeasonFormat(1, MediaFormat.DVD)

        assertEquals(AddToLibraryState(isTV = true), picked.withAllSeasons(true))
    }

    @Test
    fun aSeriesNeedsConfirmedSeasons_beforeItCanBeSaved_butAFilmNeverWaits() {
        val series = AddToLibraryState(isTV = true).withAllSeasons(false)
        assertFalse(series.canSave)
        assertFalse(series.toggling(2).canSave)
        assertTrue(series.toggling(2).copy(seasonsConfirmed = true).canSave)
        assertFalse(series.copy(seasonsConfirmed = true).canSave)
        assertTrue(AddToLibraryState(isTV = false).canSave)
        assertEquals(
            listOf(AddToLibraryRequest(MediaFormat.BLURAY, ItemCondition.VERY_GOOD, seasons = null)),
            AddToLibraryState(isTV = false, allSeasons = false).requests(),
        )
    }

    @Test
    fun seasonList_namesOneOrMany() {
        assertEquals("Season 2", seasonList(listOf(2)))
        assertEquals("Seasons 1, 3", seasonList(listOf(3, 1)))
    }
}
