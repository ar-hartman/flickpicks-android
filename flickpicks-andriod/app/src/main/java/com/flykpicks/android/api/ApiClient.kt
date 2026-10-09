package com.flykpicks.android.api

import com.flykpicks.android.api.json.LenientListJsonAdapter
import com.flykpicks.android.api.json.MediaItemJsonAdapter
import com.flykpicks.android.model.profile.PublicNameSource
import com.flykpicks.android.store.TokenStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.EnumJsonAdapter
import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builders for the networking stack. Kept free of Hilt so tests can assemble the exact same
 * stack against a MockWebServer; `di/NetworkModule` wires these up for the app.
 */
object ApiClient {

    /**
     * Wire format notes (mirrors the iOS `JSONDecoder` defaults): keys map 1:1 (no snake-case
     * conversion — DTOs map each key explicitly), dates stay ISO-8601 strings, and null fields
     * are omitted when encoding (Swift's synthesized `encodeIfPresent`).
     */
    fun moshi(): Moshi = Moshi.Builder()
        .add(LenientListJsonAdapter.FACTORY)
        .add(MediaItemJsonAdapter.FACTORY)
        .add(
            PublicNameSource::class.java,
            EnumJsonAdapter.create(PublicNameSource::class.java)
                .withUnknownFallback(PublicNameSource.DISPLAY_NAME)
                // Nullable properties (e.g. UpdateProfileRequest) must encode as omitted, not crash.
                .nullSafe(),
        )
        .build()

    /** Shared connection pool and timeouts; every other client derives from this one. */
    fun baseClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Client for API calls. Pass [tokenStore] + [authenticator] for the main client; omit both
     * for the refresh client (which must never recurse into refresh itself).
     */
    fun apiClient(
        base: OkHttpClient,
        telemetry: TelemetrySession,
        tokenStore: TokenStore? = null,
        authenticator: Authenticator? = null,
        debugLogging: Boolean = false,
    ): OkHttpClient = base.newBuilder().apply {
        addInterceptor(TelemetryInterceptor(telemetry))
        tokenStore?.let { addInterceptor(AuthInterceptor(it)) }
        authenticator?.let(::authenticator)
        if (debugLogging) {
            addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader(AUTHORIZATION_HEADER)
                },
            )
        }
    }.build()

    /**
     * Client for image loading (Coil). The bearer token goes only to [apiBaseUrl]'s origin, since
     * a `posterUrl` may point at a CDN; a 401 there refreshes like any API call. No telemetry
     * headers: iOS sends none with images, and the API skips them for posters anyway.
     */
    fun imageClient(
        base: OkHttpClient,
        tokenStore: TokenStore,
        authenticator: Authenticator,
        apiBaseUrl: HttpUrl,
    ): OkHttpClient = base.newBuilder()
        .addInterceptor(AuthInterceptor(tokenStore, onlyOrigin = apiBaseUrl))
        .authenticator(authenticator)
        .build()

    /** Pass [errorMapper] to make calls fail with [ApiError]s (all app APIs do). */
    fun retrofit(
        baseUrl: String,
        client: OkHttpClient,
        moshi: Moshi,
        errorMapper: ApiErrorMapper? = null,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .apply { errorMapper?.let { addCallAdapterFactory(ApiErrorCallAdapterFactory(it)) } }
        .build()
}
