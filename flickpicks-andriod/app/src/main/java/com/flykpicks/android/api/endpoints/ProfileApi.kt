package com.flykpicks.android.api.endpoints

import com.flykpicks.android.model.profile.AvatarUploadResponse
import com.flykpicks.android.model.profile.UpdateProfileRequest
import com.flykpicks.android.model.profile.UserProfileDTO
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Part

interface ProfileApi {
    @GET("v1/users/me")
    suspend fun fetchProfile(): UserProfileDTO

    @PATCH("v1/users/me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): UserProfileDTO

    /** Multipart form field `avatar`. */
    @Multipart
    @PUT("v1/users/me/avatar")
    suspend fun uploadAvatar(@Part avatar: MultipartBody.Part): AvatarUploadResponse

    @DELETE("v1/users/me/avatar")
    suspend fun removeAvatar()
}
