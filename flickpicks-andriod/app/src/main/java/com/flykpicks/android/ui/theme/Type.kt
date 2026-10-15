package com.flykpicks.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// iOS renders display text with the system serif (`design: .serif`), not a custom face, so the
// platform serif is the closest match (plan D12).
val SerifFamily: FontFamily = FontFamily.Serif
val SansFamily: FontFamily = FontFamily.Default

/**
 * The iOS text styles at their default Dynamic Type size, 1pt = 1sp. Components port
 * `.font(.caption.weight(.semibold)).tracking(1.2)` as
 * `FlykType.Caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)`.
 */
object FlykType {
    val LargeTitle = TextStyle(fontFamily = SansFamily, fontSize = 34.sp, lineHeight = 41.sp)
    val Title3 = TextStyle(fontFamily = SansFamily, fontSize = 20.sp)
    val Headline = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    val Body = TextStyle(fontFamily = SansFamily, fontSize = 17.sp)
    val Subheadline = TextStyle(fontFamily = SansFamily, fontSize = 15.sp)
    val Footnote = TextStyle(fontFamily = SansFamily, fontSize = 13.sp)
    val Caption = TextStyle(fontFamily = SansFamily, fontSize = 12.sp)
    val Caption2 = TextStyle(fontFamily = SansFamily, fontSize = 11.sp)
}

/** Blueprint §3.2, plus `bodyLarge` (Material's default text style) set to iOS body. */
val FlykTypography = Typography(
    // Large display titles (section headers, wordmark)
    displayLarge = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 1.6.sp,
    ),
    // Screen titles
    headlineMedium = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = 1.4.sp,
    ),
    bodyLarge = FlykType.Body,
    // Body text
    bodyMedium = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    ),
    // Caption / labels
    labelSmall = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.0.sp,
    ),
)
