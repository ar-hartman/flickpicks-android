package com.flykpicks.android.api.endpoints

import com.flykpicks.android.model.profile.BackupCodesRegenerateResponse
import com.flykpicks.android.model.profile.ChangePasswordRequest
import com.flykpicks.android.model.profile.DeleteAccountRequest
import com.flykpicks.android.model.profile.MFADisableRequest
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
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SecurityApi {
    @POST("v1/users/changePassword")
    suspend fun changePassword(@Body body: ChangePasswordRequest)

    @POST("v1/users/delete")
    suspend fun deleteAccount(@Body body: DeleteAccountRequest)

    // MARK: MFA

    @GET("v1/users/me/mfa")
    suspend fun getMFAStatus(): MFAStatusDTO

    @POST("v1/users/me/mfa/sms/enroll")
    suspend fun enrollSMS(@Body body: MFASmsEnrollRequest): MFASmsEnrollResponse

    @POST("v1/users/me/mfa/sms/verify")
    suspend fun verifySMS(@Body body: MFASmsVerifyRequest): MFAVerifyResponse

    @POST("v1/users/me/mfa/sms/resend")
    suspend fun resendSMS(@Body body: MFASmsResendRequest): MFASmsResendResponse

    @POST("v1/users/me/mfa/totp/enroll")
    suspend fun enrollTOTP(): TOTPEnrollResponse

    @POST("v1/users/me/mfa/totp/verify")
    suspend fun verifyTOTP(@Body body: MFAVerifyRequest): MFAVerifyResponse

    @POST("v1/users/me/mfa/totp/regenerate")
    suspend fun regenerateTOTP(): TOTPRegenerateResponse

    /** [method] is an `MFAMethod.raw` value: `sms`, `totp` or `email`. */
    @POST("v1/users/me/mfa/{method}/disable")
    suspend fun disableMFA(@Path("method") method: String, @Body body: MFADisableRequest)

    @POST("v1/users/me/mfa/backup-codes/regenerate")
    suspend fun regenerateBackupCodes(): BackupCodesRegenerateResponse

    // MARK: Sessions

    @GET("v1/users/me/sessions")
    suspend fun listSessions(): List<SessionDTO>

    @DELETE("v1/users/me/sessions/{id}")
    suspend fun revokeSession(@Path("id") id: String)

    @DELETE("v1/users/me/sessions")
    suspend fun revokeAllOtherSessions(@Query("exceptCurrent") exceptCurrent: Boolean = true)

    // MARK: Trusted devices

    @GET("v1/users/me/trusted-devices")
    suspend fun trustedDevices(): List<TrustedDeviceDTO>

    @DELETE("v1/users/me/trusted-devices/{deviceId}")
    suspend fun stopTrusting(@Path("deviceId") deviceId: String)

    @DELETE("v1/users/me/trusted-devices")
    suspend fun stopTrustingAll()
}
