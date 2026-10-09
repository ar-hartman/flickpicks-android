package com.flykpicks.android.model.enums

enum class ItemCondition(val raw: String, val displayName: String) {
    NEW("NEW", "New"),
    LIKE_NEW("LIKE_NEW", "Like New"),
    VERY_GOOD("VERY_GOOD", "Very Good"),
    GOOD("GOOD", "Good"),
    ACCEPTABLE("ACCEPTABLE", "Acceptable"),
    ;

    companion object {
        fun fromRaw(raw: String?): ItemCondition? = entries.firstOrNull { it.raw == raw }
    }
}
