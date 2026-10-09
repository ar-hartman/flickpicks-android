package com.flykpicks.android.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flykpicks.android.ui.components.DecoAuthScreen
import com.flykpicks.android.ui.components.DecoCTAButton
import com.flykpicks.android.ui.components.DecoSecureField
import com.flykpicks.android.ui.components.DecoSectionHeader
import com.flykpicks.android.ui.components.DecoTextField
import com.flykpicks.android.ui.components.DecoTextLink
import com.flykpicks.android.ui.components.ErrorBanner
import com.flykpicks.android.ui.components.OrDivider
import com.flykpicks.android.ui.components.rememberSecondsUntil
import com.flykpicks.android.ui.theme.FlykPicksTheme

/** Blueprint §10.1, iOS `LoginView`. */
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recoveryEnabled by viewModel.accountRecoveryEnabled.collectAsStateWithLifecycle()
    LoginContent(
        email = viewModel.loginEmail,
        onEmailChange = { viewModel.loginEmail = it },
        password = viewModel.loginPassword,
        onPasswordChange = { viewModel.loginPassword = it },
        state = state,
        recoveryEnabled = recoveryEnabled,
        onSubmit = { viewModel.login() },
        onCreateAccount = onCreateAccount,
        onForgotPassword = onForgotPassword,
    )
}

@Composable
private fun LoginContent(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    state: AuthUiState,
    recoveryEnabled: Boolean,
    onSubmit: () -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val emailFocus = remember { FocusRequester() }
    val lockedSeconds = rememberSecondsUntil(state.loginRateLimitedUntil)
    val canSubmit = email.isNotBlank() && password.isNotEmpty() && !state.isLoading && lockedSeconds == 0
    val submit = {
        if (canSubmit) {
            focusManager.clearFocus()
            onSubmit()
        }
    }

    LaunchedEffect(Unit) { emailFocus.requestFocus() }

    DecoAuthScreen {
        DecoSectionHeader("Sign In", Modifier.padding(top = 4.dp))

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DecoTextField(
                value = email,
                onValueChange = onEmailChange,
                placeholder = "Email",
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Outlined.Email,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                contentType = ContentType.EmailAddress + ContentType.Username,
                focusRequester = emailFocus,
            )
            DecoSecureField(
                value = password,
                onValueChange = onPasswordChange,
                placeholder = "Password",
                modifier = Modifier.fillMaxWidth(),
                imeAction = ImeAction.Go,
                onImeAction = submit,
            )
        }

        state.errorMessage?.takeIf { it.isNotEmpty() }?.let { ErrorBanner(it) }

        DecoCTAButton(
            title = if (lockedSeconds > 0) "Try again in ${lockedSeconds}s" else "Sign In",
            onClick = submit,
            isLoading = state.isLoading,
            isEnabled = canSubmit,
        )

        Column(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (recoveryEnabled) DecoTextLink("Forgot password?", onForgotPassword)
            OrDivider()
            DecoTextLink("Create account", onCreateAccount, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Preview(device = "spec:width=411dp,height=891dp")
@Composable
private fun LoginPreview() {
    FlykPicksTheme {
        LoginContent(
            email = "ada@example.com", onEmailChange = {}, password = "", onPasswordChange = {},
            state = AuthUiState(errorMessage = "Your session expired. Please sign in again."),
            recoveryEnabled = true, onSubmit = {}, onCreateAccount = {}, onForgotPassword = {},
        )
    }
}
