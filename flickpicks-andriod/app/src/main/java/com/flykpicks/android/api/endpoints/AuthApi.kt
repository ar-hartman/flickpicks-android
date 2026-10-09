package com.flykpicks.android.api.endpoints

import com.flykpicks.android.api.Unauthenticated
import com.flykpicks.android.model.auth.ChangeLostPasswordBody
import com.flykpicks.android.model.auth.EmailVerifyRequest
import com.flykpicks.android.model.auth.EmailVerifyResendRequest
import com.flykpicks.android.model.auth.JWTAuthRefreshRequest
import com.flykpicks.android.model.auth.JWTAuthRefreshResponse
import com.flykpicks.android.model.auth.JWTLoginRequest
import com.flykpicks.android.model.auth.JWTLoginResponse
import com.flykpicks.android.model.auth.JWTLogoutRequest
import com.flykpicks.android.model.auth.JWTMFAVerifyRequest
import com.flykpicks.android.model.auth.MFAResetRequestBody
import com.flykpicks.android.model.auth.MessageResponseDTO
import com.flykpicks.android.model.auth.OTPRequestBody
import com.flykpicks.android.model.auth.OTPRequestResponse
import com.flykpicks.android.model.auth.OTPVerifyBody
import com.flykpicks.android.model.auth.OTPVerifyResponse
import com.flykpicks.android.model.auth.UserRegistrationRequest
import com.flykpicks.android.model.auth.UserRegistrationResponseDTO
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/** Public auth endpoints: no bearer token, and a 401 never triggers a refresh. */
interface AuthApi {
    @Unauthenticated
    @POST("v2/auth/login")
    suspend fun login(@Body body: JWTLoginRequest): JWTLoginResponse

    @Unauthenticated
    @POST("v2/auth/mfa")
    suspend fun verifyMFA(@Body body: JWTMFAVerifyRequest): JWTLoginResponse

    @Unauthenticated
    @POST("v2/auth/logout")
    suspend fun logout(@Body body: JWTLogoutRequest)

    /** Always 202 (no account enumeration); only 429 is a real error. Body ignored. */
    @Unauthenticated
    @POST("v2/auth/mfa/reset-request")
    suspend fun requestMFAReset(@Body body: MFAResetRequestBody)

    @Unauthenticated
    @POST("v1/users/register")
    suspend fun register(@Body body: UserRegistrationRequest): UserRegistrationResponseDTO

    @Unauthenticated
    @POST("v1/users/verify-email")
    suspend fun verifyEmail(@Body body: EmailVerifyRequest): MessageResponseDTO

    @Unauthenticated
    @POST("v1/users/verify-email/resend")
    suspend fun resendVerification(@Body body: EmailVerifyResendRequest): MessageResponseDTO

    @Unauthenticated
    @POST("v1/users/getOneTimePassword")
    suspend fun requestOTP(@Body body: OTPRequestBody): OTPRequestResponse

    @Unauthenticated
    @POST("v1/users/verifyOtp")
    suspend fun verifyOTP(@Body body: OTPVerifyBody): OTPVerifyResponse

    /** Body ignored (ios-gaps.md G14). */
    @Unauthenticated
    @POST("v1/users/changeLostPassword")
    suspend fun changeLostPassword(@Body body: ChangeLostPasswordBody)
}

/**
 * Token refresh, called synchronously from `TokenAuthenticator` on a client without auth
 * interceptors, so refresh can never recurse into itself.
 */
interface RefreshApi {
    @POST("v2/auth/refresh")
    fun refresh(@Body body: JWTAuthRefreshRequest): Call<JWTAuthRefreshResponse>
}
