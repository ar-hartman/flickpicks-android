package com.flykpicks.android.ui.screens.library

import com.flykpicks.android.model.LibraryItemImpl
import com.flykpicks.android.model.SeasonSummaryDTO
import com.flykpicks.android.model.enums.ItemCondition
import com.flykpicks.android.model.enums.MediaFormat
import com.flykpicks.android.model.enums.MediaType
import com.flykpicks.android.store.RecommendationSection
import com.flykpicks.android.testing.HEAT_BLURAY
import com.flykpicks.android.testing.LIBRARY_PATH
import com.flykpicks.android.testing.LIBRARY_REMOVE_PATH
import com.flykpicks.android.testing.Routes
import com.flykpicks.android.testing.TestNetwork
import com.flykpicks.android.testing.TestStores
import com.flykpicks.android.testing.errorJson
import com.flykpicks.android.testing.eventually
import com.flykpicks.android.testing.heatJson
import com.flykpicks.android.testing.json
import com.flykpicks.android.testing.libraryListJson
import com.flykpicks.android.testing.libraryPageJson
import com.flykpicks.android.testing.path
import com.flykpicks.android.testing.twinPeaksJson
import com.flykpicks.android.util.primaryGenre
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var net: TestNetwork
    private val routes = Routes()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer().apply { dispatcher = routes; start() }
        net = TestNetwork(server).apply { signIn() }
    }

    @After
    fun tearDown() {
        appScope.cancel()
        server.close()
        Dispatchers.resetMain()
    }

    private val stores by lazy { TestStores(net, appScope) }

    private fun viewModel() = LibraryViewModel(stores.library, stores.recommendations)

    private fun loadedViewModel() = viewModel().also {
        routes.on(LIBRARY_PATH, json(libraryPageJson(heatJson(), twinPeaksJson())))
        runBlocking { it.load().join() }
    }

    private val LibraryViewModel.ids: List<String> get() = library.value.items.map { it.id }

    // MARK: Rows

    private fun copy(id: String, title: String, genre: String) = LibraryItemImpl(
        id = id, titleId = "t-$id", title = title, mediaType = MediaType.FILM, year = 1990, genre = genre,
        posterUrl = null, season = null, ownedSeasons = null, format = MediaFormat.DVD, condition = ItemCondition.GOOD,
        appraisalValue = 0.0, appraisalSource = "DEFAULT", dateAdded = null, overview = "",
    )

    private val shelf = listOf(
        copy("1", "Ronin", "Crime • Thriller"),
        copy("2", "Brazil", "comedy • Sci-Fi"),
        copy("3", "Heat", "Crime"),
        copy("4", "Airplane!", "Comedy"),
        copy("5", "Alien", ""),
    )

    @Test
    fun rows_groupByFirstGenre_sortedIgnoringCase_withTitlesInOrder() {
        val sections = librarySections(shelf, query = "")

        assertEquals(listOf("comedy", "Comedy", "Crime", "Unknown"), sections.map { it.genre })
        assertEquals(listOf("Heat", "Ronin"), sections[2].items.map { it.title })
    }

    @Test
    fun query_filtersByTitle_ignoringCaseAndSurroundingSpaces() {
        assertEquals(listOf("Heat"), librarySections(shelf, query = " hEa ").flatMap { it.items }.map { it.title })
        // Ronin's row loses Heat, and rows with no match are dropped.
        assertEquals(listOf(LibrarySection("Crime", listOf(shelf[0]))), librarySections(shelf, query = "ro"))
        assertTrue(librarySections(shelf, query = "zzz").isEmpty())
    }

    @Test
    fun primaryGenre_isTheFirstName_whateverSeparatesThem() {
        assertEquals("Crime", primaryGenre("Crime • Drama"))
        assertEquals("Sci-Fi & Fantasy", primaryGenre(" Sci-Fi & Fantasy / Mystery"))
        assertEquals("Action", primaryGenre("Action,Adventure"))
        assertEquals("—", primaryGenre("—"))
        assertEquals("Unknown", primaryGenre("  "))
        assertEquals("Unknown", primaryGenre("• Drama"))
    }

    // MARK: Formatting

    @Test
    fun appraisals_readAsDollars_withTheirSourceNamed() {
        assertEquals("$24.99", formatUsd(24.99, Locale.US))
        assertEquals("$61.50", formatUsd(61.5, Locale.US))
        assertEquals("eBay", appraisalSourceLabel("EBAY"))
        assertEquals("PriceCharting", appraisalSourceLabel("PRICECHARTING"))
        assertEquals("FlykPicks", appraisalSourceLabel("INTERNAL"))
        assertEquals("Manual", appraisalSourceLabel("MANUAL"))
        assertEquals("Some_New Source", appraisalSourceLabel("SOME_NEW SOURCE"))
    }

    @Test
    fun seasonFacts_formatForTheScreen() {
        assertEquals("7.9", formatRating(7.94, Locale.US))
        assertEquals("1990", SeasonSummaryDTO(1, 8, "1990-04-08", null, 8.3).airYear)
        assertNull(SeasonSummaryDTO(1, 8, null, null, 8.3).airYear)
        assertNull(SeasonSummaryDTO(1, 8, "", null, 8.3).airYear)
    }

    // MARK: Screen

    @Test
    fun load_fillsTheLibraryFromTheStore() {
        val vm = loadedViewModel()

        assertEquals(listOf("heat-dvd", "heat-bluray", "peaks-bluray"), vm.ids)
        assertTrue(vm.library.value.hasLoaded)
    }

    @Test
    fun load_thenStartsDiscoversRows_withoutWaitingForThem() {
        val vm = loadedViewModel()

        RecommendationSection.entries.forEach { routes.awaitRequests(it.path, 1) }
        assertTrue(routes.paths().indexOf(LIBRARY_PATH) < routes.paths().indexOf(RecommendationSection.POPULAR_MOVIES.path))
        eventually { stores.recommendations.state.value.hasLoadedOnce }

        runBlocking { vm.load().join() }
        assertEquals(1, routes.requestsTo(RecommendationSection.POPULAR_MOVIES.path).size)
    }

    @Test
    fun removal_waitsForConfirmation_thenRemovesTheCopy() {
        val vm = loadedViewModel()
        routes.on(LIBRARY_REMOVE_PATH, json(libraryListJson(heatJson(HEAT_BLURAY), twinPeaksJson())))
        val heatDvd = vm.library.value.item("heat-dvd")!!

        vm.requestRemoval(heatDvd)
        assertEquals(heatDvd, vm.pendingRemoval)
        assertEquals("nothing is sent before the user confirms", 0, routes.requestsTo(LIBRARY_REMOVE_PATH).size)

        runBlocking { vm.confirmRemoval().join() }

        assertNull(vm.pendingRemoval)
        assertNull(vm.removalError)
        assertEquals(listOf("heat-bluray", "peaks-bluray"), vm.ids)
    }

    @Test
    fun cancelledRemoval_sendsNothing() {
        val vm = loadedViewModel()

        vm.requestRemoval(vm.library.value.item("heat-dvd")!!)
        vm.cancelRemoval()
        runBlocking { vm.confirmRemoval().join() }

        assertEquals(0, routes.requestsTo(LIBRARY_REMOVE_PATH).size)
        assertEquals(3, vm.ids.size)
    }

    @Test
    fun refusedRemoval_putsTheCopyBack_andSaysWhy_untilDismissed() {
        val vm = loadedViewModel()
        routes.on(LIBRARY_REMOVE_PATH, errorJson(400, "bad_request", "Listed for sale."))

        vm.requestRemoval(vm.library.value.item("heat-dvd")!!)
        runBlocking { vm.confirmRemoval().join() }

        assertEquals("Couldn't remove this item.", vm.removalError)
        assertEquals(listOf("heat-dvd", "heat-bluray", "peaks-bluray"), vm.ids)

        vm.dismissRemovalError()
        assertNull(vm.removalError)
    }
}
