package com.flykpicks.android.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.flykpicks.android.api.endpoints.FlykSource
import com.flykpicks.android.model.MediaItemImpl
import com.flykpicks.android.model.enums.MediaType
import com.flykpicks.android.store.RecommendationSection
import com.flykpicks.android.store.SearchResults
import com.flykpicks.android.testing.Routes
import com.flykpicks.android.testing.SEARCH_PATH
import com.flykpicks.android.testing.TestNetwork
import com.flykpicks.android.testing.TestStores
import com.flykpicks.android.testing.errorJson
import com.flykpicks.android.testing.json
import com.flykpicks.android.testing.mediaListJson
import com.flykpicks.android.testing.movieItemJson
import com.flykpicks.android.testing.path
import com.flykpicks.android.testing.tvItemJson
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Discover screen's state, against the production networking stack. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var net: TestNetwork
    private lateinit var stores: TestStores
    private val routes = Routes()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer().apply { dispatcher = routes; start() }
        net = TestNetwork(server).apply { signIn() }
        stores = TestStores(net, appScope)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        server.close()
        Dispatchers.resetMain()
    }

    private fun viewModel() = SearchViewModel(stores.search, stores.recommendations)

    private fun SearchViewModel.searchFor(text: String) = apply {
        updateQuery(text)
        runBlocking { search()?.join() }
    }

    private val SearchViewModel.titles: List<String> get() = results.value.items.map { it.title }

    // MARK: Searching

    @Test
    fun search_sendsTheTrimmedQuery_withTheSearchSource_andShowsTheResults() {
        routes.on(SEARCH_PATH, json(mediaListJson(movieItemJson("m1", "Heat", "[80, 18]"), tvItemJson("t1", "Columbo"))))

        val vm = viewModel().searchFor("  heat 1995 ")

        val request = routes.requestsTo(SEARCH_PATH).single()
        assertEquals("heat 1995", request.url.queryParameter("query"))
        assertEquals(FlykSource.SEARCH_RESULTS, request.headers[FlykSource.HEADER])
        assertTrue(vm.hasSearched)
        assertEquals(listOf("Heat", "Columbo"), vm.titles)
        assertEquals("Crime • Drama", vm.results.value.items.first().genre)
        assertFalse(vm.results.value.isLoading)
        assertEquals("heat 1995", vm.results.value.query)
    }

    @Test
    fun aBlankQuery_staysOnTheRows_withoutSearching() {
        val vm = viewModel()
        vm.updateQuery("   ")

        assertNull(vm.search())

        assertFalse(vm.hasSearched)
        assertTrue(routes.requestsTo(SEARCH_PATH).isEmpty())
    }

    @Test
    fun whileSearching_theResultsSayLoading() {
        routes.on(SEARCH_PATH, json(mediaListJson(movieItemJson("m1", "Heat"))))
        val inFlight = routes.hold(SEARCH_PATH)
        val vm = viewModel()
        vm.updateQuery("heat")

        val job = vm.search()
        routes.awaitRequests(SEARCH_PATH, 1)

        assertTrue(vm.results.value.isLoading)
        inFlight.countDown()
        runBlocking { job?.join() }
        assertFalse(vm.results.value.isLoading)
    }

    @Test
    fun emptyingTheField_goesBackToTheRows_andDropsTheSearchInFlight() {
        routes.on(SEARCH_PATH, json(mediaListJson(movieItemJson("m1", "Heat"))))
        val inFlight = routes.hold(SEARCH_PATH)
        val vm = viewModel()
        vm.updateQuery("heat")
        val job = vm.search()
        routes.awaitRequests(SEARCH_PATH, 1)

        vm.updateQuery("")
        inFlight.countDown()
        runBlocking { job?.join() }

        assertFalse(vm.hasSearched)
        assertEquals(SearchResults(), vm.results.value.copy(generation = 0))
    }

    @Test
    fun aNewSearch_replacesOneStillInFlight() {
        routes.on(SEARCH_PATH, json(mediaListJson(movieItemJson("m1", "Heat"))), json(mediaListJson(movieItemJson("m2", "Ronin"))))
        val first = routes.hold(SEARCH_PATH)
        val vm = viewModel()
        vm.updateQuery("heat")
        val firstSearch = vm.search()
        routes.awaitRequests(SEARCH_PATH, 1)

        vm.searchFor("ronin")
        first.countDown()
        runBlocking { firstSearch?.join() }

        assertEquals(listOf("Ronin"), vm.titles)
        assertEquals("ronin", vm.results.value.query)
    }

    @Test
    fun editingTheQuery_keepsTheResults_untilTheNextSearch() {
        routes.on(SEARCH_PATH, json(mediaListJson(movieItemJson("m1", "Heat"))))
        val vm = viewModel().searchFor("heat")

        vm.updateQuery("hea")

        assertTrue(vm.hasSearched)
        assertEquals(listOf("Heat"), vm.titles)
    }

    @Test
    fun aFailedSearch_saysWhy_ratherThanNoResults() {
        routes.on(SEARCH_PATH, errorJson(500, "server_error", "Search is unavailable."))

        val vm = viewModel().searchFor("heat")

        assertTrue(vm.hasSearched)
        assertEquals("Search is unavailable.", vm.results.value.errorMessage)
        assertTrue(vm.titles.isEmpty())
    }

    @Test
    fun eachSearch_hasItsOwnGeneration_soTheResultsStartAtTheTop() {
        routes.on(SEARCH_PATH, json(mediaListJson(movieItemJson("m1", "Heat"))), json(mediaListJson(movieItemJson("m1", "Heat"))))
        val vm = viewModel().searchFor("heat")
        val first = vm.results.value.generation

        vm.searchFor("heat")

        assertTrue(vm.results.value.generation > first)
    }

    @Test
    fun leavingTheTab_endsTheScreen_andClearsTheResults() {
        routes.on(SEARCH_PATH, json(mediaListJson(movieItemJson("m1", "Heat"))))
        val owner = ViewModelStore()
        val vm = ViewModelProvider(owner, factory { viewModel() })[SearchViewModel::class.java]
        vm.searchFor("heat")
        assertTrue(stores.search.item("m1") != null)

        owner.clear()

        assertTrue(stores.search.state.value.items.isEmpty())
    }

    // MARK: Rows

    @Test
    fun results_areGroupedByFirstGenre_rowsAlphabetical_titlesByName() {
        fun item(title: String, genre: String) = MediaItemImpl(
            id = title, flyksId = title, title = title, mediaType = MediaType.FILM, originalLanguage = "en", genre = genre,
            popularity = 0.0, releaseDate = "", voteAverage = 0.0, voteCount = 0, posterUrl = null, overview = "",
        )
        val items = listOf(item("Ronin", "Thriller"), item("Heat", "Crime • Drama"), item("Brazil", "comedy"), item("Collateral", "Crime"))

        val sections = searchSections(items)

        assertEquals(listOf("comedy", "Crime", "Thriller"), sections.map { it.genre })
        assertEquals(listOf("Collateral", "Heat"), sections[1].items.map { it.title })
    }

    @Test
    fun loadRecommendations_fetchesTheRowsOnce_andRefreshReportsWhileItRuns() {
        for (section in RecommendationSection.entries) routes.on(section.path, json("[]"), json("[]"))
        val vm = viewModel()

        runBlocking { vm.loadRecommendations().join(); vm.loadRecommendations().join() }
        assertTrue(RecommendationSection.entries.all { routes.requestsTo(it.path).size == 1 })

        val inFlight = routes.hold(RecommendationSection.TOP_RATED_TV.path)
        val refresh = vm.refreshRecommendations()
        routes.awaitRequests(RecommendationSection.TOP_RATED_TV.path, 2)
        assertTrue(vm.isRefreshing)
        inFlight.countDown()
        runBlocking { refresh.join() }

        assertFalse(vm.isRefreshing)
        assertTrue(RecommendationSection.entries.all { routes.requestsTo(it.path).size == 2 })
    }

    private fun factory(create: () -> ViewModel) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
}
