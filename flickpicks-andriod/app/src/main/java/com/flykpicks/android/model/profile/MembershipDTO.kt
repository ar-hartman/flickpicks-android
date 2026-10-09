package com.flykpicks.android.model.profile

import com.flykpicks.android.api.UserFacingError
import com.flykpicks.android.model.enums.MembershipTier
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

@JsonClass(generateAdapter = true)
data class MembershipDTO(
    val tier: String,
    val price: Double,
    val renewalDate: String?,
    @Json(name = "canceled") val isCanceled: Boolean,
) {
    /** Unknown tiers read as FREE, matching iOS (ios-gaps.md G11). */
    val membershipTier: MembershipTier
        get() = MembershipTier.fromRaw(tier.uppercase(Locale.ROOT)) ?: MembershipTier.FREE
}

/** The server expects the tier lowercased (e.g. `"premium"`). */
@JsonClass(generateAdapter = true)
data class ChangePlanRequest(val newTier: String)

@JsonClass(generateAdapter = true)
data class BillingEntryDTO(
    val id: String,
    val date: String,
    val amount: Double,
    val description: String,
    val status: String,
)

/** Plan catalog. Client-side, as on iOS — there is no tiers endpoint. */
data class MembershipTierInfo(
    val tier: MembershipTier,
    val monthlyPrice: Double,
    val features: List<String>,
) {
    companion object {
        val allTiers: List<MembershipTierInfo> = listOf(
            MembershipTierInfo(
                MembershipTier.FREE,
                0.0,
                listOf("Library management", "Search & discovery", "Watchlist"),
            ),
            MembershipTierInfo(
                MembershipTier.STANDARD,
                4.99,
                listOf(
                    "1 active rental at a time",
                    "Collection analytics & insights",
                    "Priority search results",
                ),
            ),
            MembershipTierInfo(
                MembershipTier.PREMIUM,
                9.99,
                listOf(
                    "All Standard features",
                    "Up to 3 active rentals",
                    "List items for sale",
                    "Reduced marketplace fees",
                ),
            ),
            MembershipTierInfo(
                MembershipTier.VIP,
                14.99,
                listOf(
                    "All Premium features",
                    "Up to 5 active rentals",
                    "Custom collections & public lists",
                    "Early access to new features",
                ),
            ),
        )
    }
}

sealed class MembershipError(detail: String) : Exception(detail), UserFacingError {
    data object InvalidTier : MembershipError("invalid_tier") {
        override val userMessage get() = "That tier isn't available right now."
    }

    data object AlreadyCanceled : MembershipError("already_canceled") {
        override val userMessage get() = "Your membership is already canceled."
    }
}
