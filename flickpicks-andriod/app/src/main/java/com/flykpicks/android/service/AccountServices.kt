package com.flykpicks.android.service

import com.flykpicks.android.api.endpoints.FeatureFlagApi
import com.flykpicks.android.api.endpoints.ImpressionsPayload
import com.flykpicks.android.api.endpoints.ImpressionsRow
import com.flykpicks.android.api.endpoints.InsightsApi
import com.flykpicks.android.api.endpoints.NotificationsApi
import com.flykpicks.android.api.endpoints.TelemetryApi
import com.flykpicks.android.model.profile.CollectionInsightsDTO
import com.flykpicks.android.model.profile.NotificationPreferencesDTO
import com.flykpicks.android.model.profile.ReferralDTO
import com.flykpicks.android.model.profile.SellerDashboardDTO
import com.flykpicks.android.model.profile.UpdateNotificationPreferenceRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationSettingsService @Inject constructor(private val api: NotificationsApi) {

    suspend fun fetchPreferences(): NotificationPreferencesDTO = api.fetchPreferences()

    suspend fun updatePreference(category: String, push: Boolean, email: Boolean): NotificationPreferencesDTO =
        api.updatePreference(UpdateNotificationPreferenceRequest(category, push, email))
}

@Singleton
class FeatureFlagService @Inject constructor(private val api: FeatureFlagApi) {
    suspend fun fetchFlags(): Map<String, Boolean> = api.fetchFlags().flags
}

/** Seller dashboard, collection insights and referral info. */
@Singleton
class AccountInsightsService @Inject constructor(private val api: InsightsApi) {
    suspend fun fetchSellerDashboard(): SellerDashboardDTO = api.sellerDashboard()
    suspend fun fetchCollectionInsights(): CollectionInsightsDTO = api.collectionInsights()
    suspend fun fetchReferralInfo(): ReferralDTO = api.referral()
}

@Singleton
class TelemetryService @Inject constructor(private val api: TelemetryApi) {
    /** Throws `ApiError.Http(404)` when the server flag is off; the batcher must handle it. */
    suspend fun postImpressions(rows: List<ImpressionsRow>) = api.postImpressions(ImpressionsPayload(rows))
}
