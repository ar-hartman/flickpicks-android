package com.flykpicks.android.store

import com.flykpicks.android.api.endpoints.FlykSource
import com.flykpicks.android.model.MediaItemImpl
import com.flykpicks.android.model.enums.ItemCondition
import com.flykpicks.android.model.enums.MediaFormat
import com.flykpicks.android.model.enums.MediaType
import com.flykpicks.android.store.RecommendationSection.INVENTORY_MOVIES
import com.flykpicks.android.store.RecommendationSection.INVENTORY_TV
import com.flykpicks.android.store.RecommendationSection.POPULAR_MOVIES
import com.flykpicks.android.store.RecommendationSection.POPULAR_TV
import com.flykpicks.android.store.RecommendationSection.SEARCH_HISTORY_MOVIES
import com.flykpicks.android.store.RecommendationSection.SEARCH_HISTORY_TV
import com.flykpicks.android.testing.LIBRARY_ADD_PATH
import com.flykpicks.android.testing.Routes
import com.flykpicks.android.testing.SEARCH_PATH
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Discover rows, against the production networking stack. */
class RecommendationsStoreTest {

    private lateinit var server: MockWebServer
    private lateinit var net: TestNetwork
    private lateinit var stores: TestStores
    private val routes = Routes()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        server = MockWebServer().apply { dispatcher = routes; start() }
        net = TestNetwork(server).apply { signIn() }
        stores = TestStores(net, appScope)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        server.close()
    }

    private val store get() = stores.recommendations

    private val heat = MediaItemImpl(
        id = "heat", flyksId = "heat", title = "Heat", mediaType = MediaType.FILM, originalLanguage = "en",
        genre = "Crime", popularity = 0.0, releaseDate = "1995-12-15", voteAverage = 0.0, voteCount = 0,
        posterUrl = null, overview = "",
    )

    /** Queues one title per row, named after the row, unless [rows] says otherwise. */
    private fun answerEveryRow(rows: Map<RecommendationSection, String> = emptyMap(), skip: Set<RecommendationSection> = emptySet()) {
        for (section in RecommendationSection.entries - skip) {
            val title = section.name.lowercase()
            val item = if (section.name.endsWith("TV")) tvItemJson("id-$title", title) else movieItemJson("id-$title", title)
            routes.on(section.path, json(rows[section] ?: mediaListJson(item)))
        }
    }

    private fun titles(section: RecommendationSection) = store.state.value.section(section).items.map { it.title }

    private fun requestsTo(section: RecommendationSection) = routes.requestsTo(section.path).size

    private fun loaded(): RecommendationsStore {
        answerEveryRow()
        runBlocking { store.loadIfNeeded() }
        return store
    }

    // MARK: Loading

    @Test
    fun loadIfNeeded_fetchesAllEightRows_afterTheGenres_eachWithItsSource() {
        answerEveryRow()

        runBlocking { store.loadIfNeeded() }

        val state = store.state.value
        assertTrue(state.hasLoadedOnce)
        assertEquals("v1/genres", routes.paths().first())
        for (section in RecommendationSection.entries) {
            assertEquals(listOf(section.name.lowercase()), titles(section))
            assertFalse(state.section(section).isLoading)
            assertEquals(section.source, routes.requestsTo(section.path).single().headers[FlykSource.HEADER])
        }
        assertEquals("Drama", state.section(POPULAR_MOVIES).items.single().genre)
        assertEquals("Mystery", state.section(POPULAR_TV).items.single().genre)
    }

    @Test
    fun rows_areInTheOrderIosShowsThem_withItsTitles() {
        assertEquals(
            listOf(
                "Because You Own It · Movies", "Based on Searches · Movies", "Because You Own It · TV", "Based on Searches · TV",
                "Popular Movies", "Top Rated Movies", "Popular TV", "Top Rated TV",
            ),
            RecommendationSection.entries.map { it.displayTitle },
        )
        assertEquals("recommendation.searchHistoryTV", SEARCH_HISTORY_TV.source)
    }

    @Test
    fun loadIfNeeded_fetchesOnce_andCallersArrivingMidLoadWaitForIt() {
        answerEveryRow()
        val inFlight = routes.hold(POPULAR_MOVIES.path)

        val first = appScope.async { store.loadIfNeeded() }
        val second = appScope.async { store.loadIfNeeded() }
        routes.awaitRequests(POPULAR_MOVIES.path, 1)
        assertTrue(store.state.value.section(POPULAR_MOVIES).isLoading)
        assertFalse(second.isCompleted)
        inFlight.countDown()
        runBlocking { first.await(); second.await(); store.loadIfNeeded() }

        assertTrue(RecommendationSection.entries.all { requestsTo(it) == 1 })
    }

    @Test
    fun aRowThatFails_saysSo_theOthersStillLoad_andRetryFetchesOnlyThatRow() {
        routes.on(INVENTORY_TV.path, errorJson(500, "server_error", "Recommendations are down."), json(mediaListJson(tvItemJson("t9", "Columbo"))))
        answerEveryRow(skip = setOf(INVENTORY_TV))

        runBlocking { store.loadIfNeeded() }

        val failed = store.state.value.section(INVENTORY_TV)
        assertEquals("Recommendations are down.", failed.errorMessage)
        assertTrue(failed.items.isEmpty())
        assertFalse(failed.isLoading)
        assertTrue(store.state.value.hasLoadedOnce)
        assertEquals(listOf("popular_tv"), titles(POPULAR_TV))

        runBlocking { store.refreshSection(INVENTORY_TV).join() }

        assertEquals(listOf("Columbo"), titles(INVENTORY_TV))
        assertNull(store.state.value.section(INVENTORY_TV).errorMessage)
        assertEquals(2, requestsTo(INVENTORY_TV))
        assertEquals(1, requestsTo(POPULAR_TV))
    }

    @Test
    fun aRowThatFailsToRefresh_keepsWhatItShowed() {
        loaded()
        routes.on(POPULAR_MOVIES.path, errorJson(500, "server_error", "Try later."))

        runBlocking { store.refreshSection(POPULAR_MOVIES).join() }

        val row = store.state.value.section(POPULAR_MOVIES)
        assertEquals(listOf("popular_movies"), row.items.map { it.title })
        assertEquals("Try later.", row.errorMessage)
        assertFalse(row.isLoading)
    }

    @Test
    fun refresh_fetchesEveryRowAgain() {
        loaded()
        answerEveryRow()

        runBlocking { store.refresh() }

        assertTrue(RecommendationSection.entries.all { requestsTo(it) == 2 })
    }

    // MARK: Search history

    @Test
    fun searchHistoryRows_leaveOutWhatTheirOwnItRowShows_untilItStopsShowingIt() {
        val owned = movieItemJson("m1", "Heat")
        val searched = movieItemJson("m2", "Ronin")
        answerEveryRow(
            mapOf(
                INVENTORY_MOVIES to mediaListJson(owned),
                SEARCH_HISTORY_MOVIES to mediaListJson(owned, searched),
                SEARCH_HISTORY_TV to mediaListJson(tvItemJson("id-inventory_tv", "inventory_tv"), tvItemJson("t2", "Columbo")),
            ),
        )
        runBlocking { store.loadIfNeeded() }

        assertEquals(listOf("Ronin"), titles(SEARCH_HISTORY_MOVIES))
        assertEquals(listOf("Columbo"), titles(SEARCH_HISTORY_TV))
        assertEquals(listOf("Heat"), titles(INVENTORY_MOVIES))

        routes.on(INVENTORY_MOVIES.path, json("[]"))
        runBlocking { store.refreshSection(INVENTORY_MOVIES).join() }

        assertEquals(listOf("Heat", "Ronin"), titles(SEARCH_HISTORY_MOVIES))
    }

    // MARK: Going stale

    @Test
    fun aLibraryChange_reloadsTheBecauseYouOwnItRows_only() {
        loaded()
        answerEveryRow(mapOf(INVENTORY_MOVIES to mediaListJson(movieItemJson("m7", "Thief"))))
        routes.on(LIBRARY_ADD_PATH, json(libraryListJson(heatJson())))

        runBlocking { stores.library.addFromSearchItem(heat, MediaFormat.DVD, ItemCondition.GOOD) }
        eventually { titles(INVENTORY_MOVIES) == listOf("Thief") }

        eventually { requestsTo(INVENTORY_TV) == 2 }
        assertEquals(1, requestsTo(SEARCH_HISTORY_MOVIES))
        assertEquals(1, requestsTo(POPULAR_MOVIES))
    }

    @Test
    fun aSearch_reloadsTheBasedOnSearchesRows_only_andAFailedOneDoesNot() {
        loaded()
        answerEveryRow(mapOf(SEARCH_HISTORY_MOVIES to mediaListJson(movieItemJson("m8", "Collateral"))))
        routes.on(SEARCH_PATH, errorJson(500, "server_error", "Search is down."), json(mediaListJson(movieItemJson("m1", "Heat"))))

        runBlocking { stores.search.search("heat") }
        assertEquals(1, requestsTo(SEARCH_HISTORY_MOVIES))

        runBlocking { stores.search.search("heat") }
        eventually { titles(SEARCH_HISTORY_MOVIES) == listOf("Collateral") }

        eventually { requestsTo(SEARCH_HISTORY_TV) == 2 }
        assertEquals(1, requestsTo(INVENTORY_MOVIES))
        assertEquals(1, requestsTo(POPULAR_TV))
    }

    @Test
    fun changesBeforeTheFirstLoad_fetchNothing() {
        store.markStale(RecommendationRefreshTrigger.LIBRARY_CHANGED)
        store.markStale(RecommendationRefreshTrigger.SEARCH_PERFORMED)

        assertTrue(routes.paths().isEmpty())
        assertFalse(store.state.value.hasLoadedOnce)
    }

    @Test
    fun aChangeDuringTheFirstLoad_asksTheAffectedRowsAgain_andTheNewerAnswerWins() {
        answerEveryRow(mapOf(INVENTORY_MOVIES to mediaListJson(movieItemJson("m1", "Before the change"))))
        routes.on(INVENTORY_MOVIES.path, json(mediaListJson(movieItemJson("m2", "After the change"))))
        val firstAnswer = routes.hold(INVENTORY_MOVIES.path)

        val load = appScope.async { store.loadIfNeeded() }
        routes.awaitRequests(INVENTORY_MOVIES.path, 1)
        store.markStale(RecommendationRefreshTrigger.LIBRARY_CHANGED)
        eventually { titles(INVENTORY_MOVIES) == listOf("After the change") }
        firstAnswer.countDown()
        runBlocking { load.await() }

        assertEquals(listOf("After the change"), titles(INVENTORY_MOVIES))
        assertTrue(store.state.value.hasLoadedOnce)
    }

    // MARK: Accounts

    @Test
    fun reset_dropsTheRows_andAnAnswerStillInFlight_andTheNextLoadFetchesAgain() {
        answerEveryRow()
        val inFlight = routes.hold(POPULAR_MOVIES.path)
        val load = appScope.async { runCatching { store.loadIfNeeded() } }
        routes.awaitRequests(POPULAR_MOVIES.path, 1)

        store.reset()
        inFlight.countDown()
        runBlocking { load.await() }

        assertEquals(RecommendationsState(), store.state.value)

        answerEveryRow()
        runBlocking { store.loadIfNeeded() }
        assertEquals(listOf("popular_movies"), titles(POPULAR_MOVIES))
        assertEquals(2, requestsTo(POPULAR_MOVIES))
    }
}
