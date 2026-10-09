package com.flykpicks.android.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flykpicks.android.ui.components.DecoAuthScreen
import com.flykpicks.android.ui.components.DecoCTAButton
import com.flykpicks.android.ui.components.DecoSectionHeader
import com.flykpicks.android.ui.components.DecoTextField
import com.flykpicks.android.ui.components.DecoTextLink
import com.flykpicks.android.ui.components.ErrorBanner
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.FlykType

/**
 * Second sign-in step (blueprint §10.3, iOS `MFAChallengeView`, plan Q13): one free-text field
 * for an authenticator, SMS or backup code; the server works out which it is, so there's no
 * numeric keyboard and no auto-submit. Back cancels the challenge.
 */
@Composable
fun MFAChallengeScreen(viewModel: AuthViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showReset by rememberSaveable { mutableStateOf(false) }

    BackHandler { viewModel.cancelMFA() }

    MFAChallengeContent(
        code = viewModel.mfaCode,
        // iOS strips whitespace as you type but leaves the character set alone (backup codes
        // can contain punctuation).
        onCodeChange = { viewModel.mfaCode = it.filterNot(Char::isWhitespace) },
        state = state,
        onVerify = { remember -> viewModel.verifyMFA(remember) },
        onCancel = viewModel::cancelMFA,
        onCantAccess = { showReset = true },
    )

    if (showReset) {
        MFAResetSheet(email = state.pendingMFA?.email.orEmpty(), onDismiss = { showReset = false })
    }
}

@Composable
private fun MFAChallengeContent(
    code: String,
    onCodeChange: (String) -> Unit,
    state: AuthUiState,
    onVerify: (rememberDevice: Boolean) -> Unit,
    onCancel: () -> Unit,
    onCantAccess: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val codeFocus = remember { FocusRequester() }
    var rememberDevice by rememberSaveable { mutableStateOf(false) }
    val canSubmit = code.isNotEmpty() && !state.isLoading
    val submit = {
        if (canSubmit) {
            focusManager.clearFocus()
            onVerify(rememberDevice)
        }
    }

    LaunchedEffect(Unit) { codeFocus.requestFocus() }

    DecoAuthScreen {
        DecoSectionHeader(
            "Two-Factor Verification",
            Modifier.padding(top = 4.dp),
            subtitle = "Enter the code from your authenticator app, the SMS we sent, or a backup code.",
        )

        DecoTextField(
            value = code,
            onValueChange = onCodeChange,
            placeholder = "Verification code",
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = Icons.Outlined.Shield,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Go,
            onImeAction = submit,
            contentType = ContentType.SmsOtpCode,
            focusRequester = codeFocus,
        )

        state.errorMessage?.takeIf { it.isNotEmpty() }?.let { ErrorBanner(it) }

        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = rememberDevice, role = Role.Switch, onValueChange = { rememberDevice = it }),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Don't ask for a code on this device for 30 days",
                modifier = Modifier.weight(1f),
                style = FlykType.Subheadline,
                color = FlykColors.Ink,
            )
            Switch(checked = rememberDevice, onCheckedChange = null)
        }

        DecoCTAButton(
            title = if (state.isLoading) "Verifying…" else "Verify",
            onClick = submit,
            isLoading = state.isLoading,
            isEnabled = canSubmit,
        )

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            DecoTextLink("Cancel and sign in with a different account", onCancel)
            DecoTextLink("Can't access any of your methods?", onCantAccess, color = FlykColors.InkMuted, fontWeight = FontWeight.Normal)
        }
    }
}

@Preview(device = "spec:width=411dp,height=891dp")
@Composable
private fun MFAChallengePreview() {
    FlykPicksTheme {
        MFAChallengeContent(
            code = "", onCodeChange = {},
            state = AuthUiState(pendingMFA = MFAChallenge("t", "ada@example.com"), errorMessage = "Code is incorrect."),
            onVerify = {}, onCancel = {}, onCantAccess = {},
        )
    }
}
