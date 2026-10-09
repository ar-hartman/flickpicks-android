package com.flykpicks.android.di

import com.flykpicks.android.api.endpoints.AddressApi
import com.flykpicks.android.api.endpoints.AuthApi
import com.flykpicks.android.api.endpoints.DisputeApi
import com.flykpicks.android.api.endpoints.FeatureFlagApi
import com.flykpicks.android.api.endpoints.GenreApi
import com.flykpicks.android.api.endpoints.InsightsApi
import com.flykpicks.android.api.endpoints.LibraryApi
import com.flykpicks.android.api.endpoints.MediaApi
import com.flykpicks.android.api.endpoints.MembershipApi
import com.flykpicks.android.api.endpoints.NotificationsApi
import com.flykpicks.android.api.endpoints.OrderApi
import com.flykpicks.android.api.endpoints.PaymentApi
import com.flykpicks.android.api.endpoints.ProfileApi
import com.flykpicks.android.api.endpoints.RecommendationsApi
import com.flykpicks.android.api.endpoints.SearchApi
import com.flykpicks.android.api.endpoints.SecurityApi
import com.flykpicks.android.api.endpoints.TelemetryApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

/** Retrofit endpoint interfaces (blueprint §5.4). Services are `@Inject`-constructed. */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    @Provides @Singleton fun authApi(retrofit: Retrofit): AuthApi = retrofit.create()
    @Provides @Singleton fun profileApi(retrofit: Retrofit): ProfileApi = retrofit.create()
    @Provides @Singleton fun securityApi(retrofit: Retrofit): SecurityApi = retrofit.create()
    @Provides @Singleton fun libraryApi(retrofit: Retrofit): LibraryApi = retrofit.create()
    @Provides @Singleton fun searchApi(retrofit: Retrofit): SearchApi = retrofit.create()
    @Provides @Singleton fun recommendationsApi(retrofit: Retrofit): RecommendationsApi = retrofit.create()
    @Provides @Singleton fun mediaApi(retrofit: Retrofit): MediaApi = retrofit.create()
    @Provides @Singleton fun genreApi(retrofit: Retrofit): GenreApi = retrofit.create()
    @Provides @Singleton fun orderApi(retrofit: Retrofit): OrderApi = retrofit.create()
    @Provides @Singleton fun paymentApi(retrofit: Retrofit): PaymentApi = retrofit.create()
    @Provides @Singleton fun disputeApi(retrofit: Retrofit): DisputeApi = retrofit.create()
    @Provides @Singleton fun membershipApi(retrofit: Retrofit): MembershipApi = retrofit.create()
    @Provides @Singleton fun addressApi(retrofit: Retrofit): AddressApi = retrofit.create()
    @Provides @Singleton fun notificationsApi(retrofit: Retrofit): NotificationsApi = retrofit.create()
    @Provides @Singleton fun featureFlagApi(retrofit: Retrofit): FeatureFlagApi = retrofit.create()
    @Provides @Singleton fun insightsApi(retrofit: Retrofit): InsightsApi = retrofit.create()
    @Provides @Singleton fun telemetryApi(retrofit: Retrofit): TelemetryApi = retrofit.create()
}
