package com.flykpicks.android.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Identifies this install to the auth endpoints (sessions, refresh, device trust). */
interface DeviceIdProvider {
    /** Stable across launches and reinstalls (same signing key + user). */
    val deviceId: String

    /** Human-readable label shown in the Active Sessions / Trusted Devices lists. */
    val deviceName: String
}

// ANDROID_ID is used deliberately; see [deviceId].
@SuppressLint("HardwareIds")
@Singleton
class AndroidDeviceIdProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceIdProvider {

    /**
     * `ANDROID_ID` is scoped to the app signing key and user and needs no permission (plan D4).
     * It survives reinstalls, like the Keychain-stored id on iOS. A random id is kept as a
     * fallback for the rare device that reports none.
     */
    override val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: fallbackId()
    }

    /** e.g. "Google Pixel 9 (Android 17)"; mirrors iOS "iPhone (iOS 18.1)". */
    override val deviceName: String by lazy {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.ROOT) }
        val model = if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) {
            Build.MODEL
        } else {
            "$manufacturer ${Build.MODEL}"
        }
        "$model (Android ${Build.VERSION.RELEASE})"
    }

    private fun fallbackId(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        return UUID.randomUUID().toString().also { prefs.edit { putString(KEY_DEVICE_ID, it) } }
    }

    private companion object {
        const val PREFS_NAME = "flykpicks_device"
        const val KEY_DEVICE_ID = "device_id"
    }
}
