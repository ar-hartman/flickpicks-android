package com.flykpicks.android.model.enums

enum class OrderType(val raw: String, val displayName: String) {
    RENTAL("RENTAL", "Rental"),
    SALE("SALE", "Purchase"),
    BOTH("BOTH", "Rental & Purchase"),
    NONE("NONE", "None"),
    DEFAULT("DEFAULT", "—"),
    ;

    companion object {
        fun fromRaw(raw: String?): OrderType? = entries.firstOrNull { it.raw == raw }
    }
}
