package com.flykpicks.android.ui.screens.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flykpicks.android.api.ApiError
import com.flykpicks.android.api.SessionEvent
import com.flykpicks.android.api.SessionEvents
import com.flykpicks.android.api.userFacingMessage
import com.flykpicks.android.model.auth.AuthError
import com.flykpicks.android.model.auth.LoginOutcome
import com.flykpicks.android.model.profile.FeatureFlagKey
import com.flykpicks.android.service.AuthService
import com.flykpicks.android.store.FeatureFlagStore
import com.flykpicks.android.store.GenreStore
import com.flykpicks.android.store.PerUserState
import com.flykpicks.android.ui.navigation.MainTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** Blueprint §9.1, iOS `AuthStore`. */
data class AuthUiState(
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = false,
    /** Shown on Login, or on the MFA screen while a challenge is open. */
    val errorMessage: String? = null,
    /** Non-null once the password is accepted and the server asks for a second factor. */
    val pendingMFA: MFAChallenge? = null,
    /** Sign-in is blocked until then; the UI counts down and must not auto-retry. */
    val loginRateLimitedUntil: Instant? = null,
    val backupCodeAlert: BackupCodeAlert? = null,
    /** A tab some flow asked the shell to switch to; the shell clears it once switched. */
    val pendingTabSelection: MainTab? = null,
)

/**
 * An open second-factor challenge. The token lives in memory only (iOS `MFAChallenge`);
 * [email] pre-fills the MFA reset request.
 */
data class MFAChallenge(val mfaToken: String, val email: String)

/** Shown once after signing in with a backup code (iOS `BackupCodeAlertContext`, plan Q14). */
data class BackupCodeAlert(val remaining: Int) {
    val message: String
        get() = when (remaining) {
            0 -> "That was your last backup code. If you lose your authenticator, you'll need to contact support to sign in."
            1 -> "You signed in with a backup code. 1 code left. Generate a new set so you don't get locked out."
            2 -> "You signed in with a backup code. 2 codes left. Generate a new set so you don't get locked out."
            else -> "You signed in with a backup code. $remaining codes left."
        }

    /** iOS offers "Go to Security" only when the user is close to running out. */
    val offersSecurityLink: Boolean get() = remaining <= 2
}

/**
 * App-wide auth state and the sign-in / sign-out flows (iOS `AuthStore`), scoped to the activity
 * so the shell, Login and the MFA screen share one instance. Registration, password recovery and
 * the MFA reset request have their own ViewModels (plan D24).
 *
 * The session is restored from the token store on cold start. When the network layer ends the
 * session ([SessionEvents]), the app returns to Login with "Your session expired…".
 *
 * Whenever the session changes hands, every [PerUserState] is reset first, so one account never
 * sees another's data (ios-gaps.md G21).
 *
 * Actions return their [Job] so tests can wait for them; the UI ignores it.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authService: AuthService,
    private val featureFlags: FeatureFlagStore,
    private val genres: GenreStore,
    private val perUserState: Set<@JvmSuppressWildcards PerUserState>,
    sessionEvents: SessionEvents,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(isAuthenticated = authService.hasSession))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // Field text is Compose state rather than part of the StateFlow so typing stays synchronous.
    // Kept here (not in the screen) so it survives rotation and a trip to Register and back.
    var loginEmail by mutableStateOf("")
    var loginPassword by mutableStateOf("")
    var mfaCode by mutableStateOf("")

    /** Gates the Login screen's "Forgot password?" link; cached, so it works signed out (plan Q4). */
    val accountRecoveryEnabled: StateFlow<Boolean> = featureFlags.flags
        .map { it[FeatureFlagKey.ACCOUNT_RECOVERY_ENABLED] == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, featureFlags.accountRecoveryEnabled)

    init {
        viewModelScope.launch {
            sessionEvents.events.collect { event ->
                when (event) {
                    is SessionEvent.Ended -> onSessionEnded(event.userMessage)
                }
            }
        }
        if (_uiState.value.isAuthenticated) loadSessionData()
    }

    fun login(): Job = viewModelScope.launch {
        val email = loginEmail.trim()
        if (email.isEmpty() || loginPassword.isEmpty() || _uiState.value.isLoading) return@launch
        _uiState.update { it.copy(isLoading = true, errorMessage = null, pendingMFA = null, loginRateLimitedUntil = null) }
        try {
            when (val outcome = authService.login(email, loginPassword)) {
                LoginOutcome.Session -> onSignedIn(backupCodesRemaining = null)
                is LoginOutcome.MFAChallenge -> _uiState.update { it.copy(pendingMFA = MFAChallenge(outcome.mfaToken, email)) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError.RateLimited) {
            _uiState.update { it.copy(loginRateLimitedUntil = e.nextAllowedAt, errorMessage = e.serverMessage ?: RATE_LIMITED) }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.userFacingMessage) }
        } finally {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Second leg. A wrong code keeps the challenge open; an invalid, locked or expired challenge
     * ends it and returns to Login with the message. Any error that keeps the challenge open also
     * clears the code field, as on iOS.
     */
    fun verifyMFA(rememberDevice: Boolean): Job = viewModelScope.launch {
        val challenge = _uiState.value.pendingMFA ?: return@launch
        val code = mfaCode.trim()
        if (code.isEmpty() || _uiState.value.isLoading) return@launch
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val backupCodesRemaining = authService.verifyMFA(challenge.mfaToken, code, rememberDevice)
            onSignedIn(backupCodesRemaining)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthError.InvalidMFACode) {
            _uiState.update { it.copy(errorMessage = e.userMessage) }
        } catch (e: AuthError) {
            // InvalidMFAChallenge, MFAChallengeLocked, MFAChallengeExpired: the challenge is over.
            _uiState.update { it.copy(pendingMFA = null, errorMessage = e.userMessage) }
        } catch (e: ApiError.RateLimited) {
            _uiState.update { it.copy(loginRateLimitedUntil = e.nextAllowedAt, errorMessage = e.serverMessage ?: RATE_LIMITED) }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.userFacingMessage) }
        } finally {
            _uiState.update { it.copy(isLoading = false) }
            val state = _uiState.value
            if (state.pendingMFA != null && state.errorMessage != null) mfaCode = ""
        }
    }

    fun cancelMFA() {
        mfaCode = ""
        _uiState.update { it.copy(pendingMFA = null, errorMessage = null) }
    }

    /** Forget this device's MFA trust, so the next sign-in asks for a code. */
    fun clearDeviceTrust(): Job = viewModelScope.launch { authService.clearDeviceTrust() }

    /**
     * Revokes the session server-side and signs out locally, even if the server call fails (the
     * error then shows on Login, as on iOS). Device trust is kept.
     */
    fun logout(): Job = viewModelScope.launch {
        _uiState.update {
            it.copy(isLoading = true, errorMessage = null, pendingMFA = null, backupCodeAlert = null, pendingTabSelection = null)
        }
        val error = try {
            authService.logout()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.userFacingMessage
        }
        clearFields()
        resetPerUserState()
        _uiState.value = AuthUiState(isAuthenticated = false, errorMessage = error)
    }

    fun requestTab(tab: MainTab) {
        _uiState.update { it.copy(pendingTabSelection = tab) }
    }

    fun onTabRequestHandled() {
        _uiState.update { it.copy(pendingTabSelection = null) }
    }

    fun dismissBackupCodeAlert() {
        _uiState.update { it.copy(backupCodeAlert = null) }
    }

    /** "Go to Security": like iOS, switches to the Profile tab. */
    fun openSecurityFromBackupCodeAlert() {
        _uiState.update { it.copy(backupCodeAlert = null, pendingTabSelection = MainTab.Profile) }
    }

    private fun onSignedIn(backupCodesRemaining: Int?) {
        loginPassword = ""
        mfaCode = ""
        // Again here, not only on the way out: a session that ended while no screen was
        // listening never reached onSessionEnded.
        resetPerUserState()
        _uiState.update {
            it.copy(isAuthenticated = true, pendingMFA = null, backupCodeAlert = backupCodesRemaining?.let(::BackupCodeAlert))
        }
        loadSessionData()
    }

    // The network layer has already cleared the tokens. Like iOS, a late event that arrives
    // after the user is back on Login doesn't overwrite what Login is showing.
    private fun onSessionEnded(message: String) {
        if (!_uiState.value.isAuthenticated) return
        clearFields()
        resetPerUserState()
        _uiState.value = AuthUiState(isAuthenticated = false, errorMessage = message)
    }

    private fun resetPerUserState() {
        perUserState.forEach { it.reset() }
    }

    // The next person to sign in on this device starts with empty fields, as on iOS (whose
    // Login screen is rebuilt after sign-out).
    private fun clearFields() {
        loginEmail = ""
        loginPassword = ""
        mfaCode = ""
    }

    // What the signed-in shell needs, whether it was reached by signing in or by a cold start:
    // fresh flags, and the genre table (iOS `RootView` loads it when the shell appears).
    private fun loadSessionData() {
        viewModelScope.launch { featureFlags.load() }
        viewModelScope.launch { genres.loadIfNeeded() }
    }

    private companion object {
        const val RATE_LIMITED = "Too many attempts. Try again shortly."
    }
}
