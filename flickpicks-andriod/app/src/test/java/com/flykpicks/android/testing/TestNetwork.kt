package com.flykpicks.android.testing

import com.flykpicks.android.api.ApiClient
import com.flykpicks.android.api.ApiErrorMapper
import com.flykpicks.android.api.SessionEvents
import com.flykpicks.android.api.TelemetrySession
import com.flykpicks.android.api.TokenAuthenticator
import com.flykpicks.android.api.endpoints.RefreshApi
import com.flykpicks.android.store.TokenStore
import com.flykpicks.android.util.GenreTranslator
import mockwebserver3.MockResponse
import okhttp3.HttpUrl.Companion.toHttpUrl
import mockwebserver3.MockWebServer
import retrofit2.Retrofit
import retrofit2.create
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

val FIXED_NOW: Instant = Instant.parse("2026-10-07T21:00:00Z")

/**
 * The production networking stack (same `ApiClient` builders the Hilt module uses) pointed at a
 * MockWebServer. The base URL has a path prefix to prove endpoint paths are relative.
 */
class TestNetwork(server: MockWebServer, val clock: Clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC)) {
    val secureStore = InMemorySecureStore()
    val tokenStore = TokenStore(secureStore)
    val device = FakeDeviceIdProvider()
    val sessionEvents = SessionEvents()
    val genres = GenreTranslator()
    val moshi = ApiClient.moshi()
    val telemetry = TelemetrySession(clock)
    val baseUrl = server.url("/base/").toString()

    private val base = ApiClient.baseClient()
    private val refreshApi: RefreshApi =
        ApiClient.retrofit(baseUrl, ApiClient.apiClient(base, telemetry), moshi).create()

    val authenticator = TokenAuthenticator(tokenStore, device, refreshApi, sessionEvents, moshi)
    val client = ApiClient.apiClient(base, telemetry, tokenStore, authenticator)
    val retrofit: Retrofit = ApiClient.retrofit(baseUrl, client, moshi, ApiErrorMapper(moshi, clock))
    val imageClient = ApiClient.imageClient(base, telemetry, tokenStore, authenticator, baseUrl.toHttpUrl())

    inline fun <reified T : Any> api(): T = retrofit.create()

    fun signIn(access: String = "access-1", refresh: String = "refresh-1", userId: String = "user-1") {
        tokenStore.save(access, refresh, userId)
    }
}

fun json(body: String, code: Int = 200): MockResponse =
    MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

fun status(code: Int): MockResponse = MockResponse.Builder().code(code).build()

const val PROFILE_JSON = """
{
  "userId": "user-1",
  "firstName": "Ada",
  "lastName": "Lovelace",
  "displayName": "ada",
  "email": "ada@example.com",
  "avatarUrl": null,
  "roles": ["USER"],
  "createdAt": "2026-01-02T03:04:05Z",
  "publicNameSource": "FIRST_NAME"
}
"""

const val REFRESH_JSON = """{"accessToken":"access-2","refreshToken":"refresh-2","userID":"user-1"}"""

fun movieJson(flyksId: String = "m1", genreIds: String = "[28]") = """
{
  "id": "550", "flyksId": "$flyksId", "title": "Fight Club", "overview": "…",
  "media_type": "movie", "original_language": "en", "genre_ids": $genreIds,
  "popularity": 61.4, "release_date": "1999-10-15", "vote_average": 8.4, "vote_count": 27000,
  "posterUrl": "https://img.example/fc.jpg"
}
"""

fun tvJson(flyksId: String = "t1", mediaType: String? = "tv") = """
{
  "id": "1399", "flyksId": "$flyksId", "name": "Game of Thrones", "overview": "…",
  ${mediaType?.let { "\"media_type\": \"$it\"," } ?: "\"media_type\": \"\","}
  "original_language": "en", "genre_ids": [10765],
  "popularity": 300.1, "first_air_date": "2011-04-17", "vote_average": 8.5, "vote_count": 21000,
  "posterUrl": null
}
"""
