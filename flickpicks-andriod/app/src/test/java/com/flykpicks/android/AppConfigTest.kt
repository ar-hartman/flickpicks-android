package com.flykpicks.android

import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {
    @Test
    fun apiBaseUrl_endsWithSlash_forRetrofit() {
        assertTrue(AppConfig.apiBaseUrl, AppConfig.apiBaseUrl.endsWith("/"))
    }

    @Test
    fun apiBaseUrl_isHttp() {
        assertTrue(AppConfig.apiBaseUrl, AppConfig.apiBaseUrl.startsWith("http"))
    }
}
