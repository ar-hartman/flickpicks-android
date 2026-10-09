package com.flykpicks.android.model.profile

import com.flykpicks.android.api.UserFacingError
import com.flykpicks.android.model.enums.MediaFormat
import com.flykpicks.android.model.enums.OrderStatus
import com.flykpicks.android.model.enums.OrderType
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OrderDTO(
    val id: String,
    val type: String,
    val status: String,
    val itemTitle: String,
    val itemThumbnailUrl: String?,
    val mediaFormat: String,
    val price: Double,
    val orderDate: String,
    val trackingNumber: String?,
) {
    val orderType: OrderType get() = OrderType.fromRaw(type) ?: OrderType.DEFAULT
    val orderStatus: OrderStatus get() = OrderStatus.fromRaw(status) ?: OrderStatus.DEFAULT
    val formatEnum: MediaFormat get() = MediaFormat.fromRaw(mediaFormat) ?: MediaFormat.DVD
}

@JsonClass(generateAdapter = true)
data class OrderDetailDTO(
    val id: String,
    val type: String,
    val status: String,
    val itemTitle: String,
    val itemThumbnailUrl: String?,
    val mediaFormat: String,
    val condition: String,
    val price: Double,
    val shippingCost: Double,
    val tax: Double,
    val fees: Double,
    val orderDate: String,
    val trackingNumber: String?,
    val sellerName: String?,
    val buyerName: String?,
    val timeline: List<OrderTimelineEntry>,
    val rentalInfo: RentalInfoDTO?,
) {
    val total: Double get() = price + shippingCost + tax + fees
    val orderStatus: OrderStatus get() = OrderStatus.fromRaw(status) ?: OrderStatus.DEFAULT
    val isRental: Boolean get() = type == OrderType.RENTAL.raw
}

@JsonClass(generateAdapter = true)
data class RentalInfoDTO(
    val rentalStartDate: String?,
    val rentalDueDate: String?,
    val lateFeePerDay: Double?,
)

@JsonClass(generateAdapter = true)
data class OrderTimelineEntry(
    val id: String,
    val label: String,
    val date: String,
    @Json(name = "completed") val isCompleted: Boolean,
)

@JsonClass(generateAdapter = true)
data class ExtendRentalRequest(val additionalDays: Int)

enum class OrderCreationType(val raw: String) {
    PURCHASE("purchase"),
    RENTAL("rental"),
}

@JsonClass(generateAdapter = true)
data class CreateOrderRequest(val listingId: String, val type: String)

sealed class OrderError(detail: String) : Exception(detail), UserFacingError {
    data object InvalidRequest : OrderError("invalid_request") {
        override val userMessage get() = "Invalid order request."
    }

    data object NotFound : OrderError("not_found") {
        override val userMessage get() = "This listing is no longer available."
    }

    data object ListingUnavailable : OrderError("listing_unavailable") {
        override val userMessage get() = "This listing has been sold or removed."
    }

    data object TypeNotSupported : OrderError("type_not_supported") {
        override val userMessage get() = "This listing doesn't support that order type."
    }

    data object BuyingOwnListing : OrderError("buying_own_listing") {
        override val userMessage get() = "You can't buy or rent your own listing."
    }

    data object PlanDisallowsRentals : OrderError("plan_disallows_rentals") {
        override val userMessage get() = "Your current plan doesn't include rentals."
    }

    data object RentalCapReached : OrderError("rental_cap_reached") {
        override val userMessage get() = "You've reached your plan's active rental limit."
    }

    data object AvailabilityRace : OrderError("availability_race") {
        override val userMessage get() = "Someone else claimed this listing first. Try another."
    }

    companion object {
        /**
         * The backend has no sub-codes for 409, so this matches message substrings exactly as
         * iOS does (ios-gaps.md G12).
         */
        fun map409(message: String?): OrderError {
            val lower = message?.lowercase().orEmpty()
            return when {
                "own" in lower -> BuyingOwnListing
                "cap" in lower || "limit" in lower -> RentalCapReached
                "plan" in lower -> PlanDisallowsRentals
                "support" in lower || "type" in lower -> TypeNotSupported
                "race" in lower || "concurrent" in lower -> AvailabilityRace
                else -> ListingUnavailable
            }
        }
    }
}
