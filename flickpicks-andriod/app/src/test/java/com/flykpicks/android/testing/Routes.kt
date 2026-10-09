package com.flykpicks.android.testing

import com.flykpicks.android.store.FeatureFlagCache
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * MockWebServer dispatcher that answers by endpoint path (relative to [TestNetwork]'s base), each
 * with its own queue, so background calls such as the feature-flag fetch after sign-in can't take
 * another endpoint's response. Unqueued feature-flag requests get an empty flag map; anything
 * else unqueued gets a 404.
 */
class Routes : Dispatcher() {
    private val queues = ConcurrentHashMap<String, ConcurrentLinkedQueue<MockResponse>>()
    private val received = ConcurrentLinkedQueue<RecordedRequest>()

    fun on(path: String, vararg responses: MockResponse) {
        queues.getOrPut(path) { ConcurrentLinkedQueue() }.addAll(responses)
    }

    fun requestsTo(path: String): List<RecordedRequest> = received.filter { it.path() == path }

    fun bodyOf(path: String): String = requestsTo(path).single().body!!.utf8()

    override fun dispatch(request: RecordedRequest): MockResponse {
        received += request
        val path = request.path()
        return queues[path]?.poll()
            ?: if (path == "v1/feature-flags") json("""{"flags":{}}""") else status(404)
    }

    private fun RecordedRequest.path(): String = url.encodedPath.substringAfter("/base/")
}

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
