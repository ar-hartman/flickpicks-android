package com.flykpicks.android.model.enums

enum class DisputeReason(val raw: String, val displayName: String) {
    NOT_AS_DESCRIBED("NOT_AS_DESCRIBED", "Item Not As Described"),
    DAMAGED("DAMAGED", "Item Damaged"),
    NEVER_RECEIVED("NEVER_RECEIVED", "Item Never Received"),
    WRONG_ITEM("WRONG_ITEM", "Wrong Item Sent"),
    OTHER("OTHER", "Other"),
    ;

    companion object {
        fun fromRaw(raw: String?): DisputeReason? = entries.firstOrNull { it.raw == raw }
    }
}
