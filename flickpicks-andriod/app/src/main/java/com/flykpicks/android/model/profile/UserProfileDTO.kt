package com.flykpicks.android.model.profile

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Unknown values decode as [DISPLAY_NAME] (see `ApiClient.moshi`). */
enum class PublicNameSource(val label: String) {
    @Json(name = "DISPLAY_NAME") DISPLAY_NAME("Display Name"),
    @Json(name = "FIRST_NAME") FIRST_NAME("First Name"),
}

@JsonClass(generateAdapter = true)
data class UserProfileDTO(
    val userId: String,
    val firstName: String,
    val lastName: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String?,
    val roles: List<String>,
    val createdAt: String,
    val publicNameSource: PublicNameSource,
) {
    val publicName: String
        get() = when (publicNameSource) {
            PublicNameSource.DISPLAY_NAME -> displayName
            PublicNameSource.FIRST_NAME -> firstName
        }
}

/** PATCH body; null fields are omitted so only changed fields are sent. */
@JsonClass(generateAdapter = true)
data class UpdateProfileRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val displayName: String? = null,
    val publicNameSource: PublicNameSource? = null,
)

@JsonClass(generateAdapter = true)
data class AvatarUploadResponse(val avatarUrl: String)
