package com.flykpicks.android.store

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session and device-trust tokens (blueprint §8.1). Key names match iOS.
 *
 * Session keys are cleared on logout; device-trust keys survive it (trust is device-scoped,
 * not session-scoped). Writes are atomic (ios-gaps.md G9). Safe to call from any thread.
 */
@Singleton
class TokenStore @Inject constructor(private val store: SecureStore) {

    fun save(accessToken: String, refreshToken: String, userId: String) {
        store.putAll(
            mapOf(
                ACCESS_TOKEN to accessToken,
                REFRESH_TOKEN to refreshToken,
                USER_ID to userId,
            ),
        )
    }

    fun accessToken(): String? = store.getString(ACCESS_TOKEN)
    fun refreshToken(): String? = store.getString(REFRESH_TOKEN)
    fun userId(): String? = store.getString(USER_ID)

    /** Clears the session. Device trust is intentionally kept. */
    fun clear() {
        store.putAll(mapOf(ACCESS_TOKEN to null, REFRESH_TOKEN to null, USER_ID to null))
    }

    fun saveTrust(token: String, expiresAt: String) {
        store.putAll(mapOf(TRUST_TOKEN to token, TRUST_EXPIRES_AT to expiresAt))
    }

    fun trustToken(): String? = store.getString(TRUST_TOKEN)
    fun trustExpiresAt(): String? = store.getString(TRUST_EXPIRES_AT)

    fun clearTrust() {
        store.putAll(mapOf(TRUST_TOKEN to null, TRUST_EXPIRES_AT to null))
    }

    private companion object {
        const val ACCESS_TOKEN = "access_token"
        const val REFRESH_TOKEN = "refresh_token"
        const val USER_ID = "user_id"
        const val TRUST_TOKEN = "device_trust_token"
        const val TRUST_EXPIRES_AT = "device_trust_expires_at"
    }
}
