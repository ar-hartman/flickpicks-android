package com.flykpicks.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flykpicks.android.ui.theme.DS
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.FlykType

private val CardShape = RoundedCornerShape(12.dp)

/** Cream card with a brass hairline and soft shadow (iOS `.decoCard()`, blueprint §11.7). */
fun Modifier.decoCard(): Modifier = this
    .dropShadow(CardShape, Shadow(radius = 6.dp, color = FlykColors.Ink.copy(alpha = 0.10f), offset = DpOffset(0.dp, 2.dp)))
    .clip(CardShape)
    .background(FlykColors.Surface)
    .border(1.dp, FlykColors.Brass.copy(alpha = 0.35f), CardShape)

/**
 * Brass, letter-spaced uppercase title with an optional subtitle (iOS `DecoSectionHeader`).
 * With [showRules], brass hairlines frame it and everything centers.
 */
@Composable
fun DecoSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showRules: Boolean = false,
) {
    val align = if (showRules) TextAlign.Center else TextAlign.Start
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showRules) BrassRule()
        Text(
            text = title.uppercase(),
            modifier = Modifier.fillMaxWidth(),
            style = FlykType.Caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
            color = FlykColors.Brass,
            textAlign = align,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                modifier = Modifier.fillMaxWidth(),
                style = FlykType.Subheadline,
                color = FlykColors.InkMuted,
                textAlign = align,
            )
        }
        if (showRules) BrassRule()
    }
}

/** 1dp brass hairline at 35%, the divider used throughout the iOS app. */
@Composable
fun BrassRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(DS.RuleHeight).background(FlykColors.Brass.copy(alpha = 0.35f)))
}

private val BannerShape = RoundedCornerShape(14.dp)

/** Inline error message in ember (iOS `ErrorBanner`, defined in `LoginView`). */
@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(BannerShape)
            .background(FlykColors.Ember.copy(alpha = 0.12f))
            .border(1.dp, FlykColors.Ember.copy(alpha = 0.35f), BannerShape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = FlykColors.Ember, modifier = Modifier.size(18.dp))
        Text(message, style = FlykType.Footnote, color = FlykColors.Ember)
    }
}

/**
 * A settings row: brass icon, label and chevron, with an inset hairline below (iOS
 * `ProfileView.navRow`). With [onClick] null it renders the inert row iOS uses for
 * "Appearance": muted label, no chevron, no divider.
 */
@Composable
fun NavRow(
    icon: ImageVector,
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val interactive = onClick != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .then(if (interactive) Modifier.drawBehind { drawRowDivider() } else Modifier)
            .padding(vertical = 12.dp, horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = FlykColors.Brass, modifier = Modifier.size(20.dp))
        }
        Text(
            text = title,
            style = if (interactive) FlykType.Subheadline.copy(fontWeight = FontWeight.Medium) else FlykType.Subheadline,
            color = if (interactive) FlykColors.Ink else FlykColors.InkMuted,
        )
        Spacer(Modifier.weight(1f))
        if (interactive) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = FlykColors.Ink.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun DrawScope.drawRowDivider() {
    val inset = 14.dp.toPx()
    val height = 1.dp.toPx()
    drawRect(
        color = FlykColors.Ink.copy(alpha = 0.08f),
        topLeft = Offset(inset, size.height - height),
        size = Size(size.width - inset * 2, height),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F0)
@Composable
private fun DecoSurfacesPreview() {
    FlykPicksTheme {
        Column(Modifier.padding(DS.SpacingXL), verticalArrangement = Arrangement.spacedBy(DS.SpacingL)) {
            DecoSectionHeader("Two-Factor Verification", subtitle = "Enter the code from your authenticator app.")
            ErrorBanner("Too many attempts. Try again in 4:59.")
            Column {
                DecoSectionHeader("Account", Modifier.padding(bottom = 4.dp), showRules = true)
                Column(Modifier.decoCard()) {
                    NavRow(Icons.Outlined.Security, "Privacy & Security", onClick = {})
                    NavRow(Icons.Outlined.Notifications, "Notifications", onClick = {})
                    NavRow(Icons.Outlined.Palette, "Appearance", onClick = null)
                }
            }
        }
    }
}
