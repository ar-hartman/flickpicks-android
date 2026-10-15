package com.flykpicks.android.ui.screens.auth

import com.flykpicks.android.api.SessionEvent
import com.flykpicks.android.service.AuthService
import com.flykpicks.android.service.FeatureFlagService
import com.flykpicks.android.service.GenreService
import com.flykpicks.android.store.FeatureFlagStore
import com.flykpicks.android.store.GenreStore
import com.flykpicks.android.store.PerUserState
import com.flykpicks.android.testing.FIXED_NOW
import com.flykpicks.android.testing.InMemoryFeatureFlagCache
import com.flykpicks.android.testing.MFA_CHALLENGE_JSON
import com.flykpicks.android.testing.Routes
import com.flykpicks.android.testing.SESSION_JSON
import com.flykpicks.android.testing.TestNetwork
import com.flykpicks.android.testing.errorJson
import com.flykpicks.android.testing.json
import com.flykpicks.android.testing.rateLimitedJson
import com.flykpicks.android.testing.status
import com.flykpicks.android.ui.navigation.MainTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration

/** Every LoginOutcome / error branch, against the production networking stack. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var net: TestNetwork
    private val routes = Routes()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer().apply { dispatcher = routes; start() }
        net = TestNetwork(server)
    }

    @After
    fun tearDown() {
        server.close()
        Dispatchers.resetMain()
    }

    // Records, for each reset of the per-user stores, whether the app was still signed in.
    private val signedInAtReset = mutableListOf<Boolean>()
    private var current: AuthViewModel? = null
    private val perUserState = object : PerUserState {
        override fun reset() {
            signedInAtReset += current?.uiState?.value?.isAuthenticated == true
        }
    }

    private fun viewModel() = AuthViewModel(
        AuthService(net.api(), net.tokenStore, net.device, Dispatchers.Unconfined),
        FeatureFlagStore(FeatureFlagService(net.api()), InMemoryFeatureFlagCache(), net.moshi),
        GenreStore(GenreService(net.api(), net.genres)),
        setOf(perUserState),
        net.sessionEvents,
    ).also { current = it }

    private fun AuthViewModel.signInWith(vararg loginResponses: mockwebserver3.MockResponse) = apply {
        routes.on("v2/auth/login", *loginResponses)
        loginEmail = " ada@example.com "
        loginPassword = "correct horse"
        runBlocking { login().join() }
    }

    private fun AuthViewModel.verify(code: String, rememberDevice: Boolean = false) = apply {
        mfaCode = code
        runBlocking { verifyMFA(rememberDevice).join() }
    }

    // MARK: Session restore and expiry

    @Test
    fun coldStart_isSignedIn_onlyWithAStoredAccessToken() {
        assertFalse(viewModel().uiState.value.isAuthenticated)

        net.signIn()
        assertTrue(viewModel().uiState.value.isAuthenticated)
    }

    @Test
    fun sessionEndedByTheNetwork_returnsToLogin_withTheExpiryMessage_andEmptyFields() {
        net.signIn()
        val vm = viewModel()
        vm.loginEmail = "ada@example.com"
        vm.requestTab(MainTab.Search)

        net.sessionEvents.emit(SessionEvent.Ended(SessionEvent.Reason.REFRESH_REJECTED))

        val state = vm.uiState.value
        assertFalse(state.isAuthenticated)
        assertEquals("Your session expired. Please sign in again.", state.errorMessage)
        assertNull(state.pendingTabSelection)
        assertEquals("", vm.loginEmail)
    }

    @Test
    fun sessionEndedWhileAlreadySignedOut_isIgnored() {
        val vm = viewModel()

        net.sessionEvents.emit(SessionEvent.Ended(SessionEvent.Reason.TOKEN_REVOKED))

        assertNull(vm.uiState.value.errorMessage)
    }

    // MARK: Per-account state

    @Test
    fun signingIn_resetsThePerUserStores_beforeTheShellAppears_andLoadsTheGenres() {
        val vm = viewModel().signInWith(json(SESSION_JSON))

        assertTrue(vm.uiState.value.isAuthenticated)
        assertEquals(listOf(false), signedInAtReset)
        routes.awaitRequests("v1/genres", 1)
    }

    @Test
    fun signingInThroughMFA_resetsThemToo() {
        routes.on("v2/auth/mfa", json(SESSION_JSON))

        viewModel().signInWith(json(MFA_CHALLENGE_JSON)).verify("123456")

        assertEquals(listOf(false), signedInAtReset)
    }

    @Test
    fun signingOut_resetsThePerUserStores_evenWhenTheServerCallFails() {
        net.signIn()
        routes.on("v2/auth/logout", status(500))
        val vm = viewModel()

        runBlocking { vm.logout().join() }

        assertFalse(vm.uiState.value.isAuthenticated)
        assertEquals(1, signedInAtReset.size)
    }

    @Test
    fun aSessionEndedByTheNetwork_resetsThePerUserStores() {
        net.signIn()
        val vm = viewModel()

        net.sessionEvents.emit(SessionEvent.Ended(SessionEvent.Reason.TOKEN_REVOKED))

        assertFalse(vm.uiState.value.isAuthenticated)
        assertEquals(1, signedInAtReset.size)
    }

    @Test
    fun coldStartWhileSignedIn_keepsThePerUserStores_andLoadsTheGenres() {
        net.signIn()

        viewModel()

        assertTrue(signedInAtReset.isEmpty())
        routes.awaitRequests("v1/genres", 1)
    }

    // MARK: Login

    @Test
    fun login_withoutMfa_signsIn_withTrimmedEmail_andForgetsThePassword() {
        val vm = viewModel().signInWith(json(SESSION_JSON))

        assertTrue(vm.uiState.value.isAuthenticated)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals("", vm.loginPassword)
        assertEquals("access-1", net.tokenStore.accessToken())
        assertTrue(routes.bodyOf("v2/auth/login").contains("\"email\":\"ada@example.com\""))
    }

    @Test
    fun login_withMfa_opensTheChallenge_rememberingTheEmail() {
        val vm = viewModel().signInWith(json(MFA_CHALLENGE_JSON))

        val state = vm.uiState.value
        assertFalse(state.isAuthenticated)
        assertEquals(MFAChallenge("mfa-1", "ada@example.com"), state.pendingMFA)
        assertNull(net.tokenStore.accessToken())
    }

    @Test
    fun login_wrongPassword_showsTheServerMessage() {
        val vm = viewModel().signInWith(errorJson(401, "invalid_credentials", "Email or password is incorrect."))

        assertFalse(vm.uiState.value.isAuthenticated)
        assertEquals("Email or password is incorrect.", vm.uiState.value.errorMessage)
        assertEquals("the password stays for another try", "correct horse", vm.loginPassword)
    }

    @Test
    fun login_rateLimited_blocksUntilTheDeadline() {
        val vm = viewModel().signInWith(rateLimitedJson("Too many sign-in attempts."))

        assertEquals(FIXED_NOW.plus(Duration.ofMinutes(5)), vm.uiState.value.loginRateLimitedUntil)
        assertEquals("Too many sign-in attempts.", vm.uiState.value.errorMessage)
    }

    @Test
    fun login_withEmptyFields_sendsNothing() {
        val vm = viewModel()
        vm.loginEmail = "   "
        vm.loginPassword = "pw"

        runBlocking { vm.login().join() }

        assertTrue(routes.requestsTo("v2/auth/login").isEmpty())
    }

    @Test
    fun signingIn_refreshesFeatureFlags() {
        routes.on("v1/feature-flags", json("""{"flags":{"accountRecoveryEnabled":true}}"""))
        val vm = viewModel()
        assertFalse(vm.accountRecoveryEnabled.value)

        vm.signInWith(json(SESSION_JSON))

        runBlocking { withTimeout(5_000) { vm.accountRecoveryEnabled.first { it } } }
    }

    // MARK: MFA

    @Test
    fun verify_withAppCode_signsIn_withoutABackupCodeAlert() {
        routes.on("v2/auth/mfa", json(SESSION_JSON))
        val vm = viewModel().signInWith(json(MFA_CHALLENGE_JSON)).verify(" 123456 ")

        val state = vm.uiState.value
        assertTrue(state.isAuthenticated)
        assertNull(state.pendingMFA)
        assertNull(state.backupCodeAlert)
        assertTrue(routes.bodyOf("v2/auth/mfa").contains("\"code\":\"123456\""))
    }

    @Test
    fun verify_withBackupCode_signsIn_andShowsTheBackupCodeAlert() {
        routes.on("v2/auth/mfa", json(SESSION_JSON.replace("}", ""","backupCodesRemaining":1}""")))
        val vm = viewModel().signInWith(json(MFA_CHALLENGE_JSON)).verify("abcd-efgh")

        assertEquals(BackupCodeAlert(1), vm.uiState.value.backupCodeAlert)
    }

    @Test
    fun verify_rememberingTheDevice_storesTheTrustToken() {
        routes.on(
            "v2/auth/mfa",
            json(SESSION_JSON.replace("}", ""","trustToken":"trust-1","trustExpiresAt":"2026-11-07T00:00:00Z"}""")),
        )
        viewModel().signInWith(json(MFA_CHALLENGE_JSON)).verify("123456", rememberDevice = true)

        assertTrue(routes.bodyOf("v2/auth/mfa").contains("\"rememberDevice\":true"))
        assertEquals("trust-1", net.tokenStore.trustToken())
    }

    @Test
    fun verify_wrongCode_keepsTheChallengeOpen_andClearsTheField() {
        routes.on("v2/auth/mfa", errorJson(401, "invalid_mfa_code", "That code didn't work."))
        val vm = viewModel().signInWith(json(MFA_CHALLENGE_JSON)).verify("111111")

        val state = vm.uiState.value
        assertEquals(MFAChallenge("mfa-1", "ada@example.com"), state.pendingMFA)
        assertEquals("That code didn't work.", state.errorMessage)
        assertEquals("", vm.mfaCode)
    }

    @Test
    fun verify_expiredChallenge_returnsToLogin_withTheMessage() {
        routes.on("v2/auth/mfa", json("""{"code":"mfa_challenge_expired"}""", 410))
        val vm = viewModel().signInWith(json(MFA_CHALLENGE_JSON)).verify("123456")

        assertNull(vm.uiState.value.pendingMFA)
        assertEquals("Your verification session has expired. Please sign in again.", vm.uiState.value.errorMessage)
    }

    @Test
    fun verify_lockedChallenge_returnsToLogin() {
        routes.on("v2/auth/mfa", json("""{"code":"mfa_challenge_locked"}""", 403))
        val vm = viewModel().signInWith(json(MFA_CHALLENGE_JSON)).verify("123456")

        assertNull(vm.uiState.value.pendingMFA)
        assertEquals("Too many attempts. Please sign in again.", vm.uiState.value.errorMessage)
    }

    @Test
    fun verify_invalidChallenge_returnsToLogin() {
        routes.on("v2/auth/mfa", json("""{"code":"invalid_mfa_challenge"}""", 401))
        val vm = viewModel().signInWith(json(MFA_CHALLENGE_JSON)).verify("123456")

        assertNull(vm.uiState.value.pendingMFA)
        assertEquals("Your verification session is no longer valid. Please sign in again.", vm.uiState.value.errorMessage)
    }

    @Test
    fun verify_rateLimited_keepsTheChallenge_andSetsTheDeadline() {
        routes.on("v2/auth/mfa", rateLimitedJson())
        val vm = viewModel().signInWith(json(MFA_CHALLENGE_JSON)).verify("123456")

        val state = vm.uiState.value
        assertEquals("mfa-1", state.pendingMFA?.mfaToken)
        assertEquals(FIXED_NOW.plus(Duration.ofMinutes(5)), state.loginRateLimitedUntil)
        assertEquals("", vm.mfaCode)
    }

    @Test
    fun cancelMFA_closesTheChallenge_withoutAnError() {
        val vm = viewModel().signInWith(json(MFA_CHALLENGE_JSON))
        vm.mfaCode = "12"

        vm.cancelMFA()

        assertNull(vm.uiState.value.pendingMFA)
        assertNull(vm.uiState.value.errorMessage)
        assertEquals("", vm.mfaCode)
    }

    // MARK: Backup-code alert

    @Test
    fun goToSecurity_closesTheAlert_andRequestsTheProfileTab_untilTheShellHandlesIt() {
        routes.on("v2/auth/mfa", json(SESSION_JSON.replace("}", ""","backupCodesRemaining":0}""")))
        val vm = viewModel().signInWith(json(MFA_CHALLENGE_JSON)).verify("abcd-efgh")

        vm.openSecurityFromBackupCodeAlert()
        assertNull(vm.uiState.value.backupCodeAlert)
        assertEquals(MainTab.Profile, vm.uiState.value.pendingTabSelection)

        vm.onTabRequestHandled()
        assertNull(vm.uiState.value.pendingTabSelection)
    }

    @Test
    fun backupCodeAlert_matchesIosWording_andOffersSecurityAtTwoOrFewer() {
        assertEquals(
            "That was your last backup code. If you lose your authenticator, you'll need to contact support to sign in.",
            BackupCodeAlert(0).message,
        )
        assertEquals(
            "You signed in with a backup code. 1 code left. Generate a new set so you don't get locked out.",
            BackupCodeAlert(1).message,
        )
        assertEquals(
            "You signed in with a backup code. 2 codes left. Generate a new set so you don't get locked out.",
            BackupCodeAlert(2).message,
        )
        assertEquals("You signed in with a backup code. 7 codes left.", BackupCodeAlert(7).message)
        assertTrue(BackupCodeAlert(2).offersSecurityLink)
        assertFalse(BackupCodeAlert(3).offersSecurityLink)
    }

    // MARK: Logout

    @Test
    fun logout_revokesTheSession_keepsDeviceTrust_andClearsTheFields() {
        net.signIn(refresh = "refresh-9")
        net.tokenStore.saveTrust("trust-1", "2026-11-07T00:00:00Z")
        routes.on("v2/auth/logout", status(204))
        val vm = viewModel()
        vm.loginEmail = "ada@example.com"

        runBlocking { vm.logout().join() }

        assertFalse(vm.uiState.value.isAuthenticated)
        assertNull(vm.uiState.value.errorMessage)
        assertNull(net.tokenStore.accessToken())
        assertEquals("trust-1", net.tokenStore.trustToken())
        assertEquals("", vm.loginEmail)
        assertTrue(routes.bodyOf("v2/auth/logout").contains("\"refreshToken\":\"refresh-9\""))
    }

    @Test
    fun logout_whenTheServerFails_stillSignsOut_andShowsTheError() {
        net.signIn()
        routes.on("v2/auth/logout", errorJson(500, "server_error", "Logout failed."))
        val vm = viewModel()

        runBlocking { vm.logout().join() }

        assertFalse(vm.uiState.value.isAuthenticated)
        assertNull(net.tokenStore.accessToken())
        assertEquals("Logout failed.", vm.uiState.value.errorMessage)
    }
}
