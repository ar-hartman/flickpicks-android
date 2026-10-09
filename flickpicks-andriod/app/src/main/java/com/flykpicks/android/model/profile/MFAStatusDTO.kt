package com.flykpicks.android.model.profile

import com.flykpicks.android.api.UserFacingError
import com.squareup.moshi.JsonClass

/** [raw] is the path segment used by `/v1/users/me/mfa/{method}/disable`. */
enum class MFAMethod(val raw: String, val displayName: String) {
    SMS("sms", "SMS"),
    TOTP("totp", "Authenticator"),
    EMAIL("email", "Email"),
    ;

    companion object {
        fun fromRaw(raw: String?): MFAMethod? = entries.firstOrNull { it.raw == raw?.lowercase() }
    }
}

@JsonClass(generateAdapter = true)
data class MFAStatusDTO(
    val methods: List<MFAMethodDTO>,
    val backupCodesRemaining: Int?,
) {
    val smsEnabled: Boolean get() = isEnabled(MFAMethod.SMS)
    val totpEnabled: Boolean get() = isEnabled(MFAMethod.TOTP)
    val emailEnabled: Boolean get() = isEnabled(MFAMethod.EMAIL)
    val primaryMethod: MFAMethod? get() = methods.firstOrNull { it.primary }?.let { MFAMethod.fromRaw(it.type) }

    private fun isEnabled(method: MFAMethod) =
        methods.any { it.enabled && MFAMethod.fromRaw(it.type) == method }
}

/** [type] is matched case-insensitively against [MFAMethod.raw]. */
@JsonClass(generateAdapter = true)
data class MFAMethodDTO(
    val type: String,
    val enabled: Boolean,
    val primary: Boolean,
    val enrolledAt: String?,
)

@JsonClass(generateAdapter = true)
data class MFASmsEnrollRequest(val phoneNumber: String)

@JsonClass(generateAdapter = true)
data class MFASmsEnrollResponse(val verificationId: String)

@JsonClass(generateAdapter = true)
data class MFAVerifyRequest(val code: String)

@JsonClass(generateAdapter = true)
data class MFASmsVerifyRequest(val verificationId: String, val code: String)

@JsonClass(generateAdapter = true)
data class MFAVerifyResponse(val backupCodes: List<String>)

@JsonClass(generateAdapter = true)
data class TOTPEnrollResponse(val secret: String, val qrCodeUri: String)

@JsonClass(generateAdapter = true)
data class ChangePasswordRequest(val oldPassword: String, val newPassword: String)

@JsonClass(generateAdapter = true)
data class DeleteAccountRequest(val password: String)

@JsonClass(generateAdapter = true)
data class MFASmsResendRequest(val verificationId: String)

@JsonClass(generateAdapter = true)
data class MFASmsResendResponse(val verificationId: String, val sentAt: String)

@JsonClass(generateAdapter = true)
data class MFADisableRequest(val password: String)

@JsonClass(generateAdapter = true)
data class TOTPRegenerateResponse(val secret: String, val qrCodeUri: String)

/** Codes are shown once and cannot be fetched again. */
@JsonClass(generateAdapter = true)
data class BackupCodesRegenerateResponse(val backupCodes: List<String>, val viewOnce: Boolean)

sealed class MFAError(detail: String) : Exception(detail), UserFacingError {
    data object SmsVerificationNotFound : MFAError("sms_verification_not_found") {
        override val userMessage get() = "This verification session has expired. Start over."
    }

    data object SmsResendRateLimited : MFAError("sms_resend_rate_limited") {
        override val userMessage get() = "Resend limit reached. Try again in a few minutes."
    }

    data object DisableWrongPassword : MFAError("disable_wrong_password") {
        override val userMessage get() = "Password is incorrect."
    }

    data object DisableLastMethod : MFAError("disable_last_method") {
        override val userMessage
            get() = "You must keep at least one MFA method enabled. Disable MFA by removing all " +
                "methods — first enable a second method."
    }

    data object DisableNotEnrolled : MFAError("disable_not_enrolled") {
        override val userMessage get() = "This method is not enrolled."
    }

    data object TOTPNotEnrolled : MFAError("totp_not_enrolled") {
        override val userMessage get() = "Authenticator is not enrolled."
    }

    data object TOTPRegenerateRateLimited : MFAError("totp_regenerate_rate_limited") {
        override val userMessage get() = "Already regenerated recently. Try again in a few minutes."
    }

    data object BackupCodesNoMFAEnabled : MFAError("backup_codes_no_mfa_enabled") {
        override val userMessage get() = "Enable an MFA method first."
    }
}
