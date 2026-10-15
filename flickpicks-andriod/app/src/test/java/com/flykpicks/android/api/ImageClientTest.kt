package com.flykpicks.android.api

import com.flykpicks.android.testing.REFRESH_JSON
import com.flykpicks.android.testing.TestNetwork
import com.flykpicks.android.testing.json
import com.flykpicks.android.testing.status
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** The Coil OkHttp client: the bearer token stays on the API origin. */
class ImageClientTest {

    private lateinit var api: MockWebServer
    private lateinit var cdn: MockWebServer
    private lateinit var net: TestNetwork

    @Before
    fun setUp() {
        api = MockWebServer().apply { start() }
        cdn = MockWebServer().apply { start() }
        net = TestNetwork(api)
        net.signIn(access = "access-1")
    }

    @After
    fun tearDown() {
        api.close()
        cdn.close()
    }

    private fun fetch(url: HttpUrl): Int =
        net.imageClient.newCall(Request.Builder().url(url).build()).execute().use { it.code }

    private fun MockWebServer.taken(): RecordedRequest = takeRequest(5, TimeUnit.SECONDS)!!

    @Test
    fun apiImages_sendTheBearerToken_butNoTelemetryHeaders() {
        api.enqueue(status(200))

        fetch(api.url("/base/v1/media/poster?flyksId=m1"))

        val request = api.taken()
        assertEquals("Bearer access-1", request.headers[AUTHORIZATION_HEADER])
        assertNull(request.headers[TelemetryInterceptor.SESSION_HEADER])
        assertNull(request.headers[TelemetryInterceptor.CLIENT_TS_HEADER])
    }

    @Test
    fun theDetailImage_carriesTheTelemetryHeaders_asTheBackendCountsItAsADetailView() {
        api.enqueue(status(200))

        fetch(api.url("/base/v1/media/detailImage?mediaID=m1"))

        val request = api.taken()
        assertEquals("Bearer access-1", request.headers[AUTHORIZATION_HEADER])
        assertEquals(net.telemetry.sessionId, request.headers[TelemetryInterceptor.SESSION_HEADER])
        assertEquals("2026-10-07T21:00:00Z", request.headers[TelemetryInterceptor.CLIENT_TS_HEADER])
    }

    @Test
    fun otherOrigins_neverGetTheToken_orTheSessionId_evenOnTheSameHost() {
        // Both servers listen on the same host; only the port differs.
        cdn.enqueue(status(200))

        fetch(cdn.url("/posters/tt0094625.jpg"))

        val request = cdn.taken()
        assertNull(request.headers[AUTHORIZATION_HEADER])
        assertNull(request.headers[TelemetryInterceptor.SESSION_HEADER])
        assertNull(request.headers[TelemetryInterceptor.CLIENT_TS_HEADER])
    }

    @Test
    fun expiredToken_onAnApiImage_refreshesAndRetries() {
        api.enqueue(status(401))
        api.enqueue(json(REFRESH_JSON))
        api.enqueue(status(200))

        val code = fetch(api.url("/base/v1/media/poster?flyksId=m1"))

        assertEquals(200, code)
        assertEquals("Bearer access-1", api.taken().headers[AUTHORIZATION_HEADER])
        api.taken() // refresh
        assertEquals("Bearer access-2", api.taken().headers[AUTHORIZATION_HEADER])
    }
}
