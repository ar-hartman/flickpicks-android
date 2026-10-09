package com.flykpicks.android.store

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.core.content.edit
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Small encrypted key-value store for secrets (tokens). */
interface SecureStore {
    fun getString(key: String): String?

    /** Applies every entry in one atomic edit; a null value removes the key. */
    fun putAll(values: Map<String, String?>)
}

/**
 * [SecureStore] backed by SharedPreferences, with each value encrypted using AES-256-GCM under a
 * non-exportable Android Keystore key (plan D3 — replaces the deprecated EncryptedSharedPreferences).
 *
 * - The preference key is bound in as associated data, so ciphertexts can't be swapped between keys.
 * - Values that fail to decrypt (e.g. the Keystore key was lost) are dropped and read as absent.
 * - The prefs file is excluded from backup and device transfer (res/xml backup rules): restored
 *   ciphertext could never be decrypted on another device.
 */
class KeystoreSecureStore(
    context: Context,
    prefsName: String = PREFS_NAME,
    private val keyAlias: String = KEY_ALIAS,
) : SecureStore {

    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val cache = HashMap<String, String?>()
    private var secretKey: SecretKey? = null

    @Synchronized
    override fun getString(key: String): String? {
        if (cache.containsKey(key)) return cache[key]
        val value = prefs.getString(key, null)?.let { decrypt(key, it) }
        cache[key] = value
        return value
    }

    @Synchronized
    override fun putAll(values: Map<String, String?>) {
        prefs.edit {
            for ((key, value) in values) {
                if (value == null) remove(key) else putString(key, encrypt(key, value))
            }
        }
        cache.putAll(values)
    }

    private fun encrypt(key: String, plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(key.toByteArray())
        val ciphertext = cipher.doFinal(plaintext.toByteArray())
        return Base64.getEncoder().encodeToString(cipher.iv + ciphertext)
    }

    private fun decrypt(key: String, encoded: String): String? =
        try {
            val bytes = Base64.getDecoder().decode(encoded)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(TAG_LENGTH_BITS, bytes, 0, IV_LENGTH_BYTES),
            )
            cipher.updateAAD(key.toByteArray())
            String(cipher.doFinal(bytes, IV_LENGTH_BYTES, bytes.size - IV_LENGTH_BYTES))
        } catch (e: GeneralSecurityException) {
            discard(key, e)
        } catch (e: IllegalArgumentException) {
            discard(key, e)
        }

    private fun discard(key: String, cause: Exception): String? {
        Log.w(TAG, "Dropping unreadable value for '$key'", cause)
        prefs.edit { remove(key) }
        return null
    }

    private fun key(): SecretKey = secretKey ?: loadOrCreateKey().also { secretKey = it }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        /** Referenced by res/xml/backup_rules.xml and data_extraction_rules.xml. */
        const val PREFS_NAME = "flykpicks_secure_prefs"
        private const val KEY_ALIAS = "flykpicks_secure_prefs_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BITS = 128
        private const val TAG = "KeystoreSecureStore"
    }
}
