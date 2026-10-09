package com.flykpicks.android.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flykpicks.android.api.ApiError
import com.flykpicks.android.api.userFacingMessage
import com.flykpicks.android.model.auth.AuthError
import com.flykpicks.android.service.AuthService
import com.flykpicks.android.ui.components.DecoAuthScreen
import com.flykpicks.android.ui.components.DecoCTAButton
import com.flykpicks.android.ui.components.DecoSecureField
import com.flykpicks.android.ui.components.DecoSectionHeader
import com.flykpicks.android.ui.components.DecoTextField
import com.flykpicks.android.ui.components.DecoTextLink
import com.flykpicks.android.ui.components.ErrorBanner
import com.flykpicks.android.ui.components.InfoBanner
import com.flykpicks.android.ui.components.OrDivider
import com.flykpicks.android.ui.components.rememberSecondsUntil
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykType
import com.flykpicks.android.ui.theme.SerifFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

/**
 * Account creation (blueprint §10.2, iOS `RegistrationView`). A new account can't sign in until
 * its email is verified, so success shows "Check Your Email" instead of signing in.
 */
@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val authService: AuthService,
    private val clock: Clock,
) : ViewModel() {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")

    var isSubmitting by mutableStateOf(false)
        private set
    var generalError by mutableStateOf<String?>(null)
        private set
    var rateLimitedUntil by mutableStateOf<Instant?>(null)
        private set

    /** Set once the account is created; the screen switches to "Check Your Email". */
    var registeredEmail by mutableStateOf<String?>(null)
        private set
    var registeredMessage by mutableStateOf<String?>(null)
        private set

    var isResending by mutableStateOf(false)
        private set
    var resendConfirmation by mutableStateOf<String?>(null)
        private set
    var resendError by mutableStateOf<String?>(null)
        private set
    var resendBlockedUntil by mutableStateOf<Instant?>(null)
        private set

    /** Loose check; the server is the source of truth (iOS uses the same pattern). */
    val isFormValid: Boolean
        get() = EMAIL_PATTERN.matches(email.trim()) && password.length >= MIN_PASSWORD && confirmPassword == password

    private fun isBlocked(until: Instant?) = until != null && until.isAfter(clock.instant())

    fun register(): Job = viewModelScope.launch {
        if (!isFormValid || isSubmitting || isBlocked(rateLimitedUntil)) return@launch
        val normalized = email.trim().lowercase(Locale.ROOT)
        generalError = null
        isSubmitting = true
        try {
            val response = authService.register(normalized, password)
            registeredEmail = normalized
            registeredMessage = response.message
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthError.InvalidInput) {
            // Bad format, short password, or a rejected domain; the server message is for display.
            generalError = e.serverMessage ?: "Please check your input and try again."
        } catch (e: ApiError.RateLimited) {
            rateLimitedUntil = e.nextAllowedAt
            generalError = e.serverMessage ?: "Too many attempts. Please try again shortly."
        } catch (e: Exception) {
            generalError = e.userFacingMessage
        } finally {
            isSubmitting = false
        }
    }

    fun resend(): Job = viewModelScope.launch {
        val target = registeredEmail ?: return@launch
        if (isResending || isBlocked(resendBlockedUntil)) return@launch
        resendError = null
        resendConfirmation = null
        isResending = true
        try {
            resendConfirmation = authService.resendVerification(target).message
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError.RateLimited) {
            resendBlockedUntil = e.nextAllowedAt
            resendError = e.serverMessage ?: "Please wait before requesting another email."
        } catch (e: Exception) {
            resendError = e.userFacingMessage
        } finally {
            isResending = false
        }
    }

    companion object {
        const val MIN_PASSWORD = 8
        private val EMAIL_PATTERN = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
    }
}

/** [onBackToSignIn] gets the registered email when there is one, to pre-fill Login. */
@Composable
fun RegistrationScreen(viewModel: RegistrationViewModel, onBackToSignIn: (prefillEmail: String?) -> Unit) {
    DecoAuthScreen {
        val registered = viewModel.registeredEmail
        if (registered == null) RegistrationForm(viewModel, onSignIn = { onBackToSignIn(null) })
        else CheckYourEmail(viewModel, registered, onBackToSignIn = { onBackToSignIn(registered) })
    }
}

@Composable
private fun RegistrationForm(viewModel: RegistrationViewModel, onSignIn: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val emailFocus = remember { FocusRequester() }
    val lockedSeconds = rememberSecondsUntil(viewModel.rateLimitedUntil)
    val canSubmit = viewModel.isFormValid && !viewModel.isSubmitting && lockedSeconds == 0
    val submit = {
        if (canSubmit) {
            focusManager.clearFocus()
            viewModel.register()
        }
    }
    LaunchedEffect(Unit) { emailFocus.requestFocus() }

    DecoSectionHeader(
        "Create Your Account",
        Modifier.padding(top = 4.dp),
        subtitle = "Track your collection and sync across devices.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DecoTextField(
            value = viewModel.email,
            onValueChange = { viewModel.email = it },
            placeholder = "Email",
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = Icons.Outlined.Email,
            keyboardType = KeyboardType.Email,
            contentType = ContentType.EmailAddress + ContentType.NewUsername,
            focusRequester = emailFocus,
        )
        DecoSecureField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            placeholder = "Password (8+ characters)",
            modifier = Modifier.fillMaxWidth(),
            contentType = ContentType.NewPassword,
        )
        DecoSecureField(
            value = viewModel.confirmPassword,
            onValueChange = { viewModel.confirmPassword = it },
            placeholder = "Confirm password",
            modifier = Modifier.fillMaxWidth(),
            imeAction = ImeAction.Go,
            onImeAction = submit,
            contentType = ContentType.NewPassword,
        )
    }
    viewModel.generalError?.takeIf { it.isNotEmpty() }?.let { ErrorBanner(it) }
    DecoCTAButton(
        title = when {
            viewModel.isSubmitting -> "Creating Account…"
            lockedSeconds > 0 -> "Try again in ${lockedSeconds}s"
            else -> "Create Account"
        },
        onClick = submit,
        isLoading = viewModel.isSubmitting,
        isEnabled = canSubmit,
    )
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OrDivider()
        Row(
            Modifier
                .clickable(role = Role.Button, onClick = onSignIn)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Already have an account?", style = FlykType.Footnote, color = FlykColors.InkMuted)
            Text("Sign in", style = FlykType.Footnote.copy(fontWeight = FontWeight.SemiBold), color = FlykColors.Brass)
        }
    }
}

/**
 * Must be genuinely informative: an unverified account's sign-in fails with the same message as
 * a wrong password, so Login can't explain it later.
 */
@Composable
private fun CheckYourEmail(viewModel: RegistrationViewModel, email: String, onBackToSignIn: () -> Unit) {
    val blockedSeconds = rememberSecondsUntil(viewModel.resendBlockedUntil)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Outlined.MarkEmailUnread, contentDescription = null, tint = FlykColors.Brass, modifier = Modifier.size(52.dp))
        Text(
            "Check Your Email",
            style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp),
            color = FlykColors.Ink,
        )
        Text(
            viewModel.registeredMessage
                ?: "We've sent a verification link to $email. Open it to activate your account, then sign in.",
            style = FlykType.Subheadline,
            color = FlykColors.Ink.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
    viewModel.resendConfirmation?.takeIf { it.isNotEmpty() }?.let { InfoBanner(it) }
    viewModel.resendError?.takeIf { it.isNotEmpty() }?.let { ErrorBanner(it) }
    DecoCTAButton(
        title = when {
            viewModel.isResending -> "Sending…"
            blockedSeconds > 0 -> "Resend in ${blockedSeconds}s"
            else -> "Resend Verification Email"
        },
        onClick = { viewModel.resend() },
        isLoading = viewModel.isResending,
        isEnabled = !viewModel.isResending && blockedSeconds == 0,
    )
    DecoTextLink(
        "Back to sign in",
        onBackToSignIn,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        fontWeight = FontWeight.SemiBold,
    )
}
