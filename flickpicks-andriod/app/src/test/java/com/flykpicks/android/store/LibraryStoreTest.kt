package com.flykpicks.android.store

import com.flykpicks.android.api.ApiError
import com.flykpicks.android.model.MediaItemImpl
import com.flykpicks.android.model.enums.ItemCondition
import com.flykpicks.android.model.enums.MediaFormat
import com.flykpicks.android.model.enums.MediaType
import com.flykpicks.android.service.GenreService
import com.flykpicks.android.service.LibraryService
import com.flykpicks.android.testing.HEAT_BLURAY
import com.flykpicks.android.testing.HEAT_DVD
import com.flykpicks.android.testing.LIBRARY_ADD_PATH
import com.flykpicks.android.testing.LIBRARY_PATH
import com.flykpicks.android.testing.LIBRARY_REMOVE_PATH
import com.flykpicks.android.testing.LIBRARY_SEASONS_PATH
import com.flykpicks.android.testing.LIBRARY_UPDATE_PATH
import com.flykpicks.android.testing.PEAKS_BLURAY
import com.flykpicks.android.testing.Routes
import com.flykpicks.android.testing.TestNetwork
import com.flykpicks.android.testing.errorJson
import com.flykpicks.android.testing.heatJson
import com.flykpicks.android.testing.json
import com.flykpicks.android.testing.libraryListJson
import com.flykpicks.android.testing.libraryPageJson
import com.flykpicks.android.testing.status
import com.flykpicks.android.testing.twinPeaksJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** The library's shared state, against the production networking stack. */
class LibraryStoreTest {

    private lateinit var server: MockWebServer
    private lateinit var net: TestNetwork
    private val routes = Routes()

    // Stands in for the application scope: requests keep running whatever the caller does.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        server = MockWebServer().apply { dispatcher = routes; start() }
        net = TestNetwork(server).apply { signIn() }
    }

    @After
    fun tearDown() {
        appScope.cancel()
        server.close()
    }

    private val genreStore by lazy { GenreStore(GenreService(net.api(), net.genres)) }

    private fun store() = LibraryStore(LibraryService(net.api(), net.genres), genreStore, appScope)

    /** A store that has loaded Heat (DVD and Blu-Ray) and Twin Peaks. */
    private fun loadedStore(): LibraryStore = store().also {
        routes.on(LIBRARY_PATH, json(libraryPageJson(heatJson(), twinPeaksJson())))
        runBlocking { it.load() }
    }

    private val LibraryStore.ids: List<String> get() = state.value.items.map { it.id }

    private fun failureOf(block: suspend () -> Unit): Throwable? = runBlocking { runCatching { block() }.exceptionOrNull() }

    // MARK: Loading

    @Test
    fun load_readsEveryPage_afterTheGenres_andFlattensToOneItemPerCopy() {
        routes.on(
            LIBRARY_PATH,
            json(libraryPageJson(heatJson(), page = 1, totalPages = 2)),
            json(libraryPageJson(twinPeaksJson(), page = 2, totalPages = 2)),
        )
        val store = store()

        runBlocking { store.load() }

        val state = store.state.value
        assertEquals(listOf("heat-dvd", "heat-bluray", "peaks-bluray"), state.items.map { it.id })
        assertEquals("Crime • Drama", state.items.first().genre)
        assertEquals("Mystery • Drama", state.items.last().genre)
        assertTrue(state.hasLoaded)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(listOf("v1/genres", LIBRARY_PATH, LIBRARY_PATH), routes.paths())
        assertEquals(
            listOf("page=1&limit=100", "page=2&limit=100"),
            routes.requestsTo(LIBRARY_PATH).map { it.url.encodedQuery },
        )
    }

    @Test
    fun load_keepsATitleOnce_whenItShowsUpOnTwoPages() {
        routes.on(
            LIBRARY_PATH,
            json(libraryPageJson(heatJson(), page = 1, totalPages = 2)),
            json(libraryPageJson(heatJson(), twinPeaksJson(), page = 2, totalPages = 2)),
        )
        val store = store()

        runBlocking { store.load() }

        assertEquals(listOf("heat-dvd", "heat-bluray", "peaks-bluray"), store.ids)
    }

    @Test
    fun emptyLibrary_isLoaded_notMissing() {
        routes.on(LIBRARY_PATH, json(libraryPageJson()))
        val store = store()
        assertFalse(store.state.value.hasLoaded)

        runBlocking { store.load() }

        assertTrue(store.state.value.hasLoaded)
        assertTrue(store.state.value.items.isEmpty())
        assertEquals(1, routes.requestsTo(LIBRARY_PATH).size)
    }

    @Test
    fun failedLoad_setsTheMessage_andKeepsWhatWasThere() {
        val store = loadedStore()
        routes.on(LIBRARY_PATH, status(500))

        runBlocking { store.load() }

        assertEquals("Couldn't load your library. Please try again.", store.state.value.errorMessage)
        assertEquals(3, store.state.value.items.size)
        assertFalse(store.state.value.isLoading)
    }

    @Test
    fun failedFirstLoad_isNotLoaded_andTheNextLoadClearsTheMessage() {
        val store = store()
        routes.on(LIBRARY_PATH, status(500), json(libraryPageJson(heatJson())))

        runBlocking { store.load() }
        assertFalse(store.state.value.hasLoaded)
        assertEquals("Couldn't load your library. Please try again.", store.state.value.errorMessage)

        runBlocking { store.load() }
        assertTrue(store.state.value.hasLoaded)
        assertNull(store.state.value.errorMessage)
    }

    @Test
    fun loadWithoutAConnection_saysSo() {
        val store = store()
        server.close()

        runBlocking { store.load() }

        assertEquals("No internet connection. Check your connection and try again.", store.state.value.errorMessage)
        assertFalse(store.state.value.isLoading)
    }

    @Test
    fun loadsThatOverlap_shareOneFetch() {
        val store = store()
        routes.on(LIBRARY_PATH, json(libraryPageJson(heatJson())))
        val inFlight = routes.hold(LIBRARY_PATH)

        val first = appScope.async { store.load() }
        val second = appScope.async { store.load() }
        assertTrue(store.state.value.isLoading)
        inFlight.countDown()
        runBlocking { first.await(); second.await() }

        assertEquals(1, routes.requestsTo(LIBRARY_PATH).size)
        assertEquals(listOf("heat-dvd", "heat-bluray"), store.ids)
    }

    @Test
    fun genres_areRetriedByTheNextLoad_whenTheyFailed() {
        val store = store()
        routes.on("v1/genres", status(500))
        routes.on(LIBRARY_PATH, json(libraryPageJson(heatJson())), json(libraryPageJson(heatJson())))

        runBlocking { store.load() }
        assertEquals("—", store.state.value.items.first().genre)

        runBlocking { store.load() }
        assertEquals("Crime • Drama", store.state.value.items.first().genre)
        assertEquals(2, routes.requestsTo("v1/genres").size)
    }

    // MARK: Lookups

    @Test
    fun lookups_byCopy_byTitle_andBySeason() {
        val state = loadedStore().state.value

        assertEquals(MediaFormat.BLURAY, state.item("heat-bluray")?.format)
        assertNull(state.item("heat"))
        assertEquals(listOf("heat-dvd", "heat-bluray"), state.items(forTitleId = "heat").map { it.id })
        assertTrue(state.isSeasonOwned(2, titleId = "peaks"))
        assertFalse(state.isSeasonOwned(3, titleId = "peaks"))
        assertFalse(state.isSeasonOwned(1, titleId = "nope"))
    }

    // MARK: Removing

    @Test
    fun removeItem_hidesTheCopyAtOnce_thenTakesTheServersList() {
        val store = loadedStore()
        routes.on(LIBRARY_REMOVE_PATH, json(libraryListJson(heatJson(HEAT_BLURAY), twinPeaksJson())))
        val inFlight = routes.hold(LIBRARY_REMOVE_PATH)

        val removal = appScope.async { store.removeItem("heat-dvd") }
        assertEquals(listOf("heat-bluray", "peaks-bluray"), store.ids)
        inFlight.countDown()
        runBlocking { removal.await() }

        assertEquals(listOf("heat-bluray", "peaks-bluray"), store.ids)
        assertEquals("""{"mediaInventoryItemID":"heat-dvd"}""", routes.bodyOf(LIBRARY_REMOVE_PATH))
    }

    @Test
    fun removeItem_refused_bringsTheCopyBack_whereItWas() {
        val store = loadedStore()
        routes.on(LIBRARY_REMOVE_PATH, errorJson(400, "bad_request", "Listed for sale."))

        val error = failureOf { store.removeItem("heat-bluray") }

        assertEquals(LibraryRemoveError.CantRemove, error)
        assertEquals("Couldn't remove this item.", (error as LibraryRemoveError).userMessage)
        assertEquals(listOf("heat-dvd", "heat-bluray", "peaks-bluray"), store.ids)
    }

    @Test
    fun removeItem_anyOtherFailure_isRetryable_andAlsoRollsBack() {
        val store = loadedStore()
        routes.on(LIBRARY_REMOVE_PATH, status(500))
        assertEquals(LibraryRemoveError.Retryable, failureOf { store.removeItem("heat-dvd") })
        assertEquals(3, store.state.value.items.size)

        server.close()
        val offline = failureOf { store.removeItem("heat-dvd") }
        assertEquals(LibraryRemoveError.Retryable, offline)
        assertEquals("Something went wrong. Try again.", (offline as LibraryRemoveError).userMessage)
        assertEquals(3, store.state.value.items.size)
    }

    // MARK: Editing

    @Test
    fun updateItem_showsTheChangeAtOnce_thenTakesTheServersList() {
        val store = loadedStore()
        val saved = HEAT_DVD.copy(format = "FOUR_K_ULTRA_HD", condition = "NEW")
        routes.on(LIBRARY_UPDATE_PATH, json(libraryListJson(heatJson(saved, HEAT_BLURAY), twinPeaksJson())))
        val inFlight = routes.hold(LIBRARY_UPDATE_PATH)

        val update = appScope.async { store.updateItem("heat-dvd", MediaFormat.UHD_4K, ItemCondition.NEW) }
        assertEquals(MediaFormat.UHD_4K, store.state.value.item("heat-dvd")?.format)
        assertEquals(ItemCondition.NEW, store.state.value.item("heat-dvd")?.condition)
        inFlight.countDown()
        runBlocking { update.await() }

        assertEquals(MediaFormat.UHD_4K, store.state.value.item("heat-dvd")?.format)
        assertEquals(3, store.state.value.items.size)
    }

    @Test
    fun updateItem_failure_putsTheOldValuesBack_andPassesTheErrorOn() {
        val store = loadedStore()
        routes.on(LIBRARY_UPDATE_PATH, errorJson(409, "conflict", "That copy is in an open order."))

        val error = failureOf { store.updateItem("heat-dvd", MediaFormat.VHS, ItemCondition.ACCEPTABLE) }

        assertEquals("That copy is in an open order.", (error as ApiError.Http).userMessage)
        assertEquals(MediaFormat.DVD, store.state.value.item("heat-dvd")?.format)
        assertEquals(ItemCondition.GOOD, store.state.value.item("heat-dvd")?.condition)
    }

    @Test
    fun updateOwnedSeasons_sendsTheSeasons_andTakesTheServersList() {
        val store = loadedStore()
        routes.on(
            LIBRARY_SEASONS_PATH,
            json(libraryListJson(heatJson(), twinPeaksJson(PEAKS_BLURAY.copy(seasons = listOf(1, 2, 3))))),
            json(libraryListJson(heatJson(), twinPeaksJson(PEAKS_BLURAY.copy(seasons = listOf(1, 2, 3))))),
        )

        runBlocking { store.updateOwnedSeasons("peaks-bluray", listOf(1, 2, 3), selectAllSeasons = false) }
        assertTrue(store.state.value.isSeasonOwned(3, titleId = "peaks"))
        runBlocking { store.updateOwnedSeasons("peaks-bluray", ownedSeasons = null, selectAllSeasons = true) }

        assertEquals(
            listOf(
                """{"mediaInventoryItemID":"peaks-bluray","ownedSeasons":[1,2,3],"selectAllSeasons":false}""",
                """{"mediaInventoryItemID":"peaks-bluray","selectAllSeasons":true}""",
            ),
            routes.requestsTo(LIBRARY_SEASONS_PATH).map { it.body!!.utf8() },
        )
    }

    @Test
    fun addFromSearchItem_sendsTheTitle_andLoadsTheLibraryWithIt() {
        val store = store()
        routes.on(LIBRARY_ADD_PATH, json(libraryListJson(heatJson(HEAT_DVD)), 201))
        val heat = MediaItemImpl(
            id = "heat", flyksId = "heat", title = "Heat", mediaType = MediaType.FILM, originalLanguage = "en",
            genre = "Crime", popularity = 0.0, releaseDate = "1995-12-15", voteAverage = 0.0, voteCount = 0,
            posterUrl = null, overview = "",
        )

        runBlocking { store.addFromSearchItem(heat, MediaFormat.DVD, ItemCondition.GOOD) }

        assertEquals(listOf("heat-dvd"), store.ids)
        assertTrue("a mutation's answer is the whole library", store.state.value.hasLoaded)
        assertEquals(
            """{"mediaItemFlyksID":"heat","format":"DVD","condition":"GOOD","selectAllSeasons":true}""",
            routes.bodyOf(LIBRARY_ADD_PATH),
        )
    }

    // MARK: Staying consistent

    @Test
    fun aLoadStartedBeforeAChangeWasAccepted_cannotBringTheOldListBack() {
        val store = loadedStore()
        // The reload reads the library before the removal reaches the server, and lands after it.
        routes.on(LIBRARY_PATH, json(libraryPageJson(heatJson(), twinPeaksJson())))
        val staleRead = routes.hold(LIBRARY_PATH)
        routes.on(LIBRARY_REMOVE_PATH, json(libraryListJson(heatJson(HEAT_BLURAY), twinPeaksJson())))

        val reload = appScope.async { store.load() }
        runBlocking { store.removeItem("heat-dvd") }
        staleRead.countDown()
        runBlocking { reload.await() }

        assertEquals(listOf("heat-bluray", "peaks-bluray"), store.ids)
        assertFalse(store.state.value.isLoading)
    }

    @Test
    fun aLoadThatLandsWhileARemovalIsInFlight_stillHidesTheCopy() {
        val store = loadedStore()
        routes.on(LIBRARY_PATH, json(libraryPageJson(heatJson(), twinPeaksJson())))
        routes.on(LIBRARY_REMOVE_PATH, status(500))
        val inFlight = routes.hold(LIBRARY_REMOVE_PATH)

        val removal = appScope.async { runCatching { store.removeItem("heat-dvd") } }
        runBlocking { store.load() }
        assertEquals(listOf("heat-bluray", "peaks-bluray"), store.ids)

        // The removal then fails: the copy comes back, from the list the load brought.
        inFlight.countDown()
        runBlocking { removal.await() }
        assertEquals(listOf("heat-dvd", "heat-bluray", "peaks-bluray"), store.ids)
    }

    @Test
    fun changes_emitsOncePerChangeTheServerAccepted() {
        val store = loadedStore()
        val changes = AtomicInteger()
        appScope.launch { store.changes.collect { changes.incrementAndGet() } }
        val everything = json(libraryListJson(heatJson(), twinPeaksJson()))
        routes.on(LIBRARY_UPDATE_PATH, everything, status(500))
        routes.on(LIBRARY_REMOVE_PATH, everything, status(500))
        routes.on(LIBRARY_SEASONS_PATH, everything)

        runBlocking {
            store.updateItem("heat-dvd", MediaFormat.VHS, ItemCondition.GOOD)
            store.removeItem("heat-dvd")
            store.updateOwnedSeasons("peaks-bluray", listOf(1), selectAllSeasons = false)
        }
        assertEquals(3, changes.get())

        failureOf { store.updateItem("heat-dvd", MediaFormat.VHS, ItemCondition.GOOD) }
        failureOf { store.removeItem("heat-dvd") }
        assertEquals("a refused change is not a change", 3, changes.get())
    }

    // MARK: Changing accounts

    @Test
    fun reset_emptiesTheStore() {
        val store = loadedStore()

        store.reset()

        assertEquals(LibraryState(), store.state.value)
    }

    @Test
    fun aChangeStillInFlightAtReset_doesNotReachTheNextAccount() {
        val store = loadedStore()
        val changes = AtomicInteger()
        appScope.launch { store.changes.collect { changes.incrementAndGet() } }
        routes.on(LIBRARY_REMOVE_PATH, json(libraryListJson(heatJson(HEAT_BLURAY), twinPeaksJson())))
        val inFlight = routes.hold(LIBRARY_REMOVE_PATH)

        val removal = appScope.async { store.removeItem("heat-dvd") }
        store.reset()
        inFlight.countDown()
        runBlocking { removal.await() }

        assertEquals(LibraryState(), store.state.value)
        assertEquals(0, changes.get())
    }

    @Test
    fun aLoadStillInFlightAtReset_doesNotReachTheNextAccount() {
        val store = store()
        runBlocking { genreStore.loadIfNeeded() }
        routes.on(LIBRARY_PATH, json(libraryPageJson(heatJson())), json(libraryPageJson(twinPeaksJson())))
        val previousAccountsRead = routes.hold(LIBRARY_PATH)

        val abandoned = appScope.async { store.load() }
        routes.awaitRequests(LIBRARY_PATH, 1)
        store.reset()
        // The next account's own load is a new fetch, not a wait on the abandoned one.
        val next = appScope.async { store.load() }
        routes.awaitRequests(LIBRARY_PATH, 2)
        previousAccountsRead.countDown()
        runBlocking { abandoned.await(); next.await() }

        assertEquals(listOf("peaks-bluray"), store.ids)
        assertTrue(store.state.value.hasLoaded)
    }
}
