package com.flykpicks.android.testing

import com.flykpicks.android.store.SecureStore
import com.flykpicks.android.util.DeviceIdProvider

class InMemorySecureStore : SecureStore {
    private val values = HashMap<String, String>()

    @Synchronized
    override fun getString(key: String): String? = values[key]

    @Synchronized
    override fun putAll(values: Map<String, String?>) {
        for ((key, value) in values) {
            if (value == null) this.values.remove(key) else this.values[key] = value
        }
    }
}

class FakeDeviceIdProvider(
    override val deviceId: String = "device-123",
    override val deviceName: String = "Google Pixel 9 (Android 17)",
) : DeviceIdProvider
