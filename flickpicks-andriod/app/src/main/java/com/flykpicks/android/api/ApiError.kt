package com.flykpicks.android.api

import com.squareup.moshi.JsonClass
import java.time.Instant

/** Implemented by every error whose message can be shown to the user as-is. */
interface UserFacingError {
    val userMessage: String
}

private const val GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again."

/** The message to show for any failure; non-[UserFacingError]s get a generic message. */
val Throwable.userFacingMessage: String
    get() = (this as? UserFacingError)?.userMessage ?: GENERIC_ERROR_MESSAGE

/**
 * Transport-level failures, mirroring iOS `APIError`. Every Retrofit call made through
 * [ApiErrorCallAdapterFactory] fails with one of these (or a [kotlinx.coroutines.CancellationException]).
 *
 * Switch on [Http.serverCode], never on message text — messages are not stable.
 * Auth-filter codes (all 401): `missing_bearer_token`, `empty_bearer_token`, `token_revoked`,
 * `invalid_token`, `auth_error`.
 */
sealed class ApiError(detail: String? = null) : Exception(detail), UserFacingError {

    /** Non-2xx response (other than 429). [serverCode] comes from the standard error envelope. */
    data class Http(
        val status: Int,
        val serverMessage: String?,
        val serverCode: String?,
    ) : ApiError("HTTP $status${serverCode?.let { " ($it)" }.orEmpty()}") {
        override val userMessage: String get() = serverMessage ?: "Request failed."
    }

    /**
     * 429. [nextAllowedAt] is an absolute deadline, already clamped to a sane range, so
     * callers can drive countdowns from it directly.
     */
    data class RateLimited(
        val code: String,
        val serverMessage: String?,
        val nextAllowedAt: Instant,
    ) : ApiError("Rate limited until $nextAllowedAt") {
        override val userMessage: String
            get() = serverMessage ?: "Too many attempts. Please try again shortly."
    }

    /** The response arrived but could not be decoded (or was unexpectedly empty). */
    data class Decoding(val detail: String) : ApiError(detail) {
        override val userMessage: String
            get() = "Something went wrong. Please update the app if this persists."
    }

    /** Device offline, DNS failure, timeout, connection reset. */
    data object NetworkUnavailable : ApiError("Network unavailable") {
        override val userMessage: String
            get() = "No internet connection. Check your connection and try again."
    }
}

/** Standard server error envelope (SCHEMAS.md §1.1). */
@JsonClass(generateAdapter = true)
data class ApiErrorEnvelope(
    val code: String?,
    val message: String?,
    val nextAllowedAt: String?,
)
