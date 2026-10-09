package com.flykpicks.android.service

import com.flykpicks.android.api.ApiError
import com.flykpicks.android.api.endpoints.SecurityApi
import com.flykpicks.android.model.profile.BackupCodesRegenerateResponse
import com.flykpicks.android.model.profile.ChangePasswordRequest
import com.flykpicks.android.model.profile.DeleteAccountRequest
import com.flykpicks.android.model.profile.MFADisableRequest
import com.flykpicks.android.model.profile.MFAError
import com.flykpicks.android.model.profile.MFAMethod
import com.flykpicks.android.model.profile.MFASmsEnrollRequest
import com.flykpicks.android.model.profile.MFASmsEnrollResponse
import com.flykpicks.android.model.profile.MFASmsResendRequest
import com.flykpicks.android.model.profile.MFASmsResendResponse
import com.flykpicks.android.model.profile.MFASmsVerifyRequest
import com.flykpicks.android.model.profile.MFAStatusDTO
import com.flykpicks.android.model.profile.MFAVerifyRequest
import com.flykpicks.android.model.profile.MFAVerifyResponse
import com.flykpicks.android.model.profile.SessionDTO
import com.flykpicks.android.model.profile.TOTPEnrollResponse
import com.flykpicks.android.model.profile.TOTPRegenerateResponse
import com.flykpicks.android.model.profile.TrustedDeviceDTO
import javax.inject.Inject
import javax.inject.Singleton

/** Password, MFA enrollment, sessions, trusted devices, account deletion (blueprint §8.4). */
@Singleton
class SecurityService @Inject constructor(private val api: SecurityApi) {

    suspend fun changePassword(currentPassword: String, newPassword: String) =
        api.changePassword(ChangePasswordRequest(currentPassword, newPassword))

    suspend fun deleteAccount(password: String) = api.deleteAccount(DeleteAccountRequest(password))

    // MARK: MFA

    suspend fun getMFAStatus(): MFAStatusDTO = api.getMFAStatus()

    suspend fun enrollSMS(phoneNumber: String): MFASmsEnrollResponse =
        api.enrollSMS(MFASmsEnrollRequest(phoneNumber))

    /** Returns backup codes; show them once. */
    suspend fun verifySMS(verificationId: String, code: String): MFAVerifyResponse =
        api.verifySMS(MFASmsVerifyRequest(verificationId, code))

    suspend fun resendSMS(verificationId: String): MFASmsResendResponse =
        try {
            mapHttpErrors({ if (it.status == 404) MFAError.SmsVerificationNotFound else null }) {
                api.resendSMS(MFASmsResendRequest(verificationId))
            }
        } catch (_: ApiError.RateLimited) {
            // iOS intends this but its branch is unreachable (ios-gaps.md G1).
            throw MFAError.SmsResendRateLimited
        }

    suspend fun enrollTOTP(): TOTPEnrollResponse = api.enrollTOTP()

    /** Returns backup codes; show them once. */
    suspend fun verifyTOTP(code: String): MFAVerifyResponse = api.verifyTOTP(MFAVerifyRequest(code))

    suspend fun regenerateTOTP(): TOTPRegenerateResponse =
        mapHttpErrors({
            when (it.status) {
                404 -> MFAError.TOTPNotEnrolled
                409 -> MFAError.TOTPRegenerateRateLimited
                else -> null
            }
        }) { api.regenerateTOTP() }

    suspend fun disable(method: MFAMethod, password: String) =
        mapHttpErrors({
            when (it.status) {
                // The backend uses 400 for both cases without a sub-code; iOS splits them on the
                // message text (ios-gaps.md G12).
                400 -> if (it.serverMessage?.contains("last", ignoreCase = true) == true) {
                    MFAError.DisableLastMethod
                } else {
                    MFAError.DisableWrongPassword
                }
                404 -> MFAError.DisableNotEnrolled
                else -> null
            }
        }) { api.disableMFA(method.raw, MFADisableRequest(password)) }

    /** View-once: the codes can't be fetched again. */
    suspend fun regenerateBackupCodes(): BackupCodesRegenerateResponse = api.regenerateBackupCodes()

    // MARK: Sessions

    suspend fun listSessions(): List<SessionDTO> = api.listSessions()

    suspend fun revokeSession(id: String) = api.revokeSession(id)

    suspend fun revokeAllOtherSessions() = api.revokeAllOtherSessions(exceptCurrent = true)

    // MARK: Trusted devices

    suspend fun trustedDevices(): List<TrustedDeviceDTO> = api.trustedDevices()

    /** Already-removed devices (404) count as success. */
    suspend fun stopTrusting(deviceId: String) {
        try {
            api.stopTrusting(deviceId)
        } catch (e: ApiError.Http) {
            if (e.status != 404) throw e
        }
    }

    suspend fun stopTrustingAll() = api.stopTrustingAll()
}
