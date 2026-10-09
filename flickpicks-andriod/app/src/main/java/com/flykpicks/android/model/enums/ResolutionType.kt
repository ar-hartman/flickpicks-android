package com.flykpicks.android.model.enums

enum class ResolutionType(val raw: String, val displayName: String) {
    FULL_REFUND("FULL_REFUND", "Full Refund"),
    PARTIAL_REFUND("PARTIAL_REFUND", "Partial Refund"),
    REPLACEMENT("REPLACEMENT", "Replacement"),
    RETURN_AND_REFUND("RETURN_AND_REFUND", "Return & Refund"),
    ;

    companion object {
        fun fromRaw(raw: String?): ResolutionType? = entries.firstOrNull { it.raw == raw }
    }
}
