package com.flykpicks.android.api

import android.util.Log
import com.flykpicks.android.api.endpoints.RefreshApi
import com.flykpicks.android.model.auth.JWTAuthRefreshRequest
import com.flykpicks.android.store.TokenStore
import com.flykpicks.android.util.DeviceIdProvider
import com.squareup.moshi.Moshi
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException

/**
 * Handles 401s on authenticated requests (blueprint §7.3, plan D2 / D14):
 *
 * - `token_revoked` → clear the session (keeping device trust) and emit [SessionEvent.Ended]; no retry.
 * - Otherwise refresh once — serialized, so concurrent 401s share a single refresh — and retry
 *   the request with the new token. A request is retried at most once.
 * - Refresh rejected by the server (4xx) → clear the session and emit [SessionEvent.Ended].
 * - Refresh failed for transport reasons (offline, 5xx, bad body) → keep the session and let the
 *   original 401 surface. iOS logs the user out here (ios-gaps.md G2).
 *
 * Unauthenticated requests (no bearer header) are never refreshed.
 */
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val deviceIdProvider: DeviceIdProvider,
    private val refreshApi: RefreshApi,
    private val sessionEvents: SessionEvents,
    moshi: Moshi,
) : Authenticator {

    private val envelopeAdapter = moshi.adapter(ApiErrorEnvelope::class.java)
    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        val failedToken = request.header(AUTHORIZATION_HEADER)?.removePrefix("Bearer ") ?: return null
        if (response.priorResponse?.code == 401) return null

        if (serverCode(response) == TOKEN_REVOKED) {
            endSession(SessionEvent.Reason.TOKEN_REVOKED)
            return null
        }

        synchronized(lock) {
            // Session cleared (logout / revocation) while this request was in flight.
            val current = tokenStore.accessToken() ?: return null
            // Another request already refreshed while this one waited for the lock.
            if (current != failedToken) return request.withBearer(current)

            return when (val result = refresh()) {
                is RefreshResult.Refreshed -> request.withBearer(result.accessToken)
                RefreshResult.Rejected -> {
                    endSession(SessionEvent.Reason.REFRESH_REJECTED)
                    null
                }
                RefreshResult.Unavailable -> null
            }
        }
    }

    private fun refresh(): RefreshResult {
        val refreshToken = tokenStore.refreshToken() ?: return RefreshResult.Rejected
        val body = JWTAuthRefreshRequest(refreshToken, deviceIdProvider.deviceId)
        return try {
            val response = refreshApi.refresh(body).execute()
            val tokens = response.body()
            when {
                response.isSuccessful && tokens != null -> {
                    tokenStore.save(tokens.accessToken, tokens.refreshToken, tokens.userId)
                    RefreshResult.Refreshed(tokens.accessToken)
                }
                response.code() in 400..499 -> RefreshResult.Rejected
                else -> RefreshResult.Unavailable
            }
        } catch (e: IOException) {
            Log.w(TAG, "Token refresh failed; keeping session", e)
            RefreshResult.Unavailable
        } catch (e: RuntimeException) {
            // Malformed refresh body (JsonDataException) — a server bug, not a credential problem.
            Log.w(TAG, "Token refresh returned an unreadable body; keeping session", e)
            RefreshResult.Unavailable
        }
    }

    private fun endSession(reason: SessionEvent.Reason) {
        tokenStore.clear()
        sessionEvents.emit(SessionEvent.Ended(reason))
    }

    private fun serverCode(response: Response): String? =
        try {
            envelopeAdapter.fromJson(response.peekBody(ENVELOPE_PEEK_BYTES).string())?.code
        } catch (_: Exception) {
            null
        }

    private sealed interface RefreshResult {
        data class Refreshed(val accessToken: String) : RefreshResult
        data object Rejected : RefreshResult
        data object Unavailable : RefreshResult
    }

    private companion object {
        const val TAG = "TokenAuthenticator"
        const val TOKEN_REVOKED = "token_revoked"
        const val ENVELOPE_PEEK_BYTES = 4096L
    }
}
