package com.flykpicks.android.model.enums

enum class MembershipTier(val raw: String, val displayName: String, val rentalCap: Int) {
    FREE("FREE", "Free", 0),
    STANDARD("STANDARD", "Standard", 1),
    PREMIUM("PREMIUM", "Premium", 3),
    VIP("VIP", "VIP", 5),
    DEFAULT("DEFAULT", "—", 0),
    ;

    companion object {
        fun fromRaw(raw: String?): MembershipTier? = entries.firstOrNull { it.raw == raw }
    }
}
