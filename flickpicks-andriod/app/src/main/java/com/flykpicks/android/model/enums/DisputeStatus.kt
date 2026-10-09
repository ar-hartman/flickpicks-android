package com.flykpicks.android.model.enums

enum class DisputeStatus(val raw: String, val displayName: String) {
    OPEN("OPEN", "Open"),
    UNDER_REVIEW("UNDER_REVIEW", "Under Review"),
    RESOLVED("RESOLVED", "Resolved"),
    DENIED("DENIED", "Denied"),
    ESCALATED("ESCALATED", "Escalated"),
    ;

    companion object {
        fun fromRaw(raw: String?): DisputeStatus? = entries.firstOrNull { it.raw == raw }
    }
}
