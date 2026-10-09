package com.flykpicks.android.di

import android.content.Context
import coil3.ImageLoader
import coil3.network.NetworkFetcher
import coil3.network.okhttp.asNetworkClient
import com.flykpicks.android.AppConfig
import com.flykpicks.android.api.ApiClient
import com.flykpicks.android.api.TokenAuthenticator
import com.flykpicks.android.image.PosterNetworkClient
import com.flykpicks.android.store.TokenStore
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

/** Shares the base connection pool; adds the API-only bearer token and refresh. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ImageHttpClient

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {

    @Provides
    @Singleton
    @ImageHttpClient
    fun provideImageClient(
        @BaseHttpClient base: OkHttpClient,
        tokenStore: TokenStore,
        authenticator: TokenAuthenticator,
    ): OkHttpClient = ApiClient.imageClient(base, tokenStore, authenticator, AppConfig.apiBaseHttpUrl)

    /** The app's Coil singleton (`FlykPicksApp.newImageLoader`). Memory and disk caches are Coil's defaults. */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @ImageHttpClient client: Lazy<OkHttpClient>,
    ): ImageLoader = ImageLoader.Builder(context)
        .components {
            // Registered components run before Coil's service-loaded OkHttp fetcher, so every
            // http(s) image goes through this client.
            add(NetworkFetcher.Factory(networkClient = { PosterNetworkClient(client.get().asNetworkClient()) }))
        }
        .build()
}
