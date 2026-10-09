package com.flykpicks.android.api

import com.flykpicks.android.api.endpoints.ProfileApi
import com.flykpicks.android.testing.FIXED_NOW
import com.flykpicks.android.testing.TestNetwork
import com.flykpicks.android.testing.json
import com.flykpicks.android.testing.status
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration

class ApiErrorMappingTest {

    private lateinit var server: MockWebServer
    private lateinit var net: TestNetwork

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        net = TestNetwork(server)
        net.signIn()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun failureOf(response: MockResponse): Throwable? {
        server.enqueue(response)
        return runBlocking { runCatching { net.api<ProfileApi>().fetchProfile() }.exceptionOrNull() }
    }

    private fun deadlineOf(response: MockResponse): Duration {
        val error = failureOf(response) as ApiError.RateLimited
        return Duration.between(FIXED_NOW, error.nextAllowedAt)
    }

    // MARK: HTTP errors

    @Test
    fun envelopeWithCode_mapsToHttpWithServerCode() {
        val error = failureOf(json("""{"code":"display_name_taken","message":"Taken"}""", code = 409))
        assertEquals(ApiError.Http(409, "Taken", "display_name_taken"), error)
    }

    @Test
    fun nonEnvelopeBody_isKeptAsMessage() {
        val error = failureOf(MockResponse.Builder().code(500).body("upstream exploded").build())
        assertEquals(ApiError.Http(500, "upstream exploded", null), error)
    }

    @Test
    fun envelopeWithoutCode_usesRawBody_likeIos() {
        val body = """{"message":"Bad request"}"""
        assertEquals(ApiError.Http(400, body, null), failureOf(json(body, code = 400)))
    }

    @Test
    fun emptyErrorBody_hasNoMessage() {
        assertEquals(ApiError.Http(503, null, null), failureOf(status(503)))
    }

    // MARK: Rate limiting

    @Test
    fun rateLimit_usesEnvelopeDeadline() {
        val response = json(
            """{"code":"login_throttled","message":"Slow down","nextAllowedAt":"2026-10-07T21:02:00Z"}""",
            code = 429,
        )
        val error = failureOf(response) as ApiError.RateLimited
        assertEquals("login_throttled", error.code)
        assertEquals("Slow down", error.serverMessage)
        assertEquals(Duration.ofMinutes(2), Duration.between(FIXED_NOW, error.nextAllowedAt))
    }

    @Test
    fun rateLimit_fallsBackToRetryAfterSeconds() {
        val response = MockResponse.Builder().code(429).addHeader("Retry-After", "5").build()
        assertEquals(Duration.ofSeconds(5), deadlineOf(response))
    }

    @Test
    fun rateLimit_defaultsToThirtySeconds() {
        assertEquals(Duration.ofSeconds(30), deadlineOf(status(429)))
        assertEquals("rate_limited", (failureOf(status(429)) as ApiError.RateLimited).code)
    }

    @Test
    fun rateLimit_isClampedToFifteenMinutes() {
        val response = json("""{"nextAllowedAt":"2026-10-08T03:00:00Z"}""", code = 429)
        assertEquals(Duration.ofMinutes(15), deadlineOf(response))
    }

    @Test
    fun rateLimit_pastDeadline_isClampedToOneSecond() {
        val response = json("""{"nextAllowedAt":"2026-10-07T20:00:00.123Z"}""", code = 429)
        assertEquals(Duration.ofSeconds(1), deadlineOf(response))
    }

    // MARK: Transport / decoding

    @Test
    fun malformedJson_isDecodingError() {
        assertTrue(failureOf(json("""{"userId": """)) is ApiError.Decoding)
    }

    @Test
    fun missingRequiredField_isDecodingError() {
        assertTrue(failureOf(json("""{"userId":"u"}""")) is ApiError.Decoding)
    }

    @Test
    fun emptySuccessBody_forNonUnitEndpoint_isDecodingError() {
        assertTrue(failureOf(status(204)) is ApiError.Decoding)
    }

    @Test
    fun emptySuccessBody_forUnitEndpoint_succeeds() = runBlocking {
        server.enqueue(status(204))
        net.api<ProfileApi>().removeAvatar()
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun unreachableServer_isNetworkUnavailable() {
        server.close()
        val error = runBlocking { runCatching { net.api<ProfileApi>().fetchProfile() }.exceptionOrNull() }
        assertEquals(ApiError.NetworkUnavailable, error)
    }

    @Test
    fun userFacingMessage_prefersTypedMessages() {
        assertEquals("Taken", ApiError.Http(409, "Taken", null).userFacingMessage)
        assertEquals(
            "No internet connection. Check your connection and try again.",
            ApiError.NetworkUnavailable.userFacingMessage,
        )
        assertEquals("Something went wrong. Please try again.", IllegalStateException("x").userFacingMessage)
    }
}
