package com.flykpicks.android.store

import com.flykpicks.android.service.GenreService
import com.flykpicks.android.testing.Routes
import com.flykpicks.android.testing.TestNetwork
import com.flykpicks.android.testing.status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GenreStoreTest {

    private lateinit var server: MockWebServer
    private lateinit var net: TestNetwork
    private val routes = Routes()

    @Before
    fun setUp() {
        server = MockWebServer().apply { dispatcher = routes; start() }
        net = TestNetwork(server).apply { signIn() }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun store() = GenreStore(GenreService(net.api(), net.genres))

    @Test
    fun loadsOnce_howeverOftenItIsAsked() = runBlocking {
        val store = store()

        store.loadIfNeeded()
        store.loadIfNeeded()

        assertTrue(store.isLoaded)
        assertEquals("Crime • Drama", net.genres.joinedNames(listOf(80, 18)))
        assertEquals(1, routes.requestsTo("v1/genres").size)
    }

    @Test
    fun callersArrivingMidLoad_waitForIt_insteadOfFetchingAgain() = runBlocking {
        val store = store()
        val inFlight = routes.hold("v1/genres")

        val first = async(Dispatchers.Default) { store.loadIfNeeded() }
        val second = async(Dispatchers.Default) { store.loadIfNeeded() }
        routes.awaitRequests("v1/genres", 1)
        inFlight.countDown()
        first.await()
        second.await()

        assertEquals(1, routes.requestsTo("v1/genres").size)
    }

    @Test
    fun aFailedLoad_isSilent_andTheNextCallTriesAgain() = runBlocking {
        val store = store()
        routes.on("v1/genres", status(500))

        store.loadIfNeeded()
        assertFalse(store.isLoaded)
        assertEquals("—", net.genres.joinedNames(listOf(80)))

        store.loadIfNeeded()
        assertTrue(store.isLoaded)
        assertEquals("Crime", net.genres.joinedNames(listOf(80)))
    }
}
