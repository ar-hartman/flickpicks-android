package com.flykpicks.android.di

import com.flykpicks.android.service.AccountInsightsService
import com.flykpicks.android.service.AddressService
import com.flykpicks.android.service.AssetService
import com.flykpicks.android.service.AuthService
import com.flykpicks.android.service.BarcodeService
import com.flykpicks.android.service.DisputeService
import com.flykpicks.android.service.FeatureFlagService
import com.flykpicks.android.service.GenreService
import com.flykpicks.android.service.LibraryService
import com.flykpicks.android.service.MembershipService
import com.flykpicks.android.service.NotificationSettingsService
import com.flykpicks.android.service.OrderService
import com.flykpicks.android.service.PaymentService
import com.flykpicks.android.service.ProfileService
import com.flykpicks.android.service.RecommendationsService
import com.flykpicks.android.service.SearchService
import com.flykpicks.android.service.SecurityService
import com.flykpicks.android.service.TelemetryService
import com.flykpicks.android.store.GenreStore
import com.flykpicks.android.store.LibraryStore
import com.flykpicks.android.store.PerUserState
import com.flykpicks.android.store.RecommendationsStore
import com.flykpicks.android.store.SearchStore
import com.flykpicks.android.store.TokenStore
import com.flykpicks.android.util.DeviceIdProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Builds every service from the real Hilt graph on a device: proves all bindings resolve and
 * the networking stack, Keystore-backed TokenStore and device id initialise at runtime.
 */
@HiltAndroidTest
class DependencyGraphTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var auth: AuthService
    @Inject lateinit var profile: ProfileService
    @Inject lateinit var security: SecurityService
    @Inject lateinit var library: LibraryService
    @Inject lateinit var search: SearchService
    @Inject lateinit var recommendations: RecommendationsService
    @Inject lateinit var asset: AssetService
    @Inject lateinit var genres: GenreService
    @Inject lateinit var barcode: BarcodeService
    @Inject lateinit var orders: OrderService
    @Inject lateinit var payments: PaymentService
    @Inject lateinit var disputes: DisputeService
    @Inject lateinit var membership: MembershipService
    @Inject lateinit var addresses: AddressService
    @Inject lateinit var notifications: NotificationSettingsService
    @Inject lateinit var flags: FeatureFlagService
    @Inject lateinit var insights: AccountInsightsService
    @Inject lateinit var telemetry: TelemetryService
    @Inject lateinit var tokenStore: TokenStore
    @Inject lateinit var device: DeviceIdProvider
    @Inject lateinit var genreStore: GenreStore
    @Inject lateinit var libraryStore: LibraryStore
    @Inject lateinit var recommendationsStore: RecommendationsStore
    @Inject lateinit var searchStore: SearchStore
    @Inject lateinit var perUserState: Set<@JvmSuppressWildcards PerUserState>

    @Before
    fun inject() {
        hilt.inject()
    }

    @Test
    fun graphResolves_andSecureStorageWorksOnDevice() {
        tokenStore.save("a", "r", "u")
        assertEquals("a", tokenStore.accessToken())
        tokenStore.clear()
        assertTrue(!auth.hasSession)
        assertTrue(device.deviceId.isNotBlank())
        assertTrue(device.deviceName.contains("Android"))
    }

    @Test
    fun everyPerUserStore_isInTheSetThatAChangeOfSessionResets() {
        assertEquals(setOf(libraryStore, recommendationsStore, searchStore), perUserState)
    }
}
