package com.flykpicks.android.ui.screens.auth

import com.flykpicks.android.service.AuthService
import com.flykpicks.android.testing.FIXED_NOW
import com.flykpicks.android.testing.Routes
import com.flykpicks.android.testing.TestNetwork
import com.flykpicks.android.testing.errorJson
import com.flykpicks.android.testing.json
import com.flykpicks.android.testing.rateLimitedJson
import com.flykpicks.android.testing.status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration

/** Registration, password recovery and the MFA reset request, against the production stack. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthFlowViewModelsTest {

    private lateinit var server: MockWebServer
    private lateinit var net: TestNetwork
    private lateinit var auth: AuthService
    private val routes = Routes()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer().apply { dispatcher = routes; start() }
        net = TestNetwork(server)
        auth = AuthService(net.api(), net.tokenStore, net.device, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        server.close()
        Dispatchers.resetMain()
    }

    private fun Job.await() = runBlocking { join() }

    // MARK: Registration

    private fun registration() = RegistrationViewModel(auth, net.clock).apply {
        email = " Ada@Example.com "
        password = "longenough"
        confirmPassword = "longenough"
    }

    @Test
    fun register_sendsLowercasedEmail_andShowsCheckYourEmail_withoutSigningIn() {
        routes.on("v1/users/register", json("""{"message":"Check your inbox."}""", 201))
        val vm = registration()

        vm.register().await()

        assertEquals("ada@example.com", vm.registeredEmail)
        assertEquals("Check your inbox.", vm.registeredMessage)
        assertTrue(routes.bodyOf("v1/users/register").contains("\"email\":\"ada@example.com\""))
        assertNull(net.tokenStore.accessToken())
    }

    @Test
    fun register_withAnInvalidForm_sendsNothing() {
        val badEmail = registration().apply { email = "ada@example" }
        val shortPassword = registration().apply { password = "short"; confirmPassword = "short" }
        val mismatch = registration().apply { confirmPassword = "different!" }

        listOf(badEmail, shortPassword, mismatch).forEach {
            assertFalse(it.isFormValid)
            it.register().await()
        }

        assertTrue(routes.requestsTo("v1/users/register").isEmpty())
    }

    @Test
    fun register_rejected_showsTheServerMessage() {
        routes.on("v1/users/register", errorJson(400, "invalid_input", "That email domain isn't accepted."))
        val vm = registration()

        vm.register().await()

        assertNull(vm.registeredEmail)
        assertEquals("That email domain isn't accepted.", vm.generalError)
    }

    @Test
    fun register_rateLimited_blocksFurtherAttempts_untilTheDeadline() {
        routes.on("v1/users/register", rateLimitedJson("Too many sign-ups."))
        val vm = registration()

        vm.register().await()
        vm.register().await()

        assertEquals(FIXED_NOW.plus(Duration.ofMinutes(5)), vm.rateLimitedUntil)
        assertEquals("Too many sign-ups.", vm.generalError)
        assertEquals(1, routes.requestsTo("v1/users/register").size)
    }

    @Test
    fun resend_confirms_orCountsDownWhenRateLimited() {
        routes.on("v1/users/register", json("""{"message":"Check your inbox."}""", 201))
        routes.on("v1/users/verify-email/resend", json("""{"message":"Sent again."}""", 202), rateLimitedJson("Wait a bit."))
        val vm = registration()
        vm.register().await()

        vm.resend().await()
        assertEquals("Sent again.", vm.resendConfirmation)

        vm.resend().await()
        assertNull(vm.resendConfirmation)
        assertEquals("Wait a bit.", vm.resendError)
        assertEquals(FIXED_NOW.plus(Duration.ofMinutes(5)), vm.resendBlockedUntil)
    }

    // MARK: Password recovery

    private fun recovery() = ForgotPasswordViewModel(auth, net.clock).apply { email = " Ada@Example.com " }

    private fun ForgotPasswordViewModel.atCodeStep() = apply {
        routes.on("v1/users/getOneTimePassword", json("""{"expiresAt":"2026-10-07T21:10:00Z"}"""))
        requestCode().await()
    }

    private fun ForgotPasswordViewModel.enter(code: String) = apply {
        onCodeChange(code)?.await()
    }

    @Test
    fun requestCode_movesToTheCodeStep_withTheNormalizedEmail() {
        val vm = recovery().atCodeStep()

        assertEquals(RecoveryStep.VerifyCode, vm.step)
        assertEquals("ada@example.com", vm.sentTo)
        assertTrue(routes.bodyOf("v1/users/getOneTimePassword").contains("\"subjectId\":\"ada@example.com\""))
    }

    @Test
    fun requestCode_rateLimited_staysOnTheEmailStep() {
        routes.on("v1/users/getOneTimePassword", rateLimitedJson("Slow down."))
        val vm = recovery()

        vm.requestCode().await()

        assertEquals(RecoveryStep.RequestCode, vm.step)
        assertEquals("Slow down.", vm.requestError)
        assertEquals(FIXED_NOW.plus(Duration.ofMinutes(5)), vm.rateLimitedUntil)
    }

    @Test
    fun sixthDigit_submitsTheCode_andAValidOne_leadsToNewPassword() {
        routes.on("v1/users/verifyOtp", json("""{"valid":true,"recoveryToken":"rt-1"}"""))
        val vm = recovery().atCodeStep()

        vm.onCodeChange("12345")
        assertTrue("five digits don't submit", routes.requestsTo("v1/users/verifyOtp").isEmpty())
        vm.enter("123456")

        assertEquals(RecoveryStep.NewPassword, vm.step)
        assertEquals(1, routes.requestsTo("v1/users/verifyOtp").size)
    }

    @Test
    fun wrongCode_clearsTheBoxes() {
        routes.on("v1/users/verifyOtp", status(400))
        val vm = recovery().atCodeStep().enter("123456")

        assertEquals(RecoveryStep.VerifyCode, vm.step)
        assertEquals("Code is incorrect.", vm.codeError)
        assertEquals("", vm.code)
    }

    @Test
    fun expiredCode_keepsTheDigits_andSaysToRequestANewOne() {
        routes.on("v1/users/verifyOtp", status(401))
        val vm = recovery().atCodeStep().enter("123456")

        assertEquals("Code has expired. Request a new one.", vm.codeError)
        assertEquals("123456", vm.code)
    }

    @Test
    fun tooManyAttempts_locksTheCodeStep() {
        routes.on("v1/users/verifyOtp", rateLimitedJson())
        val vm = recovery().atCodeStep().enter("123456")

        assertTrue(vm.isLocked)
    }

    @Test
    fun validResponseWithoutAToken_isAFailure() {
        routes.on("v1/users/verifyOtp", json("""{"valid":true}"""))
        val vm = recovery().atCodeStep().enter("123456")

        assertEquals(RecoveryStep.VerifyCode, vm.step)
        assertEquals("Verification failed. Please try again.", vm.codeError)
        assertEquals("", vm.code)
    }

    @Test
    fun newPassword_isSentWithTheRecoveryToken_thenDone() {
        routes.on("v1/users/verifyOtp", json("""{"valid":true,"recoveryToken":"rt-1"}"""))
        routes.on("v1/users/changeLostPassword", status(200))
        val vm = recovery().atCodeStep().enter("123456")
        vm.newPassword = "brand new pw"
        vm.confirmPassword = "brand new pw"

        vm.resetPassword().await()

        assertEquals(RecoveryStep.Done, vm.step)
        val body = routes.bodyOf("v1/users/changeLostPassword")
        assertTrue(body, body.contains("\"recoveryToken\":\"rt-1\"") && body.contains("\"email\":\"ada@example.com\""))
    }

    @Test
    fun rejectedRecoveryToken_saysToRestart() {
        routes.on("v1/users/verifyOtp", json("""{"valid":true,"recoveryToken":"rt-1"}"""))
        routes.on("v1/users/changeLostPassword", status(401))
        val vm = recovery().atCodeStep().enter("123456")
        vm.newPassword = "brand new pw"
        vm.confirmPassword = "brand new pw"

        vm.resetPassword().await()

        assertEquals(RecoveryStep.NewPassword, vm.step)
        assertEquals("Recovery token is no longer valid. Please restart.", vm.resetError)
    }

    @Test
    fun back_walksTheSteps_andLeavesFromTheEnds() {
        routes.on("v1/users/verifyOtp", json("""{"valid":true,"recoveryToken":"rt-1"}"""))
        val vm = recovery()
        assertFalse(vm.back())

        vm.atCodeStep().enter("123456")
        assertEquals(RecoveryStep.NewPassword, vm.step)

        assertTrue(vm.back())
        assertEquals(RecoveryStep.VerifyCode, vm.step)
        assertEquals("", vm.code)
        assertTrue(vm.back())
        assertEquals(RecoveryStep.RequestCode, vm.step)
        assertEquals("the email is kept for a resend", " Ada@Example.com ", vm.email)
    }

    // MARK: MFA reset request

    @Test
    fun mfaReset_alwaysConfirmsTheSame_andOmitsAnEmptyNote() {
        routes.on("v2/auth/mfa/reset-request", json("""{"message":"Accepted"}""", 202))
        val vm = MFAResetViewModel(auth)
        vm.onPasswordChange("pw")

        vm.submit("ada@example.com").await()

        assertTrue(vm.submitted)
        assertFalse(routes.bodyOf("v2/auth/mfa/reset-request").contains("note"))
    }

    @Test
    fun mfaReset_rateLimited_showsTheMessage() {
        routes.on("v2/auth/mfa/reset-request", rateLimitedJson("Try later."))
        val vm = MFAResetViewModel(auth)
        vm.onPasswordChange("pw")

        vm.submit("ada@example.com").await()

        assertFalse(vm.submitted)
        assertEquals("Try later.", vm.errorMessage)
    }

    @Test
    fun mfaReset_noteIsCappedAt500Characters() {
        val vm = MFAResetViewModel(auth)

        vm.onNoteChange("x".repeat(600))

        assertEquals(500, vm.note.length)
    }
}
