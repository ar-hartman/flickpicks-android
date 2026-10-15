package com.flykpicks.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykType

// Whole-screen states for the Library and Discover screens, shown where the content would be.

/** A failed load with nothing to show in its place: the message and a way to try again. */
@Composable
fun LoadErrorNotice(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(top = 80.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = FlykColors.Brass.copy(alpha = 0.5f),
            modifier = Modifier.size(36.dp),
        )
        Text(message, style = FlykType.Subheadline, color = FlykColors.Ink.copy(alpha = 0.6f), textAlign = TextAlign.Center)
        Text(
            "Try Again",
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            style = FlykType.Subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = FlykColors.Brass,
        )
    }
}

/** An icon over a headline and a line of explanation, for "nothing here" states. */
@Composable
fun EmptyNotice(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(top = 80.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = FlykColors.Brass.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = FlykType.Headline, color = FlykColors.Ink, textAlign = TextAlign.Center)
            Text(message, style = FlykType.Subheadline, color = FlykColors.Ink.copy(alpha = 0.5f), textAlign = TextAlign.Center)
        }
    }
}
