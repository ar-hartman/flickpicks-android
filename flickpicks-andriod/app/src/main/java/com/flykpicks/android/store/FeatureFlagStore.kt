package com.flykpicks.android.store

import android.content.Context
import android.util.Log
import com.flykpicks.android.model.profile.FeatureFlagKey
import com.flykpicks.android.service.FeatureFlagService
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Where the last fetched flags are kept between launches. */
interface FeatureFlagCache {
    fun read(): String?
    fun write(json: String)
}

class SharedPreferencesFeatureFlagCache @Inject constructor(@ApplicationContext context: Context) : FeatureFlagCache {
    private val prefs = context.getSharedPreferences("feature_flags", Context.MODE_PRIVATE)
    override fun read(): String? = prefs.getString(KEY, null)
    override fun write(json: String) = prefs.edit().putString(KEY, json).apply()

    private companion object {
        // iOS UserDefaults key.
        const val KEY = "cached_feature_flags"
    }
}

/**
 * Server feature flags (blueprint §9.6, iOS `FeatureFlagStore`). Starts from the cached copy, so
 * flags needed before sign-in (the Login screen's recovery link, plan Q4) use the last known
 * value. A flag missing from the map is off. A failed fetch keeps whatever is current.
 */
@Singleton
class FeatureFlagStore @Inject constructor(
    private val service: FeatureFlagService,
    private val cache: FeatureFlagCache,
    moshi: Moshi,
) {
    private val adapter = moshi.adapter<Map<String, Boolean>>(
        Types.newParameterizedType(Map::class.java, String::class.java, java.lang.Boolean::class.java),
    )

    private val _flags = MutableStateFlow(readCache())
    val flags: StateFlow<Map<String, Boolean>> = _flags.asStateFlow()

    fun isEnabled(key: String): Boolean = _flags.value[key] ?: false

    val accountRecoveryEnabled: Boolean get() = isEnabled(FeatureFlagKey.ACCOUNT_RECOVERY_ENABLED)

    suspend fun load() {
        try {
            val fetched = service.fetchFlags()
            _flags.value = fetched
            cache.write(adapter.toJson(fetched))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Feature flag fetch failed; keeping cached flags", e)
        }
    }

    private fun readCache(): Map<String, Boolean> =
        try {
            cache.read()?.let(adapter::fromJson).orEmpty()
        } catch (e: Exception) {
            emptyMap()
        }

    private companion object {
        const val TAG = "FeatureFlagStore"
    }
}
