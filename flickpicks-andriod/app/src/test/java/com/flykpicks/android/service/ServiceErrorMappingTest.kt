package com.flykpicks.android.service

import com.flykpicks.android.api.ApiError
import com.flykpicks.android.model.enums.MembershipTier
import com.flykpicks.android.model.profile.MFAError
import com.flykpicks.android.model.profile.MFAMethod
import com.flykpicks.android.model.profile.MembershipError
import com.flykpicks.android.model.profile.OrderCreationType
import com.flykpicks.android.model.profile.OrderError
import com.flykpicks.android.testing.TestNetwork
import com.flykpicks.android.testing.json
import com.flykpicks.android.testing.status
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Per-endpoint status → typed error mapping, mirroring the iOS services. */
class ServiceErrorMappingTest {

    private lateinit var server: MockWebServer
    private lateinit var net: TestNetwork

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        net = TestNetwork(server).apply { signIn() }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun failure(block: suspend () -> Unit): Throwable? = runBlocking { runCatching { block() }.exceptionOrNull() }

    @Test
    fun mfaDisable_distinguishesLastMethodByMessage() {
        val security = SecurityService(net.api())
        server.enqueue(json("""{"code":"bad_request","message":"Cannot disable the last method"}""", 400))
        assertEquals(MFAError.DisableLastMethod, failure { security.disable(MFAMethod.TOTP, "pw") })
        assertEquals("/base/v1/users/me/mfa/totp/disable", server.takeRequest(5, TimeUnit.SECONDS)!!.url.encodedPath)

        server.enqueue(json("""{"code":"bad_request","message":"Password incorrect"}""", 400))
        assertEquals(MFAError.DisableWrongPassword, failure { security.disable(MFAMethod.SMS, "pw") })
        server.enqueue(status(404))
        assertEquals(MFAError.DisableNotEnrolled, failure { security.disable(MFAMethod.EMAIL, "pw") })
    }

    @Test
    fun smsResend_mapsNotFoundAndRateLimit() {
        val security = SecurityService(net.api())
        server.enqueue(status(404))
        assertEquals(MFAError.SmsVerificationNotFound, failure { security.resendSMS("v1") })
        server.enqueue(status(429))
        assertEquals(MFAError.SmsResendRateLimited, failure { security.resendSMS("v1") })
    }

    @Test
    fun stopTrusting_treatsMissingDeviceAsSuccess() {
        val security = SecurityService(net.api())
        server.enqueue(status(404))
        assertNull(failure { security.stopTrusting("d1") })
        server.enqueue(status(500))
        assertTrue(failure { security.stopTrusting("d1") } is ApiError.Http)
    }

    @Test
    fun revokeAllOtherSessions_sendsExceptCurrent() {
        server.enqueue(status(204))
        runBlocking { SecurityService(net.api()).revokeAllOtherSessions() }
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("DELETE", request.method)
        assertEquals("true", request.url.queryParameter("exceptCurrent"))
    }

    @Test
    fun createOrder_mapsConflictMessages() {
        val orders = OrderService(net.api())
        server.enqueue(json("""{"code":"conflict","message":"Rental cap reached"}""", 409))
        assertEquals(OrderError.RentalCapReached, failure { orders.createOrder("l1", OrderCreationType.RENTAL) })
        server.enqueue(status(404))
        assertEquals(OrderError.NotFound, failure { orders.createOrder("l1", OrderCreationType.PURCHASE) })
    }

    @Test
    fun membership_sendsLowercaseTier_andMapsErrors() {
        val membership = MembershipService(net.api())
        server.enqueue(status(400))
        assertEquals(MembershipError.InvalidTier, failure { membership.changePlan(MembershipTier.PREMIUM) })
        assertEquals("""{"newTier":"premium"}""", server.takeRequest(5, TimeUnit.SECONDS)!!.body!!.utf8())
        server.enqueue(status(409))
        assertEquals(MembershipError.AlreadyCanceled, failure { membership.cancelMembership() })
    }

    @Test
    fun payments_mapRemovalErrors() {
        val payments = PaymentService(net.api())
        server.enqueue(status(400))
        assertEquals(PaymentError.LastPaymentMethod, failure { payments.removePaymentMethod("p1") })
        server.enqueue(status(502))
        assertEquals(PaymentError.ProcessorUnavailable, failure { payments.removePaymentMethod("p1") })
    }

    @Test
    fun barcodeLookup_returnsNullForUnknownUpc() {
        server.enqueue(status(404))
        assertNull(runBlocking { BarcodeService(net.api(), net.genres).lookupBarcode("0123") })
        assertEquals("search.barcode", server.takeRequest(5, TimeUnit.SECONDS)!!.headers["X-Flyk-Source"])
    }

    @Test
    fun library_sendsPayloadKeysAndSourceHeader() {
        server.enqueue(json("[]"))
        runBlocking {
            LibraryService(net.api(), net.genres).updateItem(
                "u1",
                com.flykpicks.android.model.enums.MediaFormat.UHD_4K,
                com.flykpicks.android.model.enums.ItemCondition.LIKE_NEW,
            )
        }
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("PUT", request.method)
        assertEquals("library.detail", request.headers["X-Flyk-Source"])
        assertEquals(
            """{"mediaInventoryItemID":"u1","format":"FOUR_K_ULTRA_HD","condition":"LIKE_NEW"}""",
            request.body!!.utf8(),
        )
    }

    @Test
    fun genres_mergeMovieAndTvTables() = runBlocking {
        server.enqueue(json("""{"movie":{"28":"Action"},"tv":{"10759":"Action & Adventure","bad":"x"}}"""))
        GenreService(net.api(), net.genres).load()
        assertEquals("Action • Action & Adventure", net.genres.joinedNames(listOf(28, 10759)))
    }
}
