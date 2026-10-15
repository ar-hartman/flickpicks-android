package com.flykpicks.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * FlykPicks palette. Values are the iOS `DS` colors converted to 8-bit sRGB; the blueprint's
 * §3.1 hex values are slightly off for Surface, Brass, Jade and NavBar (ios-gaps.md).
 *
 * Namespaced in an object because top-level `Canvas` / `Surface` vals would shadow the
 * Compose `Canvas()` and `Surface()` composables at every call site that imports both.
 */
object FlykColors {
    /** Warm cream — app background. */
    val Canvas = Color(0xFFFAF7F0)

    /** Card / field cream. */
    val Surface = Color(0xFFFCFAF5)

    /** Near-black, primary text. */
    val Ink = Color(0xFF1F1C19)
    val InkMuted = Ink.copy(alpha = 0.65f)

    /** Gold accent — primary brand. */
    val Brass = Color(0xFFC7A354)
    val Sapphire = Color(0xFF1975D1)
    val Jade = Color(0xFF26A699)

    /** Orange — iOS uses it for errors and destructive actions. */
    val Ember = Color(0xFFFF9900)

    /** Deep blue-black bottom navigation background. */
    val NavBar = Color(0xFF0D121F)
    val NavBarText = Color(0xFFFAF7F0)

    /** Destructive actions outside the Deco components (iOS system red). */
    val DangerRed = Color(0xFFCC3333)

    // The library detail screens and the edit sheet on iOS use UIKit's grouped-list colors
    // rather than the Deco palette. These are their light-mode values (plan D27).

    /** Page background (iOS `systemGroupedBackground`), also the fill of pills on a card. */
    val GroupedBackground = Color(0xFFF2F2F7)

    /** Card on a grouped page (iOS `secondarySystemGroupedBackground`). */
    val GroupedCard = Color(0xFFFFFFFF)

    /** Hairline between rows of a grouped card (iOS `separator`). */
    val GroupedSeparator = Color(0x4A3C3C43)
}
