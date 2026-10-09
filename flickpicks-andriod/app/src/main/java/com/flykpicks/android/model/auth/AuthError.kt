package com.flykpicks.android.model.auth

import com.flykpicks.android.api.UserFacingError

/** Typed auth failures (iOS `AuthError`). Server messages are shown verbatim when present. */
sealed class AuthError(detail: String?) : Exception(detail), UserFacingError {

    data class InvalidInput(val serverMessage: String?) : AuthError(serverMessage) {
        override val userMessage get() = serverMessage ?: "Invalid input."
    }

    data class InvalidCredentials(val serverMessage: String?) : AuthError(serverMessage) {
        override val userMessage get() = serverMessage ?: "Invalid email or password."
    }

    /** Terminal: discard the MFA token and return to Login. */
    data class InvalidMFAChallenge(val serverMessage: String?) : AuthError(serverMessage) {
        override val userMessage
            get() = serverMessage ?: "Your verification session is no longer valid. Please sign in again."
    }

    /** Not terminal: the user can try another code. */
    data class InvalidMFACode(val serverMessage: String?) : AuthError(serverMessage) {
        override val userMessage get() = serverMessage ?: "Code is incorrect."
    }

    /** Terminal. */
    data class MFAChallengeLocked(val serverMessage: String?) : AuthError(serverMessage) {
        override val userMessage get() = serverMessage ?: "Too many attempts. Please sign in again."
    }

    /** Terminal. */
    data class MFAChallengeExpired(val serverMessage: String?) : AuthError(serverMessage) {
        override val userMessage
            get() = serverMessage ?: "Your verification session has expired. Please sign in again."
    }

    data class InvalidVerificationToken(val serverMessage: String?) : AuthError(serverMessage) {
        override val userMessage get() = serverMessage ?: "Verification link is invalid or has expired."
    }

    data object OTPInvalid : AuthError("otp_invalid") {
        override val userMessage get() = "Code is incorrect."
    }

    data object OTPExpired : AuthError("otp_expired") {
        override val userMessage get() = "Code has expired. Request a new one."
    }

    data object OTPLocked : AuthError("otp_locked") {
        override val userMessage get() = "Too many attempts. Try again in a few minutes."
    }

    data object RecoveryTokenMissing : AuthError("recovery_token_missing") {
        override val userMessage get() = "Missing recovery token — please restart."
    }

    data object RecoveryTokenInvalid : AuthError("recovery_token_invalid") {
        override val userMessage get() = "Recovery token is no longer valid. Please restart."
    }
}
