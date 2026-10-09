package com.flykpicks.android.model.profile

import com.flykpicks.android.model.enums.MediaFormat
import com.squareup.moshi.JsonClass

// MARK: Notification preferences

@JsonClass(generateAdapter = true)
data class NotificationPreferencesDTO(
    val pushEnabled: Boolean,
    val categories: List<NotificationCategoryDTO>,
)

@JsonClass(generateAdapter = true)
data class NotificationCategoryDTO(
    val category: String,
    val push: Boolean,
    val email: Boolean,
) {
    val displayName: String
        get() = when (category) {
            NotificationCategory.ORDER_UPDATES -> "Order Updates"
            NotificationCategory.PRICE_DROPS -> "Price Drops"
            NotificationCategory.NEW_LISTINGS -> "New Listings"
            NotificationCategory.LIBRARY_REMINDERS -> "Library Reminders"
            NotificationCategory.PROMOTIONS -> "Promotions"
            else -> category.split('_').joinToString(" ") { word ->
                word.lowercase().replaceFirstChar(Char::uppercaseChar)
            }
        }
}

@JsonClass(generateAdapter = true)
data class UpdateNotificationPreferenceRequest(
    val category: String,
    val push: Boolean,
    val email: Boolean,
)

/** Categories iOS recognizes. The backend doesn't enumerate them yet (iOS DC-016). */
object NotificationCategory {
    const val ORDER_UPDATES = "ORDER_UPDATES"
    const val PRICE_DROPS = "PRICE_DROPS"
    const val NEW_LISTINGS = "NEW_LISTINGS"
    const val LIBRARY_REMINDERS = "LIBRARY_REMINDERS"
    const val PROMOTIONS = "PROMOTIONS"
}

// MARK: Seller

@JsonClass(generateAdapter = true)
data class SellerDashboardDTO(
    val totalSales: Int,
    val activeListings: Int,
    val averageRating: Double,
    val revenue: Double,
    val revenuePeriod: String,
    val recentReviews: List<SellerReviewDTO>,
)

@JsonClass(generateAdapter = true)
data class SellerReviewDTO(
    val id: String,
    val buyerName: String,
    val rating: Int,
    val comment: String,
    val date: String,
)

/** Declared on iOS but no endpoint returns it yet. */
@JsonClass(generateAdapter = true)
data class ListingDTO(
    val id: String,
    val itemTitle: String,
    val itemThumbnailUrl: String?,
    val mediaFormat: String,
    val condition: String,
    val price: Double,
    val status: String,
    val listedDate: String,
)

// MARK: Collection insights

@JsonClass(generateAdapter = true)
data class CollectionInsightsDTO(
    val totalValue: Double,
    val totalItems: Int,
    val formatBreakdown: List<FormatBreakdownEntry>,
    val genreBreakdown: List<GenreBreakdownEntry>,
    val rarestItems: List<RareItemDTO>,
)

@JsonClass(generateAdapter = true)
data class FormatBreakdownEntry(val format: String, val count: Int) {
    val formatEnum: MediaFormat get() = MediaFormat.fromRaw(format) ?: MediaFormat.DVD
}

@JsonClass(generateAdapter = true)
data class GenreBreakdownEntry(val genre: String, val count: Int)

@JsonClass(generateAdapter = true)
data class RareItemDTO(
    val id: String,
    val title: String,
    val estimatedValue: Double,
    val posterUrl: String?,
)

// MARK: Referral

@JsonClass(generateAdapter = true)
data class ReferralDTO(
    val referralCode: String,
    val referralLink: String,
    val invitesSent: Int,
    val friendsJoined: Int,
    val creditsEarned: Double,
)

// MARK: Feature flags

@JsonClass(generateAdapter = true)
data class FeatureFlagsDTO(val flags: Map<String, Boolean>)

/** A key missing from the server map is treated as disabled. */
object FeatureFlagKey {
    const val ACCOUNT_RECOVERY_ENABLED = "accountRecoveryEnabled"
    const val PROFILE_HEADER_ENABLED = "profile_header_enabled"
    const val PROFILE_APPEARANCE_ENABLED = "profile_appearance_enabled"
    const val PROFILE_PAYMENT_WALLET = "profile_payment_wallet_enabled"
    const val PROFILE_SHIPPING_ADDRESS = "profile_shipping_address_enabled"
    const val PROFILE_MEMBERSHIP = "profile_membership_enabled"
    const val PROFILE_ORDER_HISTORY = "profile_order_history_enabled"
    const val PROFILE_DISPUTES = "profile_disputes_enabled"
    const val PROFILE_SELLER_DASHBOARD = "profile_seller_dashboard_enabled"
    const val PROFILE_COLLECTION_INSIGHTS = "profile_collection_insights_enabled"
    const val PROFILE_HELP_SUPPORT = "profile_help_support_enabled"
    const val PROFILE_REFERRAL = "profile_referral_enabled"
}
