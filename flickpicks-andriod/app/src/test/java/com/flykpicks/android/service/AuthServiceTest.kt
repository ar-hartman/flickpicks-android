package com.flykpicks.android.service

import com.flykpicks.android.model.auth.AuthError
import com.flykpicks.android.model.auth.LoginOutcome
import com.flykpicks.android.testing.TestNetwork
import com.flykpicks.android.testing.json
import com.flykpicks.android.testing.status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class AuthServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var net: TestNetwork
    private lateinit var auth: AuthService

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        net = TestNetwork(server)
        auth = AuthService(net.api(), net.tokenStore, net.device, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun takeBody(): String = takeRequest().body!!.utf8()
    private fun takeRequest(): RecordedRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
    private inline fun <reified T : Throwable> failure(crossinline block: suspend () -> Unit): T =
        runBlocking { runCatching { block() }.exceptionOrNull() as T }

    // MARK: Login

    @Test
    fun login_session_persistsTokens_andSendsDeviceInfo() = runBlocking {
        server.enqueue(json("""{"accessToken":"a","refreshToken":"r","userID":"u1","mfaRequired":false}"""))

        val outcome = auth.login("Ada@Example.com", "pw")

        assertEquals(LoginOutcome.Session, outcome)
        assertEquals("a", net.tokenStore.accessToken())
        assertEquals("u1", net.tokenStore.userId())
        val body = takeBody()
        assertTrue(body, body.contains("\"deviceID\":\"device-123\""))
        assertTrue(body, body.contains("\"deviceName\":\"Google Pixel 9 (Android 17)\""))
        assertFalse("no trust token stored, so none sent", body.contains("trustToken"))
    }

    @Test
    fun login_sendsStoredTrust_andKeepsItForSameUser() = runBlocking {
        net.tokenStore.save("old", "old", "u1")
        net.tokenStore.saveTrust("trust-1", "2027-01-01T00:00:00Z")
        server.enqueue(json("""{"accessToken":"a","refreshToken":"r","userID":"u1","mfaRequired":false}"""))

        auth.login("ada@example.com", "pw")

        assertTrue(takeBody().contains("\"trustToken\":\"trust-1\""))
        assertEquals("trust-1", net.tokenStore.trustToken())
    }

    @Test
    fun login_asDifferentUser_dropsPreviousUsersTrust() = runBlocking {
        net.tokenStore.save("old", "old", "u1")
        net.tokenStore.saveTrust("trust-1", "2027-01-01T00:00:00Z")
        server.enqueue(json("""{"accessToken":"a","refreshToken":"r","userID":"u2","mfaRequired":false}"""))

        auth.login("other@example.com", "pw")

        assertNull(net.tokenStore.trustToken())
        assertEquals("u2", net.tokenStore.userId())
    }

    @Test
    fun login_mfaChallenge_returnsToken_withoutPersistingSession_andBurnsIgnoredTrust() = runBlocking {
        net.tokenStore.saveTrust("trust-1", "2027-01-01T00:00:00Z")
        server.enqueue(json("""{"mfaRequired":true,"mfaToken":"mfa-1"}"""))

        val outcome = auth.login("ada@example.com", "pw")

        assertEquals(LoginOutcome.MFAChallenge("mfa-1"), outcome)
        assertNull(net.tokenStore.accessToken())
        assertNull(net.tokenStore.trustToken())
    }

    @Test
    fun login_mfaRequiredWithoutToken_isInvalidChallenge() {
        server.enqueue(json("""{"mfaRequired":true}"""))
        assertEquals(AuthError.InvalidMFAChallenge(null), failure<AuthError> { auth.login("a@b.c", "pw") })
    }

    @Test
    fun login_401_isInvalidCredentials_withServerMessage() {
        server.enqueue(json("""{"code":"bad_credentials","message":"Nope."}""", code = 401))
        assertEquals(AuthError.InvalidCredentials("Nope."), failure<AuthError> { auth.login("a@b.c", "pw") })
    }

    // MARK: MFA verify

    @Test
    fun verifyMFA_persistsSessionAndTrust_andReportsBackupCodes() = runBlocking {
        server.enqueue(
            json(
                """{"accessToken":"a","refreshToken":"r","userID":"u1","mfaRequired":false,
                   "trustToken":"trust-9","trustExpiresAt":"2027-01-01T00:00:00Z","backupCodesRemaining":3}""",
            ),
        )

        val remaining = auth.verifyMFA("mfa-1", "123456", rememberDevice = true)

        assertEquals(3, remaining)
        assertEquals("a", net.tokenStore.accessToken())
        assertEquals("trust-9", net.tokenStore.trustToken())
        assertTrue(takeBody().contains("\"rememberDevice\":true"))
    }

    @Test
    fun verifyMFA_mapsErrorCodes() {
        fun verifyFailingWith(code: Int, body: String): AuthError {
            server.enqueue(json(body, code))
            return failure { auth.verifyMFA("mfa-1", "000000", rememberDevice = false) }
        }
        assertEquals(
            AuthError.InvalidMFACode("Wrong code"),
            verifyFailingWith(401, """{"code":"invalid_mfa_code","message":"Wrong code"}"""),
        )
        assertEquals(
            AuthError.InvalidMFAChallenge(null),
            verifyFailingWith(401, """{"code":"invalid_mfa_challenge"}"""),
        )
        assertEquals(
            AuthError.MFAChallengeLocked(null),
            verifyFailingWith(403, """{"code":"mfa_challenge_locked"}"""),
        )
        assertEquals(
            AuthError.MFAChallengeExpired(null),
            verifyFailingWith(410, """{"code":"mfa_challenge_expired"}"""),
        )
    }

    // MARK: Registration & recovery

    @Test
    fun register_lowercasesEmail_andMaps400ToInvalidInput() {
        server.enqueue(json("""{"message":"Check your inbox"}"""))
        runBlocking { auth.register("Ada@Example.COM", "pw") }
        assertTrue(takeBody().contains("\"email\":\"ada@example.com\""))

        server.enqueue(json("""{"code":"weak_password","message":"Too short"}""", code = 400))
        assertEquals(AuthError.InvalidInput("Too short"), failure<AuthError> { auth.register("a@b.c", "x") })
    }

    @Test
    fun verifyOtp_mapsStatuses_includingRateLimitToLocked() {
        server.enqueue(status(400))
        assertEquals(AuthError.OTPInvalid, failure<AuthError> { auth.verifyOneTimePassword("a@b.c", "1") })
        server.enqueue(status(401))
        assertEquals(AuthError.OTPExpired, failure<AuthError> { auth.verifyOneTimePassword("a@b.c", "1") })
        server.enqueue(status(429))
        assertEquals(AuthError.OTPLocked, failure<AuthError> { auth.verifyOneTimePassword("a@b.c", "1") })
    }

    @Test
    fun changeLostPassword_toleratesEmptyBody_andMapsErrors() {
        server.enqueue(status(204))
        runBlocking { auth.changeLostPassword("A@B.C", "new-pw", "rt") }
        assertTrue(takeBody().contains("\"email\":\"a@b.c\""))

        server.enqueue(status(401))
        assertEquals(
            AuthError.RecoveryTokenInvalid,
            failure<AuthError> { auth.changeLostPassword("a@b.c", "new-pw", "rt") },
        )
    }

    // MARK: Logout

    @Test
    fun logout_revokesRefreshToken_andClearsSessionButKeepsTrust() = runBlocking {
        net.signIn(refresh = "refresh-1")
        net.tokenStore.saveTrust("trust-1", "2027-01-01T00:00:00Z")
        server.enqueue(status(204))

        auth.logout()

        val request = takeRequest()
        assertNull("logout is unauthenticated", request.headers["Authorization"])
        assertTrue(request.body!!.utf8().contains("\"refreshToken\":\"refresh-1\""))
        assertNull(net.tokenStore.accessToken())
        assertEquals("trust-1", net.tokenStore.trustToken())
    }

    @Test
    fun logout_clearsSessionEvenWhenServerFails() {
        net.signIn()
        server.enqueue(status(500))

        runBlocking { runCatching { auth.logout() } }

        assertNull(net.tokenStore.accessToken())
        assertFalse(auth.hasSession)
    }
}
