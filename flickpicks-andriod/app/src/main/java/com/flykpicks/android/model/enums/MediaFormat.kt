package com.flykpicks.android.model.enums

enum class MediaFormat(val raw: String, val displayName: String) {
    DVD("DVD", "DVD"),
    VHS("VHS", "VHS"),
    BLURAY("BLURAY", "Blu-Ray"),
    UHD_4K("FOUR_K_ULTRA_HD", "4K UHD"),
    DIGITAL("DIGITAL", "Digital"),
    ;

    companion object {
        fun fromRaw(raw: String?): MediaFormat? = entries.firstOrNull { it.raw == raw }
    }
}
