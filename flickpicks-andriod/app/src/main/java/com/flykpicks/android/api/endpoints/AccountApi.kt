package com.flykpicks.android.api.endpoints

import com.flykpicks.android.model.profile.CollectionInsightsDTO
import com.flykpicks.android.model.profile.FeatureFlagsDTO
import com.flykpicks.android.model.profile.NotificationPreferencesDTO
import com.flykpicks.android.model.profile.ReferralDTO
import com.flykpicks.android.model.profile.SellerDashboardDTO
import com.flykpicks.android.model.profile.UpdateNotificationPreferenceRequest
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST

interface NotificationsApi {
    @GET("v1/users/me/notification-preferences")
    suspend fun fetchPreferences(): NotificationPreferencesDTO

    @PATCH("v1/users/me/notification-preferences")
    suspend fun updatePreference(@Body body: UpdateNotificationPreferenceRequest): NotificationPreferencesDTO
}

interface FeatureFlagApi {
    /** Requires auth; before login the cached flags apply (plan Q4). */
    @GET("v1/feature-flags")
    suspend fun fetchFlags(): FeatureFlagsDTO
}

/** Seller dashboard, collection insights and referral (iOS `ServiceLocator`). */
interface InsightsApi {
    @GET("v1/users/me/seller/dashboard")
    suspend fun sellerDashboard(): SellerDashboardDTO

    @GET("v1/users/me/collection/insights")
    suspend fun collectionInsights(): CollectionInsightsDTO

    @GET("v1/users/me/referral")
    suspend fun referral(): ReferralDTO
}

interface TelemetryApi {
    /** 404 means the server-side flag is off; callers stop batching for the session. */
    @POST("v1/telemetry/impressions")
    suspend fun postImpressions(@Body body: ImpressionsPayload)
}

@JsonClass(generateAdapter = true)
data class ImpressionsPayload(val rows: List<ImpressionsRow>)

@JsonClass(generateAdapter = true)
data class ImpressionsRow(
    /** Stable across retries; the server dedupes on it. */
    val eventId: String,
    val source: String,
    /** ISO-8601, second precision. */
    val clientTs: String,
    /** At most 20 ids. */
    val itemIds: List<String>,
    val scrollDepth: Int,
    val dwellMs: Int? = null,
)
