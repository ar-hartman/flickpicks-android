package com.flykpicks.android.api

import com.flykpicks.android.util.DateFormatting
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import java.io.EOFException
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Translates HTTP responses and transport exceptions into [ApiError]s (iOS `APIClient.httpError`). */
class ApiErrorMapper(moshi: Moshi, private val clock: Clock) {

    private val envelopeAdapter = moshi.adapter(ApiErrorEnvelope::class.java)

    /**
     * 429 becomes [ApiError.RateLimited] with a deadline from (in order) the envelope's
     * `nextAllowedAt`, the `Retry-After` header, or a default — clamped to [1s, 15m] because
     * servers can misreport and a bad value must not disable a submit button for hours.
     */
    fun httpError(status: Int, body: String?, retryAfter: String?): ApiError {
        val envelope = body?.takeIf { it.isNotBlank() }?.let(::parseEnvelope)

        if (status == 429) {
            return ApiError.RateLimited(
                code = envelope?.code ?: "rate_limited",
                serverMessage = envelope?.message,
                nextAllowedAt = rateLimitDeadline(envelope?.nextAllowedAt, retryAfter),
            )
        }
        if (body.isNullOrEmpty()) return ApiError.Http(status, null, null)
        if (envelope?.code != null) return ApiError.Http(status, envelope.message, envelope.code)
        return ApiError.Http(status, body, null)
    }

    /** Maps a transport/decoding exception. Unknown throwables (incl. cancellation) pass through. */
    fun mapFailure(t: Throwable): Throwable = when (t) {
        is ApiError -> t
        is JsonDataException -> ApiError.Decoding(t.message ?: "Malformed response")
        // Both are IOExceptions, so they must be matched before the generic case below.
        is JsonEncodingException -> ApiError.Decoding(t.message ?: "Malformed response")
        is EOFException -> ApiError.Decoding("Empty or truncated response body")
        is IOException -> ApiError.NetworkUnavailable
        else -> t
    }

    private fun parseEnvelope(body: String): ApiErrorEnvelope? =
        try {
            envelopeAdapter.fromJson(body)
        } catch (_: IOException) {
            null
        } catch (_: JsonDataException) {
            null
        }

    private fun rateLimitDeadline(nextAllowedAt: String?, retryAfter: String?): Instant {
        val now = clock.instant()
        val candidate = nextAllowedAt?.let(DateFormatting::parseInstant)
            ?: retryAfter?.trim()?.toDoubleOrNull()?.let { now.plusMillis((it * 1000).toLong()) }
        val target = candidate ?: now.plus(RATE_LIMIT_DEFAULT)
        val interval = Duration.between(now, target).coerceIn(RATE_LIMIT_MIN, RATE_LIMIT_MAX)
        return now.plus(interval)
    }

    private companion object {
        val RATE_LIMIT_MIN: Duration = Duration.ofSeconds(1)
        val RATE_LIMIT_MAX: Duration = Duration.ofMinutes(15)
        val RATE_LIMIT_DEFAULT: Duration = Duration.ofSeconds(30)
    }
}
