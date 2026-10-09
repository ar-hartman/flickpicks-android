package com.flykpicks.android.model.profile

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

@JsonClass(generateAdapter = true)
data class PaymentMethodDTO(
    val id: String,
    val brand: String,
    val last4: String,
    val expiryMonth: Int,
    val expiryYear: Int,
    @Json(name = "default") val isDefault: Boolean,
) {
    /** `MM/YY`. */
    val displayExpiry: String
        get() = String.format(Locale.ROOT, "%02d/%02d", expiryMonth, expiryYear % 100)
}

@JsonClass(generateAdapter = true)
data class SetupIntentResponse(val clientSecret: String)

@JsonClass(generateAdapter = true)
data class PayoutStatusDTO(
    val isOnboarded: Boolean,
    val onboardingUrl: String?,
    val balance: Double?,
)

@JsonClass(generateAdapter = true)
data class PayoutOnboardResponse(val onboardingUrl: String)

@JsonClass(generateAdapter = true)
data class AddressDTO(
    /** Null for an address not yet created; omitted from the request body then. */
    val id: String? = null,
    val fullName: String,
    val line1: String,
    val line2: String? = null,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String,
    @Json(name = "default") val isDefault: Boolean,
) {
    val formattedOneLine: String
        get() = listOf(line1, line2, city, state, postalCode, country)
            .filter { !it.isNullOrEmpty() }
            .joinToString(", ")
}
