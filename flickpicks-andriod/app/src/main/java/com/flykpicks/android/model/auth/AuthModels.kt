package com.flykpicks.android.model.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Wire keys follow the iOS models exactly (note `deviceID` / `userID`). Null fields are omitted
// when encoding, matching Swift's `encodeIfPresent`.

// MARK: Login

@JsonClass(generateAdapter = true)
data class JWTLoginRequest(
    val email: String,
    val password: String,
    @Json(name = "deviceID") val deviceId: String,
    val deviceName: String,
    /** Omitted when null. */
    val trustToken: String? = null,
)

/**
 * Login returns either a session or an MFA challenge under the same 200, keyed by
 * [mfaRequired]. Tokens are optional because the challenge leg sends nulls.
 */
@JsonClass(generateAdapter = true)
data class JWTLoginResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    @Json(name = "userID") val userId: String? = null,
    val mfaRequired: Boolean,
    val mfaToken: String? = null,
    /** Present only when rememberDevice was true and the deployment supports it. */
    val trustToken: String? = null,
    val trustExpiresAt: String? = null,
    /** Present only when the code that satisfied the challenge was a backup code. */
    val backupCodesRemaining: Int? = null,
)

// MARK: MFA (second-factor challenge)

@JsonClass(generateAdapter = true)
data class JWTMFAVerifyRequest(
    val mfaToken: String,
    val code: String,
    /** Sent only when true to keep the wire format clean (iOS parity); use [of]. */
    val rememberDevice: Boolean? = null,
) {
    companion object {
        fun of(mfaToken: String, code: String, rememberDevice: Boolean) =
            JWTMFAVerifyRequest(mfaToken, code, rememberDevice.takeIf { it })
    }
}

// MARK: Logout / refresh

@JsonClass(generateAdapter = true)
data class JWTLogoutRequest(
    val refreshToken: String,
    @Json(name = "deviceID") val deviceId: String,
)

@JsonClass(generateAdapter = true)
data class JWTAuthRefreshRequest(
    val refreshToken: String,
    @Json(name = "deviceID") val deviceId: String,
)

@JsonClass(generateAdapter = true)
data class JWTAuthRefreshResponse(
    val accessToken: String,
    val refreshToken: String,
    @Json(name = "userID") val userId: String,
)

// MARK: Registration & email verification

@JsonClass(generateAdapter = true)
data class UserRegistrationRequest(val email: String, val password: String)

/**
 * A single opaque message, identical whether or not the address was already registered.
 * Never surface account-enumeration signals from it.
 */
@JsonClass(generateAdapter = true)
data class UserRegistrationResponseDTO(val message: String)

@JsonClass(generateAdapter = true)
data class EmailVerifyRequest(val token: String)

@JsonClass(generateAdapter = true)
data class EmailVerifyResendRequest(val email: String)

@JsonClass(generateAdapter = true)
data class MessageResponseDTO(val message: String)

// MARK: Account recovery

@JsonClass(generateAdapter = true)
data class OTPRequestBody(val subjectId: String)

@JsonClass(generateAdapter = true)
data class OTPRequestResponse(val expiresAt: String)

@JsonClass(generateAdapter = true)
data class OTPVerifyBody(val subjectId: String, val otp: String)

@JsonClass(generateAdapter = true)
data class OTPVerifyResponse(
    val valid: Boolean,
    val expiresAt: String? = null,
    /** Only present when [valid] is true. */
    val recoveryToken: String? = null,
)

@JsonClass(generateAdapter = true)
data class ChangeLostPasswordBody(
    val email: String,
    val newPassword: String,
    val recoveryToken: String,
)

// MARK: MFA reset request (public; always 202 so the client can't infer account existence)

@JsonClass(generateAdapter = true)
data class MFAResetRequestBody(
    val email: String,
    val password: String,
    /** Omitted when null. */
    val note: String? = null,
)

// MARK: Outcomes

/** Result of the login endpoint: a stored session, or an MFA challenge to complete. */
sealed interface LoginOutcome {
    data object Session : LoginOutcome

    /** The token is short-lived: keep it in memory only, never persist it. */
    data class MFAChallenge(val mfaToken: String) : LoginOutcome
}
