package com.flykpicks.android.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flykpicks.android.api.ApiError
import com.flykpicks.android.api.userFacingMessage
import com.flykpicks.android.service.AuthService
import com.flykpicks.android.ui.components.DecoCTAButton
import com.flykpicks.android.ui.components.DecoSecureField
import com.flykpicks.android.ui.components.DecoSectionHeader
import com.flykpicks.android.ui.components.DecoTextField
import com.flykpicks.android.ui.components.ErrorBanner
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Can't access any of your methods?" (iOS `MFAResetRequestSheet`, blueprint §8.6). The server
 * answers 202 whether or not the password matched, so success always reads the same and never
 * reveals whether the account exists.
 */
@HiltViewModel
class MFAResetViewModel @Inject constructor(private val authService: AuthService) : ViewModel() {
    var password by mutableStateOf("")
    var note by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var submitted by mutableStateOf(false)
        private set

    val canSubmit: Boolean get() = password.isNotEmpty() && !isLoading

    fun onPasswordChange(value: String) {
        password = value
        errorMessage = null
    }

    fun onNoteChange(value: String) {
        note = value.take(NOTE_LIMIT)
    }

    /** Each opening starts fresh (iOS builds a new sheet), and the password doesn't linger. */
    fun reset() {
        password = ""
        note = ""
        errorMessage = null
        submitted = false
    }

    fun submit(email: String): Job = viewModelScope.launch {
        if (!canSubmit) return@launch
        isLoading = true
        errorMessage = null
        try {
            authService.requestMFAReset(email, password, note.ifEmpty { null })
            submitted = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError.RateLimited) {
            errorMessage = e.serverMessage ?: "Too many attempts. Try again shortly."
        } catch (e: Exception) {
            errorMessage = e.userFacingMessage
        } finally {
            isLoading = false
        }
    }

    companion object {
        const val NOTE_LIMIT = 500
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MFAResetSheet(email: String, onDismiss: () -> Unit, viewModel: MFAResetViewModel = hiltViewModel()) {
    val close = {
        viewModel.reset()
        onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FlykColors.Canvas,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (viewModel.submitted) Confirmation(close) else ResetForm(email, viewModel)
        }
    }
}

@Composable
private fun ResetForm(email: String, viewModel: MFAResetViewModel) {
    DecoSectionHeader("MFA Reset Request", subtitle = "Try these first — each is faster than a support request:")
    Hint(Icons.Outlined.Key, "Enter a backup code in the field behind this sheet")
    Hint(Icons.Outlined.Autorenew, "Check whether another method is enrolled on your account")

    DecoSectionHeader("Your details", Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Email", style = FlykType.Subheadline, color = FlykColors.Ink)
        Text(email.ifEmpty { "—" }, style = FlykType.Subheadline, color = FlykColors.InkMuted)
    }
    DecoSecureField(
        value = viewModel.password,
        onValueChange = viewModel::onPasswordChange,
        placeholder = "Password",
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = null,
        imeAction = ImeAction.Next,
    )
    DecoTextField(
        value = viewModel.note,
        onValueChange = viewModel::onNoteChange,
        placeholder = "Anything that helps us confirm it's you — for example, what you last bought.",
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        imeAction = ImeAction.Default,
    )
    Text("We need your password to confirm this request is yours.", style = FlykType.Footnote, color = FlykColors.InkMuted)
    viewModel.errorMessage?.let { ErrorBanner(it) }
    DecoCTAButton(
        title = if (viewModel.isLoading) "Submitting…" else "Request a Reset",
        onClick = { viewModel.submit(email) },
        isLoading = viewModel.isLoading,
        isEnabled = viewModel.canSubmit,
    )
}

@Composable
private fun Hint(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = FlykColors.Brass, modifier = Modifier.size(18.dp))
        Text(text, style = FlykType.Footnote, color = FlykColors.InkMuted)
    }
}

@Composable
private fun Confirmation(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Outlined.MarkEmailRead, contentDescription = null, tint = FlykColors.Brass, modifier = Modifier.size(48.dp))
        Text("Request received.", style = FlykType.Title3.copy(fontWeight = FontWeight.SemiBold), color = FlykColors.Ink)
        Text(
            "If the details matched an account, we'll email you.",
            style = FlykType.Subheadline,
            color = FlykColors.InkMuted,
            textAlign = TextAlign.Center,
        )
        DecoCTAButton("Done", onClick = onDone, modifier = Modifier.padding(top = 8.dp))
    }
}
