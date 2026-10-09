package com.flykpicks.android.image

import coil3.Extras
import coil3.network.NetworkClient
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PosterLoadingTest {

    private val api = "http://10.0.2.2:8080/base/".toHttpUrl()

    // MARK: posterRequestUrl

    @Test
    fun serverPosterUrl_isUsedAsIs() {
        assertEquals(
            "https://cdn.flykpicks.com/posters/tt0094625.jpg",
            posterRequestUrl("https://cdn.flykpicks.com/posters/tt0094625.jpg", "m1", api),
        )
    }

    @Test
    fun relativePosterUrl_resolvesAgainstApi() {
        assertEquals("http://10.0.2.2:8080/base/v1/media/poster?mediaID=m1", posterRequestUrl("v1/media/poster?mediaID=m1", null, api))
        assertEquals("http://10.0.2.2:8080/media/p.jpg", posterRequestUrl("/media/p.jpg", null, api))
    }

    @Test
    fun missingPosterUrl_fallsBackToPosterEndpoint_withEncodedId() {
        val expected = "http://10.0.2.2:8080/base/v1/media/poster?mediaID=a%20b%26c"
        assertEquals(expected, posterRequestUrl(null, "a b&c", api))
        assertEquals(expected, posterRequestUrl("", "a b&c", api))
    }

    @Test
    fun nothingToLoad_isNull() {
        assertNull(posterRequestUrl(null, null, api))
        assertNull(posterRequestUrl("", "", api))
    }

    // MARK: PosterNetworkClient

    private val posterRequest = NetworkRequest(
        url = "http://api/v1/media/poster?mediaID=m1",
        extras = Extras.Builder().set(posterBackfillKey, true).build(),
    )

    @Test
    fun pendingResponses_retryAfter2And4Seconds_untilThePosterArrives() = runTest {
        val server = FakeNetworkClient(202, 404, 200)
        val client = PosterNetworkClient(server)

        val result = client.executeRequest(posterRequest) { it.code }

        assertEquals(200, result)
        assertEquals(3, server.calls)
        assertEquals(6_000L, currentTime)
    }

    @Test
    fun stillPending_afterThreeRetries_failsAsPending_withoutReachingTheFetcher() = runTest {
        val server = FakeNetworkClient(202, 202, 202, 202)
        val client = PosterNetworkClient(server)
        var fetcherSawResponse = false

        val error = runCatching { client.executeRequest(posterRequest) { fetcherSawResponse = true } }.exceptionOrNull()

        assertEquals(PosterPendingException::class.java, error?.javaClass)
        assertEquals(4, server.calls)
        assertEquals(14_000L, currentTime)
        assertFalse(fetcherSawResponse)
    }

    @Test
    fun noContent_failsAsUnavailable_withoutRetrying() = runTest {
        val server = FakeNetworkClient(204)
        var fetcherSawResponse = false

        val error = runCatching {
            PosterNetworkClient(server).executeRequest(posterRequest) { fetcherSawResponse = true }
        }.exceptionOrNull()

        assertEquals(PosterUnavailableException::class.java, error?.javaClass)
        assertEquals(1, server.calls)
        assertFalse(fetcherSawResponse)
    }

    @Test
    fun otherErrors_goStraightToTheFetcher() = runTest {
        val server = FakeNetworkClient(500)

        val result = PosterNetworkClient(server).executeRequest(posterRequest) { it.code }

        assertEquals(500, result)
        assertEquals(1, server.calls)
    }

    @Test
    fun requestsNotMarkedAsPosters_areNeverRetried() = runTest {
        val server = FakeNetworkClient(404)

        val result = PosterNetworkClient(server).executeRequest(NetworkRequest("http://cdn/x.jpg")) { it.code }

        assertEquals(404, result)
        assertEquals(1, server.calls)
        assertEquals(0L, currentTime)
    }

    /** Answers each call with the next status code. */
    private class FakeNetworkClient(private vararg val codes: Int) : NetworkClient {
        var calls = 0
            private set

        override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T =
            block(NetworkResponse(code = codes[calls++]))
    }
}
