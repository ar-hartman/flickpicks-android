package com.flykpicks.android.api

import com.flykpicks.android.api.endpoints.AuthApi
import com.flykpicks.android.api.endpoints.ProfileApi
import com.flykpicks.android.api.endpoints.SearchApi
import com.flykpicks.android.model.auth.JWTLoginRequest
import com.flykpicks.android.testing.FIXED_NOW
import com.flykpicks.android.testing.PROFILE_JSON
import com.flykpicks.android.testing.REFRESH_JSON
import com.flykpicks.android.testing.TestNetwork
import com.flykpicks.android.testing.json
import com.flykpicks.android.testing.status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AuthNetworkTest {

    private lateinit var server: MockWebServer
    private lateinit var net: TestNetwork

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        net = TestNetwork(server)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun takeRequest(): RecordedRequest = server.takeRequest(5, TimeUnit.SECONDS)!!

    // MARK: Headers

    @Test
    fun authenticatedRequest_sendsBearerAndTelemetry_underBasePath() = runBlocking {
        net.signIn(access = "access-1")
        server.enqueue(json(PROFILE_JSON))

        net.api<ProfileApi>().fetchProfile()

        val request = takeRequest()
        assertEquals("/base/v1/users/me", request.url.encodedPath)
        assertEquals("Bearer access-1", request.headers["Authorization"])
        assertEquals(net.telemetry.sessionId, request.headers["X-Flyk-Session"])
        assertEquals(FIXED_NOW.toString(), request.headers["X-Flyk-Client-Ts"])
    }

    @Test
    fun unauthenticatedEndpoint_neverSendsBearer() = runBlocking {
        net.signIn(access = "access-1")
        server.enqueue(json("""{"mfaRequired":true,"mfaToken":"mfa-1"}"""))

        net.api<AuthApi>().login(JWTLoginRequest("a@b.c", "pw", "device-123", "Pixel"))

        assertNull(takeRequest().headers["Authorization"])
    }

    @Test
    fun posterRequests_skipTelemetryHeaders() {
        server.enqueue(status(200))

        net.client.newCall(Request.Builder().url(server.url("/base/v1/media/poster?id=1")).build())
            .execute().close()

        val request = takeRequest()
        assertNull(request.headers["X-Flyk-Session"])
        assertNull(request.headers["X-Flyk-Client-Ts"])
    }

    @Test
    fun sourceHeader_isSentFromEndpointAnnotations() = runBlocking {
        net.signIn()
        server.enqueue(json("[]"))

        net.api<SearchApi>().search("dune")

        val request = takeRequest()
        assertEquals("search.results", request.headers["X-Flyk-Source"])
        assertEquals("dune", request.url.queryParameter("query"))
    }

    // MARK: Refresh

    @Test
    fun expiredToken_refreshesOnce_thenRetriesWithNewToken() = runBlocking {
        net.signIn(access = "access-1", refresh = "refresh-1")
        server.enqueue(json("""{"code":"invalid_token"}""", code = 401))
        server.enqueue(json(REFRESH_JSON))
        server.enqueue(json(PROFILE_JSON))

        val profile = net.api<ProfileApi>().fetchProfile()

        assertEquals("user-1", profile.userId)
        assertEquals("Bearer access-1", takeRequest().headers["Authorization"])
        val refresh = takeRequest()
        assertEquals("/base/v2/auth/refresh", refresh.url.encodedPath)
        assertNull("refresh must not carry a bearer token", refresh.headers["Authorization"])
        val refreshBody = refresh.body!!.utf8()
        assertTrue(refreshBody, refreshBody.contains("\"refreshToken\":\"refresh-1\""))
        assertTrue(refreshBody, refreshBody.contains("\"deviceID\":\"device-123\""))
        assertEquals("Bearer access-2", takeRequest().headers["Authorization"])
        assertEquals("access-2", net.tokenStore.accessToken())
        assertEquals("refresh-2", net.tokenStore.refreshToken())
    }

    @Test
    fun concurrent401s_shareASingleRefresh() = runBlocking {
        net.signIn(access = "access-1")
        val refreshCount = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath.endsWith("/v2/auth/refresh") -> {
                    refreshCount.incrementAndGet()
                    Thread.sleep(200) // let the other 401s queue up behind the lock
                    json(REFRESH_JSON)
                }
                request.headers["Authorization"] == "Bearer access-2" -> json(PROFILE_JSON)
                else -> json("""{"code":"invalid_token"}""", code = 401)
            }
        }
        val api = net.api<ProfileApi>()

        val results = (1..5).map { async(Dispatchers.IO) { api.fetchProfile() } }.awaitAll()

        assertEquals(5, results.size)
        assertEquals(1, refreshCount.get())
    }

    @Test
    fun retriedRequestIsNotRefreshedAgain() = runBlocking {
        net.signIn()
        server.enqueue(json("""{"code":"invalid_token"}""", code = 401))
        server.enqueue(json(REFRESH_JSON))
        server.enqueue(json("""{"code":"invalid_token","message":"nope"}""", code = 401))

        val error = runCatching { net.api<ProfileApi>().fetchProfile() }.exceptionOrNull()

        assertEquals(ApiError.Http(401, "nope", "invalid_token"), error)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun tokenRevoked_clearsSessionKeepsTrust_andEmitsEvent_withoutRefreshing() = runBlocking {
        net.signIn()
        net.tokenStore.saveTrust("trust-1", "2027-01-01T00:00:00Z")
        server.enqueue(json("""{"code":"token_revoked","message":"Revoked"}""", code = 401))
        val events = mutableListOf<SessionEvent>()
        val collector = launch(Dispatchers.Unconfined) { net.sessionEvents.events.toList(events) }

        val error = runCatching { net.api<ProfileApi>().fetchProfile() }.exceptionOrNull()
        collector.cancel()

        assertEquals(ApiError.Http(401, "Revoked", "token_revoked"), error)
        assertEquals(1, server.requestCount)
        assertNull(net.tokenStore.accessToken())
        assertNull(net.tokenStore.refreshToken())
        assertEquals("trust-1", net.tokenStore.trustToken())
        assertEquals(listOf(SessionEvent.Ended(SessionEvent.Reason.TOKEN_REVOKED)), events)
    }

    @Test
    fun refreshRejected_endsSession() = runBlocking {
        net.signIn()
        server.enqueue(json("""{"code":"invalid_token"}""", code = 401))
        server.enqueue(json("""{"code":"invalid_refresh_token"}""", code = 401))
        val events = mutableListOf<SessionEvent>()
        val collector = launch(Dispatchers.Unconfined) { net.sessionEvents.events.toList(events) }

        val error = runCatching { net.api<ProfileApi>().fetchProfile() }.exceptionOrNull()
        collector.cancel()

        assertTrue(error is ApiError.Http && error.status == 401)
        assertNull(net.tokenStore.accessToken())
        assertEquals(listOf(SessionEvent.Ended(SessionEvent.Reason.REFRESH_REJECTED)), events)
    }

    @Test
    fun refreshServerError_keepsSession() = runBlocking {
        net.signIn(access = "access-1")
        server.enqueue(json("""{"code":"invalid_token"}""", code = 401))
        server.enqueue(status(503))
        val events = mutableListOf<SessionEvent>()
        val collector = launch(Dispatchers.Unconfined) { net.sessionEvents.events.toList(events) }

        val error = runCatching { net.api<ProfileApi>().fetchProfile() }.exceptionOrNull()
        collector.cancel()

        assertTrue(error is ApiError.Http && error.status == 401)
        assertEquals("access-1", net.tokenStore.accessToken())
        assertTrue(events.isEmpty())
    }

    @Test
    fun unauthenticated401_isReturnedWithoutRefresh() = runBlocking {
        net.signIn()
        server.enqueue(json("""{"message":"Invalid email or password."}""", code = 401))

        try {
            net.api<AuthApi>().login(JWTLoginRequest("a@b.c", "bad", "device-123", "Pixel"))
            fail("expected 401")
        } catch (e: ApiError.Http) {
            assertEquals(401, e.status)
        }
        assertEquals(1, server.requestCount)
        assertEquals("access-1", net.tokenStore.accessToken())
    }

    @Test
    fun noSession_401IsReturnedWithoutRefresh() = runBlocking {
        server.enqueue(json("""{"code":"missing_bearer_token"}""", code = 401))

        val error = runCatching { net.api<ProfileApi>().fetchProfile() }.exceptionOrNull()

        assertEquals(ApiError.Http(401, null, "missing_bearer_token"), error)
        assertEquals(1, server.requestCount)
    }
}
