package com.flykpicks.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flykpicks.android.ui.theme.DS
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.FlykType

// iOS DecoButton / DecoCTAButton (blueprint §11.4–11.5).

private val DecoButtonShape = RoundedCornerShape(2.dp)

/** Small square-cornered secondary button, up to 128dp wide (iOS `DecoButton`). */
@Composable
fun DecoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .widthIn(max = 128.dp)
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .dropShadow(DecoButtonShape, Shadow(radius = 3.dp, color = FlykColors.Ink.copy(alpha = 0.12f), offset = DpOffset(0.dp, 1.dp)))
            .clip(DecoButtonShape)
            .background(FlykColors.Canvas)
            .border(1.dp, FlykColors.Ink.copy(alpha = 0.22f), DecoButtonShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides FlykColors.Ink,
            LocalTextStyle provides FlykType.Caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
        ) {
            content()
        }
    }
}

enum class DecoCTAVariant(val tint: Color) {
    Primary(FlykColors.Brass),

    /** iOS uses ember (orange) for destructive CTAs such as Log Out, not red. */
    Danger(FlykColors.Ember),
}

private val CTAShape = RoundedCornerShape(14.dp)

/**
 * Full-width call-to-action: tinted fill and outline, uppercase label (iOS `DecoCTAButton`).
 * While [isLoading] a spinner replaces the icon and taps are ignored; the colors stay enabled.
 */
@Composable
fun DecoCTAButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isLoading: Boolean = false,
    isEnabled: Boolean = true,
    variant: DecoCTAVariant = DecoCTAVariant.Primary,
) {
    val tint = variant.tint
    val content = if (isEnabled) tint else FlykColors.InkMuted
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CTAShape)
            .background(if (isEnabled) tint.copy(alpha = 0.15f) else FlykColors.Ink.copy(alpha = 0.08f))
            .border(1.dp, if (isEnabled) tint.copy(alpha = 0.55f) else FlykColors.Ink.copy(alpha = 0.15f), CTAShape)
            .clickable(enabled = isEnabled && !isLoading, role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(16.dp), color = tint, strokeWidth = 2.dp)
        } else if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        }
        Text(
            text = title.uppercase(),
            style = FlykType.Subheadline.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp),
            color = content,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F0)
@Composable
private fun DecoButtonsPreview() {
    FlykPicksTheme {
        Column(Modifier.padding(DS.SpacingXL), verticalArrangement = Arrangement.spacedBy(DS.SpacingL)) {
            DecoCTAButton("Sign In", onClick = {})
            DecoCTAButton("Verifying…", onClick = {}, isLoading = true)
            DecoCTAButton("Sign In", onClick = {}, isEnabled = false)
            DecoCTAButton("Log Out", onClick = {}, leadingIcon = Icons.AutoMirrored.Outlined.Logout, variant = DecoCTAVariant.Danger)
            Row(horizontalArrangement = Arrangement.spacedBy(DS.SpacingM)) {
                DecoButton(onClick = {}) { Text("ADD") }
                DecoButton(onClick = {}, enabled = false) { Text("EDIT") }
            }
        }
    }
}
