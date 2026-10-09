package com.flykpicks.android

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Build-time configuration (blueprint §5.2). Values come from `local.properties` or CI
 * environment variables; see app/build.gradle.kts.
 */
object AppConfig {
    val apiBaseUrl: String = BuildConfig.API_BASE_URL
    val apiBaseHttpUrl: HttpUrl = apiBaseUrl.toHttpUrl()
    val stripePublishableKey: String = BuildConfig.STRIPE_PUBLISHABLE_KEY
}
