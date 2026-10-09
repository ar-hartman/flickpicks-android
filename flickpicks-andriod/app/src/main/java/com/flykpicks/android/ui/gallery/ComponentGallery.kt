package com.flykpicks.android.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flykpicks.android.ui.components.DecoButton
import com.flykpicks.android.ui.components.DecoCTAButton
import com.flykpicks.android.ui.components.DecoCTAVariant
import com.flykpicks.android.ui.components.DecoSearchField
import com.flykpicks.android.ui.components.DecoSecureField
import com.flykpicks.android.ui.components.DecoSectionHeader
import com.flykpicks.android.ui.components.DecoTextField
import com.flykpicks.android.ui.components.ErrorBanner
import com.flykpicks.android.ui.components.HeaderStats
import com.flykpicks.android.ui.components.LibraryGenreRow
import com.flykpicks.android.ui.components.NavRow
import com.flykpicks.android.ui.components.PreviewLibraryItems
import com.flykpicks.android.ui.components.PreviewMediaItems
import com.flykpicks.android.ui.components.SearchGenreRow
import com.flykpicks.android.ui.components.ToastHost
import com.flykpicks.android.ui.components.ToastMessage
import com.flykpicks.android.ui.components.TopBand
import com.flykpicks.android.ui.components.TopBandDefaults
import com.flykpicks.android.ui.components.WordmarkHeader
import com.flykpicks.android.ui.components.decoCard
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.textileBackground

/**
 * Scratch screen showing every Phase 2 component together, for checking them against iOS on a
 * device. MainActivity shows it until Phase 3 replaces it with the real navigation.
 */
@Composable
fun ComponentGallery(toast: ToastMessage?, onShowToast: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().textileBackground()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = TopBandDefaults.ContentTopPadding, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            WordmarkHeader(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp))
            HeaderStats(totalTitles = 128, films = 97, tvShows = 31, modifier = Modifier.padding(horizontal = 20.dp))

            Section("Fields") {
                DecoTextField(email, { email = it }, "Email", Modifier.fillMaxWidth(), leadingIcon = Icons.Outlined.Email)
                DecoSecureField(password, { password = it }, "Password", Modifier.fillMaxWidth())
                DecoSearchField(query, { query = it }, "Search films & TV", onSearch = {}, modifier = Modifier.fillMaxWidth())
                ErrorBanner("Too many attempts. Try again in 4:59.")
            }

            Section("Buttons") {
                DecoCTAButton("Sign In", onClick = { loading = !loading }, isLoading = loading)
                DecoCTAButton("Sign In", onClick = {}, isEnabled = false)
                DecoCTAButton(
                    "Log Out",
                    onClick = { onShowToast("Session signed out") },
                    leadingIcon = Icons.AutoMirrored.Outlined.Logout,
                    variant = DecoCTAVariant.Danger,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DecoButton(onClick = { onShowToast("Password changed successfully") }) { Text("TOAST") }
                    DecoButton(onClick = {}, enabled = false) { Text("DISABLED") }
                }
            }

            Column(Modifier.padding(horizontal = 20.dp)) {
                DecoSectionHeader("Account", Modifier.padding(bottom = 4.dp), showRules = true)
                Column(Modifier.decoCard()) {
                    NavRow(Icons.Outlined.Security, "Privacy & Security", onClick = {})
                    NavRow(Icons.Outlined.Notifications, "Notifications", onClick = {})
                    NavRow(Icons.Outlined.CreditCard, "Payment Wallet", onClick = {})
                    NavRow(Icons.Outlined.Palette, "Appearance", onClick = null)
                }
            }

            SearchGenreRow("Drama", PreviewMediaItems, onItemClick = {}, modifier = Modifier.padding(horizontal = 16.dp))
            LibraryGenreRow(
                "Crime",
                PreviewLibraryItems,
                onItemClick = {},
                modifier = Modifier.padding(horizontal = 16.dp),
                onRemove = { onShowToast("Removed from library") },
            )
        }
        TopBand()
        ToastHost(toast, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DecoSectionHeader(title, subtitle = "Phase 2 component check")
        content()
    }
}

@Preview(device = "spec:width=411dp,height=2400dp")
@Composable
private fun ComponentGalleryPreview() {
    FlykPicksTheme { ComponentGallery(toast = ToastMessage("Password changed successfully"), onShowToast = {}) }
}
