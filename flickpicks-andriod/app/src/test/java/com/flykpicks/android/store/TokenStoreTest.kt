package com.flykpicks.android.store

import com.flykpicks.android.testing.InMemorySecureStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenStoreTest {

    private val store = TokenStore(InMemorySecureStore())

    @Test
    fun clear_removesSession_butKeepsDeviceTrust() {
        store.save("a", "r", "u")
        store.saveTrust("t", "2027-01-01T00:00:00Z")

        store.clear()

        assertNull(store.accessToken())
        assertNull(store.refreshToken())
        assertNull(store.userId())
        assertEquals("t", store.trustToken())
        assertEquals("2027-01-01T00:00:00Z", store.trustExpiresAt())
    }

    @Test
    fun clearTrust_keepsSession() {
        store.save("a", "r", "u")
        store.saveTrust("t", "x")

        store.clearTrust()

        assertNull(store.trustToken())
        assertNull(store.trustExpiresAt())
        assertEquals("a", store.accessToken())
    }

    @Test
    fun save_replacesAllSessionValues() {
        store.save("a1", "r1", "u1")
        store.save("a2", "r2", "u2")

        assertEquals("a2", store.accessToken())
        assertEquals("r2", store.refreshToken())
        assertEquals("u2", store.userId())
    }
}
