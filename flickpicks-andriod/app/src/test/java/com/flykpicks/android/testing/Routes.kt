package com.flykpicks.android.testing

import com.flykpicks.android.store.FeatureFlagCache
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * MockWebServer dispatcher that answers by endpoint path (relative to [TestNetwork]'s base), each
 * with its own queue, so background calls such as the feature-flag fetch after sign-in can't take
 * another endpoint's response. Unqueued feature-flag requests get an empty flag map and unqueued
 * genre requests get [GENRES_JSON]; anything else unqueued gets a 404.
 */
class Routes : Dispatcher() {
    private val queues = ConcurrentHashMap<String, ConcurrentLinkedQueue<MockResponse>>()
    private val received = ConcurrentLinkedQueue<RecordedRequest>()
    private val holds = ConcurrentHashMap<String, CountDownLatch>()

    fun on(path: String, vararg responses: MockResponse) {
        queues.getOrPut(path) { ConcurrentLinkedQueue() }.addAll(responses)
    }

    /**
     * Makes the next request to [path] wait, once it has been recorded, until the returned
     * latch is released: a request held "in flight" while the test does something else.
     */
    fun hold(path: String): CountDownLatch = CountDownLatch(1).also { holds[path] = it }

    fun requestsTo(path: String): List<RecordedRequest> = received.filter { it.path() == path }

    fun bodyOf(path: String): String = requestsTo(path).single().body!!.utf8()

    /** Every request so far, as endpoint paths in arrival order. */
    fun paths(): List<String> = received.map { it.path() }

    /** Blocks until [count] requests to [path] have reached the server. */
    fun awaitRequests(path: String, count: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (requestsTo(path).size < count) {
            check(System.nanoTime() < deadline) { "Expected $count request(s) to $path, saw ${requestsTo(path).size}" }
            Thread.sleep(5)
        }
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.path()
        // Responses go out in arrival order, even to a request that is then held.
        val response = queues[path]?.poll() ?: when (path) {
            "v1/feature-flags" -> json("""{"flags":{}}""")
            "v1/genres" -> json(GENRES_JSON)
            else -> status(404)
        }
        val hold = holds.remove(path)
        received += request
        hold?.await(10, TimeUnit.SECONDS)
        return response
    }

    private fun RecordedRequest.path(): String = url.encodedPath.substringAfter("/base/")
}

const val GENRES_JSON = """{"movie":{"18":"Drama","80":"Crime","35":"Comedy"},"tv":{"10765":"Sci-Fi & Fantasy","9648":"Mystery"}}"""

class InMemoryFeatureFlagCache(var json: String? = null) : FeatureFlagCache {
    override fun read(): String? = json
    override fun write(json: String) {
        this.json = json
    }
}

const val SESSION_JSON = """{"accessToken":"access-1","refreshToken":"refresh-1","userID":"user-1","mfaRequired":false}"""
const val MFA_CHALLENGE_JSON = """{"mfaRequired":true,"mfaToken":"mfa-1"}"""

fun errorJson(code: Int, serverCode: String, message: String) =
    json("""{"code":"$serverCode","message":"$message"}""", code)

/** A 429 whose deadline is five minutes after [FIXED_NOW]. */
fun rateLimitedJson(message: String = "Slow down.") =
    json("""{"code":"rate_limited","message":"$message","nextAllowedAt":"2026-10-07T21:05:00Z"}""", 429)
