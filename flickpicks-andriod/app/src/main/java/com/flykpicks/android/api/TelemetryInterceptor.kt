package com.flykpicks.android.api

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** One id per app launch, sent with every content request (blueprint §13.1, iOS `TelemetrySession`). */
@Singleton
class TelemetrySession @Inject constructor(private val clock: Clock) {
    // Uppercase to match the iOS `UUID().uuidString` format.
    val sessionId: String = UUID.randomUUID().toString().uppercase(Locale.ROOT)

    /** ISO-8601 instant with second precision, e.g. `2026-10-07T21:12:43Z`. */
    fun clientTs(): String = clock.instant().truncatedTo(ChronoUnit.SECONDS).toString()
}

/**
 * Adds `X-Flyk-Session` / `X-Flyk-Client-Ts` to every request except poster images (blueprint §7.2).
 * With [onlyOrigin] set, other origins (e.g. a CDN poster URL) never get them.
 */
class TelemetryInterceptor(
    private val session: TelemetrySession,
    private val onlyOrigin: HttpUrl? = null,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.contains("/media/poster")) return chain.proceed(request)
        if (onlyOrigin != null && !request.url.hasOriginOf(onlyOrigin)) return chain.proceed(request)
        return chain.proceed(
            request.newBuilder()
                .header(SESSION_HEADER, session.sessionId)
                .header(CLIENT_TS_HEADER, session.clientTs())
                .build(),
        )
    }

    companion object {
        const val SESSION_HEADER = "X-Flyk-Session"
        const val CLIENT_TS_HEADER = "X-Flyk-Client-Ts"
    }
}
