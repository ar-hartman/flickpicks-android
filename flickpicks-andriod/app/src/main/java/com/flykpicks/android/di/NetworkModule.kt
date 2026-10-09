package com.flykpicks.android.di

import com.flykpicks.android.AppConfig
import com.flykpicks.android.BuildConfig
import com.flykpicks.android.api.ApiClient
import com.flykpicks.android.api.ApiErrorMapper
import com.flykpicks.android.api.SessionEvents
import com.flykpicks.android.api.TelemetrySession
import com.flykpicks.android.api.TokenAuthenticator
import com.flykpicks.android.api.endpoints.RefreshApi
import com.flykpicks.android.store.TokenStore
import com.flykpicks.android.util.DeviceIdProvider
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.create
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

/** Shared connection pool / timeouts, no interceptors. Image loading (Phase 2) builds on it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseHttpClient

/** Telemetry only — used for token refresh so it can never recurse into the authenticator. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = ApiClient.moshi()

    @Provides
    @Singleton
    fun provideApiErrorMapper(moshi: Moshi, clock: Clock): ApiErrorMapper = ApiErrorMapper(moshi, clock)

    @Provides
    @Singleton
    @BaseHttpClient
    fun provideBaseClient(): OkHttpClient = ApiClient.baseClient()

    @Provides
    @Singleton
    @RefreshHttpClient
    fun provideRefreshClient(
        @BaseHttpClient base: OkHttpClient,
        telemetry: TelemetrySession,
    ): OkHttpClient = ApiClient.apiClient(base, telemetry, debugLogging = BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun provideRefreshApi(@RefreshHttpClient client: OkHttpClient, moshi: Moshi): RefreshApi =
        ApiClient.retrofit(AppConfig.apiBaseUrl, client, moshi).create()

    @Provides
    @Singleton
    fun provideTokenAuthenticator(
        tokenStore: TokenStore,
        deviceIdProvider: DeviceIdProvider,
        refreshApi: RefreshApi,
        sessionEvents: SessionEvents,
        moshi: Moshi,
    ): TokenAuthenticator = TokenAuthenticator(tokenStore, deviceIdProvider, refreshApi, sessionEvents, moshi)

    /** The client every app API uses: telemetry + bearer token + refresh on 401. */
    @Provides
    @Singleton
    fun provideApiClient(
        @BaseHttpClient base: OkHttpClient,
        telemetry: TelemetrySession,
        tokenStore: TokenStore,
        authenticator: TokenAuthenticator,
    ): OkHttpClient = ApiClient.apiClient(base, telemetry, tokenStore, authenticator, BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, moshi: Moshi, errorMapper: ApiErrorMapper): Retrofit =
        ApiClient.retrofit(AppConfig.apiBaseUrl, client, moshi, errorMapper)
}
