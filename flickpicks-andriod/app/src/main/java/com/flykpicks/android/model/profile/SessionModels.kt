package com.flykpicks.android.model.profile

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SessionDTO(
    val id: String,
    val deviceName: String?,
    val lastActive: String?,
    @Json(name = "current") val isCurrent: Boolean,
    val deviceId: String?,
)

@JsonClass(generateAdapter = true)
data class TrustedDeviceDTO(
    val deviceId: String,
    val deviceName: String?,
    val trustedAt: String?,
    val lastUsedAt: String?,
    val expiresAt: String?,
    @Json(name = "current") val isCurrent: Boolean,
)
