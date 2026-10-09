package com.flykpicks.android.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Shield
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flykpicks.android.BuildConfig
import com.flykpicks.android.api.ApiError
import com.flykpicks.android.api.userFacingMessage
import com.flykpicks.android.model.auth.AuthError
import com.flykpicks.android.service.AuthService
import com.flykpicks.android.ui.components.DecoAuthScreen
import com.flykpicks.android.ui.components.DecoCTAButton
import com.flykpicks.android.ui.components.DecoCTAVariant
import com.flykpicks.android.ui.components.DecoSecureField
import com.flykpicks.android.ui.components.DecoSectionHeader
import com.flykpicks.android.ui.components.DecoTextField
import com.flykpicks.android.ui.components.ErrorBanner
import com.flykpicks.android.ui.components.InlineError
import com.flykpicks.android.ui.components.OTPCodeField
import com.flykpicks.android.ui.components.rememberSecondsUntil
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

enum class RecoveryStep { RequestCode, VerifyCode, NewPassword, Done }

/**
 * Password recovery (blueprint §10.4, iOS `ForgotPassword*View`): email → 6-digit code → new
 * password → done. iOS pushes a screen per step; here it's one route with [step], and back
 * walks the steps. Requesting a code always "succeeds" so it can't reveal whether an account
 * exists. There's no resend on the code step, as on iOS: going back to the first step is how
 * you send another.
 */
@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authService: AuthService,
    private val clock: Clock,
) : ViewModel() {
    var step by mutableStateOf(RecoveryStep.RequestCode)
        private set
    var isSubmitting by mutableStateOf(false)
        private set

    // Step 1
    var email by mutableStateOf("")
    var requestError by mutableStateOf<String?>(null)
        private set
    var rateLimitedUntil by mutableStateOf<Instant?>(null)
        private set

    /** The normalized address the code went to. */
    var sentTo by mutableStateOf("")
        private set

    // Step 2
    var code by mutableStateOf("")
        private set
    var codeError by mutableStateOf<String?>(null)
        private set
    var isLocked by mutableStateOf(false)
        private set
    private var recoveryToken: String? = null

    // Step 3
    var newPassword by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var resetError by mutableStateOf<String?>(null)
        private set

    val canReset: Boolean
        get() = newPassword.length >= MIN_PASSWORD && confirmPassword == newPassword && !isSubmitting

    fun requestCode(): Job = viewModelScope.launch {
        val subject = email.trim().lowercase(Locale.ROOT)
        val limited = rateLimitedUntil?.isAfter(clock.instant()) == true
        if (subject.isEmpty() || isSubmitting || limited) return@launch
        requestError = null
        isSubmitting = true
        try {
            authService.requestOneTimePassword(subject)
            sentTo = subject
            code = ""
            codeError = null
            isLocked = false
            step = RecoveryStep.VerifyCode
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError.RateLimited) {
            rateLimitedUntil = e.nextAllowedAt
            requestError = e.serverMessage ?: "Too many attempts. Please try again shortly."
        } catch (e: Exception) {
            requestError = e.userFacingMessage
        } finally {
            isSubmitting = false
        }
    }

    /** The code submits itself once all digits are in, as on iOS; returns that submission. */
    fun onCodeChange(value: String): Job? {
        code = value
        codeError = null
        return if (value.length == CODE_LENGTH) verifyCode() else null
    }

    fun verifyCode(): Job = viewModelScope.launch {
        if (code.length != CODE_LENGTH || isSubmitting || isLocked) return@launch
        codeError = null
        isSubmitting = true
        try {
            val token = authService.verifyOneTimePassword(sentTo, code).recoveryToken
            if (token == null) {
                codeError = "Verification failed. Please try again."
                code = ""
            } else {
                recoveryToken = token
                newPassword = ""
                confirmPassword = ""
                resetError = null
                step = RecoveryStep.NewPassword
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthError.OTPInvalid) {
            codeError = e.userMessage
            code = ""
        } catch (e: AuthError.OTPExpired) {
            codeError = e.userMessage
        } catch (e: AuthError.OTPLocked) {
            isLocked = true
        } catch (e: Exception) {
            codeError = e.userFacingMessage
        } finally {
            isSubmitting = false
        }
    }

    fun resetPassword(): Job = viewModelScope.launch {
        val token = recoveryToken ?: return@launch
        if (!canReset) return@launch
        resetError = null
        isSubmitting = true
        try {
            authService.changeLostPassword(sentTo, newPassword, token)
            recoveryToken = null
            newPassword = ""
            confirmPassword = ""
            step = RecoveryStep.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // AuthError.RecoveryTokenInvalid / RecoveryTokenMissing carry the iOS wording.
            resetError = e.userFacingMessage
        } finally {
            isSubmitting = false
        }
    }

    /** One step back. False when back should leave the flow instead. */
    fun back(): Boolean = when (step) {
        RecoveryStep.RequestCode, RecoveryStep.Done -> false
        RecoveryStep.VerifyCode -> {
            step = RecoveryStep.RequestCode
            true
        }
        RecoveryStep.NewPassword -> {
            recoveryToken = null
            code = ""
            codeError = null
            step = RecoveryStep.VerifyCode
            true
        }
    }

    companion object {
        const val CODE_LENGTH = 6
        const val MIN_PASSWORD = 8
    }
}

/** [onClose] leaves the flow for Login (after success, on lockout, or back from the first step). */
@Composable
fun ForgotPasswordFlow(viewModel: ForgotPasswordViewModel, onClose: () -> Unit) {
    BackHandler { if (!viewModel.back()) onClose() }
    AnimatedContent(
        targetState = viewModel.step,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val spec = tween<IntOffset>(350)
            if (forward) slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it / 3 }
            else slideInHorizontally(spec) { -it / 3 } togetherWith slideOutHorizontally(spec) { it }
        },
        label = "recoveryStep",
    ) { step ->
        DecoAuthScreen {
            when (step) {
                RecoveryStep.RequestCode -> RequestCodeStep(viewModel)
                RecoveryStep.VerifyCode -> VerifyCodeStep(viewModel, onClose)
                RecoveryStep.NewPassword -> NewPasswordStep(viewModel)
                RecoveryStep.Done -> DoneStep(onClose)
            }
        }
    }
}

@Composable
private fun RequestCodeStep(viewModel: ForgotPasswordViewModel) {
    val focusManager = LocalFocusManager.current
    val emailFocus = remember { FocusRequester() }
    val lockedSeconds = rememberSecondsUntil(viewModel.rateLimitedUntil)
    val canSubmit = viewModel.email.isNotBlank() && !viewModel.isSubmitting && lockedSeconds == 0
    val submit = {
        if (canSubmit) {
            focusManager.clearFocus()
            viewModel.requestCode()
        }
    }
    LaunchedEffect(Unit) { emailFocus.requestFocus() }

    DecoSectionHeader(
        "Reset Your Password",
        Modifier.padding(top = 4.dp),
        subtitle = "Enter your email and we'll send a 6-digit code to verify your identity.",
    )
    DecoTextField(
        value = viewModel.email,
        onValueChange = { viewModel.email = it },
        placeholder = "Email",
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = Icons.Outlined.Email,
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Send,
        onImeAction = submit,
        contentType = ContentType.EmailAddress,
        focusRequester = emailFocus,
    )
    viewModel.requestError?.takeIf { it.isNotEmpty() }?.let { ErrorBanner(it) }
    DecoCTAButton(
        title = when {
            viewModel.isSubmitting -> "Sending…"
            lockedSeconds > 0 -> "Try again in ${lockedSeconds}s"
            else -> "Send Code"
        },
        onClick = submit,
        isLoading = viewModel.isSubmitting,
        isEnabled = canSubmit,
    )
    if (BuildConfig.DEBUG) {
        // iOS shows the same note in debug builds.
        val shape = RoundedCornerShape(14.dp)
        Text(
            "DEBUG: SMS delivery is currently mocked on backend. Codes are visible in server logs.",
            modifier = Modifier
                .fillMaxWidth()
                .background(FlykColors.Ember.copy(alpha = 0.10f), shape)
                .border(1.dp, FlykColors.Ember.copy(alpha = 0.35f), shape)
                .padding(10.dp),
            style = FlykType.Caption2,
            color = FlykColors.Ember,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ColumnScope.VerifyCodeStep(viewModel: ForgotPasswordViewModel, onStartOver: () -> Unit) {
    DecoSectionHeader(
        "Enter Verification Code",
        Modifier.padding(top = 4.dp),
        subtitle = "We sent a 6-digit code to ${viewModel.sentTo} if an account exists. Codes expire in 10 minutes.",
    )
    if (viewModel.isLocked) {
        Column(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Outlined.Shield, contentDescription = null, tint = FlykColors.Ember, modifier = Modifier.size(36.dp))
            Text(
                "Too many attempts. Try again in a few minutes.",
                style = FlykType.Subheadline,
                color = FlykColors.Ink,
                textAlign = TextAlign.Center,
            )
            DecoCTAButton("Start Over", onClick = onStartOver, variant = DecoCTAVariant.Danger)
        }
    } else {
        OTPCodeField(
            code = viewModel.code,
            onCodeChange = { viewModel.onCodeChange(it) },
            modifier = Modifier.fillMaxWidth(),
            length = ForgotPasswordViewModel.CODE_LENGTH,
        )
        viewModel.codeError?.takeIf { it.isNotEmpty() }?.let { InlineError(it, Modifier.align(Alignment.CenterHorizontally)) }
        DecoCTAButton(
            title = if (viewModel.isSubmitting) "Verifying…" else "Verify Code",
            onClick = { viewModel.verifyCode() },
            isLoading = viewModel.isSubmitting,
            isEnabled = viewModel.code.length == ForgotPasswordViewModel.CODE_LENGTH && !viewModel.isSubmitting,
        )
    }
}

@Composable
private fun NewPasswordStep(viewModel: ForgotPasswordViewModel) {
    val focusManager = LocalFocusManager.current
    val passwordFocus = remember { FocusRequester() }
    val submit = {
        if (viewModel.canReset) {
            focusManager.clearFocus()
            viewModel.resetPassword()
        }
    }
    LaunchedEffect(Unit) { passwordFocus.requestFocus() }

    DecoSectionHeader("Choose a New Password", Modifier.padding(top = 4.dp), subtitle = "At least 8 characters.")
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DecoSecureField(
            value = viewModel.newPassword,
            onValueChange = { viewModel.newPassword = it },
            placeholder = "New password (8+ characters)",
            modifier = Modifier.fillMaxWidth(),
            contentType = ContentType.NewPassword,
            focusRequester = passwordFocus,
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
    viewModel.resetError?.takeIf { it.isNotEmpty() }?.let { ErrorBanner(it) }
    DecoCTAButton(
        title = if (viewModel.isSubmitting) "Updating…" else "Reset Password",
        onClick = submit,
        isLoading = viewModel.isSubmitting,
        isEnabled = viewModel.canReset,
    )
}

@Composable
private fun DoneStep(onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Filled.Verified, contentDescription = null, tint = FlykColors.Jade, modifier = Modifier.size(56.dp))
        Text("Password Updated", style = FlykType.Title3.copy(fontWeight = FontWeight.SemiBold), color = FlykColors.Ink)
        Text(
            "You can now sign in with your new password.",
            style = FlykType.Subheadline,
            color = FlykColors.InkMuted,
            textAlign = TextAlign.Center,
        )
        DecoCTAButton("Continue to Sign In", onClick = onContinue, modifier = Modifier.padding(top = 4.dp))
    }
}
