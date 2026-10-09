package com.flykpicks.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.textileBackground

/**
 * Layout for the sign-in screens (iOS `DecoAuthScreen`, blueprint §11.9): textile canvas, the
 * wordmark, then [content] in a column spaced 20dp apart, all scrolling under the [TopBand].
 *
 * Content is top-aligned as on iOS (the blueprint's vertical centering isn't what iOS does). On
 * Android it also stops above the keyboard and caps at 400dp wide for tablets.
 */
@Composable
fun DecoAuthScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().textileBackground()) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = TopBandDefaults.ContentTopPadding, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            WordmarkHeader(Modifier.padding(bottom = 8.dp))
            Column(
                Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                content = content,
            )
        }
        TopBand()
    }
}

@Preview(device = "spec:width=411dp,height=891dp")
@Composable
private fun DecoAuthScreenPreview() {
    FlykPicksTheme {
        DecoAuthScreen {
            DecoSectionHeader("Welcome back", subtitle = "Sign in to your collection.")
            DecoTextField("", {}, "Email", Modifier.fillMaxWidth(), leadingIcon = Icons.Outlined.Email)
            DecoSecureField("", {}, "Password", Modifier.fillMaxWidth())
            ErrorBanner("Email or password is incorrect.")
            DecoCTAButton("Sign In", onClick = {})
        }
    }
}
