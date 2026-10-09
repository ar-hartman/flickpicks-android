package com.flykpicks.android.store

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Android Keystore, so it runs on a device or emulator. */
@RunWith(AndroidJUnit4::class)
class KeystoreSecureStoreTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefsName = "keystore_secure_store_test"
    private val keyAlias = "keystore_secure_store_test_key"
    private val prefs get() = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private fun newStore() = KeystoreSecureStore(context, prefsName, keyAlias)

    @Before
    @After
    fun reset() {
        prefs.edit().clear().commit()
    }

    @Test
    fun roundTrips_andPersistsAcrossInstances() {
        newStore().putAll(mapOf("access_token" to "secret-1", "user_id" to "u1"))

        val reopened = newStore()

        assertEquals("secret-1", reopened.getString("access_token"))
        assertEquals("u1", reopened.getString("user_id"))
    }

    @Test
    fun valuesAreNotStoredInPlaintext() {
        newStore().putAll(mapOf("access_token" to "secret-1"))

        val raw = prefs.getString("access_token", null)

        assertNotEquals("secret-1", raw)
        assertEquals(false, raw.orEmpty().contains("secret-1"))
    }

    @Test
    fun nullRemoves() {
        val store = newStore()
        store.putAll(mapOf("access_token" to "secret-1"))

        store.putAll(mapOf("access_token" to null))

        assertNull(store.getString("access_token"))
        assertNull(newStore().getString("access_token"))
    }

    @Test
    fun tamperedValue_readsAsAbsent_andIsDropped() {
        newStore().putAll(mapOf("access_token" to "secret-1"))
        prefs.edit().putString("access_token", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA").commit()

        assertNull(newStore().getString("access_token"))
        assertNull(prefs.getString("access_token", null))
    }

    @Test
    fun ciphertextIsBoundToItsKey() {
        newStore().putAll(mapOf("access_token" to "secret-1"))
        val ciphertext = prefs.getString("access_token", null)
        prefs.edit().putString("refresh_token", ciphertext).commit()

        assertNull(newStore().getString("refresh_token"))
    }
}
