package com.flykpicks.android.store

import com.flykpicks.android.service.FeatureFlagService
import com.flykpicks.android.testing.InMemoryFeatureFlagCache
import com.flykpicks.android.testing.TestNetwork
import com.flykpicks.android.testing.json
import com.flykpicks.android.testing.status
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FeatureFlagStoreTest {

    private lateinit var server: MockWebServer
    private lateinit var net: TestNetwork

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        net = TestNetwork(server)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun store(cache: FeatureFlagCache) = FeatureFlagStore(FeatureFlagService(net.api()), cache, net.moshi)

    @Test
    fun startsFromTheCache_soSignedOutScreensSeeTheLastKnownFlags() {
        val store = store(InMemoryFeatureFlagCache("""{"accountRecoveryEnabled":true}"""))

        assertTrue(store.accountRecoveryEnabled)
    }

    @Test
    fun missingKeys_andAnEmptyOrCorruptCache_readAsOff() {
        assertFalse(store(InMemoryFeatureFlagCache()).accountRecoveryEnabled)
        assertFalse(store(InMemoryFeatureFlagCache("not json")).isEnabled("profile_referral_enabled"))
    }

    @Test
    fun load_replacesTheFlags_andCachesThem() = runBlocking {
        val cache = InMemoryFeatureFlagCache("""{"accountRecoveryEnabled":true}""")
        server.enqueue(json("""{"flags":{"accountRecoveryEnabled":false,"profile_referral_enabled":true}}"""))
        val store = store(cache)

        store.load()

        assertFalse(store.accountRecoveryEnabled)
        assertTrue(store.isEnabled("profile_referral_enabled"))
        assertEquals(store.flags.value, store(cache).flags.value)
    }

    @Test
    fun failedLoad_keepsTheCurrentFlags() = runBlocking {
        server.enqueue(status(500))
        val store = store(InMemoryFeatureFlagCache("""{"accountRecoveryEnabled":true}"""))

        store.load()

        assertTrue(store.accountRecoveryEnabled)
    }
}
