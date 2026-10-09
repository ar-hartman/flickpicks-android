package com.flykpicks.android.api

import com.flykpicks.android.api.endpoints.RecommendationsApi
import com.flykpicks.android.api.endpoints.SearchApi
import com.flykpicks.android.model.InventoryTitleDTO
import com.flykpicks.android.model.MediaItem
import com.flykpicks.android.model.PaginatedResponse
import com.flykpicks.android.model.auth.JWTLoginRequest
import com.flykpicks.android.model.auth.JWTLoginResponse
import com.flykpicks.android.model.auth.JWTMFAVerifyRequest
import com.flykpicks.android.model.auth.MFAResetRequestBody
import com.flykpicks.android.model.enums.MediaFormat
import com.flykpicks.android.model.enums.MembershipTier
import com.flykpicks.android.model.enums.OrderStatus
import com.flykpicks.android.model.profile.AddressDTO
import com.flykpicks.android.model.profile.MFAMethod
import com.flykpicks.android.model.profile.MFAStatusDTO
import com.flykpicks.android.model.profile.MembershipDTO
import com.flykpicks.android.model.profile.OrderDTO
import com.flykpicks.android.model.profile.OrderTimelineEntry
import com.flykpicks.android.model.profile.PaymentMethodDTO
import com.flykpicks.android.model.profile.PublicNameSource
import com.flykpicks.android.model.profile.SessionDTO
import com.flykpicks.android.model.profile.UpdateProfileRequest
import com.flykpicks.android.model.profile.UserProfileDTO
import com.flykpicks.android.testing.PROFILE_JSON
import com.flykpicks.android.testing.TestNetwork
import com.flykpicks.android.testing.json
import com.flykpicks.android.testing.movieJson
import com.flykpicks.android.testing.tvJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Types
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonWireFormatTest {

    private val moshi = ApiClient.moshi()
    private inline fun <reified T> encode(value: T): String = moshi.adapter(T::class.java).toJson(value)
    private inline fun <reified T> decode(body: String): T = moshi.adapter(T::class.java).fromJson(body)!!

    // MARK: Request encoding (keys + null omission must match iOS)

    @Test
    fun loginRequest_usesDeviceIdKey_andOmitsMissingTrustToken() {
        assertEquals(
            """{"email":"a@b.c","password":"pw","deviceID":"d1","deviceName":"Pixel"}""",
            encode(JWTLoginRequest("a@b.c", "pw", "d1", "Pixel")),
        )
        assertTrue(encode(JWTLoginRequest("a@b.c", "pw", "d1", "Pixel", "t1")).endsWith(""","trustToken":"t1"}"""))
    }

    @Test
    fun mfaVerifyRequest_sendsRememberDeviceOnlyWhenTrue() {
        assertEquals("""{"mfaToken":"m","code":"123456"}""", encode(JWTMFAVerifyRequest.of("m", "123456", false)))
        assertEquals(
            """{"mfaToken":"m","code":"123456","rememberDevice":true}""",
            encode(JWTMFAVerifyRequest.of("m", "123456", true)),
        )
    }

    @Test
    fun optionalFields_areOmitted() {
        assertEquals("""{"email":"a@b.c","password":"pw"}""", encode(MFAResetRequestBody("a@b.c", "pw")))
        assertEquals("""{"displayName":"ada"}""", encode(UpdateProfileRequest(displayName = "ada")))
        assertEquals(
            """{"publicNameSource":"FIRST_NAME"}""",
            encode(UpdateProfileRequest(publicNameSource = PublicNameSource.FIRST_NAME)),
        )
    }

    @Test
    fun address_mapsDefaultKey_andOmitsNullId() {
        val address = AddressDTO(
            fullName = "Ada", line1 = "1 Main", city = "SF", state = "CA",
            postalCode = "94105", country = "US", isDefault = true,
        )
        val encoded = encode(address)
        assertFalse(encoded, encoded.contains("\"id\""))
        assertTrue(encoded, encoded.contains("\"default\":true"))
        assertEquals(address.copy(id = "a1"), decode<AddressDTO>(encoded.replace("{", """{"id":"a1",""")))
        assertEquals("1 Main, SF, CA, 94105, US", address.formattedOneLine)
    }

    // MARK: Response decoding

    @Test
    fun loginResponse_decodesSessionAndChallengeLegs() {
        val session = decode<JWTLoginResponse>(
            """{"accessToken":"a","refreshToken":"r","userID":"u","mfaRequired":false}""",
        )
        assertEquals("u", session.userId)
        val challenge = decode<JWTLoginResponse>(
            """{"accessToken":null,"refreshToken":null,"userID":null,"mfaRequired":true,"mfaToken":"m"}""",
        )
        assertTrue(challenge.mfaRequired)
        assertEquals("m", challenge.mfaToken)
        assertNull(challenge.accessToken)
    }

    @Test
    fun renamedBooleanKeys_decode() {
        assertTrue(decode<SessionDTO>("""{"id":"s","deviceName":null,"lastActive":null,"current":true,"deviceId":null}""").isCurrent)
        assertTrue(
            decode<PaymentMethodDTO>(
                """{"id":"p","brand":"visa","last4":"4242","expiryMonth":3,"expiryYear":2031,"default":true}""",
            ).let { it.isDefault && it.displayExpiry == "03/31" },
        )
        assertTrue(decode<OrderTimelineEntry>("""{"id":"t","label":"Shipped","date":"2026-01-01","completed":true}""").isCompleted)
        val membership = decode<MembershipDTO>("""{"tier":"premium","price":9.99,"renewalDate":null,"canceled":true}""")
        assertTrue(membership.isCanceled)
        assertEquals(MembershipTier.PREMIUM, membership.membershipTier)
    }

    @Test
    fun unknownValues_fallBackLikeIos() {
        assertEquals(PublicNameSource.FIRST_NAME, decode<UserProfileDTO>(PROFILE_JSON).publicNameSource)
        assertEquals(
            PublicNameSource.DISPLAY_NAME,
            decode<UserProfileDTO>(PROFILE_JSON.replace("FIRST_NAME", "NICKNAME")).publicNameSource,
        )
        val order = decode<OrderDTO>(
            """{"id":"o","type":"LEASE","status":"LOST_IN_SPACE","itemTitle":"x","itemThumbnailUrl":null,
               "mediaFormat":"FOUR_K_ULTRA_HD","price":1.0,"orderDate":"d","trackingNumber":null}""",
        )
        assertEquals(OrderStatus.DEFAULT, order.orderStatus)
        assertEquals(MediaFormat.UHD_4K, order.formatEnum)
        assertEquals(
            MembershipTier.FREE,
            decode<MembershipDTO>("""{"tier":"platinum","price":0,"renewalDate":null,"canceled":false}""").membershipTier,
        )
    }

    @Test
    fun mfaStatus_matchesMethodTypesCaseInsensitively() {
        val status = decode<MFAStatusDTO>(
            """{"methods":[{"type":"TOTP","enabled":true,"primary":true,"enrolledAt":null},
                           {"type":"sms","enabled":false,"primary":false,"enrolledAt":null}],
               "backupCodesRemaining":8}""",
        )
        assertTrue(status.totpEnabled)
        assertFalse(status.smsEnabled)
        assertEquals(MFAMethod.TOTP, status.primaryMethod)
    }

    @Test
    fun paginatedInventory_decodes() {
        val type = Types.newParameterizedType(PaginatedResponse::class.java, InventoryTitleDTO::class.java)
        val page = moshi.adapter<PaginatedResponse<InventoryTitleDTO>>(type).fromJson(
            """{"items":[${com.flykpicks.android.model.INVENTORY_TV_JSON}],"page":1,"totalPages":1,"totalItems":1}""",
        )!!
        assertEquals(1, page.items.size)
        assertFalse(page.hasMore)
    }

    // MARK: MediaItem

    @Test
    fun mediaItem_decodesMovieAndTv() {
        assertTrue(decode<MediaItem>(movieJson()) is MediaItem.Movie)
        assertTrue(decode<MediaItem>(tvJson()) is MediaItem.Tv)
    }

    @Test
    fun mediaItem_fallsBackToShape_whenMediaTypeIsUnhelpful() {
        assertTrue(decode<MediaItem>(tvJson(mediaType = null)) is MediaItem.Tv)
    }

    @Test(expected = JsonDataException::class)
    fun mediaItem_rejectsNeitherShape() {
        decode<MediaItem>("""{"flyksId":"x"}""")
    }

    // MARK: Lenient lists (via Retrofit, proving method-level @Lenient reaches Moshi)

    @Test
    fun lenientEndpoints_skipMalformedItems() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val net = TestNetwork(server).apply { signIn() }
            server.enqueue(json("[${movieJson("m1")}, {\"flyksId\":\"broken\"}, ${movieJson("m2")}]"))
            server.enqueue(json("[${movieJson("m1")}, {\"oops\":true}, ${tvJson("t1")}]"))

            val movies = net.api<RecommendationsApi>().popularMovies()
            val search = net.api<SearchApi>().search("x")

            assertEquals(listOf("m1", "m2"), movies.map { it.flyksId })
            assertEquals(listOf("m1", "t1"), search.map { it.flyksId })
        }
    }
}
