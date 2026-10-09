package com.flykpicks.android.service

import com.flykpicks.android.api.ApiError
import com.flykpicks.android.api.endpoints.AuthApi
import com.flykpicks.android.model.auth.AuthError
import com.flykpicks.android.model.auth.ChangeLostPasswordBody
import com.flykpicks.android.model.auth.EmailVerifyRequest
import com.flykpicks.android.model.auth.EmailVerifyResendRequest
import com.flykpicks.android.model.auth.JWTLoginRequest
import com.flykpicks.android.model.auth.JWTLogoutRequest
import com.flykpicks.android.model.auth.JWTMFAVerifyRequest
import com.flykpicks.android.model.auth.LoginOutcome
import com.flykpicks.android.model.auth.MFAResetRequestBody
import com.flykpicks.android.model.auth.MessageResponseDTO
import com.flykpicks.android.model.auth.OTPRequestBody
import com.flykpicks.android.model.auth.OTPRequestResponse
import com.flykpicks.android.model.auth.OTPVerifyBody
import com.flykpicks.android.model.auth.OTPVerifyResponse
import com.flykpicks.android.model.auth.UserRegistrationRequest
import com.flykpicks.android.model.auth.UserRegistrationResponseDTO
import com.flykpicks.android.store.TokenStore
import com.flykpicks.android.util.DeviceIdProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Login, MFA, registration, recovery and logout (blueprint §8.3–8.6, iOS `AuthService`).
 * Token refresh lives in `TokenAuthenticator`.
 */
@Singleton
class AuthService @Inject constructor(
    private val api: AuthApi,
    private val tokenStore: TokenStore,
    private val device: DeviceIdProvider,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    val hasSession: Boolean get() = tokenStore.accessToken() != null

    /**
     * Tokens are persisted only for [LoginOutcome.Session]. The MFA token stays with the caller.
     * A stored device-trust token is sent so a trusted device can skip MFA.
     */
    suspend fun login(email: String, password: String): LoginOutcome {
        val storedTrust = withContext(io) { tokenStore.trustToken() }
        val request = JWTLoginRequest(email, password, device.deviceId, device.deviceName, storedTrust)

        // 401 is deliberately uniform (contract §3): show the server message, don't infer why.
        val response = mapHttpErrors({ if (it.status == 401) AuthError.InvalidCredentials(it.serverMessage) else null }) {
            api.login(request)
        }

        if (response.mfaRequired) {
            val token = response.mfaToken?.takeIf { it.isNotEmpty() }
                ?: throw AuthError.InvalidMFAChallenge(null)
            // A challenge despite a trust token means the server stopped honouring it.
            if (storedTrust != null) withContext(io) { tokenStore.clearTrust() }
            return LoginOutcome.MFAChallenge(token)
        }

        val access = response.accessToken
        val refresh = response.refreshToken
        val userId = response.userId
        if (access == null || refresh == null || userId == null) throw AuthError.InvalidCredentials(null)

        withContext(io) {
            // Trust stored for a different account is dead.
            tokenStore.userId()?.let { if (it != userId) tokenStore.clearTrust() }
            tokenStore.save(access, refresh, userId)
        }
        return LoginOutcome.Session
    }

    /**
     * Second leg of login. Returns `backupCodesRemaining` when a backup code was used (non-null
     * is the signal), else null. On [AuthError.InvalidMFAChallenge], [AuthError.MFAChallengeLocked]
     * and [AuthError.MFAChallengeExpired] the caller must discard the MFA token and return to Login.
     */
    suspend fun verifyMFA(mfaToken: String, code: String, rememberDevice: Boolean): Int? {
        val response = mapHttpErrors(::mapMfaVerifyError) {
            api.verifyMFA(JWTMFAVerifyRequest.of(mfaToken, code, rememberDevice))
        }

        val access = response.accessToken
        val refresh = response.refreshToken
        val userId = response.userId
        if (access == null || refresh == null || userId == null || response.mfaRequired) {
            throw AuthError.InvalidMFAChallenge(null)
        }

        withContext(io) {
            tokenStore.save(access, refresh, userId)
            // Trust is granted only when rememberDevice was set and the deployment supports it.
            val trustToken = response.trustToken
            val trustExpiresAt = response.trustExpiresAt
            if (trustToken != null && trustExpiresAt != null) tokenStore.saveTrust(trustToken, trustExpiresAt)
        }
        return response.backupCodesRemaining
    }

    /** Public; spends the login throttle budget. Only [ApiError.RateLimited] is a real failure. */
    suspend fun requestMFAReset(email: String, password: String, note: String?) {
        api.requestMFAReset(MFAResetRequestBody(email, password, note))
    }

    /** No session is created. The response is identical whether or not the address exists. */
    suspend fun register(email: String, password: String): UserRegistrationResponseDTO =
        // 400 covers bad format, short password and rejected domains; the message is for display.
        mapHttpErrors({ if (it.status == 400) AuthError.InvalidInput(it.serverMessage) else null }) {
            api.register(UserRegistrationRequest(email.lowercase(Locale.ROOT), password))
        }

    /** The token comes from the verification link. Never log or forward it. */
    suspend fun verifyEmail(token: String): MessageResponseDTO =
        mapHttpErrors({ if (it.status == 400) AuthError.InvalidVerificationToken(it.serverMessage) else null }) {
            api.verifyEmail(EmailVerifyRequest(token))
        }

    /** Always 202; 429 surfaces as [ApiError.RateLimited] so the UI can show a deadline. */
    suspend fun resendVerification(email: String): MessageResponseDTO =
        api.resendVerification(EmailVerifyResendRequest(email.lowercase(Locale.ROOT)))

    suspend fun requestOneTimePassword(subjectId: String): OTPRequestResponse =
        api.requestOTP(OTPRequestBody(subjectId))

    suspend fun verifyOneTimePassword(subjectId: String, otp: String): OTPVerifyResponse =
        try {
            mapHttpErrors({
                when (it.status) {
                    400 -> AuthError.OTPInvalid
                    401 -> AuthError.OTPExpired
                    else -> null
                }
            }) { api.verifyOTP(OTPVerifyBody(subjectId, otp)) }
        } catch (_: ApiError.RateLimited) {
            // iOS intends this but its branch is unreachable (ios-gaps.md G1).
            throw AuthError.OTPLocked
        }

    suspend fun changeLostPassword(email: String, newPassword: String, recoveryToken: String) {
        mapHttpErrors({
            when (it.status) {
                400 -> AuthError.RecoveryTokenMissing
                401 -> AuthError.RecoveryTokenInvalid
                else -> null
            }
        }) {
            api.changeLostPassword(ChangeLostPasswordBody(email.lowercase(Locale.ROOT), newPassword, recoveryToken))
        }
    }

    /**
     * Revokes the refresh token server-side, then clears the local session — always, even when
     * the server call fails (iOS `AuthStore.logout` does the same). Device trust is kept.
     */
    suspend fun logout() {
        val refreshToken = withContext(io) { tokenStore.refreshToken() }
        try {
            if (refreshToken != null) api.logout(JWTLogoutRequest(refreshToken, device.deviceId))
        } finally {
            withContext(io) { tokenStore.clear() }
        }
    }

    suspend fun clearDeviceTrust() = withContext(io) { tokenStore.clearTrust() }

    private fun mapMfaVerifyError(e: ApiError.Http): Throwable? = when {
        e.status == 401 && e.serverCode == "invalid_mfa_code" -> AuthError.InvalidMFACode(e.serverMessage)
        e.status == 401 -> AuthError.InvalidMFAChallenge(e.serverMessage)
        e.status == 403 && e.serverCode == "mfa_challenge_locked" -> AuthError.MFAChallengeLocked(e.serverMessage)
        e.status == 410 && e.serverCode == "mfa_challenge_expired" -> AuthError.MFAChallengeExpired(e.serverMessage)
        else -> null
    }
}
