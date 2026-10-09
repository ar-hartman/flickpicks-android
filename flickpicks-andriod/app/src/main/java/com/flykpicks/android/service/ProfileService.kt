package com.flykpicks.android.service

import com.flykpicks.android.api.endpoints.ProfileApi
import com.flykpicks.android.model.profile.PublicNameSource
import com.flykpicks.android.model.profile.UpdateProfileRequest
import com.flykpicks.android.model.profile.UserProfileDTO
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileService @Inject constructor(private val api: ProfileApi) {

    suspend fun fetchProfile(): UserProfileDTO = api.fetchProfile()

    /** Only non-null fields are sent. */
    suspend fun updateProfile(
        firstName: String? = null,
        lastName: String? = null,
        displayName: String? = null,
        publicNameSource: PublicNameSource? = null,
    ): UserProfileDTO = api.updateProfile(UpdateProfileRequest(firstName, lastName, displayName, publicNameSource))

    /** Returns the new avatar URL. */
    suspend fun uploadAvatar(imageData: ByteArray, mimeType: String = "image/jpeg"): String {
        val extension = if (mimeType == "image/png") "png" else "jpg"
        val part = MultipartBody.Part.createFormData(
            name = "avatar",
            filename = "avatar.$extension",
            body = imageData.toRequestBody(mimeType.toMediaType()),
        )
        return api.uploadAvatar(part).avatarUrl
    }

    suspend fun removeAvatar() = api.removeAvatar()
}
